package de.rherzog.master.thesis.slicer;

import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.util.List;

import org.jgrapht.Graph;
import org.jgrapht.alg.cycle.JohnsonSimpleCycles;
import org.jgrapht.graph.DefaultDirectedGraph;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.io.ComponentNameProvider;
import org.jgrapht.io.DOTExporter;
import org.jgrapht.io.ExportException;
import org.jgrapht.io.GraphExporter;

import com.ibm.wala.shrikeBT.ConstantInstruction;
import com.ibm.wala.shrikeBT.DupInstruction;
import com.ibm.wala.shrikeBT.IArrayLoadInstruction;
import com.ibm.wala.shrikeBT.IArrayStoreInstruction;
import com.ibm.wala.shrikeBT.IBinaryOpInstruction;
import com.ibm.wala.shrikeBT.IConditionalBranchInstruction;
import com.ibm.wala.shrikeBT.IConversionInstruction;
import com.ibm.wala.shrikeBT.IGetInstruction;
import com.ibm.wala.shrikeBT.IInstruction;
import com.ibm.wala.shrikeBT.IInvokeInstruction;
import com.ibm.wala.shrikeBT.ILoadInstruction;
import com.ibm.wala.shrikeBT.IStoreInstruction;
import com.ibm.wala.shrikeBT.NewInstruction;
import com.ibm.wala.shrikeBT.PopInstruction;
import com.ibm.wala.shrikeBT.ReturnInstruction;
import com.ibm.wala.shrikeBT.shrikeCT.CTCompiler;
import com.ibm.wala.shrikeCT.InvalidClassFileException;
import com.ibm.wala.types.TypeName;
import com.ibm.wala.util.strings.StringStuff;

public class BlockDependency {
	private ControlFlow controlFlow;
	private Graph<Block, DefaultEdge> graph;
	private List<List<Block>> simpleCycles;

	public BlockDependency(ControlFlow controlFlowGraph) {
		this.controlFlow = controlFlowGraph;
	}

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
			int stack = instruction.getPushedWordSize();
			for (index++; index < instructions.length && stack > 0; index++) {
				instruction = instructions[index];
				stack -= getPoppedSize(instruction);
				stack += getPushedSize(instruction);
				if (stack < 0) {
					throw new java.lang.IllegalStateException("Stack cannot be negative. Is: " + stack);
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

	public String dotPrint() throws IOException, InvalidClassFileException {
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
		GraphExporter<Block, DefaultEdge> exporter = new DOTExporter<>(vertexIdProvider, vertexLabelProvider, null);
		Writer writer = new StringWriter();
		try {
			exporter.exportGraph(getGraph(), writer);
		} catch (ExportException e) {
			e.printStackTrace();
		}
		return writer.toString();
	}

	public List<List<Block>> getSimpleCycles() throws IOException, InvalidClassFileException {
		if (simpleCycles != null) {
			return simpleCycles;
		}

		JohnsonSimpleCycles<Block, DefaultEdge> johnsonSimpleCycles = new JohnsonSimpleCycles<>(getGraph());
		simpleCycles = johnsonSimpleCycles.findSimpleCycles();
		return simpleCycles;
	}

	public Block getBlockForIndex(int index) throws IOException, InvalidClassFileException {
		for (Block block : getGraph().vertexSet()) {
			if (block.getInstructions().containsKey(index)) {
				return block;
			}
		}
		return null;
	}

	private static byte getPoppedSize(IInstruction iInstruction) {
		if (iInstruction instanceof ILoadInstruction) {
//			ILoadInstruction instruction = (ILoadInstruction) iInstruction;
			// A load instruction does never pop anything
			return 0;
		}
		if (iInstruction instanceof IStoreInstruction) {
			IStoreInstruction instruction = (IStoreInstruction) iInstruction;
			return getWordSizeByType(instruction.getType());
		}
		if (iInstruction instanceof IInvokeInstruction) {
			IInvokeInstruction instruction = (IInvokeInstruction) iInstruction;
			String methodSignature = instruction.getMethodSignature();
			TypeName[] parameterNames = StringStuff.parseForParameterNames(methodSignature);
			byte poppedSize = (byte) (instruction.getInvocationCode().hasImplicitThis() ? 1 : 0);
			if (parameterNames != null) {
				for (TypeName parameterName : parameterNames) {
					poppedSize += getWordSizeByType(parameterName.getClassName().toString());
				}
			}
			return poppedSize;
		}
		if (iInstruction instanceof IConditionalBranchInstruction) {
			IConditionalBranchInstruction instruction = (IConditionalBranchInstruction) iInstruction;
//			return (byte) (2 * getWordSizeByType(instruction.getType()));
			return (byte) instruction.getPoppedCount();
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
			return (byte) instruction.getPoppedCount();
		}
		if (iInstruction instanceof PopInstruction) {
			PopInstruction instruction = (PopInstruction) iInstruction;
			return (byte) instruction.getPoppedCount();
		}
		if (iInstruction instanceof DupInstruction) {
			DupInstruction instruction = (DupInstruction) iInstruction;
			return (byte) instruction.getPoppedCount();
		}
		if (iInstruction instanceof IGetInstruction) {
			IGetInstruction instruction = (IGetInstruction) iInstruction;
			return (byte) instruction.getPoppedCount();
		}
		if (iInstruction instanceof ReturnInstruction) {
			ReturnInstruction instruction = (ReturnInstruction) iInstruction;
			return (byte) instruction.getPoppedCount();
		}
		if (iInstruction instanceof IConversionInstruction) {
			IConversionInstruction instruction = (IConversionInstruction) iInstruction;
			return getWordSizeByType(instruction.getFromType());
		}
		if (iInstruction instanceof NewInstruction) {
			NewInstruction instruction = (NewInstruction) iInstruction;
			return (byte) instruction.getPoppedCount();
		}
		if (iInstruction instanceof IArrayStoreInstruction) {
			IArrayStoreInstruction instruction = (IArrayStoreInstruction) iInstruction;
			// TODO Usually consumes 2 elements but what about a primitive array?
//			return getWordSizeByType(instruction.getType());
			return (byte) instruction.getPoppedCount();
		}
		throw new UnsupportedOperationException("Unhandled instruction type " + iInstruction.getClass().getName());
	}

	private static byte getPushedSize(IInstruction iInstruction) {
		if (iInstruction instanceof DupInstruction) {
			DupInstruction instruction = (DupInstruction) iInstruction;
			return (byte) (2 * instruction.getSize());
		}
		return iInstruction.getPushedWordSize();
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
