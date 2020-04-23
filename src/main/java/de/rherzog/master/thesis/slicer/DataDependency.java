package de.rherzog.master.thesis.slicer;

import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.Stack;

import org.jgrapht.Graph;
import org.jgrapht.graph.DefaultDirectedGraph;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.io.ComponentNameProvider;
import org.jgrapht.io.DOTExporter;
import org.jgrapht.io.ExportException;
import org.jgrapht.io.GraphExporter;

import com.ibm.wala.shrikeBT.ArrayStoreInstruction;
import com.ibm.wala.shrikeBT.DupInstruction;
import com.ibm.wala.shrikeBT.IInstruction;
import com.ibm.wala.shrikeBT.ILoadInstruction;
import com.ibm.wala.shrikeBT.IStoreInstruction;
import com.ibm.wala.shrikeCT.InvalidClassFileException;
import com.ibm.wala.types.TypeName;
import com.ibm.wala.util.strings.StringStuff;

import de.rherzog.master.thesis.utils.Utilities;

public class DataDependency {
	private ControlFlow controlFlow;
	private Graph<Integer, DefaultEdge> dataDependencyGraph;

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

	public Set<Integer> getDataDependencyInstructions(int index) throws IOException, InvalidClassFileException {
		// TODO In both directions?
		Graph<Integer, DefaultEdge> dataDependencyGraph = getGraph();
		Set<DefaultEdge> edges = dataDependencyGraph.edgesOf(index);

		Set<Integer> dataDependentInstructionSet = new HashSet<>();
		for (DefaultEdge dataDependencyEdge : edges) {
//			Integer edgeSource = dataDependencyGraph.getEdgeSource(dataDependencyEdge);
//			dataDependentInstructionSet.add(edgeSource);

			Integer edgeTarget = dataDependencyGraph.getEdgeTarget(dataDependencyEdge);
			dataDependentInstructionSet.add(edgeTarget);
		}
		return dataDependentInstructionSet;
	}

	private Graph<Integer, DefaultEdge> getDependencyGraph(Graph<Integer, DefaultEdge> cfg)
			throws IOException, InvalidClassFileException {
		// Create graph
		Graph<Integer, DefaultEdge> dependencyGraph = new DefaultDirectedGraph<>(DefaultEdge.class);

		// Add vertexes for data dependencies to "this" and method parameters
		boolean hasThis = !controlFlow.getMethodData().getIsStatic();
		if (hasThis) {
			dependencyGraph.addVertex(-1);
		}
		TypeName[] methodParameters = StringStuff.parseForParameterNames(controlFlow.getMethodData().getSignature());
		int methodParametersLength = 0;
		if (methodParameters != null) {
			methodParametersLength = methodParameters.length;
			for (int parameterIndex = 1; parameterIndex <= methodParameters.length; parameterIndex++) {
				dependencyGraph.addVertex(-(parameterIndex + (hasThis ? 1 : 0)));
			}
		}

		// Add a vertex for each instruction index
		cfg.vertexSet().forEach(v -> dependencyGraph.addVertex(v));

		Map<Integer, Set<Integer>> varIndexesToRenumber = controlFlow.getVarIndexesToRenumber();

		// Add edges to the graph if there is a data dependency. Start with iterating
		// for each instruction index. For each instruction, we analyze all preceding
		// instructions if there is a data dependency.
		for (int instructionIndex : cfg.vertexSet()) {
			buildGraphForVertex(cfg, hasThis, methodParametersLength, instructionIndex, instructionIndex,
					new HashSet<>(), dependencyGraph, varIndexesToRenumber);
		}
		return dependencyGraph;
	}

