package eu.h2020.helios_social.modules.socialgraphmining.diffusion;

import eu.h2020.helios_social.core.contextualegonetwork.Context;
import eu.h2020.helios_social.core.contextualegonetwork.ContextualEgoNetwork;
import eu.h2020.helios_social.core.contextualegonetwork.Edge;
import eu.h2020.helios_social.core.contextualegonetwork.Interaction;
import eu.h2020.helios_social.core.contextualegonetwork.Node;
import eu.h2020.helios_social.core.contextualegonetwork.Utils;
import eu.h2020.helios_social.modules.socialgraphmining.SocialGraphMiner;

import mklab.JGNN.core.Tensor;

/**
 * This class implements a Personalized PageRank scheme, where each ego node's personalization
 * is a vector (modeled by a JGNN library Tensor) passed to the constructor and which is smoothed over
 * the decentralized social graph. Smoothing outcome for contexts is obtained through the method
 * {@link #getSmoothedPersonalization(Context)}. The miner predicts new interactions based on the cosine
 * similarity of smoothed attributes.
 * 
 * @author Emmanouil Krasanakis
 */
public class PPRMiner extends SocialGraphMiner {
	private String name;
	private double restartProbability = 0.1;
	private boolean personalizationAsGroundTruth = false;
	private Tensor defaultPersonalization;
	
	public String getName() {
		return name;
	}
	
	public PPRMiner(String name, ContextualEgoNetwork contextualEgoNetwork, Tensor personalization) {
		super(contextualEgoNetwork);
		if(name==null || name.isEmpty())
			Utils.error(new IllegalArgumentException());
		this.name = name;
		updatePersonalization(personalization);
	}
	
	/**
	 * Retrieves the restart probability of the equivalent random walk with restart scheme associated
	 * with personalized PageRank.
	 * @return The restart probability.
	 * @see #setRestartProbability(double)
	 */
	public synchronized double getRestartProbability() {
		return restartProbability;
	}
	
	/**
	 * Sets whether personalization should be considered as ground truth. If this is true and the personalization
	 * vector has a non-zero norm (i.e. has at least one non-zero element), then the outcome
	 * of {@link #getSmoothedPersonalization()} is forcefully snapped to the personalization vector. Making this
	 * depend on the norm helps deployment of models.
	 * @param personalizationAsGroundTruth A boolean value on whether personalization should be considered ground truth.
	 * @return <code>this</code> miner's instance.
	 */
	public synchronized PPRMiner setPersonalizationAsGroundTruth(boolean personalizationAsGroundTruth) {
		this.personalizationAsGroundTruth = personalizationAsGroundTruth;
		return this;
	}
	
	/**
	 * Sets the restart probability of the personalized PageRank scheme. Smaller values induce
	 * broader diffusion of predictions, i.e. many hops away in the social graph. The equivalent
	 * random walk with restart scheme has average random walk length equal to 1/(restart probability).
	 * <br>
	 * Suggested values to experiment with:<br>
	 * - 0.15 (used in older personalized PageRank papers)<br>
	 * - 0.10 (default, used by graph neural networks to great success)<br>
	 * - 0.01 (extremely long walks, suitable to detect communities of high radius from few examples)<br>
	 * @param restartProbability The restart probability in the range (0,1) (default is 0.1).
	 * @return <code>this</code> miner's instance.
	 */
	public synchronized PPRMiner setRestartProbability(double restartProbability) {
		if(restartProbability<=0 || restartProbability>=1)
			Utils.error("Restart probabilty should be in the open range (0,1)");
		this.restartProbability = restartProbability;
		return this;
	}

	/**
	 * Copies the elements of a given vector to the ego's personalization vector in the provided context.
	 * @param context The context in which to set the new personalization vector.
	 * @param personalization The tensor to set as new personalization vector.
	 * @return <code>this</code> miner's instance.
	 * @see #updatePersonalization(Tensor)
	 */
	public synchronized PPRMiner updatePersonalization(Context context, Tensor personalization) {
		context
			.getOrCreateInstance(getModuleName()+"personalization", ()->personalization.zeroCopy())
			.setToZero()
			.selfAdd(personalization);
		updateSmoothedPersonalization(context);
		return this;
	}
	
