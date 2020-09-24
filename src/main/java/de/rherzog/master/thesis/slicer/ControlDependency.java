package de.rherzog.master.thesis.slicer;

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

import com.ibm.wala.shrikeBT.IInstruction;
import com.ibm.wala.shrikeCT.InvalidClassFileException;

import de.rherzog.master.thesis.slicer.dominance.PostDominance;

public class ControlDependency extends SlicerGraph<Integer> {
	private ControlFlow controlFlow;
	private Graph<Integer, DefaultEdge> controlFlowGraph;
	private PostDominance postDominance;

	private Graph<Integer, DefaultEdge> graph;

	public static final int ROOT_INDEX = -1;

	public ControlDependency(ControlFlow controlFlow, PostDominance postDominance)
			throws IOException, InvalidClassFileException {
		this.controlFlow = controlFlow;
		this.controlFlowGraph = controlFlow.getGraph();
		this.postDominance = postDominance;
	}

	public ControlDependency(Graph<Integer, DefaultEdge> controlFlowGraph, PostDominance postDominance) {
		this.controlFlowGraph = controlFlowGraph;
		this.postDominance = postDominance;
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

		// A node y is control dependent on node x (x → y) if
		// – ∃ path p from x to y in the CFG, such that y post‐dominates every node in p
		// (except for x), and
		// – x is not post‐dominated by y

		AllDirectedPaths<Integer, DefaultEdge> cfgPaths = new AllDirectedPaths<>(controlFlowGraph);

		for (int x : controlFlowGraph.vertexSet()) {
			for (int y : controlFlowGraph.vertexSet()) {
				if (x == y) {
					continue;
				}
				// – ∃ path p from x to y in the CFG, such that y post‐dominates every node in p
				// (except for x)
				final List<GraphPath<Integer, DefaultEdge>> xyPaths = cfgPaths.getAllPaths(x, y, false,
						controlFlowGraph.edgeSet().size());

				// such that y post‐dominates every node in p
				boolean existsAnyPathDominatingAllNodes = false;
				for (GraphPath<Integer, DefaultEdge> xyPath : xyPaths) {
					final List<Integer> pathNodes = xyPath.getVertexList();

					boolean dominatesAllNodesInPath = true;
					for (int pathNode : pathNodes) {
						if (pathNode == x) {
							// (except for x)
							continue;
						}
						dominatesAllNodesInPath &= postDominance.isPostDominating(y, pathNode);
					}
					existsAnyPathDominatingAllNodes |= dominatesAllNodesInPath;
				}

				if (!existsAnyPathDominatingAllNodes) {
					// Condition 1 failed
					continue;
				}

				// x is not post‐dominated by y
				if (postDominance.isPostDominating(y, x)) {
					continue;
				}

				// Control dependent from x to y
				graph.addEdge(x, y);
			}
		}

		return graph;
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
