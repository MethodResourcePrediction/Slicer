package de.rherzog.master.thesis.slicer;

import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.stream.IntStream;

import org.jgrapht.Graph;
import org.jgrapht.graph.DefaultDirectedGraph;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.io.ComponentNameProvider;
import org.jgrapht.io.DOTExporter;

import com.ibm.wala.shrikeBT.IInstruction;
import com.ibm.wala.shrikeBT.ILoadInstruction;
import com.ibm.wala.shrikeBT.IStoreInstruction;
import com.ibm.wala.shrikeBT.MethodData;
import com.ibm.wala.shrikeCT.InvalidClassFileException;

import de.rherzog.master.thesis.utils.Utilities;

public class ArgumentDependency implements SlicerGraph<Integer> {
	private ControlFlow controlFlow;
	private Graph<Integer, DefaultEdge> graph;

	public ArgumentDependency(ControlFlow controlFlow) {
		this.controlFlow = controlFlow;
	}

	@Override
	public Graph<Integer, DefaultEdge> getGraph() throws IOException, InvalidClassFileException {
		if (graph != null) {
			return graph;
		}
		graph = new DefaultDirectedGraph<>(DefaultEdge.class);

		IInstruction[] instructions = controlFlow.getMethodData().getInstructions();
		StackTrace stackTrace = new StackTrace(instructions);

		// Add vertices (all instructions)
		IntStream.range(0, instructions.length).forEach(i -> graph.addVertex(i));

		stackTrace.forEachPopped((index, stack) -> {
			for (Integer poppedInstructionIndex : stack) {
				graph.addEdge(index, poppedInstructionIndex);
			}
		});
		return graph;
	}

	public Set<Integer> getAllArgumentInstructionIndexes(int index) throws IOException, InvalidClassFileException {
		Graph<Integer, DefaultEdge> argumentDependencyGraph = getGraph();
		Set<DefaultEdge> outgoingEdges = argumentDependencyGraph.outgoingEdgesOf(index);

		Set<Integer> argumentInstructionSet = new HashSet<>();
		for (DefaultEdge argumentEdge : outgoingEdges) {
			Integer edgeTarget = argumentDependencyGraph.getEdgeTarget(argumentEdge);
			addAllArgumentInstructionIndexes(edgeTarget, argumentDependencyGraph, argumentInstructionSet);
		}
		return argumentInstructionSet;
	}

	private static void addAllArgumentInstructionIndexes(int index, Graph<Integer, DefaultEdge> argumentDependencyGraph,
			Set<Integer> argumentInstructionSet) {
		if (!argumentInstructionSet.add(index)) {
			return;
		}
		Set<DefaultEdge> outgoingEdges = argumentDependencyGraph.outgoingEdgesOf(index);
		for (DefaultEdge argumentEdge : outgoingEdges) {
			Integer edgeTarget = argumentDependencyGraph.getEdgeTarget(argumentEdge);
			addAllArgumentInstructionIndexes(edgeTarget, argumentDependencyGraph, argumentInstructionSet);
		}
	}

	public Set<Integer> getArgumentInstructionIndexes(int index) throws IOException, InvalidClassFileException {
		Graph<Integer, DefaultEdge> argumentDependencyGraph = getGraph();
		Set<DefaultEdge> outgoingEdges = argumentDependencyGraph.outgoingEdgesOf(index);

		Set<Integer> argumentInstructionSet = new HashSet<>();
		for (DefaultEdge argumentEdge : outgoingEdges) {
			Integer edgeTarget = argumentDependencyGraph.getEdgeTarget(argumentEdge);
			argumentInstructionSet.add(edgeTarget);
		}
		return argumentInstructionSet;
	}

