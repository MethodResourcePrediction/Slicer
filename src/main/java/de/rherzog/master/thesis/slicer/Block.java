package de.rherzog.master.thesis.slicer;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;

import com.ibm.wala.shrikeBT.IInstruction;

public class Block {
	private Map<Integer, IInstruction> instructions;
	private int id;

	public Block(int id) {
		this.id = id;
		instructions = new LinkedHashMap<>();
	}

	public Map<Integer, IInstruction> getInstructions() {
		return instructions;
	}

	public void addInstruction(int index, IInstruction instruction) {
		instructions.put(index, instruction);
	}

	@Override
	public String toString() {
		StringBuilder builder = new StringBuilder();
		builder.append("Block " + id + "\n");
		for (Entry<Integer, IInstruction> entry : instructions.entrySet()) {
			builder.append("  " + entry.getKey() + ": " + entry.getValue().toString() + "\n");
		}
		return builder.toString();
	}
}
