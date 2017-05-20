package net.florianx.jperfcounters.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

public class SampleCollector {

	static class SlotData {
		long slot;
		long timestamp;
		HashMap<String, HashMap<String, SampleData>> entry;
	}
	
	private HashMap<String, SampleMetadata> metadata;
	private Ring<SlotData> history;
	private long currentSlot;
	private SlotData currentSlotData;
	
	public SampleCollector(int historySize) {
		metadata = new HashMap<>();
		history = new Ring<SlotData>(historySize);
		currentSlot = 0;
	}
	
	public void startSlot() {
		currentSlotData = new SlotData();
		currentSlotData.slot = currentSlot;
		currentSlotData.entry = new HashMap<>();
		currentSlotData.timestamp = System.currentTimeMillis();
	}
	
	public void add(String name, String instance, SampleData sample) {
		HashMap<String, SampleData> h1 = currentSlotData.entry.get(name);
		if (h1 == null) {
			h1 = new HashMap<String, SampleData>();
			currentSlotData.entry.put(name, h1);
			metadata.put(name, sample.metadata);
		}
		SampleData old = h1.put(instance, sample);
		if (old != null) {
			throw new InternalError("Duplicate instance: " + name + ":" + instance);
		}
		
System.out.println("Added sample for " + name + ":" + instance + " in slot " + currentSlot);
	}
	
	public long getLastSlot() {
		return currentSlot - 1;
	}
	
	/**
	 * 
	 * {
	 *   "timestamps": [ ... <array of timestamps> ],
	 *   "slots": [ ... <array of slot nummbers> ],
	 *   "counters": {
	 *     <counter collection>: {
	 *       <counter name>: {
	 *         <instance>: { "value": [ <value>, ... ], ... <other attributes of the counter, as array of values> }
	 *         ... <for all instances>
	 *       },
	 *       ...
	 *     },
	 *     ...
	 *   }
	 * }
	 * 
	 */
	
	public String toJson(long lastSlot, int entries) {
		Gson gson = new GsonBuilder().setPrettyPrinting().create();
		JsonArray list = new JsonArray();
		
		SlotData[] buffer = new SlotData[entries];
		int cnt = history.getEntries(buffer, lastSlot);

		JsonArray jTimestamps = new JsonArray();
		JsonArray jSlots = new JsonArray();
		for (int i = 0; i < cnt; i++) {
			jTimestamps.add(buffer[i].timestamp);
			jSlots.add(buffer[i].slot);
		}
		
		/*
		* 
		* buffer: [
		*   collectionName: { (buffer[i].entry)
		*     instanceName: {
		*       SampleData
		*     }
		*   }
		* ]
		* 
		* We want:
		* 
		* {
		*   collectionName: {
		*     instanceName: {
		*       counterName: {
		*         val: [...],
		*         valCumulative: [...]
		*       }
		*     }
		*   }
		* }
		* 
		* We must do:
		* 
		* for each collection
		*   for each instance
		*     for each counter of the collection
		*       get all values for "value"
		*       get all values for "valueCumulative" etc., according to counter type
		*
		*/
		
		
		
		// for each collection
		JsonObject jCollections = new JsonObject();
		ArrayList<String> collectionNames = new ArrayList<>(metadata.keySet());
		Collections.sort(collectionNames);
		for (String collectionName: collectionNames) {
			// all instances ever mentioned in all counters of this collection
			Set<String> instances = new HashSet<String>();
			for (int i = 0; i < cnt; i++) {
				HashMap<String, SampleData> c = buffer[i].entry.get(collectionName);
				if (c != null) {
					instances.addAll(c.keySet());
				}
			}
			SampleMetadata collectionMd = metadata.get(collectionName);
			JsonObject jInstances = new JsonObject();
			// for each instance
			for (String instance: instances) {
				// for each counter of the collection
				JsonObject jCounters = new JsonObject();
				
				// incremental counters
				for (int cn = 0; cn < collectionMd.incrementalCounterNames.length; cn++) {
					String counterName = collectionMd.incrementalCounterNames[cn];
					// get all values for this counter in the collection/instance, for all slots
					JsonObject jCounterValues = getIncrementalCounterValues(
							collectionName, instance, cn, buffer, cnt);
					// Add values in the counter
					jCounters.add(counterName, jCounterValues);
				} // for each counter of the collection
				
				// instantaneous counters
				for (int cn = 0; cn < collectionMd.instantaneousCounterNames.length; cn++) {
					String counterName = collectionMd.instantaneousCounterNames[cn];
					// get all values for this counter in the collection/instance, for all slots
					JsonObject jCounterValues = getInstantaneousCounterValues(
							collectionName, instance, cn, buffer, cnt);
					// Add values in the counter
					jCounters.add(counterName, jCounterValues);
				} // for each counter of the collection

				// measure counters
				for (int cn = 0; cn < collectionMd.measureCounterNames.length; cn++) {
					String counterName = collectionMd.measureCounterNames[cn];
					// get all values for this counter in the collection/instance, for all slots
					JsonObject jCounterValues = getMeasureCounterValues(
							collectionName, instance, cn, buffer, cnt);
					// Add values in the counter
					jCounters.add(counterName, jCounterValues);
				} // for each counter of the collection
				
				// Add the counter into the instance
				jInstances.add(instance, jCounters);
				
			} // for each instance
			jCollections.add(collectionName, jInstances);
		} // for each collection

		JsonObject jResult = new JsonObject();
		jResult.add("timestamps", jTimestamps);
		jResult.add("slots", jSlots);
		jResult.add("counters", jCollections);


		String r = gson.toJson(jResult);

		
		return r;
	}
	
