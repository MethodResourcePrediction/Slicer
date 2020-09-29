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

import de.rherzog.master.thesis.slicer.dominance.Dominance;
import de.rherzog.master.thesis.slicer.dominance.ImmediateDominance;
import de.rherzog.master.thesis.slicer.dominance.ImmediatePostDominance;
import de.rherzog.master.thesis.slicer.dominance.PostDominance;
import de.rherzog.master.thesis.slicer.dominance.StrictDominance;
import de.rherzog.master.thesis.slicer.dominance.StrictPostDominance;
import de.rherzog.master.thesis.utils.Utilities;

@TestInstance(Lifecycle.PER_CLASS)
public class DominanceTest {
	private Graph<Integer, DefaultEdge> cfg;
	private Dominance dominance;
	private PostDominance postDominance;
	private StrictDominance strictDominance;
	private ImmediateDominance immediateDominance;
	private StrictPostDominance strictPostDominance;
	private ImmediatePostDominance immediatePostDominance;

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

		dominance = new Dominance(cfg, 1);
		postDominance = new PostDominance(cfg, 1);
		strictDominance = new StrictDominance(dominance);
		strictPostDominance = new StrictPostDominance(postDominance);
		immediateDominance = new ImmediateDominance(strictDominance);
		immediatePostDominance = new ImmediatePostDominance(strictPostDominance);
	}

	@Test
	public void d() throws IOException, InterruptedException, InvalidClassFileException, ExportException {
		dominance.writePlot(Path.of("/tmp/slicer"), "Dominance.png");
	}

	@Test
	public void sd() throws IOException, InterruptedException, InvalidClassFileException, ExportException {
		strictDominance.writePlot(Path.of("/tmp/slicer"), "StrictDominance.png");
	}

	@Test
	public void id() throws IOException, InterruptedException, InvalidClassFileException, ExportException {
		immediateDominance.writePlot(Path.of("/tmp/slicer"), "ImmediateDominance.png");
	}

	@Test
	public void pd() throws IOException, InterruptedException, InvalidClassFileException, ExportException {
		postDominance.writePlot(Path.of("/tmp/slicer"), "PostDominance.png");
	}

	@Test
	public void spd() throws IOException, InterruptedException, InvalidClassFileException, ExportException {
		strictPostDominance.writePlot(Path.of("/tmp/slicer"), "StrictPostDominance.png");
	}

	@Test
	public void ipd() throws IOException, InterruptedException, InvalidClassFileException, ExportException {
		immediatePostDominance.writePlot(Path.of("/tmp/slicer"), "ImmediatePostDominance.png");
	}
}
