package eu.h2020.helios_social.modules.socialgraphmining.GNN;

import java.util.ArrayList;
import java.util.HashMap;

import eu.h2020.helios_social.core.contextualegonetwork.Context;
import eu.h2020.helios_social.core.contextualegonetwork.ContextualEgoNetwork;
import eu.h2020.helios_social.core.contextualegonetwork.Edge;
import eu.h2020.helios_social.core.contextualegonetwork.Interaction;
import eu.h2020.helios_social.core.contextualegonetwork.Node;
import eu.h2020.helios_social.core.contextualegonetwork.Utils;
import eu.h2020.helios_social.modules.socialgraphmining.SocialGraphMiner;
import eu.h2020.helios_social.modules.socialgraphmining.GNN.simulated_communication.EmbeddingExchangeProtocol;
import mklab.JGNN.core.Tensor;
import mklab.JGNN.core.util.Loss;
import mklab.JGNN.core.util.Sort;

/**
 * This class provides an implementation of a {@link SocialGraphMiner} based on
 * a Graph Neural Network (GNN) architecture. Its parameters can be adjusted using a number of
 * setter methods.
 * 
 * @author Emmanouil Krasanakis
 */
public class GNNMiner extends SocialGraphMiner {
	private double learningRate = 1;
	private double learningRateDegradation = 0.95;
	private double regularizationWeight = 0.1;
	private double regularizationAbsorbsion = 1;
	private int maxEpoch = 1000;
	private double convergenceRelativeLoss = 0.001;
	private double trainingExampleDegradation = 0.5;
	private double trainingExampleRemovalThreshold = 0.1;
	private double egoDeniability = 0;
	//private double neighborDeniability = 0;
	private double incommingEdgeLearningRateMultiplier = 0;
	private double outgoingEdgeLearningRateMultiplier = 0;
	private double updateEgoEmbeddingsFromNeighbors = 0;
	private boolean enableTrainingExamplePropagation = false;
	private boolean enableSpectralAlignment = false;
	private boolean secondOrderProximity = false;
	private EmbeddingExchangeProtocol embeddingExchangeProtocol = null;
	/*
	private static HashMap<String, Tensor> globalEmbeddingRegistry = new HashMap<String, Tensor>();//if instantiated by default, it simulates constant federated communication
	private static HashMap<String, HashMap<String, Tensor>> federatedAveraging = new HashMap<String, HashMap<String, Tensor>>();
	private static HashMap<String, HashMap<String, Double>> federatedAveragingTimes = new HashMap<String, HashMap<String, Double>>();
	private static double current_interaction = 0;
	private static double globalEmbeddingRegistryChance = 1;*/
	
	/**
	 * Instantiates a {@link GNNMiner} on a given contextual ego network.
	 * @param contextualEgoNetwork The contextual ego network on which the miner runs and stores information.
	 */
	public GNNMiner(ContextualEgoNetwork contextualEgoNetwork) {
		super(contextualEgoNetwork);
	}
	
	/***
	 * The learning rate (default is 1) from which GNNMiner training starts. Training restarts
	 * on each {@link #newInteraction} from this value and can be potentially be adapted by
	 * {@link #setLearningRateDegradation} over training epochs.
	 * @param learningRate The learning rate to restart from. Should be positive.
	 * @return <code>this</code> GNNMiner instance.
	 * @see GNNNodeData#setLearningRate(double)
	 */
	public GNNMiner setLearningRate(double learningRate) {
		if(!Double.isFinite(learningRate) || learningRate<=0)
			Utils.error(new IllegalArgumentException("Learning rate "+learningRate+" should be positive."));
		this.learningRate = learningRate;
		return this;
	}
	
	public GNNMiner setEmbeddingExchangeProtocol(EmbeddingExchangeProtocol embeddingExchangeProtocol) {
		this.embeddingExchangeProtocol = embeddingExchangeProtocol;
		return this;
	}
	
	public GNNMiner setEdgePointsLearningMultiplier(double incomming, double outgoing) {
		incommingEdgeLearningRateMultiplier = incomming;
		outgoingEdgeLearningRateMultiplier = outgoing;
		return this;
	}

