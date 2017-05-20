package net.florianx.jperfcounters.core;



public class HiResCounter<E extends Enum<?>> {
	CounterData cd;
	String name;
	int index;
	Enum<?>[] values;
	
	HiResCounter(CounterData cd, String name, E[] values, int index) {
		this.cd = cd;
		this.name = name;
		this.index = index;
		this.values = values;
	}

	public int getStateCount() {
		return values.length;
	}
	
	public void setState(E state) {
		cd.setState(this, state);
	}
}