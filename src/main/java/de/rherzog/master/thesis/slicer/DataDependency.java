package de.rherzog.master.thesis.slicer;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

import org.jgrapht.Graph;
import org.jgrapht.graph.DefaultDirectedGraph;
import org.jgrapht.graph.DefaultEdge;

import com.ibm.wala.shrikeBT.IInstruction;
import com.ibm.wala.shrikeCT.InvalidClassFileException;

public class DataDependency {
	private ControlFlow controlFlow;
	private Graph<IInstruction, DefaultEdge> dataDependencyGraph;

	public DataDependency(ControlFlow controlFlowGraph) throws IOException, InvalidClassFileException {
		this.controlFlow = controlFlowGraph;
	}

	public Graph<IInstruction, DefaultEdge> getGraph() throws IOException, InvalidClassFileException {
		if (dataDependencyGraph != null) {
			return dataDependencyGraph;
		}

		dataDependencyGraph = new DefaultDirectedGraph<>(DefaultEdge.class);

//		EdgeReversedGraph<Integer, DefaultEdge> reversedEdgeCFG = new EdgeReversedGraph<>(controlFlow.getGraph());

		buildGraph(new HashSet<>(), controlFlow.getGraph(), 0, false, -1);
		return dataDependencyGraph;
	}

	private void buildGraph(Set<Integer> seenVertices, Graph<Integer, DefaultEdge> cfg, int instructionIndex,
			boolean check, int rootInstructionIndex) throws IOException, InvalidClassFileException {
		if (seenVertices.contains(instructionIndex) || instructionIndex == rootInstructionIndex) {
			return;
		}
		seenVertices.add(instructionIndex);

		for (DefaultEdge edge : cfg.incomingEdgesOf(instructionIndex)) {
			int sourceInstructionIndex = cfg.getEdgeSource(edge);
			System.out.println("Check dependency: " + edge.toString());
			buildGraph(new HashSet<>(), cfg, sourceInstructionIndex, true, instructionIndex);
		}

		if (!check) {
			for (DefaultEdge edge : cfg.outgoingEdgesOf(instructionIndex)) {
				int targetInstructionIndex = cfg.getEdgeTarget(edge);
				System.out.println("Focus on: " + targetInstructionIndex);
				buildGraph(seenVertices, cfg, targetInstructionIndex, false, 0);
			}
		}

//		Set<DefaultEdge> allEdges = new HashSet<>();
//		for (int otherInstructionIndex : reversedEdgeCFG.vertexSet()) {
//			if (otherInstructionIndex == instructionIndex) {
//				continue;
//			}
//			allEdges.addAll(reversedEdgeCFG.getAllEdges(instructionIndex, otherInstructionIndex));
//		}
//
//		for (DefaultEdge edge : allEdges) {
//			int targetInstructionIndex = reversedEdgeCFG.getEdgeTarget(edge);
//			buildGraph(seenVertices, reversedEdgeCFG, targetInstructionIndex);
//			
//			System.out.println(targetInstructionIndex);
//			for (int previousInstructionIndex : reversedEdgeCFG.vertexSet()) {
//			
//
//			// TODO Check for data dependency
////			System.out.println("Check: " + instructionIndex + " <=> " + targetInstructionIndex);
//
////			IInstruction[] instructions = controlFlow.getMethodData().getInstructions();
////			checkDataDependency(instructions[instructionIndex], instructions[targetInstructionIndex]);
//		}
	}