	/**
	 * Performs a fixed degradation of the learning rate over training epochs by multiplying the latter
	 * with a given factor (default is 0.95) after each epoch.
	 * @param learningRateDegradation The rate at which learning rate degrade. Should lie in the range (0,1].
	 * @return <code>this</code> GNNMiner instance.
	 * @see #setLearningRate(double)
	 */
	public GNNMiner setLearningRateDegradation(double learningRateDegradation) {
		if(!Double.isFinite(learningRateDegradation) || learningRateDegradation<=0 || learningRateDegradation>1)
			Utils.error(new IllegalArgumentException("Learning rate degradation "+learningRateDegradation+" should be in the range (0,1]"));
		this.learningRateDegradation = learningRateDegradation;
		return this;
	}
	
	/**
	 * The regularization weight (default 0.1) to apply during training of the GNNMiner.
	 * This weight ensures that training converges around given areas of the embedding space.
	 * Value of 0 disables regularization altogether, but does not guarantee convergence 
	 * (prefer setting setRegularizationAbsorbsion(0) to disable regularization propagation instead).
	 * @param regularizationWeight The regularization weight to set.
	 * @return <code>this</code> GNNMiner instance.
	 * @see GNNNodeData#setRegularizationWeight(double)
	 * @see #setRegularizationAbsorbsion(double)
	 */
	public GNNMiner setRegularizationWeight(double regularizationWeight) {
		this.regularizationWeight = regularizationWeight;
		return this;
	}
	

	/**
	 * Multiplies regularization tensors with this value before setting them as regularization;
	 * value of 1 (default) produces regularization of calculated alter embeddings towards the
	 * embeddings calculated on alter devices. Value of 0 produce regularization towards zero,
	 * which effectively limits the embedding norm to the approximate order of magnitude
	 * 1/weight, where weight is the value set to {@link #setRegularizationWeight(double)}.
	 * @param regularizationAbsorbsion A value in the range [0,1].
	 * @return <code>this</code> GNNMiner instance.
	 */
	public GNNMiner setRegularizationAbsorbsion(double regularizationAbsorbsion) {
		if(!Double.isFinite(regularizationAbsorbsion) || regularizationAbsorbsion<0 || regularizationAbsorbsion>1)
			Utils.error(new IllegalArgumentException("Regularization absorbition "+regularizationAbsorbsion+" should lie in the range [0,1]"));
		this.regularizationAbsorbsion = regularizationAbsorbsion;
		return this;
	}
	
	/**
	 * Limits the number of training epochs (default is 1000) over which to
	 * train the GNNMiner.
	 * @param maxEpoch The maximum epoch at which to train.
	 * @return <code>this</code> GNNMiner instance.
	 */
	public GNNMiner setMaxTrainingEpoch(int maxEpoch) {
		this.maxEpoch = maxEpoch;
		return this;
	}
	
	/**
	 * When the GNNMiner is being trained, training stops at epochs where
	 * abs(previous epoch loss - this epoch loss) &lt; convergenceRelativeLoss*(this epoch loss)
	 * where losses are weighted cross entropy ones. Default is 0.001.
	 * @param convergenceRelativeLoss The relative loss at which to stop training.
	 * @return <code>this</code> GNNMiner instance.
	 */
	public GNNMiner setMinTrainingRelativeLoss(double convergenceRelativeLoss) {
		this.convergenceRelativeLoss = convergenceRelativeLoss;
		return this;
	}
	
	/**
	 * Degrades example weights each time a new one is generated through {@link #newInteraction} by calling
	 * {@link ContextTrainingExampleData#degrade} to multiply previous weights with the given degradation factor
	 * (default is 0.5).
	 * @param trainingExampleDegradation The factor with which to multiply each 8previous example weight
	 * @return <code>this</code> GNNMiner instance.
	 * @see #setTrainingExampleRemovalThreshold(double)
	 */
	public GNNMiner setTrainingExampleDegradation(double trainingExampleDegradation) {
		this.trainingExampleDegradation = trainingExampleDegradation;
		return this;
	}
	
