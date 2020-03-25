package de.rherzog.master.thesis.slicer;

import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.util.HashSet;
import java.util.Set;
import java.util.Stack;
import java.util.stream.IntStream;

import org.jgrapht.Graph;
import org.jgrapht.graph.DefaultDirectedGraph;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.io.ComponentNameProvider;
import org.jgrapht.io.DOTExporter;
import org.jgrapht.io.ExportException;
import org.jgrapht.io.GraphExporter;

import com.ibm.wala.shrikeBT.IInstruction;
import com.ibm.wala.shrikeCT.InvalidClassFileException;

public class ArgumentDependency {
	private ControlFlow controlFlow;
	private Graph<Integer, DefaultEdge> graph;

	public ArgumentDependency(ControlFlow controlFlow) {
		this.controlFlow = controlFlow;
	}

	public Graph<Integer, DefaultEdge> getGraph() throws IOException, InvalidClassFileException {
		if (graph != null) {
			return graph;
		}
		graph = new DefaultDirectedGraph<>(DefaultEdge.class);

		IInstruction[] instructions = controlFlow.getMethodData().getInstructions();

		// Add vertices (all instructions)
		IntStream.range(0, instructions.length).forEach(i -> graph.addVertex(i));

		Stack<Integer> stack = new Stack<>();
		// Iterate all instructions and build the control flow
		for (int index = 0; index < instructions.length - 1; index++) {
			IInstruction instruction = instructions[index];

			boolean pushedElement = instruction.getPushedWordSize() > 0;
			int poppedElements = instruction.getPoppedCount();

			// Simulate stack execution
			for (int popIteration = 0; popIteration < poppedElements; popIteration++) {
				Integer poppedInstructionIndex = stack.pop();
				graph.addEdge(index, poppedInstructionIndex);
			}

			if (pushedElement) {
				stack.push(index);
			}
		}

		if (stack.size() > 0) {
			throw new IllegalStateException(
					"Stack must be empty after execution simulation but has " + stack.size() + " elements left");
		}
		return graph;
	}

	public Set<Integer> getArgumentInstructions(int index) throws IOException, InvalidClassFileException {
		Graph<Integer, DefaultEdge> argumentDependencyGraph = getGraph();
		Set<DefaultEdge> outgoingEdges = argumentDependencyGraph.outgoingEdgesOf(index);
		
		Set<Integer> argumentInstructionSet = new HashSet<>();
		for (DefaultEdge argumentEdge : outgoingEdges) {
			Integer edgeTarget = argumentDependencyGraph.getEdgeTarget(argumentEdge);
			argumentInstructionSet.add(edgeTarget);
		}
		return argumentInstructionSet;
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
