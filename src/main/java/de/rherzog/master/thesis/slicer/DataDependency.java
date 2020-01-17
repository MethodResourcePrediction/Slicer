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
		Graph<Integer, DefaultEdge> dependencyGraph = new DefaultDirectedGraph<>(DefaultEdge.class);
		cfg.vertexSet().forEach(v -> dependencyGraph.addVertex(v));

		for (int instructionIndex : cfg.vertexSet()) {
//			System.out.println("Focus on: " + instructionIndex);
			buildGraphForVertex(cfg, instructionIndex, instructionIndex, new HashSet<>(), dependencyGraph);
		}
		return dependencyGraph;
	}

	private void buildGraphForVertex(Graph<Integer, DefaultEdge> cfg, int focusedIndex, int instructionIndex,
			Set<Integer> visitedVertices, Graph<Integer, DefaultEdge> dependencyGraph)
			throws IOException, InvalidClassFileException {
		if (!visitedVertices.add(instructionIndex)) {
			return;
		}

		if (focusedIndex != instructionIndex) {
//			System.out.println("  Check data dependency: (" + focusedIndex + ", " + instructionIndex + ")");

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

		for (DefaultEdge edge : cfg.incomingEdgesOf(instructionIndex)) {
			int sourceInstructionIndex = cfg.getEdgeSource(edge);
			buildGraphForVertex(cfg, focusedIndex, sourceInstructionIndex, visitedVertices, dependencyGraph);
		}
	}

	private static boolean checkDataDependency(IInstruction instructionA, IInstruction instructionB) {
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
