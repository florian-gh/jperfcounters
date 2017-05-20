package net.florianx.jperfcounters.core;

import org.HdrHistogram.AtomicHistogram;
import org.HdrHistogram.Histogram;

public class HistogramCounter {
	CounterData cd;
	String name;
	int index;
	
	HistogramCounter(CounterData cd, String name, int index) {
		this.cd = cd;
		this.name = name;
		this.index = index;
	}

	Histogram createHistogram() {
		return new AtomicHistogram(10, 1000 * 60, 2); // from 10ms to 1 minute, 2 significant digits
	}
	
	public void recordValue(long value) {
		long w = cd.startUpdate();
		cd.recordValue(this, value);
		cd.endUpdate(w);
	}
	public void recordValueWithCount(long value, long count) {
		long w = cd.startUpdate();
		cd.recordValueWithCount(this, value, count);
		cd.endUpdate(w);
	}
}