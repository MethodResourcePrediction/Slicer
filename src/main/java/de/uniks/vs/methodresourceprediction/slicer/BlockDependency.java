package de.uniks.vs.slicer;

import java.io.IOException;
import java.util.List;
import java.util.Set;

import org.jgrapht.Graph;
import org.jgrapht.alg.cycle.JohnsonSimpleCycles;
import org.jgrapht.graph.DefaultDirectedGraph;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.io.ComponentNameProvider;
import org.jgrapht.io.ExportException;

import com.ibm.wala.shrikeBT.IInstruction;
import com.ibm.wala.shrikeCT.InvalidClassFileException;

import de.uniks.vs.utils.Utilities;

public class BlockDependency extends SlicerGraph<Block> {
	private ControlFlow controlFlow;
	private Graph<Block, DefaultEdge> graph;
	private List<List<Block>> simpleCycles;

	public BlockDependency(ControlFlow controlFlowGraph) {
		this.controlFlow = controlFlowGraph;
	}

	@Override
	public Graph<Block, DefaultEdge> getGraph() throws IOException, InvalidClassFileException {
		if (graph != null) {
			return graph;
		}

		// Build up block graph with vertices
		int blockId = 0;
		IInstruction[] instructions = controlFlow.getMethodData().getInstructions();
		graph = new DefaultDirectedGraph<>(DefaultEdge.class);
		for (int index = 0; index < instructions.length;) {
			// Build up blocks
			Block block = new Block(blockId++);
			graph.addVertex(block);

			IInstruction instruction = instructions[index];
			block.addInstruction(index, instruction);

			// Group subsequent instructions until the stack size equals 0. A block is
			// complete, if the stack is empty (=0) after some instructions.
			// TODO Rewrite this with Stack-Class?
			int stack = Utilities.getPushedSize(instruction);
			for (index++; index < instructions.length && stack > 0; index++) {
				instruction = instructions[index];
				stack -= Utilities.getPoppedSize(instruction);
				stack += Utilities.getPushedSize(instruction);
				if (stack < 0) {
					throw new IllegalStateException("Stack cannot be negative. Is: " + stack);
				}

				block.addInstruction(index, instruction);
			}
//			System.out.println(block);
		}

		// Add edges between the blocks (vertices)
		Graph<Integer, DefaultEdge> cfg = controlFlow.getGraph();
		for (DefaultEdge edge : cfg.edgeSet()) {
			int sourceIndex = cfg.getEdgeSource(edge);
			int targetIndex = cfg.getEdgeTarget(edge);

			Block sourceBlock = getBlockForIndex(sourceIndex);
			Block targetBlock = getBlockForIndex(targetIndex);
			if (sourceBlock != targetBlock) {
				graph.addEdge(sourceBlock, targetBlock);
			}
		}
		return graph;
	}

	@Override
	protected String dotPrint() throws IOException, InvalidClassFileException, ExportException {
		// use helper classes to define how vertices should be rendered,
		// adhering to the DOT language restrictions
		ComponentNameProvider<Block> vertexIdProvider = new ComponentNameProvider<>() {
			public String getName(Block block) {
				return String.valueOf(block.getId());
			}
		};
		ComponentNameProvider<Block> vertexLabelProvider = new ComponentNameProvider<>() {
			public String getName(Block block) {
				return block.toString();
			}
		};
		return getExporterGraphString(vertexIdProvider, vertexLabelProvider);
	}

	public List<List<Block>> getSimpleCycles() throws IOException, InvalidClassFileException {
		if (simpleCycles != null) {
			return simpleCycles;
		}

		JohnsonSimpleCycles<Block, DefaultEdge> johnsonSimpleCycles = new JohnsonSimpleCycles<>(getGraph());
		simpleCycles = johnsonSimpleCycles.findSimpleCycles();
		return simpleCycles;
	}

	public Set<Block> getBlocks() throws IOException, InvalidClassFileException {
		return getGraph().vertexSet();
	}

	public Block getBlockForIndex(int index) throws IOException, InvalidClassFileException {
		for (Block block : getGraph().vertexSet()) {
			if (block.getInstructions().containsKey(index)) {
				return block;
			}
		}
		return null;
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

	public ControlFlow getControlFlow() {
		return controlFlow;
	}
}
