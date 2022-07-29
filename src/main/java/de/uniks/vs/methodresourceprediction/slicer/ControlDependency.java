package de.uniks.vs.methodresourceprediction.slicer;

import com.ibm.wala.shrike.shrikeBT.IInstruction;
import com.ibm.wala.shrike.shrikeCT.InvalidClassFileException;
import de.uniks.vs.methodresourceprediction.slicer.dominance.ImmediatePostDominance;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import org.jgrapht.Graph;
import org.jgrapht.graph.DefaultDirectedGraph;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.io.ComponentNameProvider;
import org.jgrapht.io.ExportException;

public class ControlDependency extends SlicerGraph<Integer> {
  private ControlFlow controlFlow;
  private Graph<Integer, DefaultEdge> controlFlowGraph;
  private ImmediatePostDominance immediatePostDominance;

  private Graph<Integer, DefaultEdge> graph;
  //	private int startNode;

  public static final int ROOT_INDEX = -1;

  public ControlDependency(ControlFlow controlFlow, ImmediatePostDominance immediatePostDominance)
      throws IOException, InvalidClassFileException {
    this.controlFlow = controlFlow;
    this.controlFlowGraph = controlFlow.getGraph();
    this.immediatePostDominance = immediatePostDominance;
  }

  public ControlDependency(
      Graph<Integer, DefaultEdge> controlFlowGraph, ImmediatePostDominance immediatePostDominance) {
    this.controlFlowGraph = controlFlowGraph;
    this.immediatePostDominance = immediatePostDominance;
  }

  @Override
  public Graph<Integer, DefaultEdge> getGraph() throws IOException, InvalidClassFileException {
    if (graph != null) {
      return graph;
    }

    // Build up graph with vertices
    graph = new DefaultDirectedGraph<>(DefaultEdge.class);

    // In a control dependency graph there is a root node which marks the program
    // start. Add it first
    graph.addVertex(ROOT_INDEX);
    controlFlowGraph.vertexSet().forEach(v -> graph.addVertex(v));

    // Version 2 (Second definition)
    // http://infolab.stanford.edu/~ullman/dragon/w06/lectures/cs243-lec08-wei.pdf
    //		final Graph<Integer, DefaultEdge> immediatePostDominanceGraph =
    // immediatePostDominance.getGraph();
    final Graph<Integer, DefaultEdge> strictPostDominanceGraph =
        immediatePostDominance.getStrictPostDominance().getPostDominance().getGraph();

    for (Integer w : controlFlowGraph.vertexSet()) {
      for (DefaultEdge cfgEdge : controlFlowGraph.edgeSet()) {
        Integer u = controlFlowGraph.getEdgeSource(cfgEdge);
        Integer v = controlFlowGraph.getEdgeTarget(cfgEdge);

        // w post-dominates v
        if (strictPostDominanceGraph.containsEdge(w, v)) {
          if (!w.equals(u)) {
            if (!strictPostDominanceGraph.containsEdge(w, u)) {
              graph.addEdge(u, w);
              break;
            }
          }
        }
      }
    }

    // Root node dependency for all node without any incoming edge
    for (int node : controlFlowGraph.vertexSet()) {
      if (graph.incomingEdgesOf(node).size() > 0) {
        // has control dependent node
        continue;
      }
      graph.addEdge(ROOT_INDEX, node);
    }
    return graph;
  }

  public Set<Integer> getControlDependencyInstructions(int index)
      throws IOException, InvalidClassFileException {
    Graph<Integer, DefaultEdge> controlDependencyGraph = getGraph();
    Set<DefaultEdge> incomingEdges = controlDependencyGraph.incomingEdgesOf(index);

    Set<Integer> controlDependentInstructionSet = new HashSet<>();
    for (DefaultEdge controlEdge : incomingEdges) {
      Integer edgeSource = controlDependencyGraph.getEdgeSource(controlEdge);
      controlDependentInstructionSet.add(edgeSource);
    }
    return controlDependentInstructionSet;
  }

  @Override
  protected String dotPrint() throws IOException, InvalidClassFileException, ExportException {
    // use helper classes to define how vertices should be rendered,
    // adhering to the DOT language restrictions
    ComponentNameProvider<Integer> vertexIdProvider =
        new ComponentNameProvider<>() {
          public String getName(Integer index) {
            return String.valueOf(index);
          }
        };
    ComponentNameProvider<Integer> vertexLabelProvider =
        new ComponentNameProvider<>() {
          public String getName(Integer index) {
            if (index == ROOT_INDEX) {
              return "START";
            }
            if (controlFlow != null) {
              try {
                IInstruction[] instructions = controlFlow.getMethodData().getInstructions();
                return index + ": " + instructions[index].toString();
              } catch (IOException | InvalidClassFileException e) {
              }
            }
            return String.valueOf(index);
          }
        };
    return getExporterGraphString(vertexIdProvider, vertexLabelProvider);
  }

  @Override
  public String toString() {
    try {
      return getGraph().toString();
    } catch (IOException e) {
      e.printStackTrace();
      return e.getMessage();
    } catch (InvalidClassFileException e) {
      e.printStackTrace();
      return e.getMessage();
    }
  }
}
