package de.uniks.vs.methodresourceprediction.slicer;

import com.ibm.wala.shrikeBT.IInstruction;
import com.ibm.wala.shrikeBT.InvokeInstruction;
import com.ibm.wala.shrikeBT.LoadInstruction;
import com.ibm.wala.shrikeBT.NewInstruction;
import com.ibm.wala.shrikeCT.InvalidClassFileException;
import de.uniks.vs.methodresourceprediction.utils.Utilities;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map.Entry;
import org.jgrapht.Graph;
import org.jgrapht.graph.DefaultDirectedGraph;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.io.ComponentNameProvider;
import org.jgrapht.io.ExportException;

public class InitializerDependency extends SlicerGraph<Integer> {
  private BlockDependency blockDependency;
  private Graph<Integer, DefaultEdge> graph;

  public InitializerDependency(BlockDependency blockDependency) {
    this.blockDependency = blockDependency;
  }

  @Override
  public Graph<Integer, DefaultEdge> getGraph() throws IOException, InvalidClassFileException {
    if (graph != null) {
      return graph;
    }
    graph = new DefaultDirectedGraph<>(DefaultEdge.class);

    // At first iterate all blocks since the constructor to any new object is
    // usually encapsulated
    // TODO always?
    for (Block block : blockDependency.getBlocks()) {
      // Iterate all instructions inside the block to find the New-Instruction
      for (Entry<Integer, IInstruction> entry : block.getInstructions().entrySet()) {
        final Integer instructionIndex = entry.getKey();
        final IInstruction instruction = entry.getValue();

        graph.addVertex(instructionIndex);

        // Check if the instruction creates or loads an object
        if (!(instruction instanceof NewInstruction) && !(instruction instanceof LoadInstruction)) {
          continue;
        }

        // Next, find the constructor invoke instruction to the new-Instruction.
        // It must be inside the same block, so the index runs until block end index.
        for (int succeedingInstructionIndex = instructionIndex + 1;
            succeedingInstructionIndex <= block.getHighestIndex();
            succeedingInstructionIndex++) {
          final IInstruction succeedingInstruction =
              block.getInstructions().get(succeedingInstructionIndex);

          // If there is an invoke Instruction found, check if it is the constructor call
          if (succeedingInstruction instanceof InvokeInstruction) {
            InvokeInstruction succeedingInvokeInstruction =
                (InvokeInstruction) succeedingInstruction;
            try {
              if (instruction instanceof NewInstruction) {
                // NewInstruction
                if (Utilities.isInitializerInstruction(
                    (NewInstruction) instruction, succeedingInvokeInstruction)) {
                  // Finally the constructor instruction to any NewInstruction was found and
                  // added to the graph.
                  graph.addVertex(succeedingInstructionIndex);
                  graph.addEdge(instructionIndex, succeedingInstructionIndex);
                }
              } else {
                // LoadInstruction
                if (Utilities.isInitializerInstruction(
                    (LoadInstruction) instruction, succeedingInvokeInstruction)) {
                  // Finally the constructor instruction to any LoadInstruction was found and
                  // added to the graph.
                  graph.addVertex(succeedingInstructionIndex);
                  graph.addEdge(instructionIndex, succeedingInstructionIndex);
                }
              }
            } catch (ClassNotFoundException e) {
              e.printStackTrace();
            }
          }
        }
      }
    }
    return graph;
  }

  public List<Integer> getClassInitializerDependencyInstructions(int instructionIndex)
      throws IOException, InvalidClassFileException {
    List<Integer> classInitializerDependencyInstructions = new ArrayList<>();
    for (DefaultEdge edge : getGraph().outgoingEdgesOf(instructionIndex)) {
      classInitializerDependencyInstructions.add(getGraph().getEdgeTarget(edge));
    }
    return classInitializerDependencyInstructions;
  }

  @Override
  protected String dotPrint() throws IOException, InvalidClassFileException, ExportException {
    IInstruction[] instructions =
        blockDependency.getControlFlow().getMethodData().getInstructions();

    // use helper classes to define how vertices should be rendered,
    // adhering to the DOT language restrictions
    ComponentNameProvider<Integer> vertexIdProvider = String::valueOf;
    ComponentNameProvider<Integer> vertexLabelProvider =
        index -> index + ": " + instructions[index].toString();
    return getExporterGraphString(vertexIdProvider, vertexLabelProvider);
  }

  @Override
  public String toString() {
    try {
      return getGraph().toString();
    } catch (IOException | InvalidClassFileException e) {
      e.printStackTrace();
      return e.getMessage();
    }
  }
}