	public Map<Integer, Set<Integer>> getVarIndexesToRenumber() throws IOException, InvalidClassFileException {
		Map<Integer, Set<Integer>> varIndexesToRenumber = new HashMap<>();
		Map<Integer, Set<Integer>> varIndexInstructionMap = new HashMap<>();

		// Build a map of LoadInstruction indexes and their directly dependent
		// (by varIndex) StoreInstructions. Directly means that only the closest
		// StoreInstruction will be added in the value list.
		// After creation, a single load instruction is defined by a set of store
		// instruction indexes.
		MethodData methodData = controlFlow.getMethodData();
		int maxVarIndex = Utilities.getMaxLocalVarIndex(methodData);

		IInstruction[] instructions = methodData.getInstructions();
		for (int index = 0; index < instructions.length - 1; index++) {
			IInstruction instruction = instructions[index];
			if (instruction instanceof ILoadInstruction) {
				ILoadInstruction loadInstruction = (ILoadInstruction) instruction;
				int varIndex = loadInstruction.getVarIndex();

				if (varIndexInstructionMap.containsKey(varIndex)) {
					continue;
				}

//				System.out.println("Search Store for Load with varIndex " + varIndex + " at " + index);
				Set<Integer> directlyReachableVarInstructions = new HashSet<>();
				getNextVarInstructions(new HashSet<>(), directlyReachableVarInstructions, instructions, varIndex,
						index);

				varIndexInstructionMap.put(varIndex, directlyReachableVarInstructions);
			}
		}

		// Build a map with store instruction indexes as key and load instruction
		// indexes as values for the same varIndex.
		Map<Set<Integer>, Set<Integer>> storeLoadMap = new HashMap<>();
		for (Entry<Integer, Set<Integer>> entry : varIndexInstructionMap.entrySet()) {
			int loadInstruction = entry.getKey();
			Set<Integer> storeInstructions = entry.getValue();

			if (!storeLoadMap.containsKey(storeInstructions)) {
				Set<Integer> loadInstructions = new HashSet<>();
				loadInstructions.add(loadInstruction);
				storeLoadMap.put(storeInstructions, loadInstructions);
			} else {
				Set<Integer> loadInstructions = storeLoadMap.get(storeInstructions);
				loadInstructions.add(loadInstruction);
			}
		}

		// Iterate over the load-/store-Instruction map and lookup the default varIndex.
		// If it is used multiple times, allocate a new variable index.
//		Set<Integer> usedVarIndexes = new HashSet<>();
//		Map<Integer, Set<Integer>> newVariableForInstructions = new HashMap<>();
//		for (Entry<Set<Integer>, Set<Integer>> entry : storeLoadMap.entrySet()) {
//			Set<Integer> storeInstructions = entry.getKey();
//			Set<Integer> loadInstructions = entry.getValue();
//
//			Integer varIndex = null;
//			if (!storeInstructions.isEmpty()) {
//				int anyInstructionIndex = storeInstructions.iterator().next();
//				IStoreInstruction storeInstruction = (IStoreInstruction) instructions[anyInstructionIndex];
//				varIndex = storeInstruction.getVarIndex();
//			} else {
//				int anyInstructionIndex = loadInstructions.iterator().next();
//				ILoadInstruction loadInstruction = (ILoadInstruction) instructions[anyInstructionIndex];
//				varIndex = loadInstruction.getVarIndex();
//			}
//
//			if (!usedVarIndexes.add(varIndex)) {
//				// Get maximum variable index and increment it
//				int newVarIndex = maxVarIndex += 1;
//
//				Set<Integer> varInstructions = new HashSet<>();
//				varInstructions.addAll(storeInstructions);
//				varInstructions.addAll(loadInstructions);
//
//				newVariableForInstructions.put(newVarIndex, varInstructions);
//			}
//		}
		return varIndexesToRenumber;
	}

	private void getNextVarInstructions(Set<Integer> visited, Set<Integer> directlyReachableVarInstructions,
			IInstruction[] instructions, int varIndex, int index) throws IOException, InvalidClassFileException {
		if (!visited.add(index)) {
			return;
		}
		IInstruction instruction = instructions[index];
		if (instruction instanceof IStoreInstruction) {
			IStoreInstruction storeInstruction = (IStoreInstruction) instruction;
			if (storeInstruction.getVarIndex() == varIndex) {
				// TODO Check if the StoreInstruction is "standing-alone". If it is NOT in any
				// loop together with other Store-/Load-Instructions with the same variable
				// index, it can be considered to be independent from previous varIndex
				// operations.
				// TODO How to handle previously executed StoreInstructions on the CFG not in
				// any cycle?
				Iterator<Integer> iterator = directlyReachableVarInstructions.iterator();
				boolean dependent = false;
				while (iterator.hasNext()) {
					Integer directlyReachableVarInstructionIndex = iterator.next();
					if (controlFlow.inSameCycle(index, directlyReachableVarInstructionIndex)) {
						dependent = true;
					}
				}
				if (dependent) {
					directlyReachableVarInstructions.add(index);
				}
				return;
			}
		}
		if (instruction instanceof ILoadInstruction) {
			ILoadInstruction loadInstruction = (ILoadInstruction) instruction;
			if (loadInstruction.getVarIndex() == varIndex) {
				directlyReachableVarInstructions.add(index);
			}
		}

		Graph<Integer, DefaultEdge> cfg = getGraph();
		for (DefaultEdge edge : cfg.incomingEdgesOf(index)) {
			int prevIndex = cfg.getEdgeSource(edge);
			getNextVarInstructions(visited, directlyReachableVarInstructions, instructions, varIndex, prevIndex);
		}
		for (DefaultEdge edge : cfg.outgoingEdgesOf(index)) {
			int nextIndex = cfg.getEdgeTarget(edge);
			getNextVarInstructions(visited, directlyReachableVarInstructions, instructions, varIndex, nextIndex);
		}
	}

	@Override
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
		DOTExporter<Integer, DefaultEdge> exporter = new DOTExporter<>(vertexIdProvider, vertexLabelProvider, null);
		exporter.putGraphAttribute("label", this.getClass().getSimpleName());
		exporter.putGraphAttribute("labelloc", "t");
		exporter.putGraphAttribute("fontsize", "30");

		Writer writer = new StringWriter();
		exporter.exportGraph(getGraph(), writer);
		return writer.toString();
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
