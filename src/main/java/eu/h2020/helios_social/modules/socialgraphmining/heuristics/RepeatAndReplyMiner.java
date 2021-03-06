package eu.h2020.helios_social.modules.socialgraphmining.heuristics;


import eu.h2020.helios_social.core.contextualegonetwork.Context;
import eu.h2020.helios_social.core.contextualegonetwork.ContextualEgoNetwork;
import eu.h2020.helios_social.core.contextualegonetwork.Edge;
import eu.h2020.helios_social.core.contextualegonetwork.Interaction;
import eu.h2020.helios_social.core.contextualegonetwork.Node;
import eu.h2020.helios_social.modules.socialgraphmining.SocialGraphMiner;


/**
 * This class provides a {@link SocialGraphMiner} that re-recommends previous interactions 
 * with alters based on their chronological order.
 * 
 * @author Emmanouil Krasanakis
 */
public class RepeatAndReplyMiner extends SocialGraphMiner {
	/**
	 * This class is used to hold information about the order alters have been interacted with
	 * or have sent interaction to the device.
	 * It is tied to the edge of each interaction.
	 * 
	 * @author Emmanouil Krasanakis
	 */
	public static class OrderTimestamp {
		private static long maxValue = 0;
		private long value = 0;
		public OrderTimestamp() {}
		public void updateValue() {
			maxValue += 1;
			value = maxValue;
		}
		public long getValue() {
			//if(value==0)
			//	Utils.error("RepeatMiner have not assigned an OrderTimestamp to the edge");
			return value;
		}
	}
	
	public RepeatAndReplyMiner(ContextualEgoNetwork contextualEgoNetwork) {
		super(contextualEgoNetwork);
	}

	@Override
	public void newInteractionParameters(Interaction interaction, SocialGraphMinerParameters neighborModelParameters, InteractionType interactionType) {
		if(interactionType==InteractionType.RECEIVE_REPLY || interactionType==InteractionType.RECEIVE) 
			interaction.getEdge().getOrCreateInstance(OrderTimestamp.class).updateValue();
	}

	@Override
	public SocialGraphMinerParameters constructModelParameterObject(Interaction interaction) {
		return null;
	}

	@Override
	public double predictNewInteraction(Context context, Node destinationNode) {
		double maxVal = 0;
		for(Edge edge : context.getEdges())
			if(edge.getEgo()!=null && edge.getAlter()==destinationNode) {
				double val = edge.getOrCreateInstance(OrderTimestamp.class).getValue();
				if(val>maxVal)
					maxVal = val;
			}
		return maxVal;
	}

}
