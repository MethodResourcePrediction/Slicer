package de.rherzog.master.thesis.slicer;

import java.util.LinkedHashSet;
import java.util.Set;

import org.apache.commons.math3.util.Pair;

import com.ibm.wala.ssa.ISSABasicBlock;

public class SliceResult extends Pair<Set<ISSABasicBlock>, Set<ISSABasicBlock>> {

	public SliceResult(Set<ISSABasicBlock> normalBlocks, Set<ISSABasicBlock> ignoredBlocks) {
		super(normalBlocks, ignoredBlocks);
	}

	public Set<ISSABasicBlock> getNormalBlocks() {
		return getKey();
	}

	public Set<ISSABasicBlock> getIgnoredBlocks() {
		return getValue();
	}

	public static SliceResult emptyResult() {
		return new SliceResult(new LinkedHashSet<>(), new LinkedHashSet<>());
	}
}
