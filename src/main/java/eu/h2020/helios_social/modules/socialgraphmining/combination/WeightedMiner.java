package eu.h2020.helios_social.modules.socialgraphmining.combination;

import java.util.HashMap;

import eu.h2020.helios_social.core.contextualegonetwork.Context;
import eu.h2020.helios_social.core.contextualegonetwork.ContextualEgoNetwork;
import eu.h2020.helios_social.core.contextualegonetwork.Node;
import eu.h2020.helios_social.core.contextualegonetwork.Utils;
import eu.h2020.helios_social.modules.socialgraphmining.SocialGraphMiner;
import eu.h2020.helios_social.modules.socialgraphmining.SocialGraphMinerCombination;


/**
 * This class weighs different {@link SocialGraphMiner} implementations to produce combined scores.
 * It extends the miner management of {@link SocialGraphMinerCombination}. Miner weights are 1 by
 * default and can be changed by calling the 
 * 
 * @author Emmanouil Krasanakis
 */
public class WeightedMiner extends SocialGraphMinerCombination {
	private HashMap<String, Double> weights = new HashMap<String, Double>();

	public WeightedMiner(ContextualEgoNetwork contextualEgoNetwork) {
		super(contextualEgoNetwork);
	}

	/**
	 * Retrieves the weight associated with the provided miner name.
	 * @param minerName The registered miner's name.
	 * @return A double in the range [0, 1], 1 if the weight has never been set.
	 * @see #setMinerWeight(String, double)
	 */
	public double getMinerWeight(String minerName) {
		getMiner(minerName); // throws error if not found
		return weights.getOrDefault(minerName, 1.);
	}
	
	/**
	 * Gets a created miner with {@link #getMiner(String)} and associates a new weight with it.
	 * Miner weight of zero makes the miner not participate in predictions.
	 * {@link #predictNewInteraction(Context, Node)} and {@link #recommendInteractions(Context)}.
	 * @param minerName The name of the miner whose weight is set.
	 * @param minerWeight The weight to associate with the miner. Should lie in the range [0,1].
	 * @return <code>this</code> miner.
	 * @see #getMinerWeight(String)
	 * @see #createMiner(String, Class)
	 */
	public SocialGraphMinerCombination setMinerWeight(String minerName, double minerWeight) {
		if(!Double.isFinite(minerWeight))
			Utils.error(new IllegalArgumentException("Non-finite miner weights not allowed: "+minerWeight));
		if(minerWeight<0 || minerWeight>1)
			Utils.error(new IllegalArgumentException("Miner weight "+minerWeight+" does not lie in the range [0,1]."));
		getMiner(minerName); // throws error if not found
		weights.put(minerName, minerWeight);
		return this;
	} 

	@Override
	public double predictNewInteraction(Context context, Node destinationNode) {
		double ret = 1;
		for(String minerName : getCreatedMinerNames()) {
			double weight = getMinerWeight(minerName);
			if(weight!=0)
				ret *= Math.pow(getMiner(minerName).predictNewInteraction(context, destinationNode), weight);
		}
		return ret;
	}
	
}