	/**
	 * Wraps the method {@link #updatePersonalization(Context, Tensor)} to update <b>all</b>
	 * contexts with the given personalization. The given personalization is also automatically
	 * initialized for <b>future</b> context generations.
	 * @param personalization The tensor to set as new personalization vector in all contexts.
	 * @return <code>this</code> miner's instance.
	 */
	public PPRMiner updatePersonalization(Tensor personalization) {
		defaultPersonalization = personalization;
		for(Context context : getContextualEgoNetwork().getContexts())
			updatePersonalization(context, personalization);
		return this;
	}
	
	/**
	 * Retrieves the module's name used as prefix to identifiers for the {@link Node#getOrCreateInstance(String, Class)} 
	 * methods when retrieving data attached to nodes.
	 * @return The module name's prefix.
	 */
	protected String getModuleName() {
		return getClass().getCanonicalName()+"#"+name+".";
	}	
	
	/**
	 * Retrieves the ego node's personalization set for the <b>specific</b> context.
	 * Changing the output tensor is equivalent to changing the personalization.
	 * @return The personalization vector.
	 */
	public Tensor getPersonalization(Context context) {
		return context.getOrCreateInstance(getModuleName()+"personalization", () -> defaultPersonalization.copy());
	}
	
	/**
	 * Retrieves the outcome of smoothing the outcome of the ego's personalization through the social graph.
	 * Changing the output tensor temporarilly affects the personalization.
	 * @return A Tensor holding a smoothing of the personalization.
	 */
	public synchronized Tensor getSmoothedPersonalization(Context context) {
		return context.getOrCreateInstance(getModuleName()+"score", () -> getPersonalization(context).copy());
	}

	/**
	 * Retrieves the outcome of {@link #getSmoothedPersonalization()} and postprocesses it so that its minimum value is zero
	 * and its elements sum to 1.
	 * @return A Tensor holding a normalized version of the smoothed personalization.
	 */
	public synchronized Tensor getNormalizedSmoothedPersonalization(Context context) {
		Tensor ret = getSmoothedPersonalization(context);
		double min = ret.min();
		if(min==ret.max())
			min = 0;// if it's a uniform distribution return a uniform distribution
		return ret.add(-min).setToProbability();
	}
	
	@Override
	public synchronized void newInteractionParameters(Interaction interaction, SocialGraphMinerParameters neighborModelParameters, InteractionType interactionType) {
		if(interaction.getEdge().getAlter()==null)
			return;
		Tensor neighborScore = (Tensor) neighborModelParameters.get("score");
		interaction
			.getEdge()
			.getOrCreateInstance(getModuleName()+"score", ()->neighborScore.zeroCopy())
			.setToZero()
			.selfAdd(neighborScore);
		updateSmoothedPersonalization(interaction.getEdge().getContext());
	}
	
	protected void updateSmoothedPersonalization(Context context) {
		int numNodes = context.getNodes().size();
		if(numNodes!=0 && (!personalizationAsGroundTruth || getPersonalization(context).norm()==0)) {
			Tensor score = getSmoothedPersonalization(context)
					.setToZero()
					.selfAdd(getPersonalization(context))
					.selfMultiply(numNodes*restartProbability/(1-restartProbability));
			for(Edge edge : context.getEdges()) 
				if(edge.getEgo()!=null)
					score.selfAdd(edge.getOrCreateInstance(getModuleName()+"score", ()->score.zeroCopy()));
			score.selfMultiply((1-restartProbability)/numNodes);
		}
		else
			getSmoothedPersonalization(context)
				.setToZero()
				.selfAdd(getPersonalization(context));
	}

	@Override
	public SocialGraphMinerParameters constructModelParameterObject(Interaction interaction) {
		SocialGraphMinerParameters params = new SocialGraphMinerParameters();
		params.put("score", getSmoothedPersonalization(interaction.getEdge().getContext()));
		return params;
	}

	@Override
	public double predictNewInteraction(Context context, Node destinationNode) {
		Edge edge = context.getEdge(context.getContextualEgoNetwork().getEgo(), destinationNode);
		if(edge==null)
			return 0;
		return getSmoothedPersonalization(context)
				.dot(edge.getOrCreateInstance(getModuleName()+"score", ()->defaultPersonalization.zeroCopy()).normalized());
		//throw new RuntimeException("PPRMiner is not meant to predict interactions");
	}

}