	/**
	 * Sets the threshold weight at which old training examples are removed (default is 0.1).
	 * Basically, if the degradation set by set by {@link #setTrainingExampleDegradation} 
	 * remains constant throughout training iterations, training examples are removed if
	 * degradation^n &lt; trainingExampleRemovalThreshold
	 * where n the number of (positive) examples provided after the examined one with {@link #newInteraction}.
	 * 
	 * @param trainingExampleRemovalThreshold The weight threshold at which to remove GNN training examples which is passed as
	 * 	the second argument to {@link ContextTrainingExampleData#degrade} calls.
	 * @return <code>this</code> GNNMiner instance.
	 */
	public GNNMiner setTrainingExampleRemovalThreshold(double trainingExampleRemovalThreshold) {
		this.trainingExampleRemovalThreshold = trainingExampleRemovalThreshold;
		return this;
	}
	
	/**
	 * Enables plausible deniability and differential privacy handling by permuting the ego and its alter's parameters
	 * with a random noise proportional to a given constant and their norm. Zero values (default) ensure no privacy concerns
	 * but more exact  computations <i>for other</i> devices. The user's device would perform predictions depending on the
	 * privacy settings of their alters.
	 * 
	 * @param plausibleDeniability The permutation of the ego's parameters.
	 * @param differentialPrivacy The permutation of the neighbor's parameters.
	 * @return <code>this</code> GNNMiner instance.
	 */
	public GNNMiner setDeniability(double plausibleDeniability, double differentialPrivacy) {
		this.egoDeniability = plausibleDeniability;
		//this.neighborDeniability = differentialPrivacy;
		return this;
	}
	
	/**
	 * Sets whether to propagate training examples to communicating devices (this behavior is de-activeated by default).
	 * Propagated examples contribute to the training of the neighbors. <b>This partially violates the device's
	 * and all of its neighbors' privacy</b> regardless of what values are set with {@link #setDeniability(double, double)}.
	 * 
	 * @param enableTrainingExamplePropagation Whether to propagate training examples or not.
	 * @return <code>this</code> GNNMiner instance.
	 */
	public GNNMiner setTrainingExamplePropagation(boolean enableTrainingExamplePropagation) {
		this.enableTrainingExamplePropagation = enableTrainingExamplePropagation;
		return this;
	}
	
	/**
	 * Faster convergence to more robust embeddings of evolving user preferences by trying to align
	 * the dimensions of received embeddings towards with their locally understood spectral representation (default
	 * is false).
	 * 
	 * @param enableSpectralAlignment Whether to enable spectral alignment of learned embeddings between devices.
	 * @return <code>this</code> GNNMiner instance.
	 */
	public GNNMiner setSpectralAlignment(boolean enableSpectralAlignment) {
		this.enableSpectralAlignment = enableSpectralAlignment;
		return this;
	}
	
	protected Tensor transformLike(Tensor source, Tensor exampleSource, Tensor exampleTarget) {
		if(!enableSpectralAlignment)
			return source;
		int[] sourceIndex = Sort.sortedIndexes(exampleSource.toArray());
		int[] targetIndex = Sort.sortedIndexes(exampleSource.toArray());
		Tensor target = source.zeroCopy();
		for(int i=0;i<sourceIndex.length;i++)
			target.put(targetIndex[i], source.get(sourceIndex[i]));
		return target;
	}
	
