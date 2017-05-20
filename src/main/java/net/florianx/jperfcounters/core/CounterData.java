package net.florianx.jperfcounters.core;

import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicIntegerArray;
import java.util.concurrent.atomic.AtomicLongArray;

import org.HdrHistogram.AtomicHistogram;
import org.HdrHistogram.Histogram;
import org.HdrHistogram.WriterReaderPhaser;


public class CounterData implements PerfSampleable {
	private String name;
	private String instance;
	
	IncrementalCounter[] incrementalCounters;
	InstantaneousCounter[] instantaneousCounters;
	MeasureCounter[] measureCounters;
	HistogramCounter[] histogramCounters;
	HiResCounter<?>[] hiResCounters;
	
	private SampleMetadata metadata;
	
	class Data {
		AtomicLongArray incrementalCountersValues;
		AtomicLongArray instantaneousCountersValues;
		AtomicLongArray measureCountersCounts;
		AtomicLongArray measureCountersValues;
		Histogram[] histograms;
		long[][] hiResSlots;
		
		Data() {
			incrementalCountersValues = new AtomicLongArray(incrementalCounters.length);
			instantaneousCountersValues = new AtomicLongArray(instantaneousCounters.length);
			measureCountersCounts = new AtomicLongArray(measureCounters.length);
			measureCountersValues = new AtomicLongArray(measureCounters.length);
			histograms = new AtomicHistogram[histogramCounters.length];
			for (int i = 0; i < histogramCounters.length; i++) {
				histograms[i] = histogramCounters[i].createHistogram();
			}
			hiResSlots = new long[hiResCounters.length][];
			for (int i = 0; i < hiResCounters.length; i++) {
				hiResSlots[i] = new long[hiResCounters[i].getStateCount()];
			}
		}
	}
	
	private WriterReaderPhaser wrp;
	private volatile Data current;
	private Data other;
	private SampleData lastSample;
	
	// Instantaneous counters: keep current value in instantaneousCurrentValues,
	// maintain increments/decrements in Data
	private AtomicLongArray instantaneousCurrentValues;
	private AtomicIntegerArray hiResState;
		
	public CounterData(String name, String instance) {
		this.name = name;
		this.instance = instance;
	}
	
	public String getName() {
		return name;
	}
	
	public String getInstance() {
		return instance;
	}

	public SampleMetadata getSampleMetadata() {
		return metadata;
	}
	
	void init(
			ArrayList<IncrementalCounter> incrementalCounters,
			ArrayList<InstantaneousCounter> instantaneousCounters,
			ArrayList<MeasureCounter> measureCounters,
			ArrayList<HistogramCounter> histogramCounters,
			ArrayList<HiResCounter<? extends Enum<?>>> hiResCounters) {
		
		this.incrementalCounters = incrementalCounters.toArray(new IncrementalCounter[incrementalCounters.size()]);
		this.instantaneousCounters
				= instantaneousCounters.toArray(new InstantaneousCounter[instantaneousCounters.size()]);
		this.measureCounters = measureCounters.toArray(new MeasureCounter[measureCounters.size()]);
		this.histogramCounters = histogramCounters.toArray(new HistogramCounter[histogramCounters.size()]);
		this.hiResCounters = hiResCounters.toArray(new HiResCounter<?>[hiResCounters.size()]);
		
		SampleMetadata md = new SampleMetadata();
		md.incrementalCounterNames = new String[this.incrementalCounters.length];
		for (int i = 0; i < this.incrementalCounters.length; i++) {
			md.incrementalCounterNames[i] = this.incrementalCounters[i].name;
		}
		md.instantaneousCounterNames = new String[this.instantaneousCounters.length];
		for (int i = 0; i < this.instantaneousCounters.length; i++) {
			md.instantaneousCounterNames[i] = this.instantaneousCounters[i].name;
		}
		md.measureCounterNames = new String[this.measureCounters.length];
		for (int i = 0; i < this.measureCounters.length; i++) {
			md.measureCounterNames[i] = this.measureCounters[i].name;
		}
		md.histogramCounterNames = new String[this.histogramCounters.length];
		for (int i = 0; i < this.histogramCounters.length; i++) {
			md.histogramCounterNames[i] = this.histogramCounters[i].name;
		}
		md.hiResCounterNames = new String[this.hiResCounters.length];
		md.hiResCounterStates = new String[this.hiResCounters.length][];
		for (int i = 0; i < this.hiResCounters.length; i++) {
			md.hiResCounterNames[i] = this.hiResCounters[i].name;
			md.hiResCounterStates[i] = new String[this.hiResCounters[i].values.length];
			for (int j = 0; j < md.hiResCounterStates[i].length; j++) {
				md.hiResCounterStates[i][j] = this.hiResCounters[i].values[j].toString();
			}
		}
		metadata = md;
		
		instantaneousCurrentValues = new AtomicLongArray(instantaneousCounters.size());
		hiResState = new AtomicIntegerArray(hiResCounters.size());

		wrp = new WriterReaderPhaser();
		
		current = new Data();
		other = new Data();
		lastSample = new SampleData(this, other, null); // start from zero
	}
	
	long startUpdate() {
		return wrp.writerCriticalSectionEnter();
	}
	
	void endUpdate(long w) {
		wrp.writerCriticalSectionExit(w);
	}
	
	// Incremental counter
	void inc(IncrementalCounter c) {
		long w = wrp.writerCriticalSectionEnter();
		current.incrementalCountersValues.incrementAndGet(c.index);
		wrp.writerCriticalSectionExit(w);
	}
	void add(IncrementalCounter c, long value) {
		long w = wrp.writerCriticalSectionEnter();
		current.incrementalCountersValues.addAndGet(c.index, value);
		wrp.writerCriticalSectionExit(w);
	}
	// Instantaneous counter
	void set(InstantaneousCounter c, long value) {
		long w = wrp.writerCriticalSectionEnter();
		long oldValue = instantaneousCurrentValues.getAndSet(c.index, value);
		Data d = current;
		d.instantaneousCountersValues.addAndGet(c.index, -oldValue + value);
		wrp.writerCriticalSectionExit(w);
	}
	void inc(InstantaneousCounter c) {
		long w = wrp.writerCriticalSectionEnter();
		instantaneousCurrentValues.incrementAndGet(c.index);
		current.instantaneousCountersValues.incrementAndGet(c.index);
		wrp.writerCriticalSectionExit(w);
	}
	public void add(InstantaneousCounter c, long value) {
		long w = wrp.writerCriticalSectionEnter();
		instantaneousCurrentValues.addAndGet(c.index, value);
		current.instantaneousCountersValues.addAndGet(c.index, value);
		wrp.writerCriticalSectionExit(w);
	}
	public void dec(InstantaneousCounter c) {
		long w = wrp.writerCriticalSectionEnter();
		instantaneousCurrentValues.decrementAndGet(c.index);
		current.instantaneousCountersValues.decrementAndGet(c.index);
		wrp.writerCriticalSectionExit(w);
	}
	// Measure counter
	public void recordValue(MeasureCounter c, long value) {
		long w = wrp.writerCriticalSectionEnter();
		Data d = current;
		d.measureCountersCounts.incrementAndGet(c.index);
		d.measureCountersValues.addAndGet(c.index, value);
		wrp.writerCriticalSectionExit(w);
	}
	public void recordValueWithCount(MeasureCounter c, long value, long count) {
		long w = wrp.writerCriticalSectionEnter();
		Data d = current;
		d.measureCountersCounts.addAndGet(c.index, count);
		d.measureCountersValues.addAndGet(c.index, value);
		wrp.writerCriticalSectionExit(w);
	}
	// Histogram
	public void recordValue(HistogramCounter c, long value) {
		long w = wrp.writerCriticalSectionEnter();
		current.histograms[c.index].recordValue(value);
		wrp.writerCriticalSectionExit(w);
	}
	public void recordValueWithCount(HistogramCounter c, long value, long count) {
		long w = wrp.writerCriticalSectionEnter();
		current.histograms[c.index].recordValueWithCount(value, count);
		wrp.writerCriticalSectionExit(w);
	}
	// Hi res counter
	public <E extends Enum<?>> void setState(HiResCounter<E> c, E state) {
		// does not involve Data, so does not need wrp critical section
		hiResState.set(c.index, state.ordinal());
	}
	
	public static class Setter {
		private CounterData counterData;
		private Data setterCurrent;
		private long w;
		
		public Setter(CounterData cd) {
			counterData = cd;
		}
		
		public void prepare() {
			w = counterData.wrp.writerCriticalSectionEnter();
			setterCurrent = counterData.current;
		}
		
