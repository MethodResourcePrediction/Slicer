package de.rherzog.master.thesis.slicer.test;

import de.rherzog.master.thesis.slicer.instrumenter.export.Nothing;

public class SlicerValidation {
	public void simpleMethodCall() {
		System.currentTimeMillis();
	}

	public void simpleMethodCallWithParameter(long sleep) throws InterruptedException {
		Thread.sleep(sleep);
	}

	public void simpleMethodCallAndLoopWithParameter(long sleep) throws InterruptedException {
		for (int i = 0; i < 5; i++) {
			Thread.sleep(sleep);
		}
	}

	public void reuseVariableWithoutReinitialization() {
		int i = 0;
		for (; i < 5; i++) {
			System.currentTimeMillis();
		}
		for (; i < 10; i++) {
			System.currentTimeMillis();
		}
		Nothing.doNothing();
	}

	public void reuseVariableWithReinitialization() {
		int i = 0;
		for (; i < 2; i++) {
			System.currentTimeMillis();
		}
		i = i + 1;
		for (; i < 5; i++) {
			System.currentTimeMillis();
		}
	}

//	public void twoWordPop() {
//		Thread.activeCount();
//		System.currentTimeMillis();
//	}

//	public void twoWordPop() {
//		Integer.valueOf("1").intValue();
//		Float.valueOf("1.23f").floatValue();
//		Double.valueOf("2.34d").doubleValue();
//		Long.valueOf("2L").longValue();
//	}
}
