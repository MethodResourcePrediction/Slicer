package de.uniks.vs.methodresourceprediction.slicer.dominance;

import com.ibm.wala.shrikeBT.IInstruction;
import com.ibm.wala.shrikeCT.InvalidClassFileException;
import de.uniks.vs.methodresourceprediction.slicer.ControlFlow;
import de.uniks.vs.methodresourceprediction.slicer.SlicerGraph;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.jgrapht.Graph;
import org.jgrapht.GraphPath;
import org.jgrapht.alg.shortestpath.AllDirectedPaths;
import org.jgrapht.graph.DefaultDirectedGraph;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.io.ComponentNameProvider;
import org.jgrapht.io.ExportException;

public class PostDominance extends SlicerGraph<Integer> {
  private ControlFlow controlFlow;
  private Graph<Integer, DefaultEdge> controlFlowGraph;

  private Graph<Integer, DefaultEdge> graph;
  private int startIndex;

  public PostDominance(ControlFlow controlFlow, int startIndex)
      throws IOException, InvalidClassFileException {
    this.controlFlow = controlFlow;
    this.controlFlowGraph = controlFlow.getGraph();
    this.startIndex = startIndex;
  }

  public PostDominance(Graph<Integer, DefaultEdge> controlFlowGraph, int startIndex) {
    this.controlFlowGraph = controlFlowGraph;
    this.startIndex = startIndex;
  }

  @Override
  public Graph<Integer, DefaultEdge> getGraph() throws IOException, InvalidClassFileException {
    if (graph != null) {
      return graph;
    }
    graph = new DefaultDirectedGraph<>(DefaultEdge.class);

    // Every vertex in the control flow is present in the forward dominance graph
    // as well.
    Graph<Integer, DefaultEdge> cfg = controlFlowGraph;
    cfg.vertexSet().forEach(v -> graph.addVertex(v));

    // Get END/Exit nodes
    Set<Integer> endNodes = new HashSet<>();
    cfg.vertexSet()
        .forEach(
            v -> {
              if (cfg.outDegreeOf(v) == 0) {
                endNodes.add(v);
              }
            });

    // http://infolab.stanford.edu/~ullman/dragon/w06/lectures/cs243-lec08-wei.pdf
    // If X appears on every path from Y to END, then X post-dominates Y.

    AllDirectedPaths<Integer, DefaultEdge> cfgPaths = new AllDirectedPaths<>(cfg);
    for (int y : cfg.vertexSet()) {
      final List<GraphPath<Integer, DefaultEdge>> cfgPathFromYToEnd =
          cfgPaths.getAllPaths(Set.of(y), endNodes, true, cfg.vertexSet().size());

      Set<Integer> Xs = new HashSet<>(cfg.vertexSet());
      for (GraphPath<Integer, DefaultEdge> gp : cfgPathFromYToEnd) {
        Xs.retainAll(gp.getVertexList());
      }

      for (int x : Xs) {
        // x post-dominates y
        graph.addEdge(x, y);
      }
    }

    return graph;
  }

  @Override
  protected String dotPrint() throws IOException, InvalidClassFileException, ExportException {
    // use helper classes to define how vertices should be rendered,
    // adhering to the DOT language restrictions
    ComponentNameProvider<Integer> vertexIdProvider =
        new ComponentNameProvider<Integer>() {
          public String getName(Integer index) {
            return String.valueOf(index);
          }
        };
    ComponentNameProvider<Integer> vertexLabelProvider =
        new ComponentNameProvider<Integer>() {
          public String getName(Integer index) {
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

  public ControlFlow getControlFlow() {
    return controlFlow;
  }

  public Graph<Integer, DefaultEdge> getControlFlowGraph() {
    return controlFlowGraph;
  }

  public int getStartIndex() {
    return startIndex;
  }
}