	@Override
	public synchronized void newInteractionParameters(Interaction interaction, SocialGraphMinerParameters params, InteractionType interactionType) {
		if(interaction.getEdge().getEgo()==null || interactionType==InteractionType.SEND)
			return;
		Edge edge = interaction.getEdge();
		Node ego = edge.getEgo();
		Node alter = edge.getAlter();
		Context context = edge.getContext();
		if(updateEgoEmbeddingsFromNeighbors!=0 && params!=null)
			ego.getOrCreateInstance(GNNNodeData.class).getEmbedding()
					.selfMultiply(1-updateEgoEmbeddingsFromNeighbors)
					.selfAdd( ((Tensor)params.get("ego_embedding")).multiply(updateEgoEmbeddingsFromNeighbors) );
		
		if(embeddingExchangeProtocol!=null && params!=null)
			for(Node node : interaction.getEdge().getContext().getNodes())  {
				Tensor embedding = embeddingExchangeProtocol.requestEmbeddings(ego, node);
				if(embedding!=null && node!=ego) {
					node.getOrCreateInstance(GNNNodeData.class).forceSetEmbedding(embedding);
					node.getOrCreateInstance(GNNNodeData.class).setRegularization(embedding.multiply(regularizationAbsorbsion));
				}
			}
			
		/*
		if(globalEmbeddingRegistry!=null && Math.random()<globalEmbeddingRegistryChance)
			for(Node node : interaction.getEdge().getContext().getNodes()) {
				Tensor embedding = globalEmbeddingRegistry.get(node.getId());
				if(embedding!=null) {
					node.getOrCreateInstance(GNNNodeData.class).forceSetEmbedding(embedding.add(0));
					//node.getOrCreateInstance(GNNNodeData.class).setRegularization(embedding.add(0));//worsens results
				}
			}
		*/
		
		if(params!=null) {
			Tensor alterTensor = transformLike((Tensor)params.get("ego_embedding"),
					    ((Tensor)params.get("ego_embedding"))
					    			.add((Tensor)params.get("alter_embedding")), 
						ego.getOrCreateInstance(GNNNodeData.class).getEmbedding()
									.add(edge.getAlter().getOrCreateInstance(GNNNodeData.class).getEmbedding()));
			if(params.get("packed_examples")!=null)
				unpackExamples((String)params.get("packed_examples"), edge.getContext());
			
			alter.getOrCreateInstance(GNNNodeData.class).forceSetEmbedding(alterTensor);
			alter.getOrCreateInstance(GNNNodeData.class).setRegularization(alterTensor.multiply(regularizationAbsorbsion));
		}
		
		ContextTrainingExampleData trainingExampleData = context.getOrCreateInstance(ContextTrainingExampleData.class);

		if(trainingExampleData.transformToDstEmbedding==null) 
			trainingExampleData.transformToDstEmbedding = edge.getDst().getOrCreateInstance(GNNNodeData.class).getEmbedding().zeroCopy().setToOnes();
		if(trainingExampleData.transformToSrcEmbedding==null) 
			trainingExampleData.transformToSrcEmbedding = edge.getSrc().getOrCreateInstance(GNNNodeData.class).getEmbedding().zeroCopy().setToOnes();
		if(params.get("src_embedding") != null && params!=null)
			trainingExampleData.transformToSrcEmbedding.selfMultiply(0.5).selfAdd(((Tensor)params.get("src_embedding")).multiply(0.5));
		if(params.get("dst_embedding") != null && params!=null)
			trainingExampleData.transformToDstEmbedding.selfMultiply(0.5).selfAdd(((Tensor)params.get("dst_embedding")).multiply(0.5));
		
		//if(trainingExampleDegradation!=1)
		trainingExampleData.degrade(trainingExampleDegradation, trainingExampleRemovalThreshold);
		
		// create the positive training example
		trainingExampleData.addTrainingExample(new TrainingExample(edge.getSrc(), edge.getDst(), 1));
		// create two negative training examples
		if(context.getNodes().size()>2) {
				Node negativeNode = ego;
				ArrayList<Node> nodes = new ArrayList<Node>(); 
				/*if(globalEmbeddingRegistry!=null) {
					for(String nodeId : globalEmbeddingRegistry.keySet())
						if(!edge.getContext().getNodes().contains(edge.getContextualEgoNetwork().getOrCreateNode(nodeId, null)))
							nodes.add(edge.getContextualEgoNetwork().getOrCreateNode(nodeId, null));
				}
				else*/
				nodes = edge.getContext().getNodes();
				while(negativeNode==edge.getSrc() || negativeNode==edge.getDst()) 
					negativeNode = nodes.get((int)(Math.random()*nodes.size()));
				trainingExampleData.addTrainingExample(new TrainingExample(edge.getSrc(), negativeNode, 0));
				trainingExampleData.addTrainingExample(new TrainingExample(negativeNode, edge.getDst(), 0));
			}
		train(trainingExampleData);
		
		// send parameters to embedding exchange protocol
		if(embeddingExchangeProtocol!=null)
			for(Node node : context.getNodes()) 
				embeddingExchangeProtocol.registerEmbeddings(ego, node, node.getOrCreateInstance(GNNNodeData.class).getEmbedding());
	
		// train LSTM (carefull to do this after embedding exhanges to not affect which parameters are exchanges)
		for(Node node : context.getNodes())
			node.getOrCreateInstance(GNNNodeData.class).addEmbeddingToHistory();
		
	}
	
