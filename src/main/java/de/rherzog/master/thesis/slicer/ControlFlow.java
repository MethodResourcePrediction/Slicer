package de.rherzog.master.thesis.slicer;

import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

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
import com.ibm.wala.shrikeBT.ILoadInstruction;
import com.ibm.wala.shrikeBT.IStoreInstruction;
import com.ibm.wala.shrikeBT.MethodData;
import com.ibm.wala.shrikeBT.shrikeCT.ClassInstrumenter;
import com.ibm.wala.shrikeBT.shrikeCT.OfflineInstrumenter;
import com.ibm.wala.shrikeCT.ClassReader;
import com.ibm.wala.shrikeCT.InvalidClassFileException;
import com.ibm.wala.util.collections.Pair;

import de.rherzog.master.thesis.utils.InstrumenterComparator;
import de.rherzog.master.thesis.utils.Utilities;

public class ControlFlow {
	private String inputPath;
	private String methodSignature;

	private MethodData methodData;

	private Graph<Integer, DefaultEdge> graph;
	private List<List<Integer>> simpleCycles;
	private Map<Integer, Set<Integer>> varIndexToRenumber;
	private StackTrace stackTrace;
	private List<Pair<Integer, Integer>> loopPairs;

	public ControlFlow(String inputPath, String methodSignature) {
		this.inputPath = inputPath;
		this.methodSignature = methodSignature;
	}

	public ControlFlow(MethodData methodData) {
		this.methodData = methodData;
	}