	private JsonObject getIncrementalCounterValues(String collectionName, String instance, int counterIndex, SlotData[] buffer, int cnt) {
		JsonObject jCounter = new JsonObject();
		// get all values for this counter in the collection/instance, for all slots
		JsonArray jVal = new JsonArray();
		JsonArray jValCumulative = new JsonArray();
		for (int i = 0; i < cnt; i++) { // for each slot
			HashMap<String, SampleData> collectionSampleData = buffer[i].entry.get(collectionName);
			SampleData sampleData = (collectionSampleData != null ? collectionSampleData.get(instance) : null);
			if (sampleData == null) {
				jVal.add(JsonNull.INSTANCE);
				jValCumulative.add(JsonNull.INSTANCE);
			} else {
				jVal.add(sampleData.incrementalCounters[counterIndex]);
				jValCumulative.add(sampleData.incrementalCountersCumulative[counterIndex]);
			}
		}
		jCounter.add("values", jVal);
		jCounter.add("valuesCumulative", jValCumulative);
		return jCounter;
	}
	
	private JsonObject getInstantaneousCounterValues(String collectionName, String instance, int counterIndex, SlotData[] buffer, int cnt) {
		JsonObject jCounter = new JsonObject();
		// get all values for this counter in the collection/instance, for all slots
		JsonArray jVal = new JsonArray();
		JsonArray jDelta = new JsonArray();
		for (int i = 0; i < cnt; i++) { // for each slot
			HashMap<String, SampleData> collectionSampleData = buffer[i].entry.get(collectionName);
			SampleData sampleData = (collectionSampleData != null ? collectionSampleData.get(instance) : null);
			if (sampleData == null) {
				jVal.add(JsonNull.INSTANCE);
				jDelta.add(JsonNull.INSTANCE);
			} else {
				jVal.add(sampleData.instantaneousCountersValue[counterIndex]);
				jDelta.add(sampleData.instantaneousCountersDelta[counterIndex]);
			}
		}
		jCounter.add("values", jVal);
		jCounter.add("delta", jDelta);
		return jCounter;
	}

	private JsonObject getMeasureCounterValues(String collectionName, String instance, int counterIndex, SlotData[] buffer, int cnt) {
		JsonObject jCounter = new JsonObject();
		// get all values for this counter in the collection/instance, for all slots
		JsonArray jValue = new JsonArray();
		JsonArray jCount = new JsonArray();
		JsonArray jValueCumulative = new JsonArray();
		JsonArray jCountCumulative = new JsonArray();
		
		for (int i = 0; i < cnt; i++) { // for each slot
			HashMap<String, SampleData> collectionSampleData = buffer[i].entry.get(collectionName);
			SampleData sampleData = (collectionSampleData != null ? collectionSampleData.get(instance) : null);
			if (sampleData == null) {
				jValue.add(JsonNull.INSTANCE);
				jCount.add(JsonNull.INSTANCE);
				jValueCumulative.add(JsonNull.INSTANCE);
				jCountCumulative.add(JsonNull.INSTANCE);
			} else {
				jValue.add(sampleData.measureCountersValues[counterIndex]);
				jCount.add(sampleData.measureCountersCounts[counterIndex]);
				jValueCumulative.add(sampleData.measureCountersValuesCumulative[counterIndex]);
				jCountCumulative.add(sampleData.measureCountersCountsCumulative[counterIndex]);
			}
		}
		jCounter.add("values", jValue);
		jCounter.add("counts", jCount);
		jCounter.add("valuesCumulative", jValueCumulative);
		jCounter.add("countsCumulative", jCountCumulative);
		return jCounter;
	}