	private static boolean checkDataDependency(IInstruction instructionA, IInstruction instructionB) {
		return true;
	}

//	private void buildGraph(IInstruction[] instructions, IInstruction instruction, int instructionIndex,
//			Set<Integer> indexesToKeep) throws IOException, InvalidClassFileException {

//	IInstruction[] instructions = methodData.getInstructions();
//
//	Set<Integer> indexesToKeep = new HashSet<>();
//	for (int instructionIndex : instructionIndexes) {
//		IInstruction instruction = instructions[instructionIndex];
//		buildGraph(instructions, instruction, instructionIndex, indexesToKeep);
//	}
//
//	List<IInstruction> keptInstructions = new ArrayList<>();
//	List<Integer> sorted = indexesToKeep.stream().mapToInt(a -> a).sorted().boxed().collect(Collectors.toList());
//	for (int indexToKeep : sorted) {
//		keptInstructions.add(instructions[indexToKeep]);
//		System.out.println(indexToKeep + ": " + instructions[indexToKeep].toString());
//	}
//		boolean added = indexesToKeep.add(instructionIndex);
//		if (!added) {
//			return;
//		}
//
////		Set<DefaultEdge> edges = graph.incomingEdgesOf(instructionIndex);
////		if (edges.isEmpty()) {
////			return;
////		}
////
////		for (DefaultEdge edge : edges) {
////			int source = graph.getEdgeSource(edge);
////			instruction = instructions[source];
////			sliceBackwards(instructions, instruction, source, indexesToKeep);
////		}
//
//		IndexedVisitor visitor = new IndexedVisitor(instructions) {
//			// A LoadInstruction loads a previously stored variable. The variable must have
//			// been stored by a StoreInstruction with the same variable index
//			@Override
//			public void visitLocalLoad(Set<Integer> indexes, ILoadInstruction instruction) {
//				int varIndex = instruction.getVarIndex();
//
//				IndexedVisitor v = new IndexedVisitor(instructions) {
//					@Override
//					public void visitLocalStore(Set<Integer> indexes, IStoreInstruction instruction2) {
//						if (instruction2.getVarIndex() == varIndex) {
//							for (int index : Iterator2Iterable.make(indexes.iterator())) {
//								if (index > instructionIndex) {
//									continue;
//								}
//								sliceBackwards(instructions, instruction2, index, indexesToKeep);
//							}
//						}
//					}
//				};
//				IntStream.range(0, instructionIndex).mapToObj(i -> instructions[i]).forEach(i -> i.visit(v));
//			}
//
//			// A StoreInstruction stores the element previously pushed on the stack. Usually
//			// this element was pushed by the previous instruction
//			@Override
//			public void visitLocalStore(Set<Integer> indexes, IStoreInstruction instruction2) {
//				for (int index : Iterator2Iterable.make(indexes.iterator())) {
//					if (index > instructionIndex) {
//						continue;
//					}
//					int prevInstructionIndex = index - 1;
//					IInstruction prevInstruction = instructions[prevInstructionIndex];
//					sliceBackwards(instructions, prevInstruction, prevInstructionIndex, indexesToKeep);
//				}
//			}
//
//			// A InvokeInstruction depends on the parameters loaded as arguments
//			// TODO Does simply access the direct previous instructions solve the
//			// dependency? Maybe there is the need to resolve is with the WALA analysis.
//			@Override
//			public void visitInvoke(Set<Integer> indexes, IInvokeInstruction instruction) {
//				// TODO Should be unique
//				int index = indexes.iterator().next();
//				for (int argIndex = 0; argIndex < instruction.getPoppedCount(); argIndex++) {
//					int prevInstructionIndex = index - argIndex - 1;
//					IInstruction prevInstruction = instructions[prevInstructionIndex];
//					sliceBackwards(instructions, prevInstruction, prevInstructionIndex, indexesToKeep);
//				}
//			}
//
//			// An ArrayLoadInstruction depends on the index which specifies the array to be
//			// loaded. It is usually pushed directly before this instruction.
//			// TODO Does simply access the direct previous instruction solve the
//			// dependency? Maybe there is the need to resolve is with the WALA analysis.
//			@Override
//			public void visitArrayLoad(Set<Integer> indexes, IArrayLoadInstruction instruction) {
//				int index = choosePreferred(indexesToKeep, instructionIndex);
//				int arrayLoadIndex = index - 1;
//				int arrayIndex = index - 2;
//
//				IInstruction arrayLoadInstruction = instructions[arrayLoadIndex];
//				IInstruction arrayIndexInstruction = instructions[arrayIndex];
//
//				sliceBackwards(instructions, arrayLoadInstruction, arrayLoadIndex, indexesToKeep);
//				sliceBackwards(instructions, arrayIndexInstruction, arrayIndex, indexesToKeep);
//			}
//
//			@Override
//			public void visitBinaryOp(Set<Integer> indexes, IBinaryOpInstruction instruction) {
//				int index = choosePreferred(indexes, instructionIndex);
//				int prevInstructionIndexA = index - 1;
//				int prevInstructionIndexB = index - 2;
//
//				IInstruction prevInstructionA = instructions[prevInstructionIndexA];
//				IInstruction prevInstructionB = instructions[prevInstructionIndexB];
//
//				sliceBackwards(instructions, prevInstructionA, prevInstructionIndexA, indexesToKeep);
//				sliceBackwards(instructions, prevInstructionB, prevInstructionIndexB, indexesToKeep);
//			}
//		};
//		instruction.visit(visitor);
//	}
}
