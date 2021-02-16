package de.uniks.vs.slicer.test;

import java.util.stream.IntStream;

import org.jgrapht.Graph;
import org.jgrapht.graph.DefaultDirectedGraph;
import org.jgrapht.graph.DefaultEdge;

public class TestControlFlowGraph {
	private static final int START_NODE = 1;
	
	public static Graph<Integer, DefaultEdge> getControlFlowGraph() {
		Graph<Integer, DefaultEdge> cfg = new DefaultDirectedGraph<Integer, DefaultEdge>(DefaultEdge.class);

		



		return cfg;
	}
	
	public static int getStartNode() {
		return START_NODE;
	}
}
