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
import de.rherzog.master.thesis.slicer.dominance.StrictDominance;
import de.rherzog.master.thesis.utils.Utilities;

@TestInstance(Lifecycle.PER_CLASS)
public class DominanceTest {
	private Graph<Integer, DefaultEdge> cfg;
	private Dominance dominance;
	private StrictDominance strictDominance;
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

		dominance = new Dominance(cfg, 1);
		strictDominance = new StrictDominance(dominance);
		immediateDominance = new ImmediateDominance(strictDominance);
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

//	@Test
//	public void fd() throws IOException, InterruptedException, InvalidClassFileException, ExportException {
//		ForwardDominance forwardDominatorTree = new ForwardDominance(new Dominance(cfg, 1));
//		forwardDominatorTree.getDominators();
//		forwardDominatorTree.writePlot(Path.of("/tmp/slicer"), "ForwardDominance.png");
//	}

//	@Test
//	public void ffd() throws IOException, InterruptedException, InvalidClassFileException, ExportException {
//		FirstForwardDominatorTree firstForwardDominatorTree = new FirstForwardDominatorTree(cfg);
//		firstForwardDominatorTree.getImmediateForwardDominators();
//
//		firstForwardDominatorTree.writePlot(Path.of("/tmp/slicer"), "FirstForwardDominatorTreeGraph.png");
//	}

//	@Test
//	public void pd() throws IOException, InterruptedException, InvalidClassFileException, ExportException {
//		PostDominance postDominance = new PostDominance(cfg, 1);
//		postDominance.writePlot(Path.of("/tmp/slicer"), "PostDominance.png");
//	}
}
