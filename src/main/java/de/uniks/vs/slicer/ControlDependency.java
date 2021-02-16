package de.uniks.vs.slicer;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

import org.jgrapht.Graph;
import org.jgrapht.graph.DefaultDirectedGraph;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.io.ComponentNameProvider;
import org.jgrapht.io.ExportException;

import com.ibm.wala.shrikeBT.IInstruction;
import com.ibm.wala.shrikeCT.InvalidClassFileException;

import de.uniks.vs.slicer.dominance.ImmediatePostDominance;

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

	public ControlDependency(Graph<Integer, DefaultEdge> controlFlowGraph,
			ImmediatePostDominance immediatePostDominance) {
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

		// Version: Own
//		final Graph<Integer, DefaultEdge> immediateDominanceGraph = immediateDominance.getGraph();
//		downOnGraph(graph, immediateDominanceGraph, ROOT_INDEX, startNode);

		// Version 2 (Second definition)
		// http://infolab.stanford.edu/~ullman/dragon/w06/lectures/cs243-lec08-wei.pdf
//		final Graph<Integer, DefaultEdge> immediatePostDominanceGraph = immediatePostDominance.getGraph();
		final Graph<Integer, DefaultEdge> strictPostDominanceGraph = immediatePostDominance.getStrictPostDominance()
				.getPostDominance().getGraph();

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

//		// Version 1
//		// https://compilers.cs.uni-saarland.de/teaching/spa/2014/slides/ProgramDependenceGraph.pdf
//
//		// For every edge x →cf y
//		final Graph<Integer, DefaultEdge> immediatePostDominanceGraph = immediatePostDominance.getGraph();
//		for (DefaultEdge cfgEdge : controlFlowGraph.edgeSet()) {
//			Integer x = controlFlowGraph.getEdgeSource(cfgEdge);
//			Integer y = controlFlowGraph.getEdgeTarget(cfgEdge);
//
////			if (controlFlowGraph.incomingEdgesOf(x).isEmpty()) {
////				// Start node or no parent
////				continue;
////			}
//
//			// where x is not post-dominated by y
//			if (immediatePostDominanceGraph.containsEdge(y, x)) {
//				continue;
//			}
//
//			// one moves upwards from y in the post­‐dominator tree. Every node z
//			// visited before x’s parent is control dependent on x.
//			final Set<DefaultEdge> incomingEdgesOfX = immediatePostDominanceGraph.incomingEdgesOf(x);
//
//			if (incomingEdgesOfX.isEmpty()) {
//				graph.addEdge(ROOT_INDEX, x);
////				upFromUntil(graph, immediateDominanceGraph, x, ROOT_INDEX, y);
//			} else {
//				final DefaultEdge incomingEdgeOfX = incomingEdgesOfX.iterator().next();
//				final Integer parentOfX = immediatePostDominanceGraph.getEdgeSource(incomingEdgeOfX);
//
//				upFromUntil(graph, immediatePostDominanceGraph, x, parentOfX, y);
//			}
//
////			final Set<DefaultEdge> incomingEdgesOfY = immediateDominanceGraph.incomingEdgesOf(y);
////			final DefaultEdge incomingEdgeOfY = incomingEdgesOfY.iterator().next();
////			Integer z = immediateDominanceGraph.getEdgeSource(incomingEdgeOfY);
//
////			do {
////				graph.addEdge(z, x);
////				final Set<DefaultEdge> incomingEdgesOfZ = immediateDominanceGraph.incomingEdgesOf(z);
////				final DefaultEdge incomingEdgeOfZ = incomingEdgesOfZ.iterator().next();
////				z = immediateDominanceGraph.getEdgeSource(incomingEdgeOfZ);
////			} while (!parentOfX.equals(z));
//
////			do {
////				graph.addEdge(x, y);
////
////				final Set<DefaultEdge> incomingEdgesOfX = immediateDominanceGraph.incomingEdgesOf(y);
////				final DefaultEdge incomingEdgeOfX = incomingEdgesOfX.iterator().next();
////				y = immediateDominanceGraph.getEdgeSource(incomingEdgeOfX);
////			} while (x != y);
////			for (DefaultEdge incomingEdgeOfX : immediateDominanceGraph.incomingEdgesOf(x)) {
////				final Integer z = immediateDominanceGraph.getEdgeSource(incomingEdgeOfX);
////				if (z == x) {
////					continue;
////				}
////				System.out.println(incomingEdgeOfX);
////				graph.addEdge(immediateDominanceGraph.getEdgeSource(incomingEdgeOfX),
////						immediateDominanceGraph.getEdgeTarget(incomingEdgeOfX));
////			}
//		}

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

	private static void downOnGraph(final Graph<Integer, DefaultEdge> graph,
			final Graph<Integer, DefaultEdge> immediateDominanceGraph, int parent, int node) {
		final Set<DefaultEdge> outgoingEdgesOfZ = immediateDominanceGraph.outgoingEdgesOf(node);

		if (outgoingEdgesOfZ.size() == 0) {
			// Exit node
			graph.addEdge(parent, node);
			return;
		}

		if (outgoingEdgesOfZ.size() == 1) {
			// Dependent on parent
			graph.addEdge(parent, node);

			int nextNode = graph.getEdgeTarget(outgoingEdgesOfZ.iterator().next());
			downOnGraph(graph, immediateDominanceGraph, parent, nextNode);
		} else {
			// Has node dependent nodes (control dependency)
			for (DefaultEdge outgoingEdgeOfZ : outgoingEdgesOfZ) {
				int nextNode = immediateDominanceGraph.getEdgeTarget(outgoingEdgeOfZ);

				graph.addEdge(node, nextNode);
				downOnGraph(graph, immediateDominanceGraph, node, nextNode);
			}
		}
	}

	private static void upFromUntil(final Graph<Integer, DefaultEdge> graph,
			final Graph<Integer, DefaultEdge> immediateDominanceGraph, final Integer x, final Integer parentOfX,
			Integer z) {
		if (z.equals(parentOfX)) {
			return;
		}
		System.out.println(z + " is control dependent on " + x);
		graph.addEdge(x, z);

		final Set<DefaultEdge> incomingEdgesOfZ = immediateDominanceGraph.incomingEdgesOf(z);
		for (DefaultEdge incomingEdgeOfZ : incomingEdgesOfZ) {
			z = immediateDominanceGraph.getEdgeSource(incomingEdgeOfZ);
			upFromUntil(graph, immediateDominanceGraph, x, parentOfX, z);
		}
	}

	public Set<Integer> getControlDependencyInstructions(int index) throws IOException, InvalidClassFileException {
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
		ComponentNameProvider<Integer> vertexIdProvider = new ComponentNameProvider<>() {
			public String getName(Integer index) {
				return String.valueOf(index);
			}
		};
		ComponentNameProvider<Integer> vertexLabelProvider = new ComponentNameProvider<>() {
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
