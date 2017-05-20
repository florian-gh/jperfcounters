package net.florianx.jperfcounters.core;

public class MeasureCounter {
	CounterData cd;
	String name;
	int index;
	
	MeasureCounter(CounterData cd, String name, int index) {
		this.cd = cd;
		this.name = name;
		this.index = index;
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