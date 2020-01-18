package de.rherzog.master.thesis.slicer;

import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;

import org.jgrapht.Graph;
import org.jgrapht.alg.cycle.JohnsonSimpleCycles;
import org.jgrapht.graph.DefaultDirectedGraph;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.io.ComponentNameProvider;
import org.jgrapht.io.DOTExporter;
import org.jgrapht.io.ExportException;
import org.jgrapht.io.GraphExporter;

import com.ibm.wala.shrikeBT.ConditionalBranchInstruction;
import com.ibm.wala.shrikeBT.GotoInstruction;
import com.ibm.wala.shrikeBT.IInstruction;
import com.ibm.wala.shrikeBT.MethodData;
import com.ibm.wala.shrikeBT.shrikeCT.ClassInstrumenter;
import com.ibm.wala.shrikeBT.shrikeCT.OfflineInstrumenter;
import com.ibm.wala.shrikeCT.ClassReader;
import com.ibm.wala.shrikeCT.InvalidClassFileException;

import de.rherzog.master.thesis.utils.InstrumenterComparator;

public class ControlFlow {
	private String inputPath;
	private String methodSignature;

	private MethodData methodData;

	private Graph<Integer, DefaultEdge> graph;
	private List<List<Integer>> simpleCycles;

	public ControlFlow(String inputPath, String methodSignature) {
		this.inputPath = inputPath;
		this.methodSignature = methodSignature;
	}

	public Graph<Integer, DefaultEdge> getGraph() throws IOException, InvalidClassFileException {
		if (graph != null) {
			return graph;
		}

		MethodData methodData = getMethodData();
		IInstruction[] instructions = methodData.getInstructions();

		graph = new DefaultDirectedGraph<>(DefaultEdge.class);

		// Add all instruction indexes as vertices first
		for (int index = 0; index < instructions.length; index++) {
			graph.addVertex(index);
		}

		// Iterate all instructions and build the control flow
		for (int index = 0; index < instructions.length - 1; index++) {
			IInstruction a = instructions[index];

			int b = index + 1;
			// If the next instruction is a Goto-/ConditionalBranchInstruction, the flow can
			// differ to realize if's and loops
			if (a instanceof GotoInstruction) {
				int target = a.getBranchTargets()[0];
				b = target;
			}
			if (a instanceof ConditionalBranchInstruction) {
				graph.addEdge(index, b);
				int target = ((ConditionalBranchInstruction) a).getTarget();
				b = target;
			}
			graph.addEdge(index, b);
		}
		return graph;
	}

	public List<List<Integer>> getSimpleCycles() throws IOException, InvalidClassFileException {
		if (simpleCycles != null) {
			return simpleCycles;
		}

		JohnsonSimpleCycles<Integer, DefaultEdge> johnsonSimpleCycles = new JohnsonSimpleCycles<>(getGraph());
		simpleCycles = johnsonSimpleCycles.findSimpleCycles();
		return simpleCycles;
	}

	public String dotPrint() throws IOException, InvalidClassFileException {
		IInstruction[] instructions = getMethodData().getInstructions();

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

	public MethodData getMethodData() throws IOException, InvalidClassFileException {
		if (methodData != null) {
			return methodData;
		}

		InstrumenterComparator comparator = InstrumenterComparator.of(methodSignature);

		OfflineInstrumenter inst = new OfflineInstrumenter();
		inst.addInputJar(new File(inputPath));
		inst.beginTraversal();

		// Iterate each class in the input program and instrument it
		ClassInstrumenter ci;
		while ((ci = inst.nextClass()) != null) {
			// Search for the correct method (MethodData)
			ClassReader reader = ci.getReader();

			for (int methodIndex = 0; methodIndex < reader.getMethodCount(); methodIndex++) {
				methodData = ci.visitMethod(methodIndex);
				if (methodData == null) {
					continue;
				}

				if (!comparator.equals(methodData)) {
					methodData = null;
					continue;
				}
				break;
			}

			// Check if method was not found in this class
			if (methodData != null) {
				break;
			}
		}
		return methodData;
	}

	public List<List<Integer>> getCyclesForInstruction(int instructionIndex)
			throws IOException, InvalidClassFileException {
		List<List<Integer>> scs = getSimpleCycles();
		List<List<Integer>> scsWithInstructionIndex = new ArrayList<>();

		for (List<Integer> sc : scs) {
			if (sc.contains(instructionIndex)) {
				scsWithInstructionIndex.add(sc);
			}
		}
		return scsWithInstructionIndex;
	}
}
