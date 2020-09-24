package de.rherzog.master.thesis.slicer.dominance;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jgrapht.Graph;
import org.jgrapht.GraphPath;
import org.jgrapht.alg.shortestpath.AllDirectedPaths;
import org.jgrapht.graph.DefaultDirectedGraph;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.io.ComponentNameProvider;
import org.jgrapht.io.ExportException;

import com.ibm.wala.shrikeBT.IInstruction;
import com.ibm.wala.shrikeCT.InvalidClassFileException;

import de.rherzog.master.thesis.slicer.ControlFlow;
import de.rherzog.master.thesis.slicer.SlicerGraph;

public class FirstForwardDominatorTree extends SlicerGraph<Integer> {
	private ControlFlow controlFlow;
	private Graph<Integer, DefaultEdge> controlFlowGraph;

	private Graph<Integer, DefaultEdge> graph;

	public FirstForwardDominatorTree(ControlFlow controlFlow) throws IOException, InvalidClassFileException {
		this.controlFlow = controlFlow;
		this.controlFlowGraph = controlFlow.getGraph();
	}

	public FirstForwardDominatorTree(Graph<Integer, DefaultEdge> controlFlowGraph) {
		this.controlFlowGraph = controlFlowGraph;
	}

	@Override
	public Graph<Integer, DefaultEdge> getGraph() throws IOException, InvalidClassFileException {
		if (graph != null) {
			return graph;
		}
		graph = new DefaultDirectedGraph<>(DefaultEdge.class);

		// Logic extracted from:
		// https://www.cs.colorado.edu/~kena/classes/5828/s00/lectures/lecture15.pdf
		// => Y forward dominates X if all paths from X include Y

		AllDirectedPaths<Integer, DefaultEdge> cfgPaths = new AllDirectedPaths<>(controlFlowGraph);

		
//		final int root = -1;
//		graph.addVertex(root);
		controlFlowGraph.vertexSet().forEach(v -> graph.addVertex(v));

		for (int x : graph.vertexSet()) {
//			if (x == root) {
//				continue;
//			}
			final List<GraphPath<Integer, DefaultEdge>> allPaths = cfgPaths.getAllPaths(Set.of(x),
					controlFlowGraph.vertexSet(), true, null);

			// TODO This is really inefficient!
			final List<GraphPath<Integer, DefaultEdge>> uniquePaths = new ArrayList<>(allPaths);

			// Iterate all paths to eliminate nested paths => unique paths
			// TODO This might be preventable with the right choice of path finding
			// algorithm
			for (GraphPath<Integer, DefaultEdge> allPath : allPaths) {
				for (GraphPath<Integer, DefaultEdge> allPath2 : allPaths) {
					if (allPath.equals(allPath2)) {
						// Self-Path
						continue;
					}
					if (allPath.getLength() == 0) {
						uniquePaths.remove(allPath);
						// Node-Path which can be removed
						continue;
					}
					if (allPath2.getLength() == 0) {
						// Node-Path
						continue;
					}

					// Check if our focused path is included in an other path (includes all edges)
					boolean containsAllEdges = true;
					for (DefaultEdge allPathEdge : allPath.getEdgeList()) {
						boolean containsEdge = false;
						for (DefaultEdge allPath2Edge : allPath2.getEdgeList()) {
							if (allPathEdge.equals(allPath2Edge)) {
								containsEdge = true;
							}
						}
						containsAllEdges &= containsEdge;
					}

					// If there is a "wrapping" path, the focused one can be removed
					if (containsAllEdges) {
						uniquePaths.remove(allPath);
					}
				}
			}

			// Get the immediate first forward dominator (ifdom) for the vertex x
			Set<Integer> forwardDominators = new HashSet<>(controlFlowGraph.vertexSet());
			for (GraphPath<Integer, DefaultEdge> uniquePath : uniquePaths) {
				forwardDominators.retainAll(uniquePath.getVertexList());
			}

			// Sort the set of all dominators
			List<Integer> sortedForwardDominators = new ArrayList<>(forwardDominators);
			Collections.sort(sortedForwardDominators);

			// The vertex after the focussed one here is the immediate first forward
			// dominator
			final int vertexIndex = sortedForwardDominators.indexOf(x);
			if (vertexIndex == -1) {
				// Impossible
				continue;
			}
			if (vertexIndex + 1 >= sortedForwardDominators.size()) {
				// No forward dominator
				continue;
			}

			final Integer firstForwardDominator = sortedForwardDominators.get(vertexIndex + 1);
			graph.addEdge(x, firstForwardDominator);
//			graph.addEdge(firstForwardDominator, x);
		}
		return graph;
	}

	public Integer getImmediateForwardDominator(int index) throws IOException, InvalidClassFileException {
		final Set<DefaultEdge> incomingEdges = getGraph().incomingEdgesOf(index);
		if (incomingEdges.size() == 1) {
			// Usual case
			return getGraph().getEdgeSource(incomingEdges.iterator().next());
		}
		if (incomingEdges.size() == 0) {
			// Unusual case or exit node
			return null;
		}
		// Error
		throw new IllegalStateException("There are " + incomingEdges.size() + " immediate forward dominators for index "
				+ index + " which is not possible");
	}

	@Override
	protected String dotPrint() throws IOException, InvalidClassFileException, ExportException {
		// use helper classes to define how vertices should be rendered,
		// adhering to the DOT language restrictions
		ComponentNameProvider<Integer> vertexIdProvider = new ComponentNameProvider<Integer>() {
			public String getName(Integer index) {
				return String.valueOf(index);
			}
		};
		ComponentNameProvider<Integer> vertexLabelProvider = new ComponentNameProvider<Integer>() {
			public String getName(Integer index) {
				if (controlFlow != null) {
//					if (index == -1) {
//						return "ROOT";
//					}
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

	public Map<Integer, Integer> getImmediateForwardDominators() throws IOException, InvalidClassFileException {
		Map<Integer, Integer> dominatorMap = new HashMap<>();
		for (DefaultEdge edge : getGraph().edgeSet()) {
			final Integer edgeSource = getGraph().getEdgeSource(edge);
			final Integer edgeTarget = getGraph().getEdgeTarget(edge);
			dominatorMap.put(edgeTarget, edgeSource);
		}
		return dominatorMap;
	}
}
