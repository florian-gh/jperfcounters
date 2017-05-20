package net.florianx.jperfcounters.core;

import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import junit.framework.Assert;
import junit.framework.TestCase;
import net.florianx.jperfcounters.core.CounterData;
import net.florianx.jperfcounters.core.CounterDataBuilder;
import net.florianx.jperfcounters.core.CounterMgr;
import net.florianx.jperfcounters.core.HiResCounter;
import net.florianx.jperfcounters.core.HistogramCounter;
import net.florianx.jperfcounters.core.IncrementalCounter;
import net.florianx.jperfcounters.core.InstantaneousCounter;
import net.florianx.jperfcounters.core.MeasureCounter;
import net.florianx.jperfcounters.core.Reporter;
import net.florianx.jperfcounters.core.CounterData.Setter;

public class PerfTest extends TestCase {

	public PerfTest(String name) {
		super(name);
	}

	protected void setUp() throws Exception {
		super.setUp();
	}
	
	protected void tearDown() throws Exception {
		super.tearDown();
	}


	enum Hr1 {
		IDLE,
		ON_CPU,
		WORK1,
		WORK2
	}
	
	public void testCounterDataBuilder() {
		Assert.assertNull(null);
		
		ScheduledExecutorService executor = Executors.newScheduledThreadPool(10);
		
		// CounterData contains a number of performance counters and handles
		// operations on those counters.
		
		// CounterMgr keeps track of all CounterData, and allows getting values
		// of those counters. Typically we have one CounterMgr per application.
		CounterMgr counterMgr = new CounterMgr(executor);
		
		counterMgr.init();
		
//		try { Thread.sleep(1000); } catch (InterruptedException e) { /* do nothing */ }
		System.out.println("Create some counters");
		
		// Build a CounterData. A CounterData has a name and an instance identifier.
		// The idea is that we may have many similar instances, for example, for
		// each service, for each user session etc.
		CounterDataBuilder builder = counterMgr.createCounterBuilder("Test", "main");
		// A simple counter is just a "long" value that can be incremented or
		// decremented by 1 or by a specifed amount.
		IncrementalCounter c1 = builder.createIncrementalCounter("c1");
		InstantaneousCounter c2 = builder.createInstantaneousCounter("c2");
		final IncrementalCounter c3 = builder.createIncrementalCounter("c3");
		final InstantaneousCounter c4 = builder.createInstantaneousCounter("c4");
		// MeasureCounter keeps track of the number of events and a value of each
		// event (for example, the duration of a call).
		MeasureCounter m1 = builder.createMeasureCounter("m1");
		MeasureCounter m2 = builder.createMeasureCounter("m2");
		// HistogramCounter keeps track of the number of events and a value of each
		// event, and maintains a histogram of those values.
		HistogramCounter h1 = builder.createHistogramCounter("h1");
		// HiResCounter has one "state" at any given point in time. The state can
		// be changed at any point in time. The state is sampled at high frequency
		// (by default 100 times/second), and the result is provided as the number
		// of times each state was encountered during a time interval.
		HiResCounter<Hr1> hr1 = builder.createHiResCounter("hr1", Hr1.values());

		// Create the CounterData
		final CounterData counterData = builder.create();
		Setter setter = new Setter(counterData);
		
		Random rnd = new Random();
		for (int i = 0; i < 100; i++) { // one loop = 100 ms
		
			c2.set(i);
			
			hr1.setState(Hr1.ON_CPU);
			// do something on CPU
			c1.inc();
			try { Thread.sleep(10); } catch (InterruptedException e) { /* do nothing */ }
			
			hr1.setState(Hr1.IDLE);
			// wait
			try { Thread.sleep(30); } catch (InterruptedException e) { /* do nothing */ }
			
			hr1.setState(Hr1.WORK1);
			// call work1
			m1.recordValue(40);
			try { Thread.sleep(40); } catch (InterruptedException e) { /* do nothing */ }
			
			hr1.setState(Hr1.IDLE);
			// go to idle
			long v = (long)(20 + rnd.nextGaussian() * 10);
			if (v < 0) {
				v = 0;
			}
			System.out.println("[" + i + "] Random: " + v);
			h1.recordValue(v);
			try { Thread.sleep(20); } catch (InterruptedException e) { /* do nothing */ }
			
			if (i % 20 == 0) {
				Reporter reporter = counterMgr.createReporter();
				
				long lastSlot = reporter.getLast();
				String result = reporter.getAsJson(lastSlot, 16);
				System.out.println(result);
			}
		}

		// By using Setter, we can make changes to several counters in a consistent
		// way, so that when the result is retrieved from CounterMgr, all the
		// changes are applied during the same timeslot, and handled together.
		setter.prepare();
		setter.inc(c1);
		setter.inc(c2);
		setter.terminate();
		
		try { Thread.sleep(10000); } catch (InterruptedException e) { /* do nothing */ }
		System.out.println("Terminating");
				
		// Values of counters are retrieved via the CounterMgr.
		
		// CounterMgr maintains counter snapshots, and keeps them as history in memory
		// for a specified number of snapshots.
		
//		long snapshot = counterMgr.getLastSnapshot();
//		
//		// For the last available snapshot, get all values of CounterData named "Test",
//		// all existing instances
//		String val = counterMgr.getCounterValuesAsJson(snapshot, "Test", "*");
		
		// fail("Not yet implemented");
		Assert.assertNull(null);
	}

}
