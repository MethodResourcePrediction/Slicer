package de.uniks.vs.methodresourceprediction.slicer.test;

import com.ibm.wala.shrike.shrikeCT.InvalidClassFileException;
import de.uniks.vs.methodresourceprediction.slicer.ControlDependency;
import de.uniks.vs.methodresourceprediction.slicer.dominance.ImmediateDominance;
import de.uniks.vs.methodresourceprediction.slicer.dominance.ImmediatePostDominance;
import de.uniks.vs.methodresourceprediction.slicer.dominance.PostDominance;
import de.uniks.vs.methodresourceprediction.slicer.dominance.StrictPostDominance;
import de.uniks.vs.methodresourceprediction.utils.Utilities;
import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.nio.file.Path;
import org.jgrapht.Graph;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.io.ComponentNameProvider;
import org.jgrapht.io.DOTExporter;
import org.jgrapht.io.ExportException;
import org.junit.Test;

public class ControlDependencyTest {
  private Graph<Integer, DefaultEdge> cfg;
  private ImmediateDominance immediateDominance;
  private ImmediatePostDominance immediatePostDominance;

  public void setup() throws IOException, InterruptedException, InvalidClassFileException {
    cfg = TestControlFlowGraph.getControlFlowGraph();

    DOTExporter<Integer, DefaultEdge> dotExporter =
        new DOTExporter<>(
            new ComponentNameProvider<>() {
              @Override
              public String getName(Integer component) {
                return component.toString();
              }
            },
            new ComponentNameProvider<>() {
              @Override
              public String getName(Integer component) {
                return component.toString();
              }
            },
            null);
    Writer writer = new StringWriter();
    dotExporter.exportGraph(cfg, writer);

    Utilities.dotWriteToFile("/tmp/slicer/ControlFlowGraph.png", writer.toString());

    //		Dominance dominance = new Dominance(cfg, TestControlFlowGraph.getStartNode());
    //		StrictDominance strictDominance = new StrictDominance(dominance);
    //		immediateDominance = new ImmediateDominance(strictDominance);

    PostDominance postDominance = new PostDominance(cfg, TestControlFlowGraph.getStartNode());
    StrictPostDominance strictPostDominance = new StrictPostDominance(postDominance);
    immediatePostDominance = new ImmediatePostDominance(strictPostDominance);
  }

  @Test
  public void cd()
      throws IOException, InterruptedException, InvalidClassFileException, ExportException {
    setup();
    ControlDependency controlDependency = new ControlDependency(cfg, immediatePostDominance);
    controlDependency.writePlot(Path.of("/tmp/slicer"), "ControlDependency.png");
  }
}
