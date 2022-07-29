package de.uniks.vs.methodresourceprediction.slicer.dominance;

import com.ibm.wala.shrike.shrikeBT.IInstruction;
import com.ibm.wala.shrike.shrikeCT.InvalidClassFileException;
import de.uniks.vs.methodresourceprediction.slicer.ControlFlow;
import de.uniks.vs.methodresourceprediction.slicer.SlicerGraph;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.jgrapht.Graph;
import org.jgrapht.graph.DefaultDirectedGraph;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.io.ComponentNameProvider;
import org.jgrapht.io.ExportException;

public class ImmediatePostDominance extends SlicerGraph<Integer> {
  private Graph<Integer, DefaultEdge> graph;
  private StrictPostDominance strictPostDominance;

  public ImmediatePostDominance(StrictPostDominance strictPostDominance)
      throws IOException, InvalidClassFileException {
    this.strictPostDominance = strictPostDominance;
  }

  @Override
  public Graph<Integer, DefaultEdge> getGraph() throws IOException, InvalidClassFileException {
    if (graph != null) {
      return graph;
    }
    graph = new DefaultDirectedGraph<>(DefaultEdge.class);

    // Every vertex in the control flow is present in the forward dominance graph
    // as well.
    Graph<Integer, DefaultEdge> strictPostDominanceGraph = strictPostDominance.getGraph();
    strictPostDominanceGraph.vertexSet().forEach(v -> graph.addVertex(v));

    // The immediate dominator or idom of a node n is the unique node that strictly
    // dominates n but does not strictly dominate any other node that strictly
    // dominates n
    for (int n : strictPostDominanceGraph.vertexSet()) {
      // Find all nodes that strictly dominate n
      final Set<DefaultEdge> strictlyPostDominatingEdgesOfN =
          strictPostDominanceGraph.incomingEdgesOf(n);
      final Set<Integer> strictlyPostDominatingNodesOfN = new HashSet<>();
      for (DefaultEdge strictlyPostDominatingEdgeOfN : strictlyPostDominatingEdgesOfN) {
        strictlyPostDominatingNodesOfN.add(
            strictPostDominanceGraph.getEdgeSource(strictlyPostDominatingEdgeOfN));
      }

      // ... but does not strictly dominate any other node that strictly dominates n
      Integer uniquePostDominatingNode = null;
      for (int strictlyPostDominatingNodeOfN : strictlyPostDominatingNodesOfN) {
        boolean postDominatesOtherNodeThatDominatesN = false;
        for (int strictlyDominatingNodeOfN2 : strictlyPostDominatingNodesOfN) {
          if (strictPostDominanceGraph.containsEdge(
              strictlyPostDominatingNodeOfN, strictlyDominatingNodeOfN2)) {
            postDominatesOtherNodeThatDominatesN = true;
            break; // optimization
          }
        }

        if (!postDominatesOtherNodeThatDominatesN) {
          if (uniquePostDominatingNode == null) {
            uniquePostDominatingNode = strictlyPostDominatingNodeOfN;
          } else {
            // Not possible
            throw new RuntimeException();
          }
        }
      }

      if (uniquePostDominatingNode == null
          && !strictPostDominanceGraph.incomingEdgesOf(n).isEmpty()) {
        // Not possible
        throw new RuntimeException(
            "Starting node " + n + " has a dominating node which is not possible");
      }
      //			System.out.println(uniqueDominatingNode + " immediately dominates " + n);
      if (uniquePostDominatingNode != null) {
        graph.addEdge(uniquePostDominatingNode, n);
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
            ControlFlow controlFlow = strictPostDominance.getPostDominance().getControlFlow();
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

  public StrictPostDominance getStrictPostDominance() {
    return strictPostDominance;
  }
}
