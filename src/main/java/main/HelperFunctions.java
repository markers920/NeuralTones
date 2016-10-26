package main;

import java.awt.Color;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class HelperFunctions {

	// [min, max)
	public static double randomRange(Random random, double min, double max) {
		double r = random.nextDouble();
		//System.out.println(min + " " + max + " " + r);
		return (r*(max-min)) + min;
	}

	//value must be in [0,1]
	public static Color getGradiantColor(Color low, Color high, double value) {
		//System.out.println(value);
		float rd = (float) ((low.getRed() + value*(high.getRed() - low.getRed())) / 255.0 );
		float gd = (float) ((low.getGreen() + value*(high.getGreen() - low.getGreen())) / 255.0 );
		float bd = (float) ((low.getBlue() + value*(high.getBlue() - low.getBlue())) / 255.0 );
		float ad = (float) ((low.getAlpha() + value*(high.getAlpha() - low.getAlpha())) / 255.0 );

		return new Color(rd, gd, bd, ad);
	}


	// http://introcs.cs.princeton.edu/java/97data/FFT.java.html
	// compute the FFT of x[], assuming its length is a power of 2
	public static Complex[] fft(Complex[] x) {
		int N = x.length;

		// base case
		if (N == 1) return new Complex[] { x[0] };

		// radix 2 Cooley-Tukey FFT
		if (N % 2 != 0) { throw new RuntimeException("N is not a power of 2"); }

		// fft of even terms
		Complex[] even = new Complex[N/2];
		for (int k = 0; k < N/2; k++) {
			even[k] = x[2*k];
		}
		Complex[] q = fft(even);

		// fft of odd terms
		Complex[] odd  = even;  // reuse the array
		for (int k = 0; k < N/2; k++) {
			odd[k] = x[2*k + 1];
		}
		Complex[] r = fft(odd);

		// combine
		Complex[] y = new Complex[N];
		for (int k = 0; k < N/2; k++) {
			double kth = -2 * k * Math.PI / N;
			Complex wk = new Complex(Math.cos(kth), Math.sin(kth));
			y[k]       = q[k].plus(wk.times(r[k]));
			y[k + N/2] = q[k].minus(wk.times(r[k]));
		}
		return y;
	}
	
	
	
	public static List<Short> getAudioSamples(int size, double frequency, int audiosampleRate, double alpha) throws IOException {
		List<Short> samples = new ArrayList<Short>();
		double amplitude = Short.MAX_VALUE;
		//frequency = (periods/second)
		//sample rate = (samples/second)
		//frquency / sample rate = (periods/sample)
		for(int idx = 0; idx < size; idx++) {
			double v = amplitude * Math.sin(2*Math.PI*idx*(frequency/audiosampleRate));
			v *= tukeyWindow(idx, size, alpha);
			samples.add((short) v);
		}

		return samples;
	}
	
	
	/**
	 * 
	 * @param samples - audio samples 
	 * @param audioSampleRate - samples per second
	 * @param framesPerSecond - number of video frames per second
	 * @param numberOfFrames - number of desired spectrogram frames
	 * @param windowSize - number of samples AND fft size
	 * @return
	 */
	public static double[][] makeSpectrogram(short[] samples, double audioSampleRate, double framesPerSecond, int numberOfFrames, int windowSize) {
		double[][] spectrogram = new double[numberOfFrames][windowSize];
		//frames_per_second -> (frames/sec)
		//audio_sample_rate -> (samples/sec)
		// (1/frames_per_sec)(audio_sample_rate) -> (sec/frame)(samples/sec) -> (samples/frame)
		int samplesPerFrame = (int) (audioSampleRate / framesPerSecond);
		// 44100/60 = 735  audio samples per image frame	8000/60 = 133.3
		// 44100/30 = 1470									8000/30 = 266.6
		// 44100/15 = 2940									8000/15 = 533.3
		// 44100/10 = 4410									8000/10 = 800
		// 44100/5  = 8820									8000/5  = 1600.0
		// 44100 can capture 0 Hz to 22050 Hz ...			8000 can capture 0 Hz to 4 kHz
		// 4096 fft gives  5.4 Hz per point					
		// 2048 fft gives 10.8 Hz per point					
		// 1024 fft gives 21.5 Hz per point					1024 fft can capture  3.906 Hz per point
		//  512 fft gives 43 Hz per point					 512 fft can capture  7.812 Hz per point
		//  256 fft gives 86 Hz per point					 256 fft can capture 15.625 Hz per point
		//grab something thats a power of 2 and smaller than that
		//int windowSize = NUMBER_NEURONS_PER_LAYER_ARRAY[0] * 2;		//multiply by 2 b/c of symmetry in spectrum
		//int windowSize = 4096;
		Complex[] audioSamples = new Complex[windowSize];
		double spectrogramMax = 0.0;
		for(int frameIndex = 0; frameIndex < numberOfFrames; frameIndex++) {
			int frameSampleStart = frameIndex * samplesPerFrame;
			for(int sampleIndex = 0; sampleIndex < windowSize; sampleIndex++) {
				audioSamples[sampleIndex] = new Complex(
						samples[frameSampleStart + sampleIndex], 
						0.0);
			}
			Complex[] fft = HelperFunctions.fft(audioSamples);	//get the fft

			for(int sampleIndex = 0; sampleIndex < windowSize; sampleIndex++) {
				spectrogram[frameIndex][sampleIndex] = fft[sampleIndex].abs();
				spectrogramMax = Math.max(spectrogramMax, spectrogram[frameIndex][sampleIndex]);
			}
		}

		for(int frameIndex = 0; frameIndex < numberOfFrames; frameIndex++) {
			for(int sampleIndex = 0; sampleIndex < windowSize; sampleIndex++) {
				spectrogram[frameIndex][sampleIndex] /= spectrogramMax;
				//System.out.format("%2.1f ", spectrogram[frameIndex][sampleIndex]);
			}
			//System.out.println();
		}

		return spectrogram;
	}

	// https://en.wikipedia.org/wiki/Window_function#Tukey_window
	public static double tukeyWindow(int n, int N, double a) throws IOException {
		if(n < 0 || n >= N) {
			throw new IOException("n out of bounds: " + n + " / " + N);
		}
		double lowCut = (a*(N-1)) / 2.0;
		double highCut = (N-1)*(1.0 - (a/2.0));
		
		if(n < lowCut) {
			double in = ((2*n)/(a*(N-1))) - 1.0;
			return (0.5)*(1 + Math.cos(Math.PI * in));
		}
		
		else if(lowCut <= n && n <= highCut) {
			return 1.0;
		}
		
		else {	//if(highCut < n) {
			double in = ((2*n)/(a*(N-1))) - (2.0/a) + 1.0;
			return (0.5)*(1 + Math.cos(Math.PI * in));
		}
	}

}
