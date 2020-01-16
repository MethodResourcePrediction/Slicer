package de.rherzog.master.thesis.slicer;

import java.io.IOException;

import org.jgrapht.Graph;
import org.jgrapht.graph.DefaultDirectedGraph;
import org.jgrapht.graph.DefaultEdge;

import com.ibm.wala.shrikeBT.ConstantInstruction;
import com.ibm.wala.shrikeBT.IArrayLoadInstruction;
import com.ibm.wala.shrikeBT.IBinaryOpInstruction;
import com.ibm.wala.shrikeBT.IConditionalBranchInstruction;
import com.ibm.wala.shrikeBT.IInstruction;
import com.ibm.wala.shrikeBT.IInvokeInstruction;
import com.ibm.wala.shrikeBT.ILoadInstruction;
import com.ibm.wala.shrikeBT.IStoreInstruction;
import com.ibm.wala.shrikeBT.shrikeCT.CTCompiler;
import com.ibm.wala.shrikeCT.InvalidClassFileException;
import com.ibm.wala.types.TypeName;
import com.ibm.wala.util.strings.StringStuff;

public class Blocks {
	private ControlFlow controlFlowGraph;
	private Graph<Block, DefaultEdge> graph;

	public Blocks(ControlFlow controlFlowGraph) {
		this.controlFlowGraph = controlFlowGraph;
	}

	public Graph<Block, DefaultEdge> getGraph() throws IOException, InvalidClassFileException {
		if (graph != null) {
			return graph;
		}

		// Build up block graph with vertices
		int blockId = 0;
		IInstruction[] instructions = controlFlowGraph.getMethodData().getInstructions();
		graph = new DefaultDirectedGraph<>(DefaultEdge.class);
		for (int index = 0; index < instructions.length;) {
			// Build up blocks
			Block block = new Block(blockId++);
			graph.addVertex(block);

			IInstruction instruction = instructions[index];
			block.addInstruction(index, instruction);

			int stack = instruction.getPushedWordSize();
			for (index++; index < instructions.length && stack > 0; index++) {
				instruction = instructions[index];
				stack += instruction.getPushedWordSize();
				stack -= getPoppedSize(instruction);

				block.addInstruction(index, instruction);
			}
//			System.out.println(block);
		}

		// Add edges between the blocks (vertices)
		Graph<Integer, DefaultEdge> cfg = controlFlowGraph.getGraph();
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

	private Block getBlockForIndex(int index) {
		for (Block block : graph.vertexSet()) {
			if (block.getInstructions().containsKey(index)) {
				return block;
			}
		}
		return null;
	}

	private byte getPoppedSize(IInstruction iInstruction) {
		if (iInstruction instanceof ILoadInstruction) {
			ILoadInstruction instruction = (ILoadInstruction) iInstruction;
			return getWordSizeByType(instruction.getType());
		}
		if (iInstruction instanceof IStoreInstruction) {
			IStoreInstruction instruction = (IStoreInstruction) iInstruction;
			return getWordSizeByType(instruction.getType());
		}
		if (iInstruction instanceof IInvokeInstruction) {
			IInvokeInstruction instruction = (IInvokeInstruction) iInstruction;
			String methodSignature = instruction.getMethodSignature();
			TypeName[] parameterNames = StringStuff.parseForParameterNames(methodSignature);
			byte poppedSize = 0;
			for (TypeName parameterName : parameterNames) {
				poppedSize += getWordSizeByType(parameterName.getClassName().toString());
			}
			return poppedSize;
		}
		if (iInstruction instanceof IConditionalBranchInstruction) {
			IConditionalBranchInstruction instruction = (IConditionalBranchInstruction) iInstruction;
			return (byte) (2 * getWordSizeByType(instruction.getType()));
		}
		if (iInstruction instanceof IBinaryOpInstruction) {
			IBinaryOpInstruction instruction = (IBinaryOpInstruction) iInstruction;
			return (byte) (2 * getWordSizeByType(instruction.getType()));
		}
		if (iInstruction instanceof ConstantInstruction) {
			// A constant instruction does never pop anything
			return 0;
		}
		if (iInstruction instanceof IArrayLoadInstruction) {
			IArrayLoadInstruction instruction = (IArrayLoadInstruction) iInstruction;
			// TODO Usually consumes 2 elements but what about a primitive array?
//			return getWordSizeByType(instruction.getType());
			return 2;
		}
		throw new UnsupportedOperationException("Unhandled instruction type " + iInstruction.getClass().getName());
	}

	private static byte getWordSizeByType(String type) {
		switch (type) {
		case CTCompiler.TYPE_double:
		case CTCompiler.TYPE_float:
		case CTCompiler.TYPE_long:
			return 2;
		default:
			return 1;
		}
	}
}
