package main;

import neuron.Neuron;

public class SpikeMemory {
	public Long time;
	
	public Neuron neuron;
	public boolean isSensory;
	public double sensoryAmplitude;
	
	public boolean isPositive;
	public int sign;
	
	//sensory spike
	public SpikeMemory(long time, double sensoryAmplitude, boolean isPositive) {
		this.time = time;
		
		this.neuron = null;
		this.isSensory = true;
		this.sensoryAmplitude = sensoryAmplitude;
		
		this.isPositive = isPositive;
		this.sign = isPositive ? +1 : -1;
	}
	
	//spike from another neuron
	public SpikeMemory(long time, Neuron neuron, boolean isPositive) {
		this.time = time;
		
		this.neuron = neuron;
		this.isSensory = false;
		
		this.isPositive = isPositive;
		this.sign = isPositive ? +1 : -1;
	}
}
