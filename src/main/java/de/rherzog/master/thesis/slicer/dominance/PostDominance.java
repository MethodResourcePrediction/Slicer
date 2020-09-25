package de.rherzog.master.thesis.slicer.dominance;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.jgrapht.Graph;
import org.jgrapht.GraphPath;
import org.jgrapht.alg.shortestpath.AllDirectedPaths;
import org.jgrapht.graph.DefaultDirectedGraph;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.io.ComponentNameProvider;
import org.jgrapht.io.ExportException;

import com.ibm.wala.shrikeBT.IInstruction;
import com.ibm.wala.shrikeCT.InvalidClassFileException;

import de.rherzog.master.thesis.slicer.ControlFlow;
import de.rherzog.master.thesis.slicer.SlicerGraph;

public class PostDominance extends SlicerGraph<Integer> {
	private ControlFlow controlFlow;
	private Graph<Integer, DefaultEdge> controlFlowGraph;
	private Integer startNode;

	private Graph<Integer, DefaultEdge> graph;

	public PostDominance(ControlFlow controlFlow) throws IOException, InvalidClassFileException {
		this.controlFlow = controlFlow;
		this.controlFlowGraph = controlFlow.getGraph();
		this.startNode = controlFlow.getStartNode();
	}

	public PostDominance(Graph<Integer, DefaultEdge> controlFlowGraph, int startNode) {
		this.controlFlowGraph = controlFlowGraph;
		this.startNode = startNode;
	}

	@Override
	public Graph<Integer, DefaultEdge> getGraph() throws IOException, InvalidClassFileException {
		if (graph != null) {
			return graph;
		}
		graph = new DefaultDirectedGraph<>(DefaultEdge.class);
		controlFlowGraph.vertexSet().forEach(v -> {
			graph.addVertex(v);
		});

		// Version 2
		Set<Integer> exitVertices = new HashSet<>();
		controlFlowGraph.vertexSet().forEach(v -> {
			if (controlFlowGraph.outDegreeOf(v) == 0) {
				exitVertices.add(v);
			}
		});

		AllDirectedPaths<Integer, DefaultEdge> cfgPaths = new AllDirectedPaths<>(controlFlowGraph);

		controlFlowGraph.vertexSet().forEach(v -> {
			final List<GraphPath<Integer, DefaultEdge>> allPaths = cfgPaths.getAllPaths(Set.of(v), exitVertices, false,
					controlFlowGraph.edgeSet().size());

			Set<Integer> dominatedVertexes = new HashSet<>(controlFlowGraph.vertexSet());
			for (GraphPath<Integer, DefaultEdge> path : allPaths) {
				dominatedVertexes.retainAll(path.getVertexList());
			}
//			System.out.println(v + " dominates " + dominatedVertexes.toString());
			dominatedVertexes.forEach(dv -> graph.addEdge(v, dv));
		});

		return graph;
	}

	public Set<Integer> postDominatedBy(int vertex) throws IOException, InvalidClassFileException {
		final Set<DefaultEdge> outgoingEdgesOfVertex = getGraph().outgoingEdgesOf(vertex);
		Set<Integer> dominatedVerices = new HashSet<>();
		for (DefaultEdge outgoingEdgeOfVertex : outgoingEdgesOfVertex) {
			dominatedVerices.add(getGraph().getEdgeTarget(outgoingEdgeOfVertex));
		}
		return dominatedVerices;
	}

	public boolean isPostDominating(int x, int y) throws IOException, InvalidClassFileException {
		return postDominatedBy(x).contains(y);
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
}
