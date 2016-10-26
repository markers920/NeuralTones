package main;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.apache.commons.lang3.tuple.Pair;

import com.xuggle.mediatool.IMediaWriter;

import neuron.Neuron;

public class TrainingExample {

	private short[] samples;
	private double[][] spectrogram;
	
	private List<Neuron> neurons;	//all neurons

	//TODO: make a neuron -> double map, rather than 2 lists
	private List<Neuron> positiveActiveNeurons;	//those neurons that should be active
	private List<Neuron> negativeUnactiveNeurons;	//those neurons that should be active
	private Map<Integer, List<Pair<Neuron, SpikeMemory>>> sensoryEvents = new HashMap<Integer, List<Pair<Neuron, SpikeMemory>>>();
	
	private int numberOfRunningFrames;
	private double timeUnitsPerFrame;
	private TimeUnit frameTimeUnit;
	
	public TrainingExample(
			double audioSampleRate, 
			double framesPerSecond, 
			int numberOfRunningFrames,
			int windowSize,
			double timeUnitsPerFrame,
			TimeUnit frameTimeUnit,
			int numberOfSensoryNeurons,
			short[] samples, 
			List<Neuron> neurons,		//all neurons
			List<Neuron> specificallyActiveNeurons,			//those that should be active
			List<Neuron> specificallyUnactiveNeurons) throws IOException {		//those that should NOT be active
		
		this.numberOfRunningFrames = numberOfRunningFrames;
		this.timeUnitsPerFrame = timeUnitsPerFrame;
		this.frameTimeUnit = frameTimeUnit;
		
		this.samples = samples;
		this.neurons = neurons;
		this.positiveActiveNeurons = specificallyActiveNeurons;
		this.negativeUnactiveNeurons = specificallyUnactiveNeurons;
		
		if(this.positiveActiveNeurons != null && this.negativeUnactiveNeurons != null) {
			for(Neuron n : neurons) {
				if(this.positiveActiveNeurons.contains(n) && this.negativeUnactiveNeurons.contains(n)) {
					throw new IOException("cant set neuron pos and neg in same training");
				}
			}
		}
		
		spectrogram = HelperFunctions.makeSpectrogram(
				samples, 
				audioSampleRate, framesPerSecond, 
				numberOfRunningFrames, windowSize);
		
		for(int frameIndex = 0; frameIndex < numberOfRunningFrames; frameIndex++) {
			sensoryEvents.put(frameIndex, new ArrayList<Pair<Neuron, SpikeMemory>>());
			for(int sampleIndex = 0; sampleIndex < numberOfSensoryNeurons; sampleIndex++) {
				double amplitude = spectrogram[frameIndex][sampleIndex];
				sensoryEvents.get(frameIndex).add(
						Pair.of(neurons.get(sampleIndex),
								new SpikeMemory(frameIndex, amplitude, true)));
			}
		}
	}
	
	
	public void runNetwork(boolean doWrite, int clock, int maxLearningDepth, IMediaWriter writer, BufferedImage image) {
		//the past input spikes don't matter to this learning phase
		for(Neuron n : neurons) {
			n.clearInputSpikes();
		}

		
		for(int frameIndex = 0; frameIndex < numberOfRunningFrames; frameIndex++) {
			if(doWrite && frameIndex > 0 && frameIndex % 5 == 0) {	//dowrite is the only big one
				System.out.println("\tframeIndex: " + frameIndex + " / " + numberOfRunningFrames);
			}

			//add any sensory events that may have happened in this frame
			List<Pair<Neuron, SpikeMemory>> frameSensoryEvents = sensoryEvents.get(frameIndex);
			if(frameSensoryEvents != null) {
				for(Pair<Neuron, SpikeMemory> p : frameSensoryEvents) {
					SpikeMemory sm = p.getRight();
					p.getLeft().addSensoryInput(frameIndex, sm.sensoryAmplitude, sm.isPositive);
				}
			}

			//which should spike this frame
			//this stage prevents cascading spikes in a single frame 
			List<Neuron> spikeNeurons = new ArrayList<Neuron>();
			
			for(Neuron n : neurons) {
				if(n.shouldSpike(frameIndex)) {
					spikeNeurons.add(n);
				}
			}


			//let those which need to naturally spike, spike
			for(Neuron n : spikeNeurons) {
				//these will be force spiked later
				if(positiveActiveNeurons != null && negativeUnactiveNeurons != null) {
					if(positiveActiveNeurons.contains(n) || negativeUnactiveNeurons.contains(n)) {
						continue;
					}
				}
				
				n.spikeOut(frameIndex);
			}
			
			//forced learning
			if(positiveActiveNeurons != null) {
				for(Neuron n : positiveActiveNeurons) {
					n.learn(frameIndex, true, 0, maxLearningDepth);
				}
			}
			
			if(negativeUnactiveNeurons != null) {
				for(Neuron n : negativeUnactiveNeurons) {
					n.learn(frameIndex, false, 0, maxLearningDepth);
				}
			}


			//paint and write image to image stream
			if(doWrite) {
				//paint image
				Graphics2D g = (Graphics2D) image.getGraphics();
				g.setBackground(Color.BLACK);
				for(Neuron n : neurons) {
					n.paint(frameIndex, g);
				}
				
				writer.encodeVideo(0, image, (long)(timeUnitsPerFrame*frameIndex), frameTimeUnit);
			}
		}
		
		if(doWrite) {
			writer.encodeAudio(1, samples, clock, frameTimeUnit);
		}
	}

	
	
}
