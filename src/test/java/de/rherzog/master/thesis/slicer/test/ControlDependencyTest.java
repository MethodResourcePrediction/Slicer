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
import de.rherzog.master.thesis.slicer.dominance.Dominance;
import de.rherzog.master.thesis.slicer.dominance.ImmediateDominance;
import de.rherzog.master.thesis.slicer.dominance.StrictDominance;
import de.rherzog.master.thesis.utils.Utilities;

@TestInstance(Lifecycle.PER_CLASS)
public class ControlDependencyTest {
	private Graph<Integer, DefaultEdge> cfg;
	private ImmediateDominance immediateDominance;

	@BeforeEach
	public void setup() throws IOException, InterruptedException, InvalidClassFileException {
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

		Utilities.dotWriteToFile("/tmp/slicer/ControlFlowGraph.png", writer.toString());

		Dominance dominance = new Dominance(cfg, 1);
		StrictDominance strictDominance = new StrictDominance(dominance);
		immediateDominance = new ImmediateDominance(strictDominance);
	}

	@Test
	public void cd() throws IOException, InterruptedException, InvalidClassFileException, ExportException {
		ControlDependency controlDependency = new ControlDependency(cfg, immediateDominance);
		controlDependency.writePlot(Path.of("/tmp/slicer"), "ControlDependency.png");
	}
}