	protected Tensor aggregateNeighborEmbeddings(Context context) {
		Node egoNode = context.getContextualEgoNetwork().getEgo();
		Tensor ret = getContextualEgoNetwork().getEgo().getOrCreateInstance(GNNNodeData.class).getEmbedding().zeroCopy();
		//double totalWeight = 0;
		for(TrainingExample trainingExample : context.getOrCreateInstance(ContextTrainingExampleData.class).getTrainingExampleList()) {
			if(trainingExample.getSrc()==egoNode) {
				ret.selfAdd(trainingExample.getDst().getOrCreateInstance(GNNNodeData.class)
						.getEmbedding()
						.multiply(trainingExample.getWeight()*(trainingExample.getLabel()-0.5))
						);
				/*ret.selfAdd(trainingExample.getDst().getOrCreateInstance(GNNNodeData.class)
						.getNeighborAggregation()
						.multiply(-trainingExample.getWeight()*(trainingExample.getLabel()-0.5))
						);*/
				//totalWeight += trainingExample.getWeight();
			}
			if(trainingExample.getDst()==egoNode) {
				ret.selfAdd(trainingExample.getSrc().getOrCreateInstance(GNNNodeData.class)
						.getEmbedding()
						.multiply(trainingExample.getWeight())
						.multiply(trainingExample.getWeight()*(trainingExample.getLabel()-0.5))
						);
				/*ret.selfAdd(trainingExample.getSrc().getOrCreateInstance(GNNNodeData.class)
						.getNeighborAggregation()
						.multiply(trainingExample.getWeight())
						.multiply(-trainingExample.getWeight()*(trainingExample.getLabel()-0.5))
						);*/
				//totalWeight += trainingExample.getWeight();
			}
		}
		//if(totalWeight!=0)
		//	ret = ret.multiply(1./totalWeight);
		return ret.setToNormalized();
	}
	
	public double getConfidence(Context context) {
		/*ContextTrainingExampleData trainingExampleData = context.getOrCreateInstance(ContextTrainingExampleData.class);
		double sumWeights = 0;
		for(TrainingExample trainingExample : trainingExampleData.getTrainingExampleList())
			sumWeights += trainingExample.getWeight();
		return sumWeights;*/
		return 1;
	}
	
	protected Tensor permute(Tensor tensor, double permutation) {
		if(permutation==0)
			return tensor;
		return tensor
				.multiply(1-permutation)
				.selfAdd(tensor.zeroCopy().setToRandom().multiply(tensor.norm()*permutation));
	}
	
	private void unpackExamples(String packedExamples, Context context) {
		ContextTrainingExampleData trainingExampleData = context.getOrCreateInstance(ContextTrainingExampleData.class);
		for(String unpacked : packedExamples.split("\\]"))
			if(unpacked.length()>1)
				trainingExampleData.addTrainingExample(new TrainingExample(context.getContextualEgoNetwork(), unpacked.substring(1)));
	}
	
	private String packExamples(Context context) {
		ContextTrainingExampleData trainingExampleData = context.getOrCreateInstance(ContextTrainingExampleData.class);
		String ret = "[]";
		if(enableTrainingExamplePropagation)
			for(TrainingExample example : trainingExampleData.getTrainingExampleList())
				ret += "["+example.toString()+"]";
		return ret;
	}
	
