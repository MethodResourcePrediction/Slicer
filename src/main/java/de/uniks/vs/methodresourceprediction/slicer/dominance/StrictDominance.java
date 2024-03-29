package de.uniks.vs.methodresourceprediction.slicer.dominance;

import com.ibm.wala.shrike.shrikeBT.IInstruction;
import com.ibm.wala.shrike.shrikeCT.InvalidClassFileException;
import de.uniks.vs.methodresourceprediction.slicer.ControlFlow;
import de.uniks.vs.methodresourceprediction.slicer.SlicerGraph;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import org.jgrapht.Graph;
import org.jgrapht.graph.DefaultDirectedGraph;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.io.ComponentNameProvider;
import org.jgrapht.io.ExportException;

public class StrictDominance extends SlicerGraph<Integer> {
  private Graph<Integer, DefaultEdge> graph;
  private Dominance dominance;

  public StrictDominance(Dominance dominance) throws IOException, InvalidClassFileException {
    this.dominance = dominance;
  }

  @Override
  public Graph<Integer, DefaultEdge> getGraph() throws IOException, InvalidClassFileException {
    if (graph != null) {
      return graph;
    }
    graph = new DefaultDirectedGraph<>(DefaultEdge.class);

    // Every vertex in the control flow is present in the forward dominance graph
    // as well.
    Graph<Integer, DefaultEdge> dominanceGraph = dominance.getGraph();
    dominanceGraph.vertexSet().forEach(v -> graph.addVertex(v));

    for (DefaultEdge dominanceEdge : dominanceGraph.edgeSet()) {
      final Integer edgeSource = dominanceGraph.getEdgeSource(dominanceEdge);
      final Integer edgeTarget = dominanceGraph.getEdgeTarget(dominanceEdge);

      if (edgeSource.equals(edgeTarget)) {
        // Omit self dominance
        continue;
      }
      graph.addEdge(edgeSource, edgeTarget);
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
            ControlFlow controlFlow = dominance.getControlFlow();
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

  public Map<Integer, Integer> getDominators() throws IOException, InvalidClassFileException {
    Map<Integer, Integer> dominatorMap = new HashMap<>();
    for (DefaultEdge edge : getGraph().edgeSet()) {
      final Integer edgeSource = getGraph().getEdgeSource(edge);
      final Integer edgeTarget = getGraph().getEdgeTarget(edge);
      dominatorMap.put(edgeTarget, edgeSource);
    }
    return dominatorMap;
  }

  public Dominance getDominance() {
    return dominance;
  }
}
