package de.rherzog.master.thesis.slicer.test;

import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.nio.file.Path;

import org.jgrapht.Graph;
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
		cfg = TestControlFlowGraph.getControlFlowGraph();

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

		postDominance = new PostDominance(cfg, 1);
	}

	@Test
	public void cd() throws IOException, InterruptedException, InvalidClassFileException, ExportException {
		ControlDependency controlDependency = new ControlDependency(cfg, postDominance);
		controlDependency.writePlot(Path.of("/tmp/slicer"), "ControlDependencyTest.png");
	}
}