	@Override
	public SocialGraphMinerParameters constructModelParameterObject(Interaction interaction) {
		if(interaction==null) 
			return Utils.error("Could not find given context", null);
		Context context = interaction.getEdge().getContext();
		SocialGraphMinerParameters ret = new SocialGraphMinerParameters();
		ret.put("ego_embedding", permute(interaction.getEdge().getEgo().getOrCreateInstance(GNNNodeData.class).getEmbedding(), egoDeniability));
		ret.put("alter_embedding", permute(interaction.getEdge().getAlter().getOrCreateInstance(GNNNodeData.class).getEmbedding(), egoDeniability));
		ret.put("confidence", (Double)getConfidence(context));
		if(enableTrainingExamplePropagation)
			ret.put("packed_examples", packExamples(context));
		ret.put("src_embedding", context.getOrCreateInstance(ContextTrainingExampleData.class).transformToSrcEmbedding);
		ret.put("dst_embedding", context.getOrCreateInstance(ContextTrainingExampleData.class).transformToDstEmbedding);
		//if(context.getOrCreateInstance(ContextTrainingExampleData.class).lstm!=null)
		//	ret.put("LSTM", context.getOrCreateInstance(ContextTrainingExampleData.class).lstm);
		/*return permute(interaction.getEdge().getEgo().getOrCreateInstance(GNNNodeData.class).getEmbedding(), egoDeniability).toString()+";"
			 + permute(aggregateNeighborEmbeddings(context), neighborDeniability).toString()+";"
			 + permute(interaction.getEdge().getAlter().getOrCreateInstance(GNNNodeData.class).getEmbedding(), egoDeniability).toString()+";"
			 + getConfidence(context)+";"
			 + packExamples(context)+";"
			 + packLSTM(context)+";";*/
		return ret;
	}
	
	protected void train(ContextTrainingExampleData trainingExampleData) {
		double learningRate = this.learningRate;
		double previousLoss = -1;
		for(int epoch=0;epoch<maxEpoch;epoch++) {
			double loss = trainEpoch(trainingExampleData, learningRate);
			learningRate *= this.learningRateDegradation;
			if(Math.abs(previousLoss-loss)<convergenceRelativeLoss*loss)
				break;
			previousLoss = loss;
		}
	}
	
