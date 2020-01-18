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

import com.ibm.wala.shrikeBT.IInstruction;
import com.ibm.wala.shrikeBT.ILoadInstruction;
import com.ibm.wala.shrikeBT.IStoreInstruction;
import com.ibm.wala.shrikeCT.InvalidClassFileException;

public class DataDependency {
	private ControlFlow controlFlow;
	private Graph<Integer, DefaultEdge> dataDependencyGraph;
	private List<List<Integer>> simpleCycles;

	public DataDependency(ControlFlow controlFlowGraph) throws IOException, InvalidClassFileException {
		this.controlFlow = controlFlowGraph;
	}

	public Graph<Integer, DefaultEdge> getGraph() throws IOException, InvalidClassFileException {
		if (dataDependencyGraph != null) {
			return dataDependencyGraph;
		}
		dataDependencyGraph = getDependencyGraph(controlFlow.getGraph());
		return dataDependencyGraph;
	}

	public List<List<Integer>> getSimpleCycles() throws IOException, InvalidClassFileException {
		if (simpleCycles != null) {
			return simpleCycles;
		}

		JohnsonSimpleCycles<Integer, DefaultEdge> johnsonSimpleCycles = new JohnsonSimpleCycles<>(getGraph());
		simpleCycles = johnsonSimpleCycles.findSimpleCycles();
		return simpleCycles;
	}

	private Graph<Integer, DefaultEdge> getDependencyGraph(Graph<Integer, DefaultEdge> cfg)
			throws IOException, InvalidClassFileException {
		// Create and add vertices (instruction indexes) first
		Graph<Integer, DefaultEdge> dependencyGraph = new DefaultDirectedGraph<>(DefaultEdge.class);
		cfg.vertexSet().forEach(v -> dependencyGraph.addVertex(v));

		// Add edges to the graph if there is a data dependency. Start with iterating
		// for each instruction index. For each instruction, we analyze all preceding
		// instructions if there is a data dependency.
		for (int instructionIndex : cfg.vertexSet()) {
			buildGraphForVertex(cfg, instructionIndex, instructionIndex, new HashSet<>(), dependencyGraph);
		}
		return dependencyGraph;
	}

	private void buildGraphForVertex(Graph<Integer, DefaultEdge> cfg, int focusedIndex, int instructionIndex,
			Set<Integer> visitedVertices, Graph<Integer, DefaultEdge> dependencyGraph)
			throws IOException, InvalidClassFileException {
		// We skip already visited instructions
		if (!visitedVertices.add(instructionIndex)) {
			return;
		}

		// Only data dependencies between different instructions are interesting
		if (focusedIndex != instructionIndex) {
			// Check dependencies for both instructions
			IInstruction[] instructions = controlFlow.getMethodData().getInstructions();
			IInstruction instructionA = instructions[focusedIndex];
			IInstruction instructionB = instructions[instructionIndex];

			if (checkDataDependency(instructionA, instructionB)) {
				if (!dependencyGraph.containsEdge(focusedIndex, instructionIndex)) {
					dependencyGraph.addEdge(focusedIndex, instructionIndex);
				}
			}
		}

		// Recursively analyze preceding instructions
		for (DefaultEdge edge : cfg.incomingEdgesOf(instructionIndex)) {
			int sourceInstructionIndex = cfg.getEdgeSource(edge);
			buildGraphForVertex(cfg, focusedIndex, sourceInstructionIndex, visitedVertices, dependencyGraph);
		}
	}

	private static boolean checkDataDependency(IInstruction instructionA, IInstruction instructionB) {
		// A data dependency exists, if a there is a load instruction which are a
		// preceding store instruction in all possible execution paths.
		if (instructionA instanceof ILoadInstruction) {
			ILoadInstruction loadInstruction = (ILoadInstruction) instructionA;
			if (instructionB instanceof IStoreInstruction) {
				IStoreInstruction storeInstruction = (IStoreInstruction) instructionB;
				if (loadInstruction.getVarIndex() == storeInstruction.getVarIndex()) {
					return true;
				}
			}
		}
		return false;
	}

	public String dotPrint() throws IOException, InvalidClassFileException {
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
		GraphExporter<Integer, DefaultEdge> exporter = new DOTExporter<>(vertexIdProvider, vertexLabelProvider, null);
		Writer writer = new StringWriter();
		try {
			exporter.exportGraph(getGraph(), writer);
		} catch (ExportException e) {
			e.printStackTrace();
		}
		return writer.toString();
	}
}
