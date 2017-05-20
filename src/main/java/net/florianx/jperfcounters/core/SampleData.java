package net.florianx.jperfcounters.core;

import java.nio.ByteBuffer;

import net.florianx.jperfcounters.core.CounterData.Data;

public class SampleData {
	SampleMetadata metadata;
	
	long[] incrementalCounters;
	long[] incrementalCountersCumulative;
	long[] instantaneousCountersValue;
	long[] instantaneousCountersDelta;
	long[] measureCountersCounts;
	long[] measureCountersValues;
	long[] measureCountersCountsCumulative;
	long[] measureCountersValuesCumulative;
	byte[][] histograms; // compressed histogram to byte[]
	long[][] hiResSlots;
	
	SampleData(CounterData cd, Data data, SampleData lastSample) {
		metadata = cd.getSampleMetadata();
		
		incrementalCounters = new long[cd.incrementalCounters.length];
		incrementalCountersCumulative = new long[cd.incrementalCounters.length];
		instantaneousCountersValue = new long[cd.instantaneousCounters.length];
		instantaneousCountersDelta = new long[cd.instantaneousCounters.length];
		measureCountersCounts = new long[cd.measureCounters.length];
		measureCountersCountsCumulative = new long[cd.measureCounters.length];
		measureCountersValues = new long[cd.measureCounters.length];
		measureCountersValuesCumulative = new long[cd.measureCounters.length];
		histograms = new byte[cd.histogramCounters.length][];
		hiResSlots = new long[cd.hiResCounters.length][];
		for (int i = 0; i < cd.hiResCounters.length; i++) {
			hiResSlots[i] = new long[cd.hiResCounters[i].getStateCount()];
		}
		
		// copy 
		for (int i = 0; i < incrementalCounters.length; i++) {
			incrementalCounters[i] = data.incrementalCountersValues.get(i);
			incrementalCountersCumulative[i] =
					(lastSample != null ? lastSample.incrementalCountersCumulative[i] : 0)
					+ incrementalCounters[i];
		}
		for (int i = 0; i < instantaneousCountersValue.length; i++) {
			instantaneousCountersDelta[i] = data.instantaneousCountersValues.get(i);
			instantaneousCountersValue[i] =
					(lastSample != null ? lastSample.instantaneousCountersValue[i] : 0)
					+ instantaneousCountersDelta[i];
		}
		for (int i = 0; i < measureCountersCounts.length; i++) {
			measureCountersCounts[i] = data.measureCountersCounts.get(i);
			measureCountersValues[i] = data.measureCountersValues.get(i);
			
			measureCountersCountsCumulative[i] =
					(lastSample != null ? lastSample.measureCountersCountsCumulative[i] : 0)
					+ measureCountersCounts[i];
			measureCountersValuesCumulative[i] =
					(lastSample != null ? lastSample.measureCountersValuesCumulative[i] : 0)
					+ measureCountersValues[i];

		}
		for (int i = 0; i < data.histograms.length; i++) {
			ByteBuffer b = ByteBuffer.allocate(data.histograms[i].getNeededByteBufferCapacity());
			int written = data.histograms[i].encodeIntoCompressedByteBuffer(b);
			byte[] bytes = new byte[written];
			b.flip();
			b.get(bytes);
			histograms[i] = bytes;
		}
		for (int i = 0; i < hiResSlots.length; i++) {
			System.arraycopy(data.hiResSlots[i], 0, hiResSlots[i], 0, hiResSlots[i].length);
		}
	}

}
