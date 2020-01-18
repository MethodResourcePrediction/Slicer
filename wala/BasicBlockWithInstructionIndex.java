package de.rherzog.master.thesis.slicer.wala;

import com.ibm.wala.ssa.SSACFG.BasicBlock;

public class BasicBlockWithInstructionIndex {
	private Integer instructionIndex;
	private BasicBlock basicBlock;

	public BasicBlockWithInstructionIndex(BasicBlock basicBlock, Integer instructionIndex) {
		this.basicBlock = basicBlock;
		this.instructionIndex = instructionIndex;
	}

	public Integer getInstructionIndex() {
		return instructionIndex;
	}

	public void setInstructionIndex(Integer instructionIndex) {
		this.instructionIndex = instructionIndex;
	}

	public BasicBlock getBasicBlock() {
		return basicBlock;
	}

	public void setBasicBlock(BasicBlock basicBlock) {
		this.basicBlock = basicBlock;
	}
}