	private JsonObject getHiResCounterValues(String collectionName, String instance, int counterIndex, SlotData[] buffer, int cnt) {
		JsonObject jCounter = new JsonObject();
		// get all values for this counter in the collection/instance, for all slots
		JsonArray jSamples = new JsonArray();
		JsonArray jTime = new JsonArray(); // array of arrays of percentages as doubles
		
//		for (int i = 0; i < cnt; i++) { // for each slot
//			HashMap<String, SampleData> collectionSampleData = buffer[i].entry.get(collectionName);
//			SampleData sampleData = (collectionSampleData != null ? collectionSampleData.get(instance) : null);
//			if (sampleData == null) {
//				jValue.add(JsonNull.INSTANCE);
//				jCount.add(JsonNull.INSTANCE);
//				jValueCumulative.add(JsonNull.INSTANCE);
//				jCountCumulative.add(JsonNull.INSTANCE);
//			} else {
//				jValue.add(sampleData.hiResSlots.measureCountersValues[counterIndex]);
//				jCount.add(sampleData.measureCountersCounts[counterIndex]);
//				jValueCumulative.add(sampleData.measureCountersValuesCumulative[counterIndex]);
//				jCountCumulative.add(sampleData.measureCountersCountsCumulative[counterIndex]);
//			}
//		}
//		jCounter.add("values", jValue);
//		jCounter.add("counts", jCount);
//		jCounter.add("valuesCumulative", jValueCumulative);
//		jCounter.add("countsCumulative", jCountCumulative);
		return jCounter;
	}
	
//		JsonArray timestamps = new JsonArray();
//
//		
//		for (String name: names) {
//			HashMap<String, SampleData> instances = slotData.entry.get(name);
//			
//			
//			
//		}
//		
//	
//		SampleMetadata md = metadata.get(name);
//		for (int i = 0; i < md.incrementalCounterNames.length; i++) {
//			String n = md.incrementalCounterNames[i];
//			long v = sample.incrementalCounters[i];
//			long cume = sample.incrementalCountersCumulative[i];
//System.out.println("    incremental " + name + ":" + instance + ":" + n + " = " + v + " (total " + cume + ")");
//		}
//		for (int i = 0; i < md.instantaneousCounterNames.length; i++) {
//			String n = md.instantaneousCounterNames[i];
//			long v = sample.instantaneousCountersValue[i];
//			long d = sample.instantaneousCountersDelta[i];
//System.out.println("    instantaneous " + name + ":" + instance + ":" + n + " = " + v + " (" + String.format("%+d", d) + ")");
//		}
//		for (int i = 0; i < md.measureCounterNames.length; i++) {
//			String n = md.measureCounterNames[i];
//			long v = sample.measureCountersValues[i];
//			long c = sample.measureCountersCounts[i];
//			long vcume = sample.measureCountersValuesCumulative[i];
//			long ccume = sample.measureCountersCountsCumulative[i];
//System.out.println("    measure " + name + ":" + instance + ":" + n + " = " + v + "/" + c
//				+ " (total " + vcume + "/" + ccume + ")");
//		}
//		StringBuilder sb = new StringBuilder();
//		for (int i = 0; i < md.hiResCounterNames.length; i++) {
//			sb.setLength(0);
//			long cnt = 0;
//			for (int j = 0; j < sample.hiResSlots[i].length; j++) {
//				cnt += sample.hiResSlots[i][j];
//			}
//			sb.append("    hiRes " + name + ":" + instance + ":" + md.hiResCounterNames[i] + " = count: " + cnt + ", ");
//			for (int j = 0; j < sample.hiResSlots[i].length; j++) {
//				sb.append(
//						(j > 0 ? ", " : "")
//						+ md.hiResCounterStates[i][j] + ": "
//						+ String.format("%02.1f", (double)sample.hiResSlots[i][j] / (double)cnt));
//			}
//System.out.println(sb.toString());
//		}
//		for (int i = 0; i < md.histogramCounterNames.length; i++) {
//			String n = md.histogramCounterNames[i];
//			byte[] v = sample.histograms[i];
//			ByteBuffer buffer = ByteBuffer.wrap(v);
//			Histogram h;
//			try {
//				h = Histogram.decodeFromCompressedByteBuffer(buffer, 0);
//			} catch (DataFormatException e) {
//				System.out.println("Exception " + e);
//				continue;
//			}
//			
//			ByteArrayOutputStream bo = new ByteArrayOutputStream();
//			PrintStream ps = new PrintStream(bo);
//			h.outputPercentileDistribution(ps, 1.0);
//			ps.flush();
//System.out.println("    histogram " + name + ":" + instance + ":" + n);
//System.out.println(bo.toString());
//		}
//	
	
	public void endSlot() {
		history.add(currentSlotData);
		++currentSlot;
		currentSlotData = null;
	}
	
}
