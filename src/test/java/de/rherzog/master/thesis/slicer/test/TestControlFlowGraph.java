package de.rherzog.master.thesis.slicer.test;

import java.util.stream.IntStream;

import org.jgrapht.Graph;
import org.jgrapht.graph.DefaultDirectedGraph;
import org.jgrapht.graph.DefaultEdge;

public class TestControlFlowGraph {
	public static Graph<Integer, DefaultEdge> getControlFlowGraph() {
		Graph<Integer, DefaultEdge> cfg = new DefaultDirectedGraph<Integer, DefaultEdge>(DefaultEdge.class);

		// https://www.cs.colorado.edu/~kena/classes/5828/s00/lectures/lecture15.pdf
//		IntStream.rangeClosed(1, 6).forEach(i -> cfg.addVertex(i));
//		cfg.addEdge(1, 2);
//		cfg.addEdge(2, 3);
//		cfg.addEdge(2, 4);
//		cfg.addEdge(3, 5);
//		cfg.addEdge(4, 5);
//		cfg.addEdge(5, 6);

		// Extended 1:
		// https://www.cs.colorado.edu/~kena/classes/5828/s00/lectures/lecture15.pdf
//		IntStream.rangeClosed(1, 10).forEach(i -> cfg.addVertex(i));
//		cfg.addEdge(1, 2);
//
//		cfg.addEdge(2, 3);
//		cfg.addEdge(2, 4);
//
//		cfg.addEdge(3, 5);
//		cfg.addEdge(5, 6);
//		cfg.addEdge(6, 9);
//
//		cfg.addEdge(4, 7);
//		cfg.addEdge(7, 8);
//		cfg.addEdge(8, 9);
//
//		cfg.addEdge(9, 10);
		
		// Extended 2:
		// https://www.cs.colorado.edu/~kena/classes/5828/s00/lectures/lecture15.pdf
//		IntStream.rangeClosed(1, 9).forEach(i -> cfg.addVertex(i));
//		cfg.addEdge(1, 2);
//		cfg.addEdge(2, 3);
//		cfg.addEdge(2, 4);
//		cfg.addEdge(3, 8);
//		cfg.addEdge(4, 5);
//		cfg.addEdge(4, 6);
//		cfg.addEdge(5, 7);
//		cfg.addEdge(6, 7);
//		cfg.addEdge(7, 8);
//		cfg.addEdge(8, 9);

		// https://en.wikipedia.org/wiki/Dominator_(graph_theory)
		IntStream.rangeClosed(1, 6).forEach(i -> cfg.addVertex(i));
		cfg.addEdge(1, 2);
		cfg.addEdge(2, 3);
		cfg.addEdge(2, 4);
		cfg.addEdge(2, 6);
		cfg.addEdge(3, 5);
		cfg.addEdge(4, 5);
		cfg.addEdge(5, 2);

		return cfg;
	}
}
