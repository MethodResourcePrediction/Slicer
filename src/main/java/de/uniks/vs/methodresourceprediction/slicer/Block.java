package de.uniks.vs.methodresourceprediction.slicer;

import com.ibm.wala.shrikeBT.IInstruction;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

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

  public Integer getHighestIndex() {
    Integer highestIndex = null;
    for (int index : getInstructions().keySet()) {
      if (highestIndex == null) {
        highestIndex = index;
        continue;
      }
      highestIndex = Math.max(highestIndex, index);
    }
    return highestIndex;
  }

  public Integer getLowestIndex() {
    Integer lowestIndex = null;
    for (int index : getInstructions().keySet()) {
      if (lowestIndex == null) {
        lowestIndex = index;
        continue;
      }
      lowestIndex = Math.min(lowestIndex, index);
    }
    return lowestIndex;
  }

  public List<Integer> getInstructionIndexes() {
    ArrayList<Integer> instructionIndexList = new ArrayList<>(getInstructions().keySet());
    instructionIndexList.sort(Integer::compareTo);
    return instructionIndexList;
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

  public int getId() {
    return id;
  }
}
