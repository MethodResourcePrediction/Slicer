package de.rherzog.master.thesis.slicer;

import java.util.HashSet;
import java.util.Set;

import org.apache.commons.math3.util.Pair;

public class SliceResult extends Pair<Set<Integer>, Set<Integer>> {
	public SliceResult(Set<Integer> instructionsToKeep, Set<Integer> instructionsToIgnore) {
		super(instructionsToKeep, instructionsToIgnore);
	}

	public Set<Integer> getInstructionsToKeep() {
		return getKey();
	}

	public Set<Integer> getInstructionsToIgnore() {
		return getValue();
	}

	public static SliceResult emptyResult() {
		return new SliceResult(new HashSet<>(), new HashSet<>());
	}
}
