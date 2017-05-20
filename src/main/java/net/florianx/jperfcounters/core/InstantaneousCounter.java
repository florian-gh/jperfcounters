package net.florianx.jperfcounters.core;

public class InstantaneousCounter {
	CounterData cd;
	String name;
	int index;
	
	InstantaneousCounter(CounterData cd, String name, int index) {
		this.cd = cd;
		this.name = name;
		this.index = index;
	}
	
	public void inc() {
		long w = cd.startUpdate();
		cd.inc(this);
		cd.endUpdate(w);
	}
	public void dec() {
		long w = cd.startUpdate();
		cd.dec(this);
		cd.endUpdate(w);
	}
	public void add(long value) {
		long w = cd.startUpdate();
		cd.add(this, value);
		cd.endUpdate(w);
	}
	public void set(long value) {
		// no need for startUpdate(), endUpdate() here
		cd.set(this, value);
	}
}