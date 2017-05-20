package net.florianx.jperfcounters.core;

public interface PerfSampleable {
	enum SampleType {
		HiFreq,
		Normal,
		Histo
	}
	public void sampleHiRes();
	public void sample(SampleType sampleType, SampleCollector sampleCollector);
}
