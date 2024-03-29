package de.uniks.vs.methodresourceprediction.slicer;

import com.ibm.wala.shrike.shrikeBT.IInstruction;
import com.ibm.wala.shrike.shrikeBT.InvokeInstruction;
import com.ibm.wala.shrike.shrikeCT.InvalidClassFileException;
import org.jgrapht.Graph;
import org.jgrapht.graph.DefaultDirectedGraph;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.io.ComponentNameProvider;
import org.jgrapht.io.ExportException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Stack;

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

    StackTrace stackTrace = new StackTrace(blockDependency.getControlFlow().getMethodData());

    // Iterate all instructions to find those calling a constructor
    IInstruction[] instructions =
        blockDependency.getControlFlow().getMethodData().getInstructions();
    for (int instructionIndex = 0; instructionIndex < instructions.length; instructionIndex++) {
      IInstruction instruction = instructions[instructionIndex];
      graph.addVertex(instructionIndex);

      if (!(instruction instanceof InvokeInstruction)) {
        continue;
      }
      // Check if the invoke instruction is calling the constructor
      InvokeInstruction invokeInstruction = (InvokeInstruction) instruction;
      if (!invokeInstruction.getMethodName().equals("<init>")) {
        continue;
      }

      // Get popped elements stack for InvokeInstruction to figure out on which top stack element
      // the constructor is called
      List<Stack<Integer>> poppedStacks =
          stackTrace.getPoppedStackAtInstructionIndex(instructionIndex);
      if (poppedStacks.isEmpty()) {
        throw new RuntimeException(
            "There must be an object popped by an constructor invoke instruction");
      }
      for (Stack<Integer> poppedStack : poppedStacks) {
        // The last popped element (arguments were popped before) is the object
        int poppedInstructionIndex = poppedStack.firstElement();

        graph.addVertex(poppedInstructionIndex);
        graph.addEdge(poppedInstructionIndex, instructionIndex);
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
