package de.uniks.vs.methodresourceprediction.slicer;

import com.ibm.wala.shrike.shrikeBT.GotoInstruction;
import com.ibm.wala.shrike.shrikeBT.IConditionalBranchInstruction;
import com.ibm.wala.shrike.shrikeBT.IInstruction;
import com.ibm.wala.shrike.shrikeCT.InvalidClassFileException;
import org.jgrapht.Graph;
import org.jgrapht.graph.DefaultDirectedGraph;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.io.ComponentNameProvider;
import org.jgrapht.io.ExportException;

import java.io.IOException;
import java.util.Set;
import java.util.stream.Collectors;

public class ConditionalDependency extends SlicerGraph<Integer> {
  private ControlFlow controlFlow;
  private Graph<Integer, DefaultEdge> graph;

  public ConditionalDependency(ControlFlow controlFlow) {
    this.controlFlow = controlFlow;
  }

  @Override
  public Graph<Integer, DefaultEdge> getGraph() throws IOException, InvalidClassFileException {
    if (graph != null) {
      return graph;
    }
    graph = new DefaultDirectedGraph<>(DefaultEdge.class);

    StackTrace stackTrace = new StackTrace(controlFlow.getMethodData());

    // Iterate all instructions to find those calling a constructor
    IInstruction[] instructions = controlFlow.getMethodData().getInstructions();
    for (int instructionIndex = 0; instructionIndex < instructions.length; instructionIndex++) {
      IInstruction instruction = instructions[instructionIndex];
      graph.addVertex(instructionIndex);

      if (!(instruction instanceof IConditionalBranchInstruction)) {
        continue;
      }

      IConditionalBranchInstruction conditionalBranchInstruction =
          (IConditionalBranchInstruction) instruction;

      //      int firstCaseIndex = instructionIndex + 1;
      int secondCaseIndex = conditionalBranchInstruction.getTarget();

      int firstCaseEndIndex = secondCaseIndex - 1;
      if (instructions[firstCaseEndIndex] instanceof GotoInstruction) {
        // If-else condition
        GotoInstruction firstCaseGoto = (GotoInstruction) instructions[firstCaseEndIndex];
        int secondCaseEndIndex = firstCaseGoto.getLabel();

        graph.addVertex(firstCaseEndIndex);
        graph.addEdge(instructionIndex, firstCaseEndIndex);

        graph.addVertex(secondCaseEndIndex);
        graph.addEdge(instructionIndex, secondCaseEndIndex);
      } else {
        // If-only condition
        graph.addVertex(secondCaseIndex);
        graph.addEdge(instructionIndex, secondCaseIndex);
      }
    }
    return graph;
  }

  public Set<Integer> getConditionalDependencyInstructions(int index) throws IOException, InvalidClassFileException {
    return getGraph().outgoingEdgesOf(index).stream()
        .map(getGraph()::getEdgeTarget)
        .collect(Collectors.toSet());
  }

  @Override
  protected String dotPrint() throws IOException, InvalidClassFileException, ExportException {
    IInstruction[] instructions = controlFlow.getMethodData().getInstructions();

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