	protected double trainEpoch(ContextTrainingExampleData trainingExampleData, double learningRate) {
		Tensor zero = getContextualEgoNetwork().getEgo().getOrCreateInstance(GNNNodeData.class).getEmbedding().zeroCopy();
		HashMap<Node, Tensor> derivatives = new HashMap<Node, Tensor>();
		HashMap<Node, Double> totalWeights = new HashMap<Node, Double>();
		Tensor transformToSrcEmbeddingDerivative = zero.zeroCopy();
		Tensor transformToDstEmbeddingDerivative = zero.zeroCopy();
		double transformToSrcEmbeddingDerivativeWeight = 0;
		double transformToDstEmbeddingDerivativeWeight = 0;
		double loss = 0;
		for(TrainingExample trainingExample : trainingExampleData.getTrainingExampleList()) {
			Node u = trainingExample.getSrc();
			Node v = trainingExample.getDst();
			if(secondOrderProximity) {
				Tensor embedding_u = u.getOrCreateInstance(GNNNodeData.class).getEmbedding().multiply(trainingExampleData.transformToSrcEmbedding);
				Tensor embedding_v = v.getOrCreateInstance(GNNNodeData.class).getEmbedding().multiply(trainingExampleData.transformToDstEmbedding);
				Tensor secondOrder_u = u.getOrCreateInstance(GNNNodeData.class).getNeighborAggregation().multiply(trainingExampleData.transformToSrcEmbedding);
				Tensor secondOrder_v = v.getOrCreateInstance(GNNNodeData.class).getNeighborAggregation().multiply(trainingExampleData.transformToSrcEmbedding);
				
				double firstOrderActivation = embedding_u.dot(embedding_v);
				
				totalWeights.put(u, totalWeights.getOrDefault(u, 0.)+trainingExample.getWeight());
				totalWeights.put(v, totalWeights.getOrDefault(v, 0.)+trainingExample.getWeight());
				
				transformToSrcEmbeddingDerivativeWeight += trainingExample.getWeight();
				transformToDstEmbeddingDerivativeWeight += trainingExample.getWeight();
				
				double weight = trainingExample.getWeight();
				double secondOrderActivation_u = embedding_u.dot(secondOrder_v);
				double secondOrderActivation_v = secondOrder_u.dot(embedding_v);
				derivatives.put(u, secondOrder_v
									.multiply(trainingExampleData.transformToSrcEmbedding)
									.selfMultiply(weight*Loss.sigmoid(secondOrderActivation_u)*Loss.crossEntropySigmoidDerivative(firstOrderActivation, trainingExample.getLabel()))
									.selfAdd(derivatives.getOrDefault(u, zero)));
				derivatives.put(v, secondOrder_u
									.multiply(trainingExampleData.transformToDstEmbedding)
									.selfMultiply(weight*Loss.sigmoid(secondOrderActivation_v)*Loss.crossEntropySigmoidDerivative(firstOrderActivation, trainingExample.getLabel()))
									.selfAdd(derivatives.getOrDefault(v, zero)));
				
				weight *= Loss.sigmoid(secondOrderActivation_u)*Loss.sigmoid(secondOrderActivation_v);
				double crossEntropyDerivative = weight*Loss.crossEntropySigmoidDerivative(embedding_u.dot(embedding_v), trainingExample.getLabel());
				derivatives.put(u, embedding_v
									.multiply(trainingExampleData.transformToSrcEmbedding)
									.selfMultiply(weight*crossEntropyDerivative)
									.selfAdd(derivatives.getOrDefault(u, zero)));
				derivatives.put(v, embedding_u
									.multiply(trainingExampleData.transformToDstEmbedding)
									.selfMultiply(weight*crossEntropyDerivative)
									.selfAdd(derivatives.getOrDefault(v, zero)));

				loss += trainingExample.getWeight()
						*Loss.crossEntropy(Loss.sigmoid(firstOrderActivation)*Loss.sigmoid(secondOrderActivation_u)*Loss.sigmoid(secondOrderActivation_v), trainingExample.getLabel());
				

				/*derivatives.put(u, secondOrder_v
									.multiply(trainingExampleData.transformToSrcEmbedding)
									.selfMultiply(weight*firstOrderActivation*Loss.crossEntropySigmoidDerivative(secondOrderActivation, trainingExample.getLabel()))
									.selfAdd(derivatives.getOrDefault(u, zero)));
				derivatives.put(v, secondOrder_u
									.multiply(trainingExampleData.transformToDstEmbedding)
									.selfMultiply(weight*firstOrderActivation*Loss.crossEntropySigmoidDerivative(secondOrderActivation, trainingExample.getLabel()))
									.selfAdd(derivatives.getOrDefault(v, zero)));*/
				
			}
			else {
				Tensor embedding_u = u.getOrCreateInstance(GNNNodeData.class).getEmbedding().multiply(trainingExampleData.transformToSrcEmbedding);
				Tensor embedding_v = v.getOrCreateInstance(GNNNodeData.class).getEmbedding().multiply(trainingExampleData.transformToDstEmbedding);
				totalWeights.put(u, totalWeights.getOrDefault(u, 0.)+trainingExample.getWeight());
				totalWeights.put(v, totalWeights.getOrDefault(v, 0.)+trainingExample.getWeight());
				transformToSrcEmbeddingDerivativeWeight += trainingExample.getWeight();
				transformToDstEmbeddingDerivativeWeight += trainingExample.getWeight();
				loss += trainingExample.getWeight()*Loss.crossEntropy(Loss.sigmoid(embedding_u.dot(embedding_v)), trainingExample.getLabel());
				double crossEntropyDerivative = trainingExample.getWeight()
						*Loss.crossEntropySigmoidDerivative(embedding_u.dot(embedding_v), trainingExample.getLabel());
				derivatives.put(u, embedding_v
									.multiply(trainingExampleData.transformToSrcEmbedding)
									.selfMultiply(crossEntropyDerivative)
									.selfAdd(derivatives.getOrDefault(u, zero)));
				derivatives.put(v, embedding_u
									.multiply(trainingExampleData.transformToDstEmbedding)
									.selfMultiply(crossEntropyDerivative)
									.selfAdd(derivatives.getOrDefault(v, zero)));
				
				transformToSrcEmbeddingDerivative = embedding_v
						.multiply(u.getOrCreateInstance(GNNNodeData.class).getEmbedding())
						.selfMultiply(crossEntropyDerivative)
						.selfAdd(transformToSrcEmbeddingDerivative);
				transformToDstEmbeddingDerivative = embedding_u
						.multiply(v.getOrCreateInstance(GNNNodeData.class).getEmbedding())
						.selfMultiply(crossEntropyDerivative)
						.selfAdd(transformToSrcEmbeddingDerivative);
			}
		}
		for(Node u : derivatives.keySet()) {
			u.getOrCreateInstance(GNNNodeData.class)
				.setLearningRate(learningRate)
				.setRegularizationWeight(regularizationWeight)
				.updateEmbedding(derivatives.get(u).multiply(1./totalWeights.get(u)));
		}
		
		if(outgoingEdgeLearningRateMultiplier!=0 && transformToSrcEmbeddingDerivativeWeight!=0)
			trainingExampleData.transformToSrcEmbedding = transformToSrcEmbeddingDerivative
				.selfMultiply(outgoingEdgeLearningRateMultiplier*learningRate/transformToSrcEmbeddingDerivativeWeight)
				.selfAdd(trainingExampleData.transformToSrcEmbedding.selfMultiply(1-regularizationWeight*learningRate*outgoingEdgeLearningRateMultiplier));
		if(incommingEdgeLearningRateMultiplier!=0 && transformToDstEmbeddingDerivativeWeight!=0)
			trainingExampleData.transformToDstEmbedding = transformToDstEmbeddingDerivative
					.selfMultiply(incommingEdgeLearningRateMultiplier*learningRate/transformToDstEmbeddingDerivativeWeight)
					.selfAdd(trainingExampleData.transformToDstEmbedding.selfMultiply(1-regularizationWeight*learningRate*incommingEdgeLearningRateMultiplier));
		return loss;
	}

