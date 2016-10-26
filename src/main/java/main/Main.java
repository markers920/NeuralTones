package main;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import com.xuggle.mediatool.IMediaWriter;
import com.xuggle.mediatool.ToolFactory;
import com.xuggle.xuggler.ICodec;

import neuron.Neuron;

public class Main {
	//    C:\Users\581990\workspace\NeuralPlayground
	private static final String outputFilename = "video_learn1.mp4";
	private static final int IMAGE_WIDTH = 1920;
	private static final int IMAGE_HEIGHT = 1080;
	private static BufferedImage image = new BufferedImage(IMAGE_WIDTH, IMAGE_HEIGHT, BufferedImage.TYPE_3BYTE_BGR);
	
	private static Random random = new Random(0);

	private static final int[] NUMBER_NEURONS_PER_LAYER_ARRAY = {64, 64, 8};
	private static List<Neuron> neurons = new ArrayList<Neuron>();


	private static final double FRAMES_PER_SECOND = 10.0;
	private static final double NUMBER_OF_SECONDS = 10*FRAMES_PER_SECOND;
	private static final int NUMBER_OF_FRAMES = (int)(FRAMES_PER_SECOND * NUMBER_OF_SECONDS);
	private static final double TIME_UNITS_PER_FRAME = (1000.0/FRAMES_PER_SECOND);
	private static final TimeUnit FRAME_TIME_UNIT = TimeUnit.MILLISECONDS;
	private static final int AUDIO_SAMPLE_RATE = 44100;
	//private static final int AUDIO_SAMPLE_RATE = 8000;
	private static final int WINDOW_SIZE = 4096;
	private static final double TUKEY_ALPHA = 0.2;
	
	//private static final int audioStreamIndex = 1;	//TODO: change case
	//private static final int audioStreamId = 0;
	//private static final int channels = 1;

	private static final int TRAINING_COUNT = 100;
	
	
	// https://en.wikipedia.org/wiki/Piano_key_frequencies
	//                      	   C       D       E       F       G       A    B       C
	static double[] frequencies = {261.62, 293.67, 329.63, 349.23, 392.00, 440, 493.88, 523.25};

	public static void main(String[] args) throws IOException {
		IMediaWriter writer = ToolFactory.makeWriter(outputFilename);
		writer.addVideoStream(0, 0, ICodec.ID.CODEC_ID_MPEG4, IMAGE_WIDTH, IMAGE_HEIGHT);
		writer.addAudioStream(1, 0, 1, AUDIO_SAMPLE_RATE);
		
		if(false) {
			makeThreeNeuronExmple(writer);
			return;
		} else if(false) {
			makeFourNeuronExmple(writer);
			return;
		} else if(true) {
			makeLearn1Exmple(writer);
			return;
		}
		
		System.out.println("init network");
		initilizeNetwork();

		System.out.println("generate traiing samples");
		List<TrainingExample> exampleList = generateTrainingExamples();
		
		System.out.println("train network");
		trainNetwork(exampleList);
		
		System.out.println("get test audio smples");
		short[] samples = getTestAudioSamples();
		
		System.out.println("build test example");
		TrainingExample te = new TrainingExample(
				AUDIO_SAMPLE_RATE, FRAMES_PER_SECOND, NUMBER_OF_FRAMES, WINDOW_SIZE,
				TIME_UNITS_PER_FRAME,
				FRAME_TIME_UNIT,
				NUMBER_NEURONS_PER_LAYER_ARRAY[0],
				samples, 
				neurons,		//all neurons
				null,
				null);
		
		System.out.println("run test example");
		int clock = 0;
		te.runNetwork( 
				true,
				clock,
				-1,		//no learning back prop
				writer, 
				image);

		writer.close();
	}


