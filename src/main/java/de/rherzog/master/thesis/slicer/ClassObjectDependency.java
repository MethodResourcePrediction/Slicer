package de.rherzog.master.thesis.slicer;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map.Entry;

import org.jgrapht.Graph;
import org.jgrapht.graph.DefaultDirectedGraph;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.io.ComponentNameProvider;
import org.jgrapht.io.ExportException;

import com.ibm.wala.shrikeBT.IInstruction;
import com.ibm.wala.shrikeBT.InvokeInstruction;
import com.ibm.wala.shrikeBT.NewInstruction;
import com.ibm.wala.shrikeCT.InvalidClassFileException;

import de.rherzog.master.thesis.utils.Utilities;

public class ClassObjectDependency extends SlicerGraph<Integer> {
	private BlockDependency blockDependency;
	private Graph<Integer, DefaultEdge> graph;

	public ClassObjectDependency(BlockDependency blockDependency) {
		this.blockDependency = blockDependency;
	}

	@Override
	public Graph<Integer, DefaultEdge> getGraph() throws IOException, InvalidClassFileException {
		if (graph != null) {
			return graph;
		}
		graph = new DefaultDirectedGraph<>(DefaultEdge.class);

		// At first iterate all blocks since the constructor to any new object is
		// usually encapsulated
		// TODO always?
		for (Block block : blockDependency.getBlocks()) {
			// Iterate all instructions inside the block to find the New-Instruction
			for (Entry<Integer, IInstruction> entry : block.getInstructions().entrySet()) {
				final Integer instructionIndex = entry.getKey();
				final IInstruction instruction = entry.getValue();

				graph.addVertex(instructionIndex);

				// Check if the instruction creates a new object
				if (instruction instanceof NewInstruction) {
					NewInstruction newInstruction = (NewInstruction) instruction;

					// Next, find the constructor invoke instruction to the new-Instruction.
					// It must be inside the same block, so the index runs until block end index.
					for (int succeedingInstructionIndex = instructionIndex + 1; succeedingInstructionIndex < block
							.getHighestIndex(); succeedingInstructionIndex++) {
						final IInstruction succeedingInstruction = block.getInstructions()
								.get(succeedingInstructionIndex);

						// If there is an invoke Instruction found, check if it is the constructor call
						if (succeedingInstruction instanceof InvokeInstruction) {
							InvokeInstruction succeedingInvokeInstruction = (InvokeInstruction) succeedingInstruction;
							if (Utilities.isConstructorInvokeInstruction(newInstruction, succeedingInvokeInstruction)) {
								// Finally the constructor instruction to any New-Instruction was found and
								// added to the graph.
								graph.addVertex(succeedingInstructionIndex);
								graph.addEdge(instructionIndex, succeedingInstructionIndex);
							}
						}
					}
				}
			}
		}
		return graph;
	}

	public List<Integer> getClassObjectDependencyInstructions(int instructionIndex)
			throws IOException, InvalidClassFileException {
		List<Integer> classObjectDependencyInstructions = new ArrayList<>();
		for (DefaultEdge edge : getGraph().outgoingEdgesOf(instructionIndex)) {
			classObjectDependencyInstructions.add(getGraph().getEdgeTarget(edge));
		}
		return classObjectDependencyInstructions;
	}

	@Override
	protected String dotPrint() throws IOException, InvalidClassFileException, ExportException {
		IInstruction[] instructions = blockDependency.getControlFlow().getMethodData().getInstructions();

		// use helper classes to define how vertices should be rendered,
		// adhering to the DOT language restrictions
		ComponentNameProvider<Integer> vertexIdProvider = new ComponentNameProvider<>() {
			public String getName(Integer index) {
				return String.valueOf(index);
			}
		};
		ComponentNameProvider<Integer> vertexLabelProvider = new ComponentNameProvider<>() {
			public String getName(Integer index) {
				return index + ": " + instructions[index].toString();
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
