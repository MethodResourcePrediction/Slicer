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

	public void reuseVariableWithoutReinitializationDoubleSized() {
		long i = 0;
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

	public void reuseVariableWithReinitializationDoubleSized() {
		long i = 0;
		for (; i < 2; i++) {
			System.currentTimeMillis();
		}
		i = i + 1;
		for (; i < 5; i++) {
			System.currentTimeMillis();
		}
	}

	public void twoWordPop() {
		System.currentTimeMillis();
	}
}
