package net.florianx.jperfcounters.core;

public class IncrementalCounter {
	CounterData cd;
	String name;
	int index;
	
	IncrementalCounter(CounterData cd, String name, int index) {
		this.cd = cd;
		this.name = name;
		this.index = index;
	}
	
	public void inc() {
		long w = cd.startUpdate();
		cd.inc(this);
		cd.endUpdate(w);
	}
	public void add(long value) {
		long w = cd.startUpdate();
		cd.add(this, value);
		cd.endUpdate(w);
	}
}