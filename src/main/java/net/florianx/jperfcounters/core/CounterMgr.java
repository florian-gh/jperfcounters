package net.florianx.jperfcounters.core;

import java.util.HashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;


public class CounterMgr {

	private long sampleFreqHiResMillis;
	private int sampleNormalMultiplier;
	private int sampleHistoMultiplier;
	private ScheduledExecutorService executor;
	// Map by name, then instance
	private HashMap<String, HashMap<String, CounterData>> sampleableList;
	
	private SampleCollector sampleCollector;
	
	public CounterMgr(ScheduledExecutorService executor) {
		this.sampleFreqHiResMillis = 10; // 100/sec
		this.sampleNormalMultiplier = (int)(1000 / sampleFreqHiResMillis); // 2 secs
		this.sampleHistoMultiplier = (int)(10000 / sampleFreqHiResMillis); // 10 secs
		this.executor = executor;
		sampleableList = new HashMap<>();
		sampleCollector = new SampleCollector(1000);
	}
	
	public long getSampleFreqHiResMillis() {
		return sampleFreqHiResMillis;
	}
	public int getSampleNormalMultiplier() {
		return sampleNormalMultiplier;
	}
	public int getSampleHistoMultiplier() {
		return sampleHistoMultiplier;
	}
	
	public void setSamplingRate(long period, TimeUnit tu) {
	}
	
	public void setHistogramSamplingPeriods(int periods) {
	}
	
	public void setHiResSamplingsPerPeriod(int samples) {
	}

	public CounterDataBuilder createCounterBuilder(String name, String instance) {
		return new CounterDataBuilder(this, name, instance);
	}
	
	synchronized void registerCounterData(CounterData cd) {
		HashMap<String, CounterData> n = sampleableList.get(cd.getName());
		if (n == null) {
			n = new HashMap<String, CounterData>();
			sampleableList.put(cd.getName(), n);
		}
		CounterData oldCd = n.get(cd.getInstance());
		if (oldCd != null) {
			throw new InternalError("Instance already exists: " + cd.getName() + ":" + cd.getInstance());
		}
		n.put(cd.getInstance(), cd);
	}
	
	synchronized void unregisterCounterData(CounterData cd) {
		HashMap<String, CounterData> n = sampleableList.get(cd.getName());
		if (n == null) {
			throw new InternalError("Name does not exist: " + cd.getName());
		}
		CounterData oldCd = n.remove(cd.getInstance());
		if (oldCd == null) {
			throw new InternalError("Instance does not exists: " + cd.getName() + ":" + cd.getInstance());
		}
	}
	
	public Reporter createReporter() {
		return new Reporter(sampleCollector);
	}

	public void init() {
		long startTs = System.currentTimeMillis();
		long histoFreq = sampleFreqHiResMillis * sampleHistoMultiplier;
		long histoDelay = histoFreq - (startTs % histoFreq);
		
		long hiResFreq = sampleFreqHiResMillis;
		long hiResDelay = hiResFreq - (startTs % hiResFreq);
		final long startingSampleCount = (histoFreq - (histoDelay - hiResDelay)) / hiResFreq;
	
		executor.scheduleAtFixedRate(new Runnable() {
			long lastHiResRunTs = System.nanoTime();
			long lastNormalRunTs = lastHiResRunTs;
			long lastHistoRunTs = lastHiResRunTs;
			long sampleCount = startingSampleCount;
			public void run() {
				try {
					synchronized(CounterMgr.this) {
						long currentRunTs = System.nanoTime();

						// see if we're in a HiFreq, Normal or Histo sampling
						PerfSampleable.SampleType sampleType = PerfSampleable.SampleType.HiFreq;
						if (sampleCount % sampleHistoMultiplier == 0) {
							sampleType = PerfSampleable.SampleType.Histo;
						} else if (sampleCount % sampleNormalMultiplier == 0) {
							sampleType = PerfSampleable.SampleType.Normal;
						}
						++sampleCount;

						// do not run if we're too late
						boolean skip = false;
						switch (sampleType) {
						case HiFreq:
							long sinceLastHiRes = currentRunTs - lastHiResRunTs;
							skip = (sinceLastHiRes > (sampleFreqHiResMillis * 2) * 1000_000L);
							break;
						case Normal:
							long sinceLastNormal = currentRunTs - lastNormalRunTs;
							skip = (sinceLastNormal > (sampleFreqHiResMillis * sampleNormalMultiplier * 2) * 1000_000L);
							break;
						case Histo:
							long sinceLastHisto = currentRunTs - lastHistoRunTs;
							skip = (sinceLastHisto > (sampleFreqHiResMillis * sampleHistoMultiplier * 2) * 1000_000L);
							break;
						}
if (skip) {
	System.out.println("We're late, skipping...");
}
						if (!skip) {
							if (sampleType == PerfSampleable.SampleType.HiFreq) {
								for (HashMap<String, CounterData> list: sampleableList.values()) {
									for (CounterData cd: list.values()) {
										cd.sampleHiRes();
									}
								}
							} else {
								sampleCollector.startSlot();
								for (HashMap<String, CounterData> list: sampleableList.values()) {
									for (CounterData cd: list.values()) {
										cd.sample(sampleType, sampleCollector);
									}
								}
								sampleCollector.endSlot();
							}
						}
						switch (sampleType) {
						case Histo:
							lastHistoRunTs = currentRunTs;
							// drop through
						case Normal:
							lastNormalRunTs = currentRunTs;
							// drop through
						case HiFreq:
							lastHiResRunTs = currentRunTs;
						}
					}
				}
				catch (Throwable e) {
					e.printStackTrace(System.err);
					Thread.getDefaultUncaughtExceptionHandler().uncaughtException(Thread.currentThread(), e);
				}
			}
		}, hiResDelay, sampleFreqHiResMillis, TimeUnit.MILLISECONDS);
	}

}