		public void terminate() {
			counterData.wrp.writerCriticalSectionExit(w);
		}
		
		// Incremental counter
		public void inc(IncrementalCounter c) {
			setterCurrent.incrementalCountersValues.incrementAndGet(c.index);
		}
		public void add(IncrementalCounter c, long value) {
			setterCurrent.incrementalCountersValues.addAndGet(c.index, value);
		}
		// Instantaneous counter
		public void set(InstantaneousCounter c, long value) {
			long oldValue = counterData.instantaneousCurrentValues.getAndSet(c.index, value);
			setterCurrent.instantaneousCountersValues.addAndGet(c.index, -oldValue + value);
		}
		public void inc(InstantaneousCounter c) {
			counterData.instantaneousCurrentValues.incrementAndGet(c.index);
			setterCurrent.instantaneousCountersValues.incrementAndGet(c.index);
		}
		public void add(InstantaneousCounter c, long value) {
			counterData.instantaneousCurrentValues.addAndGet(c.index, value);
			setterCurrent.instantaneousCountersValues.addAndGet(c.index, value);
		}
		public void dec(InstantaneousCounter c) {
			counterData.instantaneousCurrentValues.decrementAndGet(c.index);
			setterCurrent.instantaneousCountersValues.decrementAndGet(c.index);
		}
		// Measure counter
		public void recordValue(MeasureCounter c, long value) {
			setterCurrent.measureCountersCounts.incrementAndGet(c.index);
			setterCurrent.measureCountersCounts.addAndGet(c.index, value);
		}
		public void recordValueWithCount(MeasureCounter c, long value, long count) {
			setterCurrent.measureCountersCounts.addAndGet(c.index, count);
			setterCurrent.measureCountersCounts.addAndGet(c.index, value);
		}
		// Histogram
		public void recordValue(HistogramCounter c, long value) {
			setterCurrent.histograms[c.index].recordValue(value);
		}
		public void recordValueWithCount(HistogramCounter c, long value, long count) {
			setterCurrent.histograms[c.index].recordValueWithCount(value, count);
		}
		// Hi res -- setting it in a consistent way with other counters is not applicable
	}

	@Override
	public void sampleHiRes() {
		Data data = current;
		for (int i = 0; i < hiResState.length(); i++) {
			int val = hiResState.get(i);
			++data.hiResSlots[i][val];
			// we can do this because we're the only writer ever in hiResSlots, and the only reader at this time
			// because the CounterMgr calls sampleHiRes() only sequentially
		}
	}
	
	@Override
	public void sample(SampleType sampleType, SampleCollector sampleCollector) {
		Data data = current;
		try {
			wrp.readerLock();
			
			if (sampleType == SampleType.Normal || sampleType == SampleType.Histo) {
			
				// reset other
				for (int i = 0; i < other.incrementalCountersValues.length(); i++) {
					other.incrementalCountersValues.set(i, 0);
				}
				for (int i = 0; i < other.instantaneousCountersValues.length(); i++) {
					other.instantaneousCountersValues.set(i, 0);
				}
				for (int i = 0; i < other.measureCountersCounts.length(); i++) {
					other.measureCountersCounts.set(i, 0);
					other.measureCountersValues.set(i, 0);
				}
				for (int i = 0; i < other.histograms.length; i++) {
					other.histograms[i].reset();
				}
				for (int i = 0; i < other.hiResSlots.length; i++) {
					long[] slots = other.hiResSlots[i];
					for (int j = 0; j < slots.length; j++) {
						slots[j] = 0;
					}
				}
				
				// swap data with other
				current = other;
				other = data;
				
				// Wait until all writers started before the call are done,
				// so we're sure no writer will write to "other" anymore.
				// In the meanwhile, writers may have written to "current"
				// or to "other" (after swap), but they do so consistently (they
				// get the "current", and perform all operations that need
				// to be consistent in it, even though "current" could have become
				// in the meantime "other".
				wrp.flipPhase();
				
				data = current;
				
				// Now take a sample from "other". Needs the previous sample, because
				// values to show for some counters are cumulative from
				// previous values (e.g., instantaneous counters)
			
				SampleData sample = new SampleData(this, other, lastSample);
				sampleCollector.add(name, instance, sample);
				lastSample = sample;
			}
		}
		finally {
			wrp.readerUnlock();
		}
	}
	
}