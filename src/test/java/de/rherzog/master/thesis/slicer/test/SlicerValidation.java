package de.rherzog.master.thesis.slicer.test;

import de.rherzog.master.thesis.slicer.instrumenter.export.Nothing;

public class SlicerValidation {
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
