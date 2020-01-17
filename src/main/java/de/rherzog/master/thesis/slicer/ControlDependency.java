package de.rherzog.master.thesis.slicer;

import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.jgrapht.Graph;
import org.jgrapht.alg.cycle.JohnsonSimpleCycles;
import org.jgrapht.graph.DefaultDirectedGraph;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.io.ComponentNameProvider;
import org.jgrapht.io.DOTExporter;
import org.jgrapht.io.ExportException;
import org.jgrapht.io.GraphExporter;

import com.ibm.wala.shrikeBT.IConditionalBranchInstruction;
import com.ibm.wala.shrikeBT.IInstruction;
import com.ibm.wala.shrikeCT.InvalidClassFileException;

public class ControlDependency {
	private ControlFlow controlFlow;
	private Graph<Integer, DefaultEdge> graph;
	private List<List<Integer>> simpleCycles;

	private final int rootIndex = -1;

	public ControlDependency(ControlFlow controlFlowGraph) {
		this.controlFlow = controlFlowGraph;
	}

	public Graph<Integer, DefaultEdge> getGraph() throws IOException, InvalidClassFileException {
		if (graph != null) {
			return graph;
		}

		// Build up block graph with vertices
		IInstruction[] instructions = controlFlow.getMethodData().getInstructions();
		graph = new DefaultDirectedGraph<>(DefaultEdge.class);

		graph.addVertex(rootIndex);
		Graph<Integer, DefaultEdge> cfg = controlFlow.getGraph();
		cfg.vertexSet().forEach(v -> graph.addVertex(v));

		iterate(new HashSet<>(), cfg, instructions, rootIndex, 0);
		return graph;
	}

	private void iterate(Set<Integer> visited, Graph<Integer, DefaultEdge> cfg, IInstruction[] instructions, int parent,
			int index) {
		if (!visited.add(index)) {
			return;
		}

		graph.addEdge(parent, index);
		for (DefaultEdge edge : cfg.outgoingEdgesOf(index)) {
			int targetInstructionIndex = cfg.getEdgeTarget(edge);

			IInstruction instruction = instructions[index];
			if (instruction instanceof IConditionalBranchInstruction) {
				iterate(visited, cfg, instructions, index, targetInstructionIndex);
			} else {
				iterate(visited, cfg, instructions, parent, targetInstructionIndex);
			}
		}
	}

	public String dotPrint() throws IOException, InvalidClassFileException {
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
				if (index == rootIndex) {
					return "START";
				}
				return index + ": " + instructions[index].toString();
			}
		};
		GraphExporter<Integer, DefaultEdge> exporter = new DOTExporter<>(vertexIdProvider, vertexLabelProvider, null);
		Writer writer = new StringWriter();
		try {
			exporter.exportGraph(getGraph(), writer);
		} catch (ExportException e) {
			e.printStackTrace();
		}
		return writer.toString();
	}

	public List<List<Integer>> getSimpleCycles() throws IOException, InvalidClassFileException {
		if (simpleCycles != null) {
			return simpleCycles;
		}

		JohnsonSimpleCycles<Integer, DefaultEdge> johnsonSimpleCycles = new JohnsonSimpleCycles<>(getGraph());
		simpleCycles = johnsonSimpleCycles.findSimpleCycles();
		return simpleCycles;
	}
}
