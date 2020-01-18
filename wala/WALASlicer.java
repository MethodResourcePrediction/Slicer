package de.rherzog.master.thesis.slicer.wala;

import java.util.HashSet;
import java.util.Set;

import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ssa.SSAArrayLoadInstruction;
import com.ibm.wala.ssa.SSACFG;
import com.ibm.wala.ssa.SSACFG.BasicBlock;
import com.ibm.wala.ssa.SSAInstruction;
import com.ibm.wala.util.collections.Iterator2Iterable;

public class WALASlicer {
	private Set<Integer> instructionIndexes;
	private SSACFG controlFlowGraph;

	public WALASlicer(CGNode callerNode, Set<Integer> instructionIndexes) {
		this.instructionIndexes = instructionIndexes;

		this.controlFlowGraph = callerNode.getIR().getControlFlowGraph();
	}

	public Set<Integer> sliceBackwards() {
		Set<Integer> instructionIndexSet = new HashSet<>();
		for (Integer instructionIndex : instructionIndexes) {
			instructionIndexSet.add(instructionIndex);

			BasicBlock block = controlFlowGraph.getBlockForInstruction(instructionIndex);

			SSAInstruction instruction = null;
			for (SSAInstruction inst : Iterator2Iterable.make(block.iterator())) {
				if (inst.iIndex() == instructionIndex) {
					instruction = inst;
				}
			}

			for (int iUse = 0; iUse < instruction.getNumberOfUses(); iUse++) {
				int use = instruction.getUse(iUse);
				Set<Integer> instructions = sliceBackwards(use);
				instructionIndexSet.addAll(instructions);
			}
		}

//		int max = instructionIndexes.stream().mapToInt(i -> i).max().getAsInt();
//		Set<Integer> instructionIndexSet = blockSet.stream().flatMapToInt(block -> {
//			int lastInstructionIndex = block.getLastInstructionIndex();
//			IntStream range = IntStream.rangeClosed(block.getFirstInstructionIndex(),
//					Math.min(lastInstructionIndex, max));
//			return range;
//		}).boxed().collect(Collectors.toSet());

		return instructionIndexSet;
	}

	private Set<Integer> sliceBackwards(int def) {
		return sliceBackwards(def, new HashSet<>());
	}

	private Set<Integer> sliceBackwards(int def, Set<Integer> instructionIndexes) {
		// TODO Iterate over predecessing blocks or just lookup instruction due to
		// def-use assumption?
		SSAInstruction instruction = getInstructionByDef(def);
		if (instruction != null) {
			instructionIndexes.add(instruction.iIndex());
			if (instruction instanceof SSAArrayLoadInstruction) {
//				SSAArrayLoadInstruction arrayLoadInstruction = (SSAArrayLoadInstruction) instruction;
				instructionIndexes.add(instruction.iIndex() - 1);
			}
		} else {
			return instructionIndexes;
		}

		for (int iUse = 0; iUse < instruction.getNumberOfUses(); iUse++) {
			int use = instruction.getUse(iUse);
			sliceBackwards(use, instructionIndexes);
		}
		return instructionIndexes;
	}

	private SSAInstruction getInstructionByDef(int def) {
		for (SSAInstruction inst : controlFlowGraph.getInstructions()) {
			if (inst != null && inst.hasDef() && def == inst.getDef()) {
				return inst;
			}
		}
		return null;
	}
}
