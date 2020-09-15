package de.rherzog.master.thesis.slicer;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.jgrapht.Graph;
import org.jgrapht.GraphPath;
import org.jgrapht.alg.cycle.JohnsonSimpleCycles;
import org.jgrapht.alg.shortestpath.AllDirectedPaths;
import org.jgrapht.graph.DefaultDirectedGraph;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.io.ComponentNameProvider;
import org.jgrapht.io.ExportException;

import com.ibm.wala.shrikeBT.IConditionalBranchInstruction;
import com.ibm.wala.shrikeBT.IInstruction;
import com.ibm.wala.shrikeCT.InvalidClassFileException;

public class ControlDependency extends SlicerGraph<Integer> {
	private ControlFlow controlFlow;
	private ForwardDominanceTree forwardDominanceTree;

	private Graph<Integer, DefaultEdge> graph;
	private List<List<Integer>> simpleCycles;

	public static final int ROOT_INDEX = -1;

	public ControlDependency(ControlFlow controlFlowGraph, ForwardDominanceTree forwardDominanceTree) {
		this.controlFlow = controlFlowGraph;
		this.forwardDominanceTree = forwardDominanceTree;
	}

	@Override
	public Graph<Integer, DefaultEdge> getGraph() throws IOException, InvalidClassFileException {
		if (graph != null) {
			return graph;
		}

		// Build up graph with vertices
//		IInstruction[] instructions = controlFlow.getMethodData().getInstructions();
		graph = new DefaultDirectedGraph<>(DefaultEdge.class);

		// In a control dependency graph there is a root node which marks the program
		// start. Add it first
		graph.addVertex(ROOT_INDEX);

		// Every vertex in the control flow is present in the control dependency graph
		// as well.
		final Graph<Integer, DefaultEdge> cfg = controlFlow.getGraph();
		final Graph<Integer, DefaultEdge> fdt = forwardDominanceTree.getGraph();
		cfg.vertexSet().forEach(v -> graph.addVertex(v));

		// https://www.cs.colorado.edu/~kena/classes/5828/s00/lectures/lecture15.pdf
		// Y is control dependent on X <=> there is a path in the CFG from X to Y that
		// doesn't contain the immediate forward dominator of X
		AllDirectedPaths<Integer, DefaultEdge> cfgPaths = new AllDirectedPaths<>(cfg);
		for (int x : cfg.vertexSet()) {
			for (int y : cfg.vertexSet()) {
				System.out.println("X: " + x + ", Y: " + y);
//				if (x == y) {
//					continue;
//				}
				final List<GraphPath<Integer, DefaultEdge>> paths = cfgPaths.getAllPaths(x, y, true, null);

				boolean controlDependent = false;
				for (GraphPath<Integer, DefaultEdge> path : paths) {
					if (path.getLength() > 0) {
//						System.out.println(path.toString());
						final HashSet<Integer> vertexSetOnPath = new HashSet<>(path.getVertexList());

						final Integer intermediateForwardDominator = forwardDominanceTree
								.getImmediateForwardDominator(x);
						if (intermediateForwardDominator == null) {
//							controlDependent = true;
						}
						if (!vertexSetOnPath.contains(intermediateForwardDominator)) {
							controlDependent = true;
						}
					}
				}

				System.out.println(y + " is" + (controlDependent ? "" : " NOT") + " control dependent on " + x);
				if (controlDependent) {
					final Integer intermediateForwardDominator = forwardDominanceTree.getImmediateForwardDominator(y);
					graph.addEdge(x, y);
					break;
				}
			}
		}

		// Build up the edges which shows the control dependencies
		// Start with the root node (index) and begin to analyze with the first
		// instruction (index 0)
//		iterate(new HashSet<>(), cfg, instructions, ROOT_INDEX, 0);
		return graph;
	}

	private void iterate(Set<Integer> visited, Graph<Integer, DefaultEdge> cfg, IInstruction[] instructions, int parent,
			int index) {
		// We just need to visit each node once
		if (!visited.add(index)) {
			return;
		}

		// If we havn't seen the instruction with "index" before, it always depends on
		// the parent index.
		graph.addEdge(parent, index);
		// If there is a edge to one or more instructions in the cfg focus on it.
		for (DefaultEdge edge : cfg.outgoingEdgesOf(index)) {
			int targetInstructionIndex = cfg.getEdgeTarget(edge);

			IInstruction instruction = instructions[index];
			// If we visit a ConditionalBranchInstruction (CBI), all dependent instructions
			// can only be reached by executing the CBI first. Hence, the new parent is the
			// CBI instruction.
			if (instruction instanceof IConditionalBranchInstruction) {
				iterate(visited, cfg, instructions, index, targetInstructionIndex);
			} else {
				iterate(visited, cfg, instructions, parent, targetInstructionIndex);
			}
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
		IInstruction[] instructions = controlFlow.getMethodData().getInstructions();

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
				return index + ": " + instructions[index].toString();
			}
		};
		return getExporterGraphString(vertexIdProvider, vertexLabelProvider);
	}

	public List<List<Integer>> getSimpleCycles() throws IOException, InvalidClassFileException {
		if (simpleCycles != null) {
			return simpleCycles;
		}

		JohnsonSimpleCycles<Integer, DefaultEdge> johnsonSimpleCycles = new JohnsonSimpleCycles<>(getGraph());
		simpleCycles = johnsonSimpleCycles.findSimpleCycles();
		return simpleCycles;
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