	public StackTrace getStackTrace() throws IOException, InvalidClassFileException {
		if (stackTrace != null) {
			return stackTrace;
		}

		MethodData methodData = getMethodData();
		IInstruction[] instructions = methodData.getInstructions();
		stackTrace = new StackTrace(instructions);
		return stackTrace;
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

	public List<Pair<Integer, Integer>> getLoopPairs() throws IOException, InvalidClassFileException {
		if (loopPairs != null) {
			return loopPairs;
		}

		loopPairs = new ArrayList<>();
		for (List<Integer> simpleCycle : getSimpleCycles()) {
			int min = simpleCycle.stream().mapToInt(Integer::intValue).min().getAsInt();
			int max = simpleCycle.stream().mapToInt(Integer::intValue).max().getAsInt();

			Pair<Integer, Integer> loopPair = Pair.make(min, max);
			if (!loopPairs.stream().filter(loopIndex -> loopIndex.equals(loopPair)).findAny().isPresent()) {
				loopPairs.add(loopPair);
			}
		}
		return loopPairs;
	}

	public boolean isPartOfCycle(int instructionIndex) throws IOException, InvalidClassFileException {
		for (List<Integer> cycle : getSimpleCycles()) {
			if (cycle.contains(instructionIndex)) {
				return true;
			}
		}
		return false;
	}

	public Set<Integer> getInstructionsInCycles() throws IOException, InvalidClassFileException {
		Set<Integer> instructionsInCycleSet = new HashSet<>();
		for (int index = 0; index < getMethodData().getInstructions().length; index++) {
			if (isPartOfCycle(index)) {
				instructionsInCycleSet.add(index);
			}
		}
		return instructionsInCycleSet;
	}

	public Map<Integer, Set<Integer>> getVarIndexesToRenumber() throws IOException, InvalidClassFileException {
		if (varIndexToRenumber != null) {
			return varIndexToRenumber;
		}
		int maxVarIndex = Utilities.getMaxLocalVarIndex(getMethodData());
		varIndexToRenumber = renumberVarIndexes(getMethodData().getInstructions(), getGraph(), maxVarIndex);
		return varIndexToRenumber;
	}

	public void renumberVarIndexes() throws IOException, InvalidClassFileException {
		Map<Integer, Set<Integer>> varIndexesToRenumber = getVarIndexesToRenumber();

		IInstruction[] instructions = getMethodData().getInstructions();
		for (int index = 0; index < instructions.length; index++) {
			IInstruction instruction = instructions[index];
			instructions[index] = Utilities.rewriteVarIndex(varIndexesToRenumber, instruction, index);
		}
	}

	private static Map<Integer, Set<Integer>> renumberVarIndexes(IInstruction[] instructions,
			Graph<Integer, DefaultEdge> cfg, int maxVarIndex) {
		Map<Integer, Set<Integer>> loadInstStoreInstMap = new HashMap<>();

		// Build a map of LoadInstruction indexes and their directly dependent
		// (by varIndex) StoreInstructions. Directly means that only the closest
		// StoreInstruction will be added in the value list.
		// After creation, a single load instruction is defined by a set of store
		// instruction indexes.
		for (int index = 0; index < instructions.length - 1; index += 1) {
			IInstruction instruction = instructions[index];
			if (instruction instanceof ILoadInstruction) {
				ILoadInstruction loadInstruction = (ILoadInstruction) instruction;
				int varIndex = loadInstruction.getVarIndex();

//				System.out.println("Search Store for Load with varIndex " + varIndex + " at " + index);
				Set<Integer> directlyReachableStoreInstructions = new HashSet<>();
				for (DefaultEdge edge : cfg.incomingEdgesOf(index)) {
					int prevIndex = cfg.getEdgeSource(edge);
					getDirectlyPreceedingStoreInstructions(new HashSet<>(), directlyReachableStoreInstructions,
							instructions, cfg, varIndex, prevIndex);
				}
//				System.out.println(directlyReachableStoreInstructions);

				loadInstStoreInstMap.put(index, directlyReachableStoreInstructions);
			}
		}

		// Build a map with store instruction indexes as key and load instruction
		// indexes as values for the same varIndex.
		Map<Set<Integer>, Set<Integer>> storeLoadMap = new HashMap<>();
		for (Entry<Integer, Set<Integer>> entry : loadInstStoreInstMap.entrySet()) {
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
		Set<Integer> usedVarIndexes = new HashSet<>();
		Map<Integer, Set<Integer>> newVariableForInstructions = new HashMap<>();
		for (Entry<Set<Integer>, Set<Integer>> entry : storeLoadMap.entrySet()) {
			Set<Integer> storeInstructions = entry.getKey();
			Set<Integer> loadInstructions = entry.getValue();

			Integer varIndex = null;
			if (!storeInstructions.isEmpty()) {
				int anyInstructionIndex = storeInstructions.iterator().next();
				IStoreInstruction storeInstruction = (IStoreInstruction) instructions[anyInstructionIndex];
				varIndex = storeInstruction.getVarIndex();
			} else {
				int anyInstructionIndex = loadInstructions.iterator().next();
				ILoadInstruction loadInstruction = (ILoadInstruction) instructions[anyInstructionIndex];
				varIndex = loadInstruction.getVarIndex();
			}

			if (!usedVarIndexes.add(varIndex)) {
				// Get maximum variable index and increment it
				int newVarIndex = maxVarIndex += 1;

				Set<Integer> varInstructions = new HashSet<>();
				varInstructions.addAll(storeInstructions);
				varInstructions.addAll(loadInstructions);

				newVariableForInstructions.put(newVarIndex, varInstructions);
			}
		}
		return newVariableForInstructions;
	}

	private static void getDirectlyPreceedingStoreInstructions(Set<Integer> visited,
			Set<Integer> directlyReachableStoreInstructions, IInstruction[] instructions,
			Graph<Integer, DefaultEdge> cfg, int varIndex, int index) {
		if (!visited.add(index)) {
			return;
		}
		IInstruction instruction = instructions[index];
		if (instruction instanceof IStoreInstruction) {
			IStoreInstruction storeInstruction = (IStoreInstruction) instruction;
			if (storeInstruction.getVarIndex() == varIndex) {
				if (directlyReachableStoreInstructions.add(index)) {
					return;
				}
			}
		}
		for (DefaultEdge edge : cfg.incomingEdgesOf(index)) {
			int prevIndex = cfg.getEdgeSource(edge);
			getDirectlyPreceedingStoreInstructions(visited, directlyReachableStoreInstructions, instructions, cfg,
					varIndex, prevIndex);
		}
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

	public boolean inSameCycle(int instructionIndexA, int instructionIndexB)
			throws IOException, InvalidClassFileException {
		return inSameCycle(instructionIndexA, instructionIndexB, null);
	}

	public boolean inSameCycle(int instructionIndexA, int instructionIndexB, int... moreInstructionIndexes)
			throws IOException, InvalidClassFileException {
		if (!getCyclesForInstruction(instructionIndexA).equals(getCyclesForInstruction(instructionIndexB))) {
			return false;
		}
		if (moreInstructionIndexes != null) {
			for (int instructionIndex : moreInstructionIndexes) {
				if (!getCyclesForInstruction(instructionIndexA).equals(getCyclesForInstruction(instructionIndex))) {
					return false;
				}
			}
		}
		return true;
	}
}
