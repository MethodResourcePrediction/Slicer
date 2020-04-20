package de.rherzog.master.thesis.slicer.test;

public class SlicerValidation {
	public void reuseVariableWithoutReinitialization() {
		int i = 0;
		for (; i < 5; i++) {
			System.currentTimeMillis();
		}
		for (; i < 10; i++) {
			System.currentTimeMillis();
		}
	}

	public void reuseVariableWitReinitialization() {
		int i = 0;
		for (; i < 2; i++) {
			System.currentTimeMillis();
		}
		i = 0;
		for (; i < 5; i++) {
			System.currentTimeMillis();
		}
	}
}
