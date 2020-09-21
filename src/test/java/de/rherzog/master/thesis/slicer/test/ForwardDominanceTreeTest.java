package de.rherzog.master.thesis.slicer.test;

import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.jgrapht.Graph;
import org.jgrapht.GraphPath;
import org.jgrapht.alg.connectivity.ConnectivityInspector;
import org.jgrapht.alg.shortestpath.AllDirectedPaths;
import org.jgrapht.graph.DefaultDirectedGraph;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.io.ComponentNameProvider;
import org.jgrapht.io.DOTExporter;
import org.jgrapht.io.ExportException;
import org.jgrapht.io.GraphExporter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;

import com.ibm.wala.shrikeCT.InvalidClassFileException;

import de.rherzog.master.thesis.slicer.ControlFlow;
import de.rherzog.master.thesis.slicer.FirstForwardDominatorTree;
import de.rherzog.master.thesis.slicer.DominanceTree;
import de.rherzog.master.thesis.utils.Utilities;

@TestInstance(Lifecycle.PER_CLASS)
public class ForwardDominanceTreeTest {
	Graph<Integer, DefaultEdge> cfg;

	@BeforeEach
	public void foo() throws IOException, InterruptedException {
		cfg = new DefaultDirectedGraph<Integer, DefaultEdge>(DefaultEdge.class);

		// https://www.cs.colorado.edu/~kena/classes/5828/s00/lectures/lecture15.pdf
//		IntStream.rangeClosed(1, 6).forEach(i -> cfg.addVertex(i));
//		cfg.addEdge(1, 2);
//		cfg.addEdge(2, 3);
//		cfg.addEdge(2, 4);
//		cfg.addEdge(3, 5);
//		cfg.addEdge(4, 5);
//		cfg.addEdge(5, 6);

		// Extended:
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

		// https://en.wikipedia.org/wiki/Dominator_(graph_theory)
		IntStream.rangeClosed(1, 6).forEach(i -> cfg.addVertex(i));
		cfg.addEdge(1, 2);
		cfg.addEdge(2, 3);
		cfg.addEdge(2, 4);
		cfg.addEdge(2, 6);
		cfg.addEdge(3, 5);
		cfg.addEdge(4, 5);
		cfg.addEdge(5, 2);

		DOTExporter<Integer, DefaultEdge> dotExporter = new DOTExporter<>(new ComponentNameProvider<>() {
			@Override
			public String getName(Integer component) {
				return component.toString();
			}
		}, new ComponentNameProvider<>() {
			@Override
			public String getName(Integer component) {
				return component.toString();
			}
		}, null);
		Writer writer = new StringWriter();
		dotExporter.exportGraph(cfg, writer);

		Utilities.dotWriteToFile("/tmp/slicer/ControlFlowGraphTest.png", writer.toString());
	}

	@Test
	public void fd() throws IOException, InterruptedException, InvalidClassFileException, ExportException {
		DominanceTree forwardDominatorTree = new DominanceTree(cfg, 1);
		forwardDominatorTree.getDominators();
		forwardDominatorTree.writePlot(Path.of("/tmp/slicer"), "DominanceTreeGraphTest.png");
	}

	@Test
	public void ffd() throws IOException, InterruptedException, InvalidClassFileException, ExportException {
		FirstForwardDominatorTree firstForwardDominatorTree = new FirstForwardDominatorTree(cfg);
		firstForwardDominatorTree.getImmediateForwardDominators();

		firstForwardDominatorTree.writePlot(Path.of("/tmp/slicer"), "FirstForwardDominatorTreeGraphTest.png");
	}
}