	private static short[] getTestAudioSamples() throws IOException {
		List<Short> samples = new ArrayList<Short>();
		
		//frequency = (periods/second)
		//sample rate = (samples/second)
		//frquency / sample rate = (periods/sample)
		int numberSamplesForEachFrequency = (int)((NUMBER_OF_SECONDS * AUDIO_SAMPLE_RATE) / frequencies.length);
		//int coolDownPeriod = numberSamplesForEachFrequency / 10;
		//numberSamplesForEachFrequency -= coolDownPeriod;
		for(double frequency : frequencies) {
			List<Short> l1 = HelperFunctions.getAudioSamples(numberSamplesForEachFrequency, frequency, AUDIO_SAMPLE_RATE, TUKEY_ALPHA);
			samples.addAll(l1);
			
			////zero out the end
			//for(int idx = 0; idx < coolDownPeriod; idx++) {
			//	samples.add((short)0);
			//}
		}
		
		
		
		//TODO: add lowpass filter to drop the sharp noise spikes between notes

		short[] ret = new short[samples.size()];
		for(int idx = 0; idx < samples.size(); idx++) {
			ret[idx] = samples.get(idx);
		}
		
		return ret;
	}
	
	
	private static void initilizeNetwork() throws IOException {
		//create neurons and lay out the layers
		int neuronName = 0;
		for(int layerIndex = 0; layerIndex < NUMBER_NEURONS_PER_LAYER_ARRAY.length; layerIndex++) {
			for(int withinLayerIndex = 0; withinLayerIndex < NUMBER_NEURONS_PER_LAYER_ARRAY[layerIndex]; withinLayerIndex++) {
				int widthBuffer = 2*Neuron.SIZE;
				int paintableWidth = IMAGE_WIDTH - 2*widthBuffer;
				int widthSpacing = paintableWidth / NUMBER_NEURONS_PER_LAYER_ARRAY[layerIndex];
				
				int heightBuffer = 2*Neuron.SIZE;
				int paintableHeight = IMAGE_HEIGHT - 2*heightBuffer;
				int heightSpacing = paintableHeight / (NUMBER_NEURONS_PER_LAYER_ARRAY.length - 1);
				
				int neuronX = widthBuffer + widthSpacing*withinLayerIndex;
				int neuronY = heightBuffer + heightSpacing*layerIndex;
				
				neurons.add(
						new Neuron(
								neuronName,
								layerIndex,
								Neuron.DEFAULT_THRESHOLD, 
								neuronX, 
								neuronY));
				/*double neuronX = Neuron.SIZE*2 + (layerIndex*LAYER_PAINT_WIDTH); 
				int withinLayerPaintHeight = Y_PAINT_SIZE / NUMBER_NEURONS_PER_LAYER_ARRAY[layerIndex];
				int neuronY = Y_BUFFER_SIZE + (withinLayerIndex*withinLayerPaintHeight);
				neurons.add(
						new Neuron(
								neuronName,
								layerIndex,
								Neuron.DEFAULT_THRESHOLD, 
								(int)neuronX, 
								neuronY));*/
				neuronName++;
			}
		}

		//connections
		Set<Neuron> lastLayer = new HashSet<Neuron>();
		for(Neuron n : neurons) {
			if(n.getLayer() == NUMBER_NEURONS_PER_LAYER_ARRAY.length-1) {
				lastLayer.add(n); 
			}
		}
		
		//initial framework generation
		Set<Neuron> outputTo = new HashSet<Neuron>();
		for(Neuron neuron : neurons) {
			if(lastLayer.contains(neuron)) {
				continue;
			}
			
			outputTo.clear();
			for(Neuron neighbor : neurons) {
				if(neuron.getLayer() + 1 == neighbor.getLayer()) {	//next layer
					//int mod = Math.min(
					//		NUMBER_NEURONS_PER_LAYER_ARRAY[neuron.getLayer()], 
					//		NUMBER_NEURONS_PER_LAYER_ARRAY[neighbor.getLayer()]);
					//int n1Mod = neuron.getName() % mod;
					//int n2Mod = neighbor.getName() % mod;
					//if(Math.abs(n2Mod-n1Mod) < 2) {
						outputTo.add(neighbor);
					//}
				}
			}
			
			//outputTo.addAll(lastLayer);

			for(Neuron neighbor : outputTo) {
				neuron.addOutputNeuron(neighbor);
				//neighbor.addInputNeuron(neuron, 1.0);
				//double r = 2*random.nextDouble() - 1;
				//neighbor.addInputNeuron(neuron, r);
				neighbor.addInputNeuron(neuron, random.nextBoolean() ? +1 : -1);
				//System.out.println(neuron.getName() + " -> " + neighbor.getName() + "\t" + r);
			}
		}

		//normalize all inputs to each neuron
		for(Neuron neuron : neurons) {
			neuron.normalizeInputWeights();
		}

	}
	
