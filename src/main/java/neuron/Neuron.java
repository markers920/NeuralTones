package neuron;

import java.awt.Color;
import java.awt.Graphics2D;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.apache.commons.lang3.tuple.Pair;

import main.HelperFunctions;
import main.SpikeMemory;

public class Neuron {
	
	//when the potential crosses this value, the neuron will spike out to its output neighbors
	public static final double DEFAULT_THRESHOLD = 1.0;
	
	//the rate of decay for the neuron's potential
	// a spikes contribution is dampened by exp(-decayCoefficient * amountOfTimePassed)
	private static final double decayCoefficient = 0.1;		//TODO: make configurable or even learnable
	
	//at this time delta, with this decay, a spike will be <the value in the log> times the original size
	// if a spike happened within this time range, increase the weight of the contributing neuron
	// for those connections that didn't contribute in this time, decrease their weight contribution  
	private static final double RECENT_SPIKE_CUTOFF = (-1.0/decayCoefficient) * Math.log(0.5);
	
	//the amount to increase "close" neighbor weights when co-spiking
	//ALSO the inverse amount of the decrease
	//TODO:
	private static final double WEIGHT_LEARNING_COEFFICIENT = 1.1;
	
	
	private int name;
	private int layer;
	
	//time of spike and neuron (null if sensory) as well as amplitude
	// used to calculate potential
	private List<SpikeMemory> inputSpikes = new ArrayList<SpikeMemory>();
	
	//threshold on the absolute value of the internal potential
	private double threshold;
	
	private Pair<Double, Double> location;
	
	//neuron -- to --> weight
	//TODO: make private again
	public Map<Neuron, Double> inputs = new HashMap<Neuron, Double>();	//weight an be positive or negative 
	private Set<Neuron> outputs = new HashSet<Neuron>();


	//paint variables
	public static final int SIZE = 201;	//TODO: set to 11
	public static final double HALF_SIZE = SIZE / 2.0;
	
	public static final Color POTENTIAL_NEG_COLOR = Color.RED;
	public static final Color POTENTIAL_ZERO_COLOR = Color.GRAY;
	public static final Color POTENTIAL_POS_COLOR = Color.GREEN;
	
	public static final Color WEIGHT_NEG_COLOR = Color.RED;
	public static final Color WEIGHT_ZERO_COLOR = new Color(0, 0, 0, 0);	//transparent
	//public static final Color WEIGHT_ZERO_COLOR = Color.WHITE;
	public static final Color WEIGHT_POS_COLOR = Color.GREEN;

	///////////////////////////////////////////////////////
	
	public Neuron(int name, int layer, double threshold, double x, double y) throws IOException {
		this.setName(name);
		this.setLayer(layer);
		this.setThreshold(threshold);
		
		this.location = Pair.of(x, y);
	}
	
	///////////////////////////////////////////////////////
	
	public int getName() {
		return this.name;
	}
	
	private void setName(int name) {
		this.name = name;
	}
	
	///////////////////////////////////////////////////////
	
	public int getLayer() {
		return this.layer;
	}

	private void setLayer(int layer) {
		this.layer = layer;
	}

	///////////////////////////////////////////////////////
	
	public double getThreshold() {
		return this.threshold;
	}
	
	public void setThreshold(double threshold) {
		this.threshold = threshold;
	}
	
	///////////////////////////////////////////////////////
	
	public double getPotential(long now) {
		double potential = 0.0;
		for(SpikeMemory sm : inputSpikes) {
			long timeOfSpike = sm.time;
			double spikeAmplitude = 0.0;
			
			if(sm.isSensory) {
				spikeAmplitude = sm.sensoryAmplitude * sm.sign;
			}
			else {
				spikeAmplitude = inputs.get(sm.neuron) * sm.sign;
			}
			
			potential += spikeAmplitude * Math.exp(-decayCoefficient * (now-timeOfSpike));
		}
		//System.out.println(now + " " + this.getName() + " " + potential);
		return potential;
	}
	
	///////////////////////////////////////////////////////
	
