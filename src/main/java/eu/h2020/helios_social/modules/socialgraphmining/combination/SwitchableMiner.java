package eu.h2020.helios_social.modules.socialgraphmining.combination;

import java.util.HashMap;

import eu.h2020.helios_social.core.contextualegonetwork.Context;
import eu.h2020.helios_social.core.contextualegonetwork.ContextualEgoNetwork;
import eu.h2020.helios_social.core.contextualegonetwork.Node;
import eu.h2020.helios_social.core.contextualegonetwork.Utils;
import eu.h2020.helios_social.modules.socialgraphmining.SocialGraphMiner;
import eu.h2020.helios_social.modules.socialgraphmining.SocialGraphMinerCombination;


/**
 * This class enables switching between different {@link SocialGraphMiner} implementations.
 * It extends the miner management of {@link SocialGraphMinerCombination}. The active miner can
 * be selected using the {@link #setActiveMiner(String)} method.
 * 
 * @author Emmanouil Krasanakis
 */
public class SwitchableMiner extends SocialGraphMinerCombination {
	private SocialGraphMiner activeMiner;

	public SwitchableMiner(ContextualEgoNetwork contextualEgoNetwork) {
		super(contextualEgoNetwork);
	}

	
	/**
	 * Retrieves the active miner.
	 * @return The active miner.
	 * @see #setActiveMiner(String)
	 */
	public SocialGraphMiner getActiveMiner() {
		return activeMiner;
	}

	
	/**
	 * Gets a created miner with {@link #getMiner(String)} and, if such a miner is found,
	 * this is set as the active miner. The active miner is subsequently called to expose its outcome
	 * of {@link #predictNewInteraction(Context, Node)} and {@link #recommendInteractions(Context)}.
	 * Other functionalities are shared between all created miners.
	 * @param minerName The name of the miner to set as the active one.
	 * @return The new active miner.
	 * @see #getActiveMiner()
	 * @see #createMiner(String, Class)
	 */
	public SocialGraphMiner setActiveMiner(String minerName) {
		activeMiner = getMiner(minerName);
		return activeMiner;
	} 

	@Override
	public double predictNewInteraction(Context context, Node destinationNode) {
		if(activeMiner==null)
			Utils.error("Must set an active miner before trying to predict interactions");
		return activeMiner.predictNewInteraction(context, destinationNode);
	}
	
	@Override
    public HashMap<Node, Double> recommendInteractions(Context context) {
		if(activeMiner==null)
			Utils.error("Must set an active miner before trying to predict interactions");
		return activeMiner.recommendInteractions(context);
	}
	
}