	private static List<TrainingExample> generateTrainingExamples() throws IOException {
		List<TrainingExample> examples = new ArrayList<TrainingExample>();
		
		int numberTrainingSamples = 3*AUDIO_SAMPLE_RATE;
		
		int neuronOffset = 0;	//first one in last layer
		for(int layer = 0; layer < NUMBER_NEURONS_PER_LAYER_ARRAY.length - 1; layer++) {
			neuronOffset += NUMBER_NEURONS_PER_LAYER_ARRAY[layer];
		}
		
		for(int fIdx = 0; fIdx < frequencies.length; fIdx++) {
			//get training element for a tone
			double frequency = frequencies[fIdx];
			List<Short> samplesList = HelperFunctions.getAudioSamples(numberTrainingSamples, frequency, AUDIO_SAMPLE_RATE, TUKEY_ALPHA);
			short[] samples = new short[samplesList.size()];
			for(int idx = 0; idx < samplesList.size(); idx++) {
				samples[idx] = samplesList.get(idx);
			}
			
			List<Neuron> positiveNeurons = new ArrayList<Neuron>();
			List<Neuron> negativeNeurons = new ArrayList<Neuron>();
			
			int lastLayerSize = NUMBER_NEURONS_PER_LAYER_ARRAY[NUMBER_NEURONS_PER_LAYER_ARRAY.length-1];
			System.out.print("\t" + fIdx + " " + frequency + " *** ");
			for(int nIdx = 0; nIdx < lastLayerSize; nIdx++) {
				if(nIdx == fIdx) {
					positiveNeurons.add(neurons.get(nIdx + neuronOffset));
					System.out.print("\t<" + neurons.get(nIdx + neuronOffset).getName() + "> ");
				}
				
				else {
					//TODO: add me back!
					//negativeNeurons.add(neurons.get(nIdx + neuronOffset));
					//System.out.print("\t(" + neurons.get(nIdx + neuronOffset).getName() + ") ");
				}
			}
			System.out.println();
			
			TrainingExample te = new TrainingExample(
					AUDIO_SAMPLE_RATE, FRAMES_PER_SECOND, 
					30,	//has to be less than the number of samples div window size
					WINDOW_SIZE,
					TIME_UNITS_PER_FRAME,
					FRAME_TIME_UNIT,
					NUMBER_NEURONS_PER_LAYER_ARRAY[0],
					samples, 
					neurons,		//all neurons
					positiveNeurons,
					negativeNeurons);
			
			examples.add(te);
		}
		
		return examples;
	}

	
	private static void trainNetwork(List<TrainingExample> exampleList) throws IOException {
		Map<TrainingExample, Integer> trainingCounts = new HashMap<TrainingExample, Integer>();
		for(TrainingExample te : exampleList) {
			trainingCounts.put(te, 0);
		}
		
		int numberOfTrainingRuns = TRAINING_COUNT * trainingCounts.size();
		int trainingIndex = 0;
		
		while(trainingIndex < numberOfTrainingRuns) {
			int r = random.nextInt(exampleList.size());
			TrainingExample example = exampleList.get(r);
			int numberOfTimesTrained = trainingCounts.get(example);
			if(numberOfTimesTrained < TRAINING_COUNT) {
				example.runNetwork( 
						false,		//doWrite
						-1,			//clock,
						3,			//learnDepth
						null, 
						null);
				
				trainingCounts.put(example, numberOfTimesTrained+1);
				trainingIndex++;
			}
		}
	}
	

	
	
	
	
