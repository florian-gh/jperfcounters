package net.florianx.jperfcounters.core;

public class Reporter {

	private SampleCollector sampleCollector;
	
	Reporter(SampleCollector sampleCollector) {
		this.sampleCollector = sampleCollector;
	}
	
	public long getLast() {
		return sampleCollector.getLastSlot();
	}
	
	public String getAsJson(long lastSlot, int entries) {
		return sampleCollector.toJson(lastSlot, entries);
	}
	
}
