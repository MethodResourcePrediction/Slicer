package de.rherzog.master.thesis.slicer;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.jgrapht.Graph;
import org.jgrapht.graph.DefaultDirectedGraph;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.io.ComponentNameProvider;
import org.jgrapht.io.ExportException;

import com.ibm.wala.shrikeBT.IInstruction;
import com.ibm.wala.shrikeCT.InvalidClassFileException;

public class ForwardDominanceTree extends SlicerGraph<Integer> {
	private ControlFlow controlFlow;
	private Graph<Integer, DefaultEdge> graph;

	public ForwardDominanceTree(ControlFlow controlFlowGraph) {
		this.controlFlow = controlFlowGraph;
	}

	@Override
	public Graph<Integer, DefaultEdge> getGraph() throws IOException, InvalidClassFileException {
		if (graph != null) {
			return graph;
		}
		IInstruction[] instructions = controlFlow.getMethodData().getInstructions();
		graph = new DefaultDirectedGraph<>(DefaultEdge.class);

		// Every vertex in the control flow is present in the forward dominance graph
		// as well.
		Graph<Integer, DefaultEdge> cfg = controlFlow.getGraph();
		cfg.vertexSet().forEach(v -> graph.addVertex(v));

		Map<Integer, Set<Integer>> dom = new HashMap<>();

		// https://en.wikipedia.org/wiki/Dominator_(graph_theory)
		final int n0 = 0;
//		 // dominator of the start node is the start itself
//		 Dom(n0) = {n0}
		dom.put(n0, Set.of(0));

//		 // for all other nodes, set all nodes as the dominators
//		 for each n in N - {n0}
//		     Dom(n) = N;
		final Set<Integer> N = IntStream.range(0, instructions.length).boxed().collect(Collectors.toSet());
//		 for each n in N - {n0}
		for (int n : N) {
			if (n == n0) {
				continue;
			}
//		     Dom(n) = N;
			dom.put(n, N);
		}

//		 // iteratively eliminate nodes that are not dominators
//		 while changes in any Dom(n)
//		     for each n in N - {n0}:
//		         Dom(n) = {n} union with intersection over Dom(p) for all p in pred(n)

		// while changes in any Dom(n)
		boolean changes = false;
		do {
			changes = false;

			// for each n in N - {n0}:
			for (int n : N) {
				if (n == n0) {
					continue;
				}
				// Dom(n) = {n} union with intersection over Dom(p) for all p in pred(n)
				final Set<Integer> dom_n = new HashSet<>(Set.of(n));
				final Set<Integer> intersection = new HashSet<>(N);

				// for all p in pred(n)
				for (DefaultEdge edge : controlFlow.getGraph().incomingEdgesOf(n)) {
					final int p = controlFlow.getGraph().getEdgeSource(edge);
					final Set<Integer> dom_p = dom.get(p);

					intersection.retainAll(dom_p);
				}

				// Dom(n) = {n} union with intersection
				dom_n.addAll(intersection);
				final Set<Integer> oldDom_n = dom.get(n);
				if (!oldDom_n.equals(dom_n)) {
					changes |= true;
					dom.put(n, dom_n);
				}
			}
		} while (changes);

		// Add domination indexes to the final graph
		for (Entry<Integer, Set<Integer>> entry : dom.entrySet()) {
			for (int domIndex : entry.getValue()) {
				// Only add an edge to the graph if there is a precedence in the cfg. Instead it
				// would yield to an absolute chaotic graph faaar away from being a tree.
				if (controlFlow.getGraph().containsEdge(domIndex, entry.getKey())) {
					graph.addEdge(entry.getKey(), domIndex);
				}
			}
		}
		return graph;
	}

	@Override
	protected String dotPrint() throws IOException, InvalidClassFileException, ExportException {
		IInstruction[] instructions = controlFlow.getMethodData().getInstructions();

		// use helper classes to define how vertices should be rendered,
		// adhering to the DOT language restrictions
		ComponentNameProvider<Integer> vertexIdProvider = new ComponentNameProvider<Integer>() {
			public String getName(Integer index) {
				return String.valueOf(index);
			}
		};
		ComponentNameProvider<Integer> vertexLabelProvider = new ComponentNameProvider<Integer>() {
			public String getName(Integer index) {
				return index + ": " + instructions[index].toString();
			}
		};
		return getExporterGraphString(vertexIdProvider, vertexLabelProvider);
	}

}