	public static void makeThreeNeuronExmple(IMediaWriter writer) throws IOException {
		//set up
		Neuron n0 = new Neuron(
						0,
						0,
						Neuron.DEFAULT_THRESHOLD, 
						100, 
						IMAGE_HEIGHT/2);
		neurons.add(n0);
		
		Neuron n1 = new Neuron(
						1,
						1,
						Neuron.DEFAULT_THRESHOLD, 
						100 + ((IMAGE_WIDTH-200)/2), 
						IMAGE_HEIGHT/2);
		neurons.add(n1);
		
		Neuron n2 = new Neuron(
						3,
						3,
						Neuron.DEFAULT_THRESHOLD, 
						100 + 2*((IMAGE_WIDTH-200)/2), 
						IMAGE_HEIGHT/2);
		neurons.add(n2);
		
		
		n0.addOutputNeuron(n1);
		n1.addInputNeuron(n0, +1);
		
		n1.addOutputNeuron(n2);
		n2.addInputNeuron(n1, +1);
		
		
		
		for(Neuron n : neurons) {
			n.clearInputSpikes();
		}
		
		
		double[] sensoryInput = new double[NUMBER_OF_FRAMES];
		for(int idx = 0; idx < sensoryInput.length; idx++) {
			//sensoryInput[idx] = random.nextDouble() > 0.9 ? +1 : 0;
			sensoryInput[idx] = ((idx % 15) < 5) ? +0.3 : 0;
		}
		
		
		for(int frameIndex = 0; frameIndex < sensoryInput.length; frameIndex++) {
			System.out.print(frameIndex);
			for(Neuron n : neurons) {
				System.out.print("\t" + n.getPotential(frameIndex));
			}
			System.out.println();
			
			//add any sensory events that may have happened in this frame
			//List<Pair<Neuron, SpikeMemory>> frameSensoryEvents = sensoryEvents.get(frameIndex);
			//if(frameSensoryEvents != null) {
			//	for(Pair<Neuron, SpikeMemory> p : frameSensoryEvents) {
			//		SpikeMemory sm = p.getRight();
			//		p.getLeft().addSensoryInput(frameIndex, sm.sensoryAmplitude, sm.isPositive);
			//	}
			//}
			n0.addSensoryInput(frameIndex, sensoryInput[frameIndex], true);

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
				//if(positiveActiveNeurons != null && negativeUnactiveNeurons != null) {
				//	if(positiveActiveNeurons.contains(n) || negativeUnactiveNeurons.contains(n)) {
				//		continue;
				//	}
				//}
				
				n.spikeOut(frameIndex);
			}
			
			//forced learning
			//if(positiveActiveNeurons != null) {
			//	for(Neuron n : positiveActiveNeurons) {
			//		n.learn(frameIndex, true, 0, maxLearningDepth);
			//	}
			//}
			
			//if(negativeUnactiveNeurons != null) {
			//	for(Neuron n : negativeUnactiveNeurons) {
			//		n.learn(frameIndex, false, 0, maxLearningDepth);
			//	}
			//}


			//paint and write image to image stream
			//if(doWrite) {
				//paint image
				Graphics2D g = (Graphics2D) image.getGraphics();
				g.setBackground(Color.BLACK);
				for(Neuron n : neurons) {
					n.paint(frameIndex, g);
				}
				
				writer.encodeVideo(0, image, (long)(TIME_UNITS_PER_FRAME*frameIndex), FRAME_TIME_UNIT);
			//}
		}
		
		//if(doWrite) {
		//	writer.encodeAudio(1, samples, clock, frameTimeUnit);
		//}
		