	public void addInputNeuron(Neuron input, double inputWeight) throws IOException {
		if(input == this) {
			throw new IOException("NO SELF LOOPS ALLOWED");
		}
		inputs.put(input, inputWeight);
	}
	
	public void addOutputNeuron(Neuron out) throws IOException {
		if(out == this) {
			throw new IOException("NO SELF LOOPS ALLOWED");
		}
		outputs.add(out);
	}
	
	//TODO: right?
	public void normalizeInputWeights() {
		/*double avg = 0.0;
		for(double d : inputs.values()) {
			avg += Math.abs(d);
		}
		avg /= inputs.size();
		
		for(Entry<Neuron, Double> e : inputs.entrySet()) {
			inputs.put(e.getKey(), e.getValue() - avg);
		}*/
		
		double absMax = 0.0;
		for(double d : inputs.values()) {
			absMax = Math.max(absMax, Math.abs(d));
		}
		
		for(Entry<Neuron, Double> e : inputs.entrySet()) {
			inputs.put(e.getKey(), e.getValue() / absMax);
		}
	}
	
	///////////////////////////////////////////////////////

	//sensory input
	public void addSensoryInput(long now, double sensoryAmplitude, boolean isPositive) {
		//System.out.println(this.getName() + " - SENSORY OF " + sensoryAmplitude);
		inputSpikes.add(new SpikeMemory(now, sensoryAmplitude, isPositive));
		
	}
	
	//neuron input
	public void receiveSpike(long now, Neuron spikingNeuron, boolean isPositive) {
		//System.out.println(this.getName() + " - SPIKE FROM " + spikingNeuron.getName());
		inputSpikes.add(new SpikeMemory(now, spikingNeuron, isPositive));
	}
	
	///////////////////////////////////////////////////////
	
	//TODO: add element to prevent spikes when it just spiked... cooldown period?
	public boolean shouldSpike(long now) {
		return Math.abs(this.getPotential(now)) >= this.threshold;
	}
	
