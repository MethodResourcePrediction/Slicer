package de.rherzog.master.thesis.slicer.test;

import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.nio.file.Path;
import java.util.stream.IntStream;

import org.jgrapht.Graph;
import org.jgrapht.graph.DefaultDirectedGraph;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.io.ComponentNameProvider;
import org.jgrapht.io.DOTExporter;
import org.jgrapht.io.ExportException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;

import com.ibm.wala.shrikeCT.InvalidClassFileException;

import de.rherzog.master.thesis.slicer.ControlDependency;
import de.rherzog.master.thesis.slicer.dominance.PostDominance;
import de.rherzog.master.thesis.utils.Utilities;

@TestInstance(Lifecycle.PER_CLASS)
public class ControlDependencyTest {
	private Graph<Integer, DefaultEdge> cfg;
	private PostDominance postDominance;

	@BeforeEach
	public void setup() throws IOException, InterruptedException {
		cfg = new DefaultDirectedGraph<Integer, DefaultEdge>(DefaultEdge.class);

		// https://www.cs.colorado.edu/~kena/classes/5828/s00/lectures/lecture15.pdf
		IntStream.rangeClosed(1, 6).forEach(i -> cfg.addVertex(i));
		cfg.addEdge(1, 2);
		cfg.addEdge(2, 3);
		cfg.addEdge(2, 4);
		cfg.addEdge(3, 5);
		cfg.addEdge(4, 5);
		cfg.addEdge(5, 6);

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
//		IntStream.rangeClosed(1, 6).forEach(i -> cfg.addVertex(i));
//		cfg.addEdge(1, 2);
//		cfg.addEdge(2, 3);
//		cfg.addEdge(2, 4);
//		cfg.addEdge(2, 6);
//		cfg.addEdge(3, 5);
//		cfg.addEdge(4, 5);
//		cfg.addEdge(5, 2);

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

		postDominance = new PostDominance(cfg);
	}

	@Test
	public void cd() throws IOException, InterruptedException, InvalidClassFileException, ExportException {
		ControlDependency controlDependency = new ControlDependency(cfg, postDominance);
		controlDependency.writePlot(Path.of("/tmp/slicer"), "ControlDependencyTest.png");
	}
}
