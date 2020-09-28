package de.rherzog.master.thesis.slicer.dominance;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.jgrapht.Graph;
import org.jgrapht.graph.DefaultDirectedGraph;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.io.ComponentNameProvider;
import org.jgrapht.io.ExportException;

import com.ibm.wala.shrikeBT.IInstruction;
import com.ibm.wala.shrikeCT.InvalidClassFileException;

import de.rherzog.master.thesis.slicer.ControlFlow;
import de.rherzog.master.thesis.slicer.SlicerGraph;

public class ImmediateDominance extends SlicerGraph<Integer> {
	private Graph<Integer, DefaultEdge> graph;
	private StrictDominance strictDominance;

	public ImmediateDominance(StrictDominance strictDominance) throws IOException, InvalidClassFileException {
		this.strictDominance = strictDominance;
	}

	@Override
	public Graph<Integer, DefaultEdge> getGraph() throws IOException, InvalidClassFileException {
		if (graph != null) {
			return graph;
		}
		graph = new DefaultDirectedGraph<>(DefaultEdge.class);

		// Every vertex in the control flow is present in the forward dominance graph
		// as well.
		Graph<Integer, DefaultEdge> strictDominanceGraph = strictDominance.getGraph();
		strictDominanceGraph.vertexSet().forEach(v -> graph.addVertex(v));

		// The immediate dominator or idom of a node n is the unique node that strictly
		// dominates n but does not strictly dominate any other node that strictly
		// dominates n
		for (int n : strictDominanceGraph.vertexSet()) {
			// Find all nodes that strictly dominate n
			final Set<DefaultEdge> strictlyDominatingEdgesOfN = strictDominanceGraph.incomingEdgesOf(n);
			final Set<Integer> strictlyDominatingNodesOfN = new HashSet<>();
			for (DefaultEdge strictlyDominatingEdgeOfN : strictlyDominatingEdgesOfN) {
				strictlyDominatingNodesOfN.add(strictDominanceGraph.getEdgeSource(strictlyDominatingEdgeOfN));
			}

			// ... but does not strictly dominate any other node that strictly dominates n
			Integer uniqueDominatingNode = null;
			for (int strictlyDominatingNodeOfN : strictlyDominatingNodesOfN) {
				boolean dominatesOtherNodeThatDominatesN = false;
				for (int strictlyDominatingNodeOfN2 : strictlyDominatingNodesOfN) {
					if (strictDominanceGraph.containsEdge(strictlyDominatingNodeOfN, strictlyDominatingNodeOfN2)) {
						dominatesOtherNodeThatDominatesN = true;
						break; // optimization
					}
				}

				if (!dominatesOtherNodeThatDominatesN) {
					if (uniqueDominatingNode == null) {
						uniqueDominatingNode = strictlyDominatingNodeOfN;
					} else {
						// Not possible
						throw new RuntimeException();
					}
				}
			}

			if (uniqueDominatingNode == null && !strictDominanceGraph.incomingEdgesOf(n).isEmpty()) {
				// Not possible
				throw new RuntimeException("Starting node " + n + " has a dominating node which is not possible");
			}
//			System.out.println(uniqueDominatingNode + " immediately dominates " + n);
			if (uniqueDominatingNode != null) {
				graph.addEdge(uniqueDominatingNode, n);
			}
		}
		return graph;
	}

	@Override
	protected String dotPrint() throws IOException, InvalidClassFileException, ExportException {
		// use helper classes to define how vertices should be rendered,
		// adhering to the DOT language restrictions
		ComponentNameProvider<Integer> vertexIdProvider = new ComponentNameProvider<Integer>() {
			public String getName(Integer index) {
				return String.valueOf(index);
			}
		};
		ComponentNameProvider<Integer> vertexLabelProvider = new ComponentNameProvider<Integer>() {
			public String getName(Integer index) {
				ControlFlow controlFlow = strictDominance.getDominance().getControlFlow();
				if (controlFlow != null) {
					try {
						IInstruction[] instructions = controlFlow.getMethodData().getInstructions();
						return index + ": " + instructions[index].toString();
					} catch (IOException | InvalidClassFileException e) {
					}
				}
				return String.valueOf(index);
			}
		};
		return getExporterGraphString(vertexIdProvider, vertexLabelProvider);
	}

	public Map<Integer, Integer> getDominators() throws IOException, InvalidClassFileException {
		Map<Integer, Integer> dominatorMap = new HashMap<>();
		for (DefaultEdge edge : getGraph().edgeSet()) {
			final Integer edgeSource = getGraph().getEdgeSource(edge);
			final Integer edgeTarget = getGraph().getEdgeTarget(edge);
			dominatorMap.put(edgeTarget, edgeSource);
		}
		return dominatorMap;
	}

}
