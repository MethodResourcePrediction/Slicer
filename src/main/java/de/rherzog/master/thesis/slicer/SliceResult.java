package de.rherzog.master.thesis.slicer;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.ibm.wala.shrikeBT.IInstruction;
import com.ibm.wala.shrikeBT.PopInstruction;
import com.ibm.wala.shrikeCT.InvalidClassFileException;

public class SliceResult {
	private String methodSignature;
	private Set<Integer> instructionIndex, instructionsToKeep, instructionsToIgnore;
	private Map<Integer, Integer> instructionPopMap;
	private ControlFlow controlFlow;

	public SliceResult(String methodSignature, Set<Integer> instructionIndex, Set<Integer> instructionsToKeep,
			Set<Integer> instructionsToIgnore, Map<Integer, Integer> instructionPopMap, ControlFlow controlFlow) {
		this.methodSignature = methodSignature;
		this.instructionIndex = instructionIndex;
		this.instructionsToKeep = instructionsToKeep;
		this.instructionsToIgnore = instructionsToIgnore;
		this.instructionPopMap = instructionPopMap;
		this.controlFlow = controlFlow;
	}

	public Set<Integer> getInstructionIndex() {
		return instructionIndex;
	}

	public Set<Integer> getInstructionsToKeep() {
		return instructionsToKeep;
	}

	public Set<Integer> getInstructionsToIgnore() {
		return instructionsToIgnore;
	}

	public Map<Integer, Integer> getInstructionPopMap() {
		return instructionPopMap;
	}

	public String getMethodSignature() {
		return methodSignature;
	}

	public static SliceResult emptyResult() {
		return new SliceResult(null, Collections.emptySet(), Collections.emptySet(), Collections.emptySet(),
				Collections.emptyMap(), null);
	}

	public List<IInstruction> getSlice() throws IOException, InvalidClassFileException {
		List<IInstruction> slice = new ArrayList<>();
		IInstruction[] instructions = getControlFlow().getMethodData().getInstructions();
		for (int index = 0; index < instructions.length; index++) {
			IInstruction instruction = instructions[index];

			if (getInstructionsToKeep().contains(index)) {
				slice.add(instruction);
			} else if (getInstructionsToIgnore().contains(index)) {
				slice.add(instruction);
			}
			if (getInstructionPopMap().containsKey(index)) {
				for (int popCount = 0; popCount < getInstructionPopMap().get(index); popCount++) {
					slice.add(PopInstruction.make(1));
				}
			}
		}
		return slice;
	}

	@Override
	public String toString() {
		try {
			StringBuilder builder = new StringBuilder();

			builder.append(getMethodSignature());
			builder.append("\n");
			builder.append("InstructionIndexes: " + getInstructionIndex() + "\n");
			builder.append("InstructionIndexesToKeep: " + getInstructionsToKeep() + "\n");
			builder.append("instructionIndexesToIgnore: " + getInstructionsToIgnore() + "\n");
			builder.append("instructionPopMap: " + getInstructionPopMap() + "\n");
			builder.append("VarIndexesToRenumber: " + getControlFlow().getVarIndexesToRenumber() + "\n");

			IInstruction[] instructions = getControlFlow().getMethodData().getInstructions();
			int padding = instructions.length / 10;

			builder.append("\n");
			builder.append("=== Slice ===" + "\n");
			for (int index = 0; index < instructions.length; index++) {
				IInstruction instruction = instructions[index];

				String str = String.format("%" + padding + "s", index);
				if (getInstructionsToKeep().contains(index)) {
					builder.append(str + ": " + instruction + "\n");
				} else if (getInstructionsToIgnore().contains(index)) {
					builder.append(str + ": " + instruction + " (IGNORED)" + "\n");
				}
				if (instructionPopMap.containsKey(index)) {
					for (int popCount = 0; popCount < instructionPopMap.get(index); popCount++) {
						builder.append(str + ": " + PopInstruction.make(1) + " (ADDITIONAL)" + "\n");
					}
				}
			}
			return builder.toString();
		} catch (IOException | InvalidClassFileException e) {
			e.printStackTrace();
		}
		return "SliceResult [empty]";
	}

	public ControlFlow getControlFlow() {
		return controlFlow;
	}
}
