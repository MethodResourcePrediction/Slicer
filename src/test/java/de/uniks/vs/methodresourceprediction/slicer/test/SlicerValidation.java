package de.uniks.vs.methodresourceprediction.slicer.test;

import java.io.PrintStream;

import de.uniks.vs.methodresourceprediction.slicer.export.Nothing;

public class SlicerValidation {
	// DO NOT CHANGE THESE METHODS
	//
	// Used for slice validation tests

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
		i = 0;
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

	public int simpleReturnValue() {
		return 0;
	}

	public long simpleReturnValue2() {
		return System.currentTimeMillis();
	}

	public long simpleReturnValue3() {
		// Something that could be sliced
		System.currentTimeMillis();
		return 0L;
	}

	public void simpleConditional() {
		if (Thread.currentThread() != null) {
			Thread.dumpStack();
		} else {
			Thread.dumpStack();
		}
		Thread.dumpStack();
	}

	public PrintStream returnObject() {
		return System.out;
	}

	public static int staticMethodWithoutDependencies(int someNumber) {
		return someNumber;
	}

	private static final int STATIC_CONSTANT = 10;
	public static int staticMethodWithStaticFieldDependency(int someNumber) {
		return someNumber + STATIC_CONSTANT;
	}

	// Public access to prevent replacing to constant optimization
	public static int staticVariable = 20;
	public static int staticMethodWithStaticVariableDependency(int someNumber) {
		return someNumber + staticVariable;
	}

	public int methodWithWithoutDependency(int someNumber) {
		return someNumber;
	}

	public long methodWithWithJavaLangDependency(int someNumber) {
		return someNumber + Thread.currentThread().getId();
	}

	public static long staticMethodWithWithJavaLangDependency(int someNumber) {
		return someNumber + Thread.currentThread().getId();
	}

	// Public access to prevent replacing to constant optimization
	public int variable = 20;
	public int methodWithVariableDependency(int someNumber) {
		return someNumber + variable;
	}
}