	// (-1) (0 - no spike) (+1)
	public int spikeSign(long now) {
		if(this.shouldSpike(now)) {
			if(this.getPotential(now) > 0)
				return +1;
			else
				return -1;
		}
		return 0;
	}
	
	
	//if this neuron just spiked... or is about to... or should (training)
	//
	//neuron just had a positive spike ----------------------------------
	//	boost the effect of those neurons that
	//		recently spiked positive AND have positive associated weights
	//		recently spiked negative AND have negative associated weights
	//	decrease the effect of those neurons that
	//		recently spiked positive AND have negative associated weights
	//		recently spiked negative AND have positive associated weights
	//neuron just had a negative spike ----------------------------------
	//	boost the effect of those neurons that
	//		recently spiked positive AND have negative associated weights
	//		recently spiked negative AND have positive associated weights
	//	decrease the effect of those neurons that
	//		recently spiked positive AND have positive associated weights
	//		recently spiked negative AND have negative associated weights
	public void learn(long now, boolean positiveSpike, int learnLayer, int maxLearningDepth) {
		//System.out.println("\t LEARN " + this.getName() + " " + this.getLayer() + " **********************");
		//note: positiveSpike should be (spikeSign > 0) if this is learning naturally
		// otherwise, if forced learning, it would need to be set 
		
		//for(Neuron neighbor : inputs.keySet()) {
		//	System.out.println("\tOLD WEIGHT " + neighbor.getName() + " " + inputs.get(neighbor));
		//}
		
		//TODO: ???
		//if not touched by the above conditions, and low weight (diven down from past runs)
		//	switch sign of the input
		//didn't recently spike either way
		//	decrease input a little
		
		//add one for each time that input was helpful
		//sub one for each time that input was harmful
		//large positive numbers mean the input helped a lot (with either pos or neg spikes)... and should be told that
		Map<Neuron, Integer> learningPowers = new HashMap<Neuron, Integer>();
		
		//+1 for positive spikes
		//-1 for negative spikes
		Map<Neuron, Integer> learningDirections = new HashMap<Neuron, Integer>();
		//TODO: redundant?
		
		for(Neuron n : inputs.keySet()) {
			learningPowers.put(n, 0);
			learningDirections.put(n, 0);
		}
		
		for(SpikeMemory sm : inputSpikes) {
			if(sm.isSensory) {
				continue;		//can't reward sensory input (directly)
			}
			
			long timeDelta = now - sm.time;
			boolean isRecent = timeDelta < Neuron.RECENT_SPIKE_CUTOFF;	//TODO: make flexible function of time passed
			
			//intentionally spelled out rather than shortcut the logic
			if(isRecent) {
				//System.out.println("spike memory: " + sm.neuron.getName());
				boolean inputPositiveSpike = sm.isPositive;	//was the input spike positive?
				boolean inputWeightPositive = inputs.get(sm.neuron) > 0;
				
				if(positiveSpike) {
					//spike and weight are the same sign -> product is positive -> helped positive spike
					if(inputPositiveSpike == inputWeightPositive) {
						//helpfulNeurons.add(sm.neuron);
						learningPowers.put(sm.neuron, learningPowers.get(sm.neuron) + 1);
						learningDirections.put(sm.neuron, learningDirections.get(sm.neuron) + 1);
					}
					
					//spike and weight are the different sign -> product is negative -> harmed positive spike
					else {
						//harmfulNeurons.add(sm.neuron);
						learningPowers.put(sm.neuron, learningPowers.get(sm.neuron) - 1);
					}
				}
				
				else {	//negative spike
					//spike and weight are the different sign -> product is negative -> helped negative spike
					if(inputPositiveSpike != inputWeightPositive) {
						//helpfulNeurons.add(sm.neuron);
						learningPowers.put(sm.neuron, learningPowers.get(sm.neuron) + 1);
						learningDirections.put(sm.neuron, learningDirections.get(sm.neuron) - 1);
					}
					
					//spike and weight are the same sign -> product is positive -> harmed negative spike
					else {
						//harmfulNeurons.add(sm.neuron);
						learningPowers.put(sm.neuron, learningPowers.get(sm.neuron) - 1);
					}
				}
			}//END recent
		}//end for each of the remembered spikes
		
		double minAbsWeight = Double.MAX_VALUE;
		Neuron minAbsWeightNeuron = null;
		
		for(Entry<Neuron, Integer> e : learningPowers.entrySet()) {
			Neuron n = e.getKey();
			int learningPower = e.getValue();
			//if(learningPower == 0) {
			//	continue;
			//}
			
			double oldWeight = inputs.get(n);
			double learningCoefficient = Math.pow(Neuron.WEIGHT_LEARNING_COEFFICIENT, learningPower);
			double newWeight = oldWeight * learningCoefficient;
			inputs.put(n, newWeight);
			//System.out.println("learn: " + n.getName() + " -> " + oldWeight + " " + learningCoefficient + " " + newWeight);
			
			if(Math.abs(newWeight) < minAbsWeight) {
				minAbsWeight = Math.abs(newWeight);
				minAbsWeightNeuron = n;
			}
		}
		
		//TODO: track some of these... does this help? Does a wait period need to be added to prevent multiple flips
		//flip the sign of the least significant input, in hopes that it will make it better on the othe side
		if(minAbsWeight < 0.1) {	//has to be small
			double origSmallWeight = inputs.get(minAbsWeightNeuron);
			inputs.put(minAbsWeightNeuron, -1 * origSmallWeight);
			//System.out.println("\t\tmin weight: " + origSmallWeight);
		}
		
		//for(Neuron neighbor : inputs.keySet()) {
		//	System.out.println("\tNEW WEIGHT (before norm)" + neighbor.getName() + " " + inputs.get(neighbor));
		//}
		
		this.normalizeInputWeights();
		
		//TODO:  back propagate the learning
		//this should be done last because of cycles/loops
		if(learnLayer < maxLearningDepth) {
			for(Entry<Neuron, Integer> e : learningDirections.entrySet()) {
				int learningDirection = e.getValue();
				if(e.getValue() > 0) {	//TODO: higher?
					//System.out.println("\t\tlearning direction: " + learningDirection);
					Neuron input = e.getKey();
					input.learn(now, learningDirection>0, learnLayer+1, maxLearningDepth);
				}
			}
		}
		
		//for(Neuron neighbor : inputs.keySet()) {
		//	System.out.println("\tNEW WEIGHT " + neighbor.getName() + " " + inputs.get(neighbor));
		//}
	}
	
	
	//automatic spike, w/o any checking
	public void spikeOut(long now) {
		//System.out.println(this.name + " - SPIKE");
		boolean isPositive = this.spikeSign(now) > 0;	//should be -1 or +1...NOT zero
		
		//send out spikes to neighbors
		for(Neuron neighbor : outputs) {
			neighbor.receiveSpike(now, this, isPositive);
		}
		
		clearInputSpikes();		//like reseting the potential
	}
		
	
	public void clearInputSpikes() {
		inputSpikes.clear();
	}
	
