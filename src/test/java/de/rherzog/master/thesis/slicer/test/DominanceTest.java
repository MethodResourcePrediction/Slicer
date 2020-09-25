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
import de.rherzog.master.thesis.slicer.dominance.FirstForwardDominatorTree;
import de.rherzog.master.thesis.slicer.dominance.PostDominance;
import de.rherzog.master.thesis.slicer.dominance.StrictDominance;
import de.rherzog.master.thesis.utils.Utilities;

@TestInstance(Lifecycle.PER_CLASS)
public class DominanceTest {
	Graph<Integer, DefaultEdge> cfg;

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
	}

	@Test
	public void d() throws IOException, InterruptedException, InvalidClassFileException, ExportException {
		Dominance dominance = new Dominance(cfg, 1);
		dominance.writePlot(Path.of("/tmp/slicer"), "DominanceTest.png");
	}

	@Test
	public void fd() throws IOException, InterruptedException, InvalidClassFileException, ExportException {
		Dominance forwardDominatorTree = new Dominance(cfg, 1);
		forwardDominatorTree.getDominators();
		forwardDominatorTree.writePlot(Path.of("/tmp/slicer"), "DominanceTreeGraphTest.png");
	}

	@Test
	public void ffd() throws IOException, InterruptedException, InvalidClassFileException, ExportException {
		FirstForwardDominatorTree firstForwardDominatorTree = new FirstForwardDominatorTree(cfg);
		firstForwardDominatorTree.getImmediateForwardDominators();

		firstForwardDominatorTree.writePlot(Path.of("/tmp/slicer"), "FirstForwardDominatorTreeGraphTest.png");
	}

	@Test
	public void pd() throws IOException, InterruptedException, InvalidClassFileException, ExportException {
		PostDominance postDominance = new PostDominance(cfg, 1);
		postDominance.writePlot(Path.of("/tmp/slicer"), "PostDominanceTest.png");
	}

	@Test
	public void sd() throws IOException, InterruptedException, InvalidClassFileException, ExportException {
		StrictDominance strictDominance = new StrictDominance(new Dominance(cfg, 1));
		strictDominance.writePlot(Path.of("/tmp/slicer"), "StrictDominanceTest.png");
	}
}