	public double predictNewInteraction(Context context, Node u, Node v) {
		if(context==null || u==null || v==null)
			Utils.error(new IllegalArgumentException());
		ContextTrainingExampleData trainingExampleData = context.getOrCreateInstance(ContextTrainingExampleData.class);
		
		if(trainingExampleData.transformToDstEmbedding==null) 
			trainingExampleData.transformToDstEmbedding = u.getOrCreateInstance(GNNNodeData.class).getEmbedding().zeroCopy().setToOnes();
		if(trainingExampleData.transformToSrcEmbedding==null) 
			trainingExampleData.transformToSrcEmbedding = v.getOrCreateInstance(GNNNodeData.class).getEmbedding().zeroCopy().setToOnes();
		
		
		Tensor embedding_u = u.getOrCreateInstance(GNNNodeData.class).getEmbedding().multiply(trainingExampleData.transformToSrcEmbedding);
		Tensor embedding_v = v.getOrCreateInstance(GNNNodeData.class).getEmbedding().multiply(trainingExampleData.transformToDstEmbedding);
		Tensor secondOrder_u = u.getOrCreateInstance(GNNNodeData.class).getNeighborAggregation().multiply(trainingExampleData.transformToSrcEmbedding);
		Tensor secondOrder_v = v.getOrCreateInstance(GNNNodeData.class).getNeighborAggregation().multiply(trainingExampleData.transformToSrcEmbedding);
		
		
		double firstOrderActivation = Loss.sigmoid(embedding_u.dot(embedding_v));
		double secondOrderActivation = secondOrderProximity?Loss.sigmoid(embedding_u.dot(secondOrder_v))*Loss.sigmoid(embedding_v.dot(secondOrder_u)):1;
		
		return firstOrderActivation*secondOrderActivation;
	}
	
	@Override
	public double predictNewInteraction(Context context, Node destinationNode) {
		Node u = destinationNode.getContextualEgoNetwork().getEgo();
		Node v = destinationNode;
		return predictNewInteraction(context, u, v);
		
	}
}
