package de.rherzog.master.thesis.slicer.dominance;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
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

public class Dominance extends SlicerGraph<Integer> {
	private ControlFlow controlFlow;
	private Graph<Integer, DefaultEdge> controlFlowGraph;

	private Graph<Integer, DefaultEdge> graph;
	private int startIndex;

	public Dominance(ControlFlow controlFlow, int startIndex) throws IOException, InvalidClassFileException {
		this.controlFlow = controlFlow;
		this.controlFlowGraph = controlFlow.getGraph();
		this.startIndex = startIndex;
	}

	public Dominance(Graph<Integer, DefaultEdge> controlFlowGraph, int startIndex) {
		this.controlFlowGraph = controlFlowGraph;
		this.startIndex = startIndex;
	}

	@Override
	public Graph<Integer, DefaultEdge> getGraph() throws IOException, InvalidClassFileException {
		if (graph != null) {
			return graph;
		}
		graph = new DefaultDirectedGraph<>(DefaultEdge.class);

		// Every vertex in the control flow is present in the dominance graph
		// as well.
		Graph<Integer, DefaultEdge> cfg = controlFlowGraph;
		cfg.vertexSet().forEach(v -> graph.addVertex(v));

		// http://infolab.stanford.edu/~ullman/dragon/w06/lectures/cs243-lec08-wei.pdf
		// If X appears on every path from START to Y, then X dominates Y.
		
		AllDirectedPaths<Integer, DefaultEdge> cfgPaths = new AllDirectedPaths<>(cfg);
		for (int y : cfg.vertexSet()) {
			final List<GraphPath<Integer, DefaultEdge>> cfgPathFromStartToY = cfgPaths.getAllPaths(startIndex, y, true,
					cfg.vertexSet().size());
			
			Set<Integer> Xs = new HashSet<>(cfg.vertexSet());
			for (GraphPath<Integer, DefaultEdge> gp : cfgPathFromStartToY) {
				Xs.retainAll(gp.getVertexList());
			}
			
			for (int x : Xs) {
				// x dominates y
				graph.addEdge(x, y);
			}
		}

		return graph;
	}

//	@Override
//	public Graph<Integer, DefaultEdge> getGraph() throws IOException, InvalidClassFileException {
//		if (graph != null) {
//			return graph;
//		}
//		graph = new DefaultDirectedGraph<>(DefaultEdge.class);
//
//		// Every vertex in the control flow is present in the dominance graph
//		// as well.
//		Graph<Integer, DefaultEdge> cfg = controlFlowGraph;
//		cfg.vertexSet().forEach(v -> graph.addVertex(v));
//
//		Map<Integer, Set<Integer>> dom = new HashMap<>();
//
//		// https://en.wikipedia.org/wiki/Dominator_(graph_theory)
//		final int n0 = startIndex;
////		 // dominator of the start node is the start itself
////		 Dom(n0) = {n0}
//		dom.put(n0, new HashSet<>(Set.of(n0)));
//
////		 // for all other nodes, set all nodes as the dominators
////		 for each n in N - {n0}
////		     Dom(n) = N;
//		final Set<Integer> N = cfg.vertexSet();
////		 for each n in N - {n0}
//		for (int n : N) {
//			if (n == n0) {
//				continue;
//			}
////		     Dom(n) = N;
//			dom.put(n, N);
//		}
//
////		 // iteratively eliminate nodes that are not dominators
////		 while changes in any Dom(n)
////		     for each n in N - {n0}:
////		         Dom(n) = {n} union with intersection over Dom(p) for all p in pred(n)
//
////		AllDirectedPaths<Integer, DefaultEdge> cfgPaths = new AllDirectedPaths<>(cfg);
//
//		// while changes in any Dom(n)
//		boolean changes = false;
//		do {
//			changes = false;
//
//			// for each n in N - {n0}:
//			for (int n : N) {
//				if (n == n0) {
//					continue;
//				}
//				// Dom(n) = {n} union with intersection over Dom(p) for all p in pred(n)
//				final Set<Integer> dom_n = new HashSet<>(Set.of(n));
//				final Set<Integer> intersection = new HashSet<>(N);
//
//				// for all p in pred(n)
//				for (DefaultEdge edge : controlFlowGraph.incomingEdgesOf(n)) {
//					final int p = controlFlowGraph.getEdgeSource(edge);
//
//					final Set<Integer> dom_p = dom.get(p);
//					intersection.retainAll(dom_p);
//
////					// TODO This is somehow inefficient here. There are all paths generated by
////					// JGrapthT and compared. At least nested paths can be omitted since they are
////					// included in the longer ones. Even the target vertices are not selected
////					// optimal. It might be possible to figure out if the target vertex set can be
////					// reduced. For example it is impossible to reach a previous instruction if
////					// there is no conditional branch or loop to go back on the CFG.
////					final List<GraphPath<Integer, DefaultEdge>> allPathsFromPrevN = cfgPaths.getAllPaths(Set.of(prevN),
////							N, true, null);
////					for (GraphPath<Integer, DefaultEdge> graphPath : allPathsFromPrevN) {
////						if (graphPath.getLength() == 0) {
////							// Self-path is not interesting right now
////							continue;
////						}
////
////						// Get all dominators for all vertices on the path and intersect them
////						for (int p : graphPath.getVertexList()) {
////							final Set<Integer> dom_p = dom.get(p);
////							intersection.retainAll(dom_p);
////						}
////					}
//
//				}
//
//				// Dom(n) = {n} union with intersection
//				dom_n.addAll(intersection);
//				final Set<Integer> oldDom_n = dom.get(n);
//				if (!oldDom_n.equals(dom_n)) {
//					changes |= true;
//					dom.put(n, dom_n);
//				}
//			}
//		} while (changes);
//
////		for (Entry<Integer, Set<Integer>> entry : dom.entrySet()) {
////			System.out.println(entry);
////		}
//
////		Map<Integer, Integer> ifdom = new HashMap<>();
////		// Add domination indexes to the final graph
//		for (Entry<Integer, Set<Integer>> entry : dom.entrySet()) {
////			if (entry.getKey() == n0) {
////				continue;
////			}
//
////			int forwardDominator = n0;
//			for (int domIndex : entry.getValue()) {
////				if (domIndex == entry.getKey()) {
////					continue;
////				}
////				forwardDominator = Math.max(forwardDominator, domIndex);
//
////				// Only add an edge to the graph if there is a precedence in the cfg. Instead it
////				// would yield to an absolute chaotic graph faaar away from being a tree.
////				if (controlFlowGraph.containsEdge(domIndex, entry.getKey())) {
////					graph.addEdge(entry.getKey(), domIndex); // Reverse
////					graph.addEdge(domIndex, entry.getKey()); // Forward
////				}
//				graph.addEdge(domIndex, entry.getKey()); // Forward
//			}
////			graph.addEdge(entry.getKey(), forwardDominator); // Reverse
////			graph.addEdge(forwardDominator, entry.getKey()); // Forward
//		}
//		return graph;
//	}

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