	// TODO Obsolete parameter varIndexesToRenumber
	private void buildGraphForVertex(Graph<Integer, DefaultEdge> cfg, boolean hasThis, int methodParameters,
			int focusedIndex, int instructionIndex, Set<Integer> visitedVertices,
			Graph<Integer, DefaultEdge> dependencyGraph, Map<Integer, Set<Integer>> varIndexesToRenumber)
			throws IOException, InvalidClassFileException {
		// We skip already visited instructions
		if (!visitedVertices.add(instructionIndex)) {
			return;
		}
		IInstruction[] instructions = controlFlow.getMethodData().getInstructions();
		IInstruction instructionA = instructions[focusedIndex];

		// Check data dependency for ArrayStoreInstruction using stack simulation
		if (instructionA instanceof ArrayStoreInstruction) {
			Stack<Integer> poppedStack = controlFlow.getStackTrace().getPoppedStackAtInstructionIndex(focusedIndex);
			poppedStack.pop(); // elementInstructionIndex
			poppedStack.pop(); // indexInstructionIndex
			Integer arrayRefInstructionIndex = poppedStack.pop();

			dependencyGraph.addEdge(focusedIndex, arrayRefInstructionIndex);
		}

		// Check data dependency for DupInstruction using stack simulation
		if (instructionA instanceof DupInstruction) {
			Stack<Integer> poppedStack = controlFlow.getStackTrace().getPoppedStackAtInstructionIndex(focusedIndex);
			Integer elementInstructionIndex = poppedStack.pop();

			dependencyGraph.addEdge(focusedIndex, elementInstructionIndex);
		}

		// Only data dependencies between different instructions are interesting
		if (focusedIndex != instructionIndex) {
			// TODO Disabled for now
//			instructionA = Utilities.rewriteVarIndex(varIndexesToRenumber, instructionA, focusedIndex);

			boolean hasDataDependency = false;
			if (instructionIndex < 0) {
				// Check dependency against method parameter
				if (checkDataDependency(instructionA, hasThis, methodParameters, instructionIndex)) {
					hasDataDependency = true;
				}
			} else {
				// Check dependencies for both normal instructions
				IInstruction instructionB = instructions[instructionIndex];
				// TODO Disabled for now
//				instructionB = Utilities.rewriteVarIndex(varIndexesToRenumber, instructionB, instructionIndex);
				if (checkDataDependency(instructionA, instructionB)) {
					hasDataDependency = true;
				}
			}

			if (hasDataDependency && !dependencyGraph.containsEdge(focusedIndex, instructionIndex)) {
				dependencyGraph.addEdge(focusedIndex, instructionIndex);
			}
		}

		// Check instruction dependency against "this"
		buildGraphForVertex(cfg, hasThis, methodParameters, focusedIndex, -1, visitedVertices, dependencyGraph,
				varIndexesToRenumber);
		// Check instruction dependency against method parameters
		for (int methodParameterIndex = 1; methodParameterIndex <= methodParameters; methodParameterIndex++) {
			int index = -(methodParameterIndex + (hasThis ? 1 : 0));
			buildGraphForVertex(cfg, hasThis, methodParameters, focusedIndex, index, visitedVertices, dependencyGraph,
					varIndexesToRenumber);
		}

		if (instructionIndex < 0) {
			// There cannot be any control flow for method parameters
			return;
		}

		// Recursively analyze preceding instructions
		for (DefaultEdge edge : cfg.incomingEdgesOf(instructionIndex)) {
			int sourceInstructionIndex = cfg.getEdgeSource(edge);
			buildGraphForVertex(cfg, hasThis, methodParameters, focusedIndex, sourceInstructionIndex, visitedVertices,
					dependencyGraph, varIndexesToRenumber);
		}
	}

	public boolean hasDependencyToMethodParameter(int instructionIndex) throws IOException, InvalidClassFileException {
		Graph<Integer, DefaultEdge> graph = getGraph();

		boolean hasThis = !controlFlow.getMethodData().getIsStatic();
		Set<Integer> parameterIndexes = new HashSet<>();
		TypeName[] methodParameters = StringStuff.parseForParameterNames(controlFlow.getMethodData().getSignature());
		if (methodParameters != null) {
			for (int parameterIndex = 1; parameterIndex <= methodParameters.length; parameterIndex++) {
				parameterIndexes.add(-(parameterIndex + (hasThis ? 1 : 0)));
			}
		}

		boolean hasDependencyToParameters = false;
		for (int parameterIndex : parameterIndexes) {
			hasDependencyToParameters |= graph.containsEdge(instructionIndex, parameterIndex);
		}
		return hasDependencyToParameters;
	}

	/*
	 * Checks a data dependency either to "this" if in a non-static context or a
	 * method argument
	 */
	private static boolean checkDataDependency(IInstruction instructionA, boolean hasThis, int methodParameters,
			int instructionIndex) {
		if (instructionA instanceof ILoadInstruction) {
			ILoadInstruction loadInstruction = (ILoadInstruction) instructionA;

			int varIndex = loadInstruction.getVarIndex();
			// Check if the varIndex is 0 and refers to "this"
			if (hasThis && instructionIndex == -1) {
				return varIndex == 0;
			} else {
				// Check if the loaded varIndex refers to a method argument
				int methodVars = (hasThis ? 1 : 0) + methodParameters;
				int paramVarIndex = -((hasThis ? 1 : 0) + instructionIndex);
				if (varIndex < methodVars) {
					// varIndex references to a method parameter, check the correctness
					if (varIndex == paramVarIndex) {
						return true;
					}
				}
			}
		}
		return false;
	}

	private static boolean checkDataDependency(IInstruction instructionA, IInstruction instructionB) {
		// A data dependency exists, if a there is a load instruction which are a
		// preceding store instruction in all possible execution paths.
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
		boolean hasThis = !controlFlow.getMethodData().getIsStatic();

		// use helper classes to define how vertices should be rendered,
		// adhering to the DOT language restrictions
		ComponentNameProvider<Integer> vertexIdProvider = new ComponentNameProvider<Integer>() {
			public String getName(Integer index) {
				return String.valueOf(index);
			}
		};
		ComponentNameProvider<Integer> vertexLabelProvider = new ComponentNameProvider<Integer>() {
			public String getName(Integer index) {
				if (hasThis && index == -1) {
					return "this";
				}
				if ((hasThis && index < -1) || (!hasThis && index < 0)) {
					return "arg " + -(index + (hasThis ? 1 : 0));
				}
				IInstruction instruction = instructions[index];
				return index + ": " + instruction.toString();
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
