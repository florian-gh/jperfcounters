package net.florianx.jperfcounters.core;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReferenceArray;

import org.HdrHistogram.Histogram;

public class CounterDataBuilder {
	
	private CounterMgr counterMgr;
	private ArrayList<IncrementalCounter> incrementalCounters;
	private ArrayList<InstantaneousCounter> instantaneousCounters;
	private ArrayList<MeasureCounter> measureCounters;
	private ArrayList<HistogramCounter> histogramCounters;
	private ArrayList<HiResCounter<? extends Enum<?>>> hiResCounters;
	private CounterData counterData;
	
	public CounterDataBuilder(CounterMgr counterMgr, String name, String instance) {
		this.counterMgr = counterMgr;
		counterData = new CounterData(name, instance);
		incrementalCounters = new ArrayList<>();
		instantaneousCounters = new ArrayList<>();
		measureCounters = new ArrayList<>();
		histogramCounters = new ArrayList<>();
		hiResCounters = new ArrayList<>();
	}
	
	public IncrementalCounter createIncrementalCounter(String name) {
		IncrementalCounter c = new IncrementalCounter(counterData, name, incrementalCounters.size());
		incrementalCounters.add(c);
		return c;
	}

	public InstantaneousCounter createInstantaneousCounter(String name) {
		InstantaneousCounter c = new InstantaneousCounter(counterData, name, instantaneousCounters.size());
		instantaneousCounters.add(c);
		return c;
	}

	public MeasureCounter createMeasureCounter(String name) {
		MeasureCounter c = new MeasureCounter(counterData, name, measureCounters.size());
		measureCounters.add(c);
		return c;
	}

	public HistogramCounter createHistogramCounter(String name) {
		HistogramCounter c = new HistogramCounter(counterData, name, histogramCounters.size());
		histogramCounters.add(c);
		return c;
	}
	
	public <E extends Enum<?>> HiResCounter<E> createHiResCounter(String name, E[] stateValues) {
		HiResCounter<E> c = new HiResCounter<E>(counterData, name, stateValues, hiResCounters.size());
		hiResCounters.add(c);
		return c;
	}
	
	public CounterData create() {
		counterData.init(incrementalCounters, instantaneousCounters, measureCounters, histogramCounters, hiResCounters);
		counterMgr.registerCounterData(counterData);
		
		CounterData cd = counterData;
		counterData = null;
		return cd;
	}
}



