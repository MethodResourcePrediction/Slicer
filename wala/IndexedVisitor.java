package de.rherzog.master.thesis.slicer;

import java.util.HashSet;
import java.util.Set;

import com.ibm.wala.shrikeBT.IArrayLoadInstruction;
import com.ibm.wala.shrikeBT.IBinaryOpInstruction;
import com.ibm.wala.shrikeBT.IConditionalBranchInstruction;
import com.ibm.wala.shrikeBT.IInstruction;
import com.ibm.wala.shrikeBT.IInstruction.Visitor;
import com.ibm.wala.shrikeBT.IInvokeInstruction;
import com.ibm.wala.shrikeBT.ILoadInstruction;
import com.ibm.wala.shrikeBT.IStoreInstruction;

public abstract class IndexedVisitor extends Visitor {
	private IInstruction[] instructions;

	public IndexedVisitor(IInstruction[] instructions) {
		this.instructions = instructions;
	}

	@Override
	public void visitLocalLoad(ILoadInstruction instruction) {
		visitLocalLoad(getInstructionIndexes(instruction), instruction);
	}

	public void visitLocalLoad(Set<Integer> indexes, ILoadInstruction instruction) {
	}

	@Override
	public void visitLocalStore(IStoreInstruction instruction) {
		visitLocalStore(getInstructionIndexes(instruction), instruction);
	}

	public void visitLocalStore(Set<Integer> indexes, IStoreInstruction instruction) {
	}

	@Override
	public void visitInvoke(IInvokeInstruction instruction) {
		visitInvoke(getInstructionIndexes(instruction), instruction);
	}

	public void visitInvoke(Set<Integer> indexes, IInvokeInstruction instruction) {
	}

	@Override
	public void visitArrayLoad(IArrayLoadInstruction instruction) {
		visitArrayLoad(getInstructionIndexes(instruction), instruction);
	}

	public void visitArrayLoad(Set<Integer> indexes, IArrayLoadInstruction instruction) {
	}

	@Override
	public void visitBinaryOp(IBinaryOpInstruction instruction) {
		visitBinaryOp(getInstructionIndexes(instruction), instruction);
	}

	public void visitBinaryOp(Set<Integer> indexes, IBinaryOpInstruction instruction) {
	}

	@Override
	public void visitConditionalBranch(IConditionalBranchInstruction instruction) {
		visitConditionalBranch(getInstructionIndexes(instruction), instruction);
	}

	public void visitConditionalBranch(Set<Integer> index, IConditionalBranchInstruction instruction) {
	}

	private Set<Integer> getInstructionIndexes(IInstruction instruction) {
		Set<Integer> indexList = new HashSet<>();
		for (int index = 0; index < instructions.length; index++) {
			if (instructions[index] == instruction) {
				indexList.add(index);
			}
		}
		switch (indexList.size()) {
		case 0:
			throw new UnsupportedOperationException("No instruction found for: " + instruction.toString());
		case 1:
			return Set.of(indexList.iterator().next());
		default:
			return indexList;
//			if (indexList.contains(perferredIndex)) {
//				return perferredIndex;
//			}
//			StringBuilder builder = new StringBuilder();
//			builder.append("Instruction index is not unique for: " + instruction.toString() + "\n");
//			builder.append("Possible indexes are: " + indexList.toString());
//			throw new UnsupportedOperationException(builder.toString());
		}
	}
}