	///////////////////////////////////////////////////////
	
	///////////////////////////////////////////////////////
	
	public Pair<Double, Double> getLocation() {
		return location;
	}
	
	public void paint(long now, Graphics2D g) {
		Color oc = g.getColor();
		
		//body
		double currentPotential = this.getPotential(now);
		boolean isPositive = currentPotential > 0.0;
		double potentialRatio = Math.abs(currentPotential / this.threshold);
		potentialRatio = Math.min(potentialRatio, 1.0);	//potential may be really high for a short time
		
		if(isPositive) {
			g.setColor(HelperFunctions.getGradiantColor(POTENTIAL_ZERO_COLOR, POTENTIAL_POS_COLOR, potentialRatio));
		}
		else {
			g.setColor(HelperFunctions.getGradiantColor(POTENTIAL_ZERO_COLOR, POTENTIAL_NEG_COLOR, potentialRatio));
		}
		
		g.fillOval(
				(int)(location.getLeft() - HALF_SIZE), 
				(int)(location.getRight() - HALF_SIZE), 
				SIZE, 
				SIZE);
		
		
		//sensory indicator
		/*boolean recentlyHadSensoryInput = false;
		for(SpikeMemory sm : inputSpikes) {
			long timeOfSpike = sm.time;
			Neuron n = sm.neuron;
			if(n == null && (now - timeOfSpike) < RECENT_SPIKE_CUTOFF) {	//if sensory AND recent
				recentlyHadSensoryInput = true;
			}
		}
		
		if(recentlyHadSensoryInput) {
			g.setColor(Color.WHITE);
			g.drawOval(
					(int)(location.getLeft() - SIZE), 
					(int)(location.getRight() - SIZE), 
					2*SIZE, 
					2*SIZE);
		}*/
		
		
		//name
		String nameString = String.valueOf(this.name);
		g.drawChars(
				nameString.toCharArray(), 0, nameString.length(), 
				(int)(location.getLeft() - HALF_SIZE), 
				(int)(location.getRight() - HALF_SIZE));
		
		//input dendrites
		double maxAbsWeight = 0.0;
		for(Neuron n : inputs.keySet()) {
			maxAbsWeight = Math.max(maxAbsWeight, Math.abs(inputs.get(n)));
		}
		
		for(Neuron n : inputs.keySet()) {
			double currentWeight = inputs.get(n);
			boolean positiveWeight = currentWeight > 0;
			double weightRatio = Math.abs(currentWeight / maxAbsWeight);
			
			if(positiveWeight) {
				g.setColor(HelperFunctions.getGradiantColor(WEIGHT_ZERO_COLOR, WEIGHT_POS_COLOR, weightRatio));
			}
			else {
				g.setColor(HelperFunctions.getGradiantColor(WEIGHT_ZERO_COLOR, WEIGHT_NEG_COLOR, weightRatio));
			}
			
			Pair<Double, Double> nbrLocation = n.getLocation();
			
			g.drawLine(this.location.getLeft().intValue(), this.location.getRight().intValue(), 
					nbrLocation.getLeft().intValue(), nbrLocation.getRight().intValue());
			//System.out.println(currentWeight + " " + g.getColor());
		}
		
		g.setColor(oc);
	}
}