		writer.close();
	}
	
	
	public static void makeFourNeuronExmple(IMediaWriter writer) throws IOException {
		//set up
		Neuron n0 = new Neuron(
						0,
						0,
						Neuron.DEFAULT_THRESHOLD, 
						100, 
						IMAGE_HEIGHT/2);
		neurons.add(n0);
		
		Neuron n1 = new Neuron(
						1,
						1,
						Neuron.DEFAULT_THRESHOLD, 
						100 + ((IMAGE_WIDTH-200)/3), 
						IMAGE_HEIGHT/2);
		neurons.add(n1);
		
		Neuron n2 = new Neuron(
				3,
				3,
				Neuron.DEFAULT_THRESHOLD, 
				100 + 2*((IMAGE_WIDTH-200)/3), 
				IMAGE_HEIGHT/2);
		neurons.add(n2);
		
		Neuron n3 = new Neuron(
				3,
				3,
				Neuron.DEFAULT_THRESHOLD, 
				100 + 3*((IMAGE_WIDTH-200)/3), 
				IMAGE_HEIGHT/2);
		neurons.add(n3);
		
		
		n0.addOutputNeuron(n1);
		n1.addInputNeuron(n0, +1);
		
		n1.addOutputNeuron(n2);
		n2.addInputNeuron(n1, +1);
		
		n2.addOutputNeuron(n3);
		n3.addInputNeuron(n2, +1);
		
		
		for(Neuron n : neurons) {
			n.clearInputSpikes();
		}
		
		
		double[] sensoryInput = new double[NUMBER_OF_FRAMES];
		for(int idx = 0; idx < sensoryInput.length; idx++) {
			//sensoryInput[idx] = random.nextDouble() > 0.9 ? +1 : 0;
			sensoryInput[idx] = ((idx % 8) < 4) ? +0.3 : 0;
		}
		
		
		for(int frameIndex = 0; frameIndex < sensoryInput.length; frameIndex++) {
			System.out.print(frameIndex);
			for(Neuron n : neurons) {
				System.out.print("\t" + n.getPotential(frameIndex));
			}
			System.out.println();
			
			//add any sensory events that may have happened in this frame
			//List<Pair<Neuron, SpikeMemory>> frameSensoryEvents = sensoryEvents.get(frameIndex);
			//if(frameSensoryEvents != null) {
			//	for(Pair<Neuron, SpikeMemory> p : frameSensoryEvents) {
			//		SpikeMemory sm = p.getRight();
			//		p.getLeft().addSensoryInput(frameIndex, sm.sensoryAmplitude, sm.isPositive);
			//	}
			//}
			n0.addSensoryInput(frameIndex, sensoryInput[frameIndex], true);

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
				//if(positiveActiveNeurons != null && negativeUnactiveNeurons != null) {
				//	if(positiveActiveNeurons.contains(n) || negativeUnactiveNeurons.contains(n)) {
				//		continue;
				//	}
				//}
				
				n.spikeOut(frameIndex);
			}
			
			//forced learning
			//if(positiveActiveNeurons != null) {
			//	for(Neuron n : positiveActiveNeurons) {
			//		n.learn(frameIndex, true, 0, maxLearningDepth);
			//	}
			//}
			
			//if(negativeUnactiveNeurons != null) {
			//	for(Neuron n : negativeUnactiveNeurons) {
			//		n.learn(frameIndex, false, 0, maxLearningDepth);
			//	}
			//}


			//paint and write image to image stream
			//if(doWrite) {
				//paint image
				Graphics2D g = (Graphics2D) image.getGraphics();
				g.setBackground(Color.BLACK);
				for(Neuron n : neurons) {
					n.paint(frameIndex, g);
				}
				
				writer.encodeVideo(0, image, (long)(TIME_UNITS_PER_FRAME*frameIndex), FRAME_TIME_UNIT);
			//}
		}
		
		//if(doWrite) {
		//	writer.encodeAudio(1, samples, clock, frameTimeUnit);
		//}
		
		writer.close();
	}
	
	
	public static void makeLearn1Exmple(IMediaWriter writer) throws IOException {
		//set up
		Neuron n00 = new Neuron(
						0,
						0,
						Neuron.DEFAULT_THRESHOLD, 
						100, 
						IMAGE_HEIGHT/2 - 200);
		neurons.add(n00);
		
		Neuron n01 = new Neuron(
						1,
						0,
						Neuron.DEFAULT_THRESHOLD, 
						100, 
						IMAGE_HEIGHT/2 + 200);
		neurons.add(n01);
		
		Neuron n10 = new Neuron(
				2,
				1,
				Neuron.DEFAULT_THRESHOLD, 
				IMAGE_WIDTH - 100,
				IMAGE_HEIGHT/2 - 300);
		neurons.add(n10);
		
		Neuron n11 = new Neuron(
				3,
				1,
				Neuron.DEFAULT_THRESHOLD, 
				IMAGE_WIDTH - 100,
				IMAGE_HEIGHT/2 - 100);
		neurons.add(n11);
		
		Neuron n12 = new Neuron(
				4,
				1,
				Neuron.DEFAULT_THRESHOLD, 
				IMAGE_WIDTH - 100,
				IMAGE_HEIGHT/2 + 100);
		neurons.add(n12);
		
		Neuron n13 = new Neuron(
				4,
				1,
				Neuron.DEFAULT_THRESHOLD, 
				IMAGE_WIDTH - 100,
				IMAGE_HEIGHT/2 + 300);
		neurons.add(n13);
		
		
		n00.addOutputNeuron(n10);
		n10.addInputNeuron(n00, +1);
		
		n00.addOutputNeuron(n11);
		n11.addInputNeuron(n00, +1);
		
		n00.addOutputNeuron(n12);
		n12.addInputNeuron(n00, +1);
		
		n00.addOutputNeuron(n13);
		n13.addInputNeuron(n00, +1);
		
		
		n01.addOutputNeuron(n10);
		n10.addInputNeuron(n01, +1);
		
		n01.addOutputNeuron(n11);
		n11.addInputNeuron(n01, +1);
		
		n01.addOutputNeuron(n12);
		n12.addInputNeuron(n01, +1);
		
		n01.addOutputNeuron(n13);
		n13.addInputNeuron(n01, +1);
		
		
		for(Neuron n : neurons) {
			n.clearInputSpikes();
		}
		
		
		double[] sensoryInput00 = new double[NUMBER_OF_FRAMES];
		double[] sensoryInput01 = new double[NUMBER_OF_FRAMES];
		for(int idx = 0; idx < sensoryInput00.length; idx++) {
			int m16 = idx % 16;
			sensoryInput00[idx] = (m16 < 4) ? +0.6 : 0;								// 0 1 2 3
			sensoryInput01[idx] = (m16 >= 8 && m16 < 12) ? +0.6 : 0;				// 8 9 10 11
		}
		
		
		for(int frameIndex = 0; frameIndex < sensoryInput00.length; frameIndex++) {
			System.out.print(frameIndex);
			for(Neuron n : neurons) {
				System.out.print("\t" + n.getPotential(frameIndex));
				System.out.print("\t" + n.inputs.get(n00) + "\t" + n.inputs.get(n01));
			}
			System.out.println();
			
			//System.out.println("\t10: " + n10.inputs.get(n00) + "\t" + n10.inputs.get(n01));
			//System.out.println("\t11: " + n11.inputs.get(n00) + "\t" + n11.inputs.get(n01));
			//System.out.println("\t12: " + n12.inputs.get(n00) + "\t" + n12.inputs.get(n01));
			//System.out.println("\t13: " + n13.inputs.get(n00) + "\t" + n13.inputs.get(n01));
			
			
			//add any sensory events that may have happened in this frame
			n00.addSensoryInput(frameIndex, sensoryInput00[frameIndex], true);
			n01.addSensoryInput(frameIndex, sensoryInput01[frameIndex], true);

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
				//if(positiveActiveNeurons != null && negativeUnactiveNeurons != null) {
				//	if(positiveActiveNeurons.contains(n) || negativeUnactiveNeurons.contains(n)) {
				//		continue;
				//	}
				//}
				if(n.getLayer() == 1) {
					continue;
				}
				
				n.spikeOut(frameIndex);
			}
			
			//forced learning
			//if(positiveActiveNeurons != null) {
			//	for(Neuron n : positiveActiveNeurons) {
			//		n.learn(frameIndex, true, 0, maxLearningDepth);
			//	}
			//}
			
			//if(negativeUnactiveNeurons != null) {
			//	for(Neuron n : negativeUnactiveNeurons) {
			//		n.learn(frameIndex, false, 0, maxLearningDepth);
			//	}
			//}
			
			if(sensoryInput00[frameIndex] > 0) {
				//System.out.println("00 on");
				n10.learn(frameIndex, true, 0, 0);
				n11.learn(frameIndex, false, 0, 0);
			}
			
			//if(sensoryInput00[frameIndex] > 0 && sensoryInput01[frameIndex] > 0) {
			//	n11.learn(frameIndex, true, 0, 0);
			//}
			
			if(sensoryInput01[frameIndex] > 0) {
				//System.out.println("01 on");
				n12.learn(frameIndex, true, 0, 0);
				n13.learn(frameIndex, false, 0, 0);
			}
			
			


			//paint and write image to image stream
			//if(doWrite) {
				//paint image
				Graphics2D g = (Graphics2D) image.getGraphics();
				g.setBackground(Color.BLACK);
				for(Neuron n : neurons) {
					n.paint(frameIndex, g);
				}
				
				writer.encodeVideo(0, image, (long)(TIME_UNITS_PER_FRAME*frameIndex), FRAME_TIME_UNIT);
			//}
		}
		
		//if(doWrite) {
		//	writer.encodeAudio(1, samples, clock, frameTimeUnit);
		//}
		
		writer.close();
	}
}
