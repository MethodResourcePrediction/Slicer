package de.uniks.vs.methodresourceprediction.slicer.test;

import com.ibm.wala.shrike.shrikeCT.InvalidClassFileException;
import de.uniks.vs.methodresourceprediction.slicer.ControlDependency;
import de.uniks.vs.methodresourceprediction.slicer.SlicerGraph;
import de.uniks.vs.methodresourceprediction.slicer.dominance.Dominance;
import de.uniks.vs.methodresourceprediction.slicer.dominance.ImmediateDominance;
import de.uniks.vs.methodresourceprediction.slicer.dominance.ImmediatePostDominance;
import de.uniks.vs.methodresourceprediction.slicer.dominance.PostDominance;
import de.uniks.vs.methodresourceprediction.slicer.dominance.StrictDominance;
import de.uniks.vs.methodresourceprediction.slicer.dominance.StrictPostDominance;
import de.uniks.vs.methodresourceprediction.utils.Utilities;
import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.IntStream;
import org.jgrapht.Graph;
import org.jgrapht.graph.AbstractGraph;
import org.jgrapht.graph.DefaultDirectedGraph;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.io.ComponentNameProvider;
import org.jgrapht.io.DOTExporter;
import org.jgrapht.io.ExportException;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class DominanceTest {
  public void setup() throws IOException, InterruptedException, InvalidClassFileException {}

  @Test
  public void cfg1()
      throws IOException, InvalidClassFileException, InterruptedException, ExportException {
    // https://www.cs.colorado.edu/~kena/classes/5828/s00/lectures/lecture15.pdf
    Graph<Integer, DefaultEdge> cfg =
        new DefaultDirectedGraph<Integer, DefaultEdge>(DefaultEdge.class);
    IntStream.rangeClosed(1, 6).forEach(i -> cfg.addVertex(i));
    cfg.addEdge(1, 2);
    cfg.addEdge(2, 3);
    cfg.addEdge(2, 4);
    cfg.addEdge(3, 5);
    cfg.addEdge(4, 5);
    cfg.addEdge(5, 6);

    Dominance dominance = new Dominance(cfg, 1);
    PostDominance postDominance = new PostDominance(cfg, 1);
    StrictDominance strictDominance = new StrictDominance(dominance);
    StrictPostDominance strictPostDominance = new StrictPostDominance(postDominance);
    ImmediateDominance immediateDominance = new ImmediateDominance(strictDominance);
    ImmediatePostDominance immediatePostDominance = new ImmediatePostDominance(strictPostDominance);
    ControlDependency controlDependency = new ControlDependency(cfg, immediatePostDominance);

    plot(cfg);
    plot(dominance);
    plot(postDominance);
    plot(strictDominance);
    plot(strictPostDominance);
    plot(immediateDominance);
    plot(immediatePostDominance);
    plot(controlDependency);

    //		System.out.println(javaSourceFromGraph(dominance));
    //		System.out.println(javaSourceFromGraph(postDominance));
    //		System.out.println(javaSourceFromGraph(strictDominance));
    //		System.out.println(javaSourceFromGraph(strictPostDominance));
    //		System.out.println(javaSourceFromGraph(immediateDominance));
    //		System.out.println(javaSourceFromGraph(immediatePostDominance));
    //		System.out.println(javaSourceFromGraph(controlDependency));

    // Test Dominance
    Graph<Integer, DefaultEdge> expectedDominance =
        new DefaultDirectedGraph<Integer, DefaultEdge>(DefaultEdge.class);
    IntStream.rangeClosed(1, 6).forEach(i -> expectedDominance.addVertex(i));
    expectedDominance.addEdge(1, 1);
    expectedDominance.addEdge(1, 2);
    expectedDominance.addEdge(2, 2);
    expectedDominance.addEdge(1, 3);
    expectedDominance.addEdge(2, 3);
    expectedDominance.addEdge(3, 3);
    expectedDominance.addEdge(1, 4);
    expectedDominance.addEdge(2, 4);
    expectedDominance.addEdge(4, 4);
    expectedDominance.addEdge(1, 5);
    expectedDominance.addEdge(2, 5);
    expectedDominance.addEdge(5, 5);
    expectedDominance.addEdge(1, 6);
    expectedDominance.addEdge(2, 6);
    expectedDominance.addEdge(5, 6);
    expectedDominance.addEdge(6, 6);
    compareGraphs(dominance.getGraph(), expectedDominance);

    // Test PostDominance
    Graph<Integer, DefaultEdge> expectedPostDominance =
        new DefaultDirectedGraph<Integer, DefaultEdge>(DefaultEdge.class);
    IntStream.rangeClosed(1, 6).forEach(i -> expectedPostDominance.addVertex(i));
    expectedPostDominance.addEdge(1, 1);
    expectedPostDominance.addEdge(2, 1);
    expectedPostDominance.addEdge(5, 1);
    expectedPostDominance.addEdge(6, 1);
    expectedPostDominance.addEdge(2, 2);
    expectedPostDominance.addEdge(5, 2);
    expectedPostDominance.addEdge(6, 2);
    expectedPostDominance.addEdge(3, 3);
    expectedPostDominance.addEdge(5, 3);
    expectedPostDominance.addEdge(6, 3);
    expectedPostDominance.addEdge(4, 4);
    expectedPostDominance.addEdge(5, 4);
    expectedPostDominance.addEdge(6, 4);
    expectedPostDominance.addEdge(5, 5);
    expectedPostDominance.addEdge(6, 5);
    expectedPostDominance.addEdge(6, 6);
    compareGraphs(postDominance.getGraph(), expectedPostDominance);

    // Test StrictDominance
    Graph<Integer, DefaultEdge> expectedStrictDominance =
        new DefaultDirectedGraph<Integer, DefaultEdge>(DefaultEdge.class);
    IntStream.rangeClosed(1, 6).forEach(i -> expectedStrictDominance.addVertex(i));
    expectedStrictDominance.addEdge(1, 2);
    expectedStrictDominance.addEdge(1, 3);
    expectedStrictDominance.addEdge(2, 3);
    expectedStrictDominance.addEdge(1, 4);
    expectedStrictDominance.addEdge(2, 4);
    expectedStrictDominance.addEdge(1, 5);
    expectedStrictDominance.addEdge(2, 5);
    expectedStrictDominance.addEdge(1, 6);
    expectedStrictDominance.addEdge(2, 6);
    expectedStrictDominance.addEdge(5, 6);
    compareGraphs(strictDominance.getGraph(), expectedStrictDominance);

    // Test StrictPostDominance
    Graph<Integer, DefaultEdge> expectedStrictPostDominance =
        new DefaultDirectedGraph<Integer, DefaultEdge>(DefaultEdge.class);
    IntStream.rangeClosed(1, 6).forEach(i -> expectedStrictPostDominance.addVertex(i));
    expectedStrictPostDominance.addEdge(2, 1);
    expectedStrictPostDominance.addEdge(5, 1);
    expectedStrictPostDominance.addEdge(6, 1);
    expectedStrictPostDominance.addEdge(5, 2);
    expectedStrictPostDominance.addEdge(6, 2);
    expectedStrictPostDominance.addEdge(5, 3);
    expectedStrictPostDominance.addEdge(6, 3);
    expectedStrictPostDominance.addEdge(5, 4);
    expectedStrictPostDominance.addEdge(6, 4);
    expectedStrictPostDominance.addEdge(6, 5);
    compareGraphs(strictPostDominance.getGraph(), expectedStrictPostDominance);

    // Test ImmediateDominance
    Graph<Integer, DefaultEdge> expectedImmediateDominance =
        new DefaultDirectedGraph<Integer, DefaultEdge>(DefaultEdge.class);
    IntStream.rangeClosed(1, 6).forEach(i -> expectedImmediateDominance.addVertex(i));
    expectedImmediateDominance.addEdge(1, 2);
    expectedImmediateDominance.addEdge(2, 3);
    expectedImmediateDominance.addEdge(2, 4);
    expectedImmediateDominance.addEdge(2, 5);
    expectedImmediateDominance.addEdge(5, 6);
    compareGraphs(immediateDominance.getGraph(), expectedImmediateDominance);

    // Test ImmediatePostDominance
    Graph<Integer, DefaultEdge> expectedImmediatePostDominance =
        new DefaultDirectedGraph<Integer, DefaultEdge>(DefaultEdge.class);
    IntStream.rangeClosed(1, 6).forEach(i -> expectedImmediatePostDominance.addVertex(i));
    expectedImmediatePostDominance.addEdge(2, 1);
    expectedImmediatePostDominance.addEdge(5, 2);
    expectedImmediatePostDominance.addEdge(5, 3);
    expectedImmediatePostDominance.addEdge(5, 4);
    expectedImmediatePostDominance.addEdge(6, 5);
    compareGraphs(immediatePostDominance.getGraph(), expectedImmediatePostDominance);

    // Test ControlDependency
    Graph<Integer, DefaultEdge> expectedControlDependency =
        new DefaultDirectedGraph<Integer, DefaultEdge>(DefaultEdge.class);
    expectedControlDependency.addVertex(ControlDependency.ROOT_INDEX);
    IntStream.rangeClosed(1, 6).forEach(i -> expectedControlDependency.addVertex(i));
    expectedControlDependency.addEdge(2, 3);
    expectedControlDependency.addEdge(2, 4);
    expectedControlDependency.addEdge(-1, 1);
    expectedControlDependency.addEdge(-1, 2);
    expectedControlDependency.addEdge(-1, 5);
    expectedControlDependency.addEdge(-1, 6);
    compareGraphs(controlDependency.getGraph(), expectedControlDependency);
  }

  @Test
  public void cfg2()
      throws IOException, InvalidClassFileException, InterruptedException, ExportException {
    // Extended 1:
    // https://www.cs.colorado.edu/~kena/classes/5828/s00/lectures/lecture15.pdf
    AbstractGraph<Integer, DefaultEdge> cfg =
        new DefaultDirectedGraph<Integer, DefaultEdge>(DefaultEdge.class);
    IntStream.rangeClosed(1, 10).forEach(i -> cfg.addVertex(i));
    cfg.addEdge(1, 2);

    cfg.addEdge(2, 3);
    cfg.addEdge(2, 4);

    cfg.addEdge(3, 5);
    cfg.addEdge(5, 6);
    cfg.addEdge(6, 9);

    cfg.addEdge(4, 7);
    cfg.addEdge(7, 8);
    cfg.addEdge(8, 9);

    cfg.addEdge(9, 10);

    Dominance dominance = new Dominance(cfg, 1);
    PostDominance postDominance = new PostDominance(cfg, 1);
    StrictDominance strictDominance = new StrictDominance(dominance);
    StrictPostDominance strictPostDominance = new StrictPostDominance(postDominance);
    ImmediateDominance immediateDominance = new ImmediateDominance(strictDominance);
    ImmediatePostDominance immediatePostDominance = new ImmediatePostDominance(strictPostDominance);
    ControlDependency controlDependency = new ControlDependency(cfg, immediatePostDominance);

    plot(cfg);
    plot(dominance);
    plot(postDominance);
    plot(strictDominance);
    plot(strictPostDominance);
    plot(immediateDominance);
    plot(immediatePostDominance);
    plot(controlDependency);

    //		System.out.println(javaSourceFromGraph(dominance));
    //		System.out.println(javaSourceFromGraph(postDominance));
    //		System.out.println(javaSourceFromGraph(strictDominance));
    //		System.out.println(javaSourceFromGraph(strictPostDominance));
    //		System.out.println(javaSourceFromGraph(immediateDominance));
    //		System.out.println(javaSourceFromGraph(immediatePostDominance));
    //		System.out.println(javaSourceFromGraph(controlDependency));

    // Test Dominance
    Graph<Integer, DefaultEdge> expectedDominance =
        new DefaultDirectedGraph<Integer, DefaultEdge>(DefaultEdge.class);
    IntStream.rangeClosed(1, 10).forEach(i -> expectedDominance.addVertex(i));
    expectedDominance.addEdge(1, 1);
    expectedDominance.addEdge(1, 2);
    expectedDominance.addEdge(2, 2);
    expectedDominance.addEdge(1, 3);
    expectedDominance.addEdge(2, 3);
    expectedDominance.addEdge(3, 3);
    expectedDominance.addEdge(1, 4);
    expectedDominance.addEdge(2, 4);
    expectedDominance.addEdge(4, 4);
    expectedDominance.addEdge(1, 5);
    expectedDominance.addEdge(2, 5);
    expectedDominance.addEdge(3, 5);
    expectedDominance.addEdge(5, 5);
    expectedDominance.addEdge(1, 6);
    expectedDominance.addEdge(2, 6);
    expectedDominance.addEdge(3, 6);
    expectedDominance.addEdge(5, 6);
    expectedDominance.addEdge(6, 6);
    expectedDominance.addEdge(1, 7);
    expectedDominance.addEdge(2, 7);
    expectedDominance.addEdge(4, 7);
    expectedDominance.addEdge(7, 7);
    expectedDominance.addEdge(1, 8);
    expectedDominance.addEdge(2, 8);
    expectedDominance.addEdge(4, 8);
    expectedDominance.addEdge(7, 8);
    expectedDominance.addEdge(8, 8);
    expectedDominance.addEdge(1, 9);
    expectedDominance.addEdge(2, 9);
    expectedDominance.addEdge(9, 9);
    expectedDominance.addEdge(1, 10);
    expectedDominance.addEdge(2, 10);
    expectedDominance.addEdge(9, 10);
    expectedDominance.addEdge(10, 10);
    compareGraphs(dominance.getGraph(), expectedDominance);

    // Test PostDominance
    Graph<Integer, DefaultEdge> expectedPostDominance =
        new DefaultDirectedGraph<Integer, DefaultEdge>(DefaultEdge.class);
    IntStream.rangeClosed(1, 10).forEach(i -> expectedPostDominance.addVertex(i));
    expectedPostDominance.addEdge(1, 1);
    expectedPostDominance.addEdge(2, 1);
    expectedPostDominance.addEdge(9, 1);
    expectedPostDominance.addEdge(10, 1);
    expectedPostDominance.addEdge(2, 2);
    expectedPostDominance.addEdge(9, 2);
    expectedPostDominance.addEdge(10, 2);
    expectedPostDominance.addEdge(3, 3);
    expectedPostDominance.addEdge(5, 3);
    expectedPostDominance.addEdge(6, 3);
    expectedPostDominance.addEdge(9, 3);
    expectedPostDominance.addEdge(10, 3);
    expectedPostDominance.addEdge(4, 4);
    expectedPostDominance.addEdge(7, 4);
    expectedPostDominance.addEdge(8, 4);
    expectedPostDominance.addEdge(9, 4);
    expectedPostDominance.addEdge(10, 4);
    expectedPostDominance.addEdge(5, 5);
    expectedPostDominance.addEdge(6, 5);
    expectedPostDominance.addEdge(9, 5);
    expectedPostDominance.addEdge(10, 5);
    expectedPostDominance.addEdge(6, 6);
    expectedPostDominance.addEdge(9, 6);
    expectedPostDominance.addEdge(10, 6);
    expectedPostDominance.addEdge(7, 7);
    expectedPostDominance.addEdge(8, 7);
    expectedPostDominance.addEdge(9, 7);
    expectedPostDominance.addEdge(10, 7);
    expectedPostDominance.addEdge(8, 8);
    expectedPostDominance.addEdge(9, 8);
    expectedPostDominance.addEdge(10, 8);
    expectedPostDominance.addEdge(9, 9);
    expectedPostDominance.addEdge(10, 9);
    expectedPostDominance.addEdge(10, 10);
    compareGraphs(postDominance.getGraph(), expectedPostDominance);

    // Test StrictDominance
    Graph<Integer, DefaultEdge> expectedStrictDominance =
        new DefaultDirectedGraph<Integer, DefaultEdge>(DefaultEdge.class);
    IntStream.rangeClosed(1, 10).forEach(i -> expectedStrictDominance.addVertex(i));
    expectedStrictDominance.addEdge(1, 2);
    expectedStrictDominance.addEdge(1, 3);
    expectedStrictDominance.addEdge(2, 3);
    expectedStrictDominance.addEdge(1, 4);
    expectedStrictDominance.addEdge(2, 4);
    expectedStrictDominance.addEdge(1, 5);
    expectedStrictDominance.addEdge(2, 5);
    expectedStrictDominance.addEdge(3, 5);
    expectedStrictDominance.addEdge(1, 6);
    expectedStrictDominance.addEdge(2, 6);
    expectedStrictDominance.addEdge(3, 6);
    expectedStrictDominance.addEdge(5, 6);
    expectedStrictDominance.addEdge(1, 7);
    expectedStrictDominance.addEdge(2, 7);
    expectedStrictDominance.addEdge(4, 7);
    expectedStrictDominance.addEdge(1, 8);
    expectedStrictDominance.addEdge(2, 8);
    expectedStrictDominance.addEdge(4, 8);
    expectedStrictDominance.addEdge(7, 8);
    expectedStrictDominance.addEdge(1, 9);
    expectedStrictDominance.addEdge(2, 9);
    expectedStrictDominance.addEdge(1, 10);
    expectedStrictDominance.addEdge(2, 10);
    expectedStrictDominance.addEdge(9, 10);
    compareGraphs(strictDominance.getGraph(), expectedStrictDominance);

    // Test StrictPostDominance
    Graph<Integer, DefaultEdge> expectedStrictPostDominance =
        new DefaultDirectedGraph<Integer, DefaultEdge>(DefaultEdge.class);
    IntStream.rangeClosed(1, 10).forEach(i -> expectedStrictPostDominance.addVertex(i));
    expectedStrictPostDominance.addEdge(2, 1);
    expectedStrictPostDominance.addEdge(9, 1);
    expectedStrictPostDominance.addEdge(10, 1);
    expectedStrictPostDominance.addEdge(9, 2);
    expectedStrictPostDominance.addEdge(10, 2);
    expectedStrictPostDominance.addEdge(5, 3);
    expectedStrictPostDominance.addEdge(6, 3);
    expectedStrictPostDominance.addEdge(9, 3);
    expectedStrictPostDominance.addEdge(10, 3);
    expectedStrictPostDominance.addEdge(7, 4);
    expectedStrictPostDominance.addEdge(8, 4);
    expectedStrictPostDominance.addEdge(9, 4);
    expectedStrictPostDominance.addEdge(10, 4);
    expectedStrictPostDominance.addEdge(6, 5);
    expectedStrictPostDominance.addEdge(9, 5);
    expectedStrictPostDominance.addEdge(10, 5);
    expectedStrictPostDominance.addEdge(9, 6);
    expectedStrictPostDominance.addEdge(10, 6);
    expectedStrictPostDominance.addEdge(8, 7);
    expectedStrictPostDominance.addEdge(9, 7);
    expectedStrictPostDominance.addEdge(10, 7);
    expectedStrictPostDominance.addEdge(9, 8);
    expectedStrictPostDominance.addEdge(10, 8);
    expectedStrictPostDominance.addEdge(10, 9);
    compareGraphs(strictPostDominance.getGraph(), expectedStrictPostDominance);

    // Test ImmediateDominance
    Graph<Integer, DefaultEdge> expectedImmediateDominance =
        new DefaultDirectedGraph<Integer, DefaultEdge>(DefaultEdge.class);
    IntStream.rangeClosed(1, 10).forEach(i -> expectedImmediateDominance.addVertex(i));
    expectedImmediateDominance.addEdge(1, 2);
    expectedImmediateDominance.addEdge(2, 3);
    expectedImmediateDominance.addEdge(2, 4);
    expectedImmediateDominance.addEdge(3, 5);
    expectedImmediateDominance.addEdge(5, 6);
    expectedImmediateDominance.addEdge(4, 7);
    expectedImmediateDominance.addEdge(7, 8);
    expectedImmediateDominance.addEdge(2, 9);
    expectedImmediateDominance.addEdge(9, 10);
    compareGraphs(immediateDominance.getGraph(), expectedImmediateDominance);

    // Test ImmediatePostDominance
    Graph<Integer, DefaultEdge> expectedImmediatePostDominance =
        new DefaultDirectedGraph<Integer, DefaultEdge>(DefaultEdge.class);
    IntStream.rangeClosed(1, 10).forEach(i -> expectedImmediatePostDominance.addVertex(i));
    expectedImmediatePostDominance.addEdge(2, 1);
    expectedImmediatePostDominance.addEdge(9, 2);
    expectedImmediatePostDominance.addEdge(5, 3);
    expectedImmediatePostDominance.addEdge(7, 4);
    expectedImmediatePostDominance.addEdge(6, 5);
    expectedImmediatePostDominance.addEdge(9, 6);
    expectedImmediatePostDominance.addEdge(8, 7);
    expectedImmediatePostDominance.addEdge(9, 8);
    expectedImmediatePostDominance.addEdge(10, 9);
    compareGraphs(immediatePostDominance.getGraph(), expectedImmediatePostDominance);

    // Test ControlDependency
    Graph<Integer, DefaultEdge> expectedControlDependency =
        new DefaultDirectedGraph<Integer, DefaultEdge>(DefaultEdge.class);
    expectedControlDependency.addVertex(ControlDependency.ROOT_INDEX);
    IntStream.rangeClosed(1, 10).forEach(i -> expectedControlDependency.addVertex(i));
    expectedControlDependency.addEdge(2, 3);
    expectedControlDependency.addEdge(2, 4);
    expectedControlDependency.addEdge(2, 5);
    expectedControlDependency.addEdge(2, 6);
    expectedControlDependency.addEdge(2, 7);
    expectedControlDependency.addEdge(2, 8);
    expectedControlDependency.addEdge(-1, 1);
    expectedControlDependency.addEdge(-1, 2);
    expectedControlDependency.addEdge(-1, 9);
    expectedControlDependency.addEdge(-1, 10);
    compareGraphs(controlDependency.getGraph(), expectedControlDependency);
  }

  @Test
  public void cfg3()
      throws IOException, InvalidClassFileException, InterruptedException, ExportException {
    // Extended 2:
    // https://www.cs.colorado.edu/~kena/classes/5828/s00/lectures/lecture15.pdf
    AbstractGraph<Integer, DefaultEdge> cfg =
        new DefaultDirectedGraph<Integer, DefaultEdge>(DefaultEdge.class);
    IntStream.rangeClosed(1, 9).forEach(i -> cfg.addVertex(i));
    cfg.addEdge(1, 2);
    cfg.addEdge(2, 3);
    cfg.addEdge(2, 4);
    cfg.addEdge(3, 8);
    cfg.addEdge(4, 5);
    cfg.addEdge(4, 6);
    cfg.addEdge(5, 7);
    cfg.addEdge(6, 7);
    cfg.addEdge(7, 8);
    cfg.addEdge(8, 9);

    Dominance dominance = new Dominance(cfg, 1);
    PostDominance postDominance = new PostDominance(cfg, 1);
    StrictDominance strictDominance = new StrictDominance(dominance);
    StrictPostDominance strictPostDominance = new StrictPostDominance(postDominance);
    ImmediateDominance immediateDominance = new ImmediateDominance(strictDominance);
    ImmediatePostDominance immediatePostDominance = new ImmediatePostDominance(strictPostDominance);
    ControlDependency controlDependency = new ControlDependency(cfg, immediatePostDominance);

    plot(cfg);
    plot(dominance);
    plot(postDominance);
    plot(strictDominance);
    plot(strictPostDominance);
    plot(immediateDominance);
    plot(immediatePostDominance);
    plot(controlDependency);

    //		System.out.println(javaSourceFromGraph(dominance));
    //		System.out.println(javaSourceFromGraph(postDominance));
    //		System.out.println(javaSourceFromGraph(strictDominance));
    //		System.out.println(javaSourceFromGraph(strictPostDominance));
    //		System.out.println(javaSourceFromGraph(immediateDominance));
    //		System.out.println(javaSourceFromGraph(immediatePostDominance));
    //		System.out.println(javaSourceFromGraph(controlDependency));

    // Test Dominance
    Graph<Integer, DefaultEdge> expectedDominance =
        new DefaultDirectedGraph<Integer, DefaultEdge>(DefaultEdge.class);
    IntStream.rangeClosed(1, 9).forEach(i -> expectedDominance.addVertex(i));
    expectedDominance.addEdge(1, 1);
    expectedDominance.addEdge(1, 2);
    expectedDominance.addEdge(2, 2);
    expectedDominance.addEdge(1, 3);
    expectedDominance.addEdge(2, 3);
    expectedDominance.addEdge(3, 3);
    expectedDominance.addEdge(1, 4);
    expectedDominance.addEdge(2, 4);
    expectedDominance.addEdge(4, 4);
    expectedDominance.addEdge(1, 5);
    expectedDominance.addEdge(2, 5);
    expectedDominance.addEdge(4, 5);
    expectedDominance.addEdge(5, 5);
    expectedDominance.addEdge(1, 6);
    expectedDominance.addEdge(2, 6);
    expectedDominance.addEdge(4, 6);
    expectedDominance.addEdge(6, 6);
    expectedDominance.addEdge(1, 7);
    expectedDominance.addEdge(2, 7);
    expectedDominance.addEdge(4, 7);
    expectedDominance.addEdge(7, 7);
    expectedDominance.addEdge(1, 8);
    expectedDominance.addEdge(2, 8);
    expectedDominance.addEdge(8, 8);
    expectedDominance.addEdge(1, 9);
    expectedDominance.addEdge(2, 9);
    expectedDominance.addEdge(8, 9);
    expectedDominance.addEdge(9, 9);
    compareGraphs(dominance.getGraph(), expectedDominance);

    // Test PostDominance
    Graph<Integer, DefaultEdge> expectedPostDominance =
        new DefaultDirectedGraph<Integer, DefaultEdge>(DefaultEdge.class);
    IntStream.rangeClosed(1, 9).forEach(i -> expectedPostDominance.addVertex(i));
    expectedPostDominance.addEdge(1, 1);
    expectedPostDominance.addEdge(2, 1);
    expectedPostDominance.addEdge(8, 1);
    expectedPostDominance.addEdge(9, 1);
    expectedPostDominance.addEdge(2, 2);
    expectedPostDominance.addEdge(8, 2);
    expectedPostDominance.addEdge(9, 2);
    expectedPostDominance.addEdge(3, 3);
    expectedPostDominance.addEdge(8, 3);
    expectedPostDominance.addEdge(9, 3);
    expectedPostDominance.addEdge(4, 4);
    expectedPostDominance.addEdge(7, 4);
    expectedPostDominance.addEdge(8, 4);
    expectedPostDominance.addEdge(9, 4);
    expectedPostDominance.addEdge(5, 5);
    expectedPostDominance.addEdge(7, 5);
    expectedPostDominance.addEdge(8, 5);
    expectedPostDominance.addEdge(9, 5);
    expectedPostDominance.addEdge(6, 6);
    expectedPostDominance.addEdge(7, 6);
    expectedPostDominance.addEdge(8, 6);
    expectedPostDominance.addEdge(9, 6);
    expectedPostDominance.addEdge(7, 7);
    expectedPostDominance.addEdge(8, 7);
    expectedPostDominance.addEdge(9, 7);
    expectedPostDominance.addEdge(8, 8);
    expectedPostDominance.addEdge(9, 8);
    expectedPostDominance.addEdge(9, 9);
    compareGraphs(postDominance.getGraph(), expectedPostDominance);

    // Test StrictDominance
    Graph<Integer, DefaultEdge> expectedStrictDominance =
        new DefaultDirectedGraph<Integer, DefaultEdge>(DefaultEdge.class);
    IntStream.rangeClosed(1, 9).forEach(i -> expectedStrictDominance.addVertex(i));
    expectedStrictDominance.addEdge(1, 2);
    expectedStrictDominance.addEdge(1, 3);
    expectedStrictDominance.addEdge(2, 3);
    expectedStrictDominance.addEdge(1, 4);
    expectedStrictDominance.addEdge(2, 4);
    expectedStrictDominance.addEdge(1, 5);
    expectedStrictDominance.addEdge(2, 5);
    expectedStrictDominance.addEdge(4, 5);
    expectedStrictDominance.addEdge(1, 6);
    expectedStrictDominance.addEdge(2, 6);
    expectedStrictDominance.addEdge(4, 6);
    expectedStrictDominance.addEdge(1, 7);
    expectedStrictDominance.addEdge(2, 7);
    expectedStrictDominance.addEdge(4, 7);
    expectedStrictDominance.addEdge(1, 8);
    expectedStrictDominance.addEdge(2, 8);
    expectedStrictDominance.addEdge(1, 9);
    expectedStrictDominance.addEdge(2, 9);
    expectedStrictDominance.addEdge(8, 9);
    compareGraphs(strictDominance.getGraph(), expectedStrictDominance);

    // Test StrictPostDominance
    Graph<Integer, DefaultEdge> expectedStrictPostDominance =
        new DefaultDirectedGraph<Integer, DefaultEdge>(DefaultEdge.class);
    IntStream.rangeClosed(1, 9).forEach(i -> expectedStrictPostDominance.addVertex(i));
    expectedStrictPostDominance.addEdge(2, 1);
    expectedStrictPostDominance.addEdge(8, 1);
    expectedStrictPostDominance.addEdge(9, 1);
    expectedStrictPostDominance.addEdge(8, 2);
    expectedStrictPostDominance.addEdge(9, 2);
    expectedStrictPostDominance.addEdge(8, 3);
    expectedStrictPostDominance.addEdge(9, 3);
    expectedStrictPostDominance.addEdge(7, 4);
    expectedStrictPostDominance.addEdge(8, 4);
    expectedStrictPostDominance.addEdge(9, 4);
    expectedStrictPostDominance.addEdge(7, 5);
    expectedStrictPostDominance.addEdge(8, 5);
    expectedStrictPostDominance.addEdge(9, 5);
    expectedStrictPostDominance.addEdge(7, 6);
    expectedStrictPostDominance.addEdge(8, 6);
    expectedStrictPostDominance.addEdge(9, 6);
    expectedStrictPostDominance.addEdge(8, 7);
    expectedStrictPostDominance.addEdge(9, 7);
    expectedStrictPostDominance.addEdge(9, 8);
    compareGraphs(strictPostDominance.getGraph(), expectedStrictPostDominance);

    // Test ImmediateDominance
    Graph<Integer, DefaultEdge> expectedImmediateDominance =
        new DefaultDirectedGraph<Integer, DefaultEdge>(DefaultEdge.class);
    IntStream.rangeClosed(1, 9).forEach(i -> expectedImmediateDominance.addVertex(i));
    expectedImmediateDominance.addEdge(1, 2);
    expectedImmediateDominance.addEdge(2, 3);
    expectedImmediateDominance.addEdge(2, 4);
    expectedImmediateDominance.addEdge(4, 5);
    expectedImmediateDominance.addEdge(4, 6);
    expectedImmediateDominance.addEdge(4, 7);
    expectedImmediateDominance.addEdge(2, 8);
    expectedImmediateDominance.addEdge(8, 9);
    compareGraphs(immediateDominance.getGraph(), expectedImmediateDominance);

    // Test ImmediatePostDominance
    Graph<Integer, DefaultEdge> expectedImmediatePostDominance =
        new DefaultDirectedGraph<Integer, DefaultEdge>(DefaultEdge.class);
    IntStream.rangeClosed(1, 9).forEach(i -> expectedImmediatePostDominance.addVertex(i));
    expectedImmediatePostDominance.addEdge(2, 1);
    expectedImmediatePostDominance.addEdge(8, 2);
    expectedImmediatePostDominance.addEdge(8, 3);
    expectedImmediatePostDominance.addEdge(7, 4);
    expectedImmediatePostDominance.addEdge(7, 5);
    expectedImmediatePostDominance.addEdge(7, 6);
    expectedImmediatePostDominance.addEdge(8, 7);
    expectedImmediatePostDominance.addEdge(9, 8);
    compareGraphs(immediatePostDominance.getGraph(), expectedImmediatePostDominance);

    // Test ControlDependency
    Graph<Integer, DefaultEdge> expectedControlDependency =
        new DefaultDirectedGraph<Integer, DefaultEdge>(DefaultEdge.class);
    expectedControlDependency.addVertex(ControlDependency.ROOT_INDEX);
    IntStream.rangeClosed(1, 9).forEach(i -> expectedControlDependency.addVertex(i));
    expectedControlDependency.addEdge(2, 3);
    expectedControlDependency.addEdge(2, 4);
    expectedControlDependency.addEdge(4, 5);
    expectedControlDependency.addEdge(4, 6);
    expectedControlDependency.addEdge(2, 7);
    expectedControlDependency.addEdge(-1, 1);
    expectedControlDependency.addEdge(-1, 2);
    expectedControlDependency.addEdge(-1, 8);
    expectedControlDependency.addEdge(-1, 9);
    compareGraphs(controlDependency.getGraph(), expectedControlDependency);
  }

  @Test
  public void cfg4()
      throws IOException, InvalidClassFileException, InterruptedException, ExportException {
    // https://en.wikipedia.org/wiki/Dominator_(graph_theory)
    AbstractGraph<Integer, DefaultEdge> cfg =
        new DefaultDirectedGraph<Integer, DefaultEdge>(DefaultEdge.class);
    IntStream.rangeClosed(1, 6).forEach(i -> cfg.addVertex(i));
    cfg.addEdge(1, 2);
    cfg.addEdge(2, 3);
    cfg.addEdge(2, 4);
    cfg.addEdge(2, 6);
    cfg.addEdge(3, 5);
    cfg.addEdge(4, 5);
    cfg.addEdge(5, 2);

    Dominance dominance = new Dominance(cfg, 1);
    PostDominance postDominance = new PostDominance(cfg, 1);
    StrictDominance strictDominance = new StrictDominance(dominance);
    StrictPostDominance strictPostDominance = new StrictPostDominance(postDominance);
    ImmediateDominance immediateDominance = new ImmediateDominance(strictDominance);
    ImmediatePostDominance immediatePostDominance = new ImmediatePostDominance(strictPostDominance);
    ControlDependency controlDependency = new ControlDependency(cfg, immediatePostDominance);

    plot(cfg);
    plot(dominance);
    plot(postDominance);
    plot(strictDominance);
    plot(strictPostDominance);
    plot(immediateDominance);
    plot(immediatePostDominance);
    plot(controlDependency);

    //		System.out.println(javaSourceFromGraph(dominance));
    //		System.out.println(javaSourceFromGraph(postDominance));
    //		System.out.println(javaSourceFromGraph(strictDominance));
    //		System.out.println(javaSourceFromGraph(strictPostDominance));
    //		System.out.println(javaSourceFromGraph(immediateDominance));
    //		System.out.println(javaSourceFromGraph(immediatePostDominance));
    //		System.out.println(javaSourceFromGraph(controlDependency));

    // Test Dominance
    Graph<Integer, DefaultEdge> expectedDominance =
        new DefaultDirectedGraph<Integer, DefaultEdge>(DefaultEdge.class);
    IntStream.rangeClosed(1, 6).forEach(i -> expectedDominance.addVertex(i));
    expectedDominance.addEdge(1, 1);
    expectedDominance.addEdge(1, 2);
    expectedDominance.addEdge(2, 2);
    expectedDominance.addEdge(1, 3);
    expectedDominance.addEdge(2, 3);
    expectedDominance.addEdge(3, 3);
    expectedDominance.addEdge(1, 4);
    expectedDominance.addEdge(2, 4);
    expectedDominance.addEdge(4, 4);
    expectedDominance.addEdge(1, 5);
    expectedDominance.addEdge(2, 5);
    expectedDominance.addEdge(5, 5);
    expectedDominance.addEdge(1, 6);
    expectedDominance.addEdge(2, 6);
    expectedDominance.addEdge(6, 6);
    compareGraphs(dominance.getGraph(), expectedDominance);

    // Test PostDominance
    Graph<Integer, DefaultEdge> expectedPostDominance =
        new DefaultDirectedGraph<Integer, DefaultEdge>(DefaultEdge.class);
    IntStream.rangeClosed(1, 6).forEach(i -> expectedPostDominance.addVertex(i));
    expectedPostDominance.addEdge(1, 1);
    expectedPostDominance.addEdge(2, 1);
    expectedPostDominance.addEdge(6, 1);
    expectedPostDominance.addEdge(2, 2);
    expectedPostDominance.addEdge(6, 2);
    expectedPostDominance.addEdge(2, 3);
    expectedPostDominance.addEdge(3, 3);
    expectedPostDominance.addEdge(5, 3);
    expectedPostDominance.addEdge(6, 3);
    expectedPostDominance.addEdge(2, 4);
    expectedPostDominance.addEdge(4, 4);
    expectedPostDominance.addEdge(5, 4);
    expectedPostDominance.addEdge(6, 4);
    expectedPostDominance.addEdge(2, 5);
    expectedPostDominance.addEdge(5, 5);
    expectedPostDominance.addEdge(6, 5);
    expectedPostDominance.addEdge(6, 6);
    compareGraphs(postDominance.getGraph(), expectedPostDominance);

    // Test StrictDominance
    Graph<Integer, DefaultEdge> expectedStrictDominance =
        new DefaultDirectedGraph<Integer, DefaultEdge>(DefaultEdge.class);
    IntStream.rangeClosed(1, 6).forEach(i -> expectedStrictDominance.addVertex(i));
    expectedStrictDominance.addEdge(1, 2);
    expectedStrictDominance.addEdge(1, 3);
    expectedStrictDominance.addEdge(2, 3);
    expectedStrictDominance.addEdge(1, 4);
    expectedStrictDominance.addEdge(2, 4);
    expectedStrictDominance.addEdge(1, 5);
    expectedStrictDominance.addEdge(2, 5);
    expectedStrictDominance.addEdge(1, 6);
    expectedStrictDominance.addEdge(2, 6);
    compareGraphs(strictDominance.getGraph(), expectedStrictDominance);

    // Test StrictPostDominance
    Graph<Integer, DefaultEdge> expectedStrictPostDominance =
        new DefaultDirectedGraph<Integer, DefaultEdge>(DefaultEdge.class);
    IntStream.rangeClosed(1, 6).forEach(i -> expectedStrictPostDominance.addVertex(i));
    expectedStrictPostDominance.addEdge(2, 1);
    expectedStrictPostDominance.addEdge(6, 1);
    expectedStrictPostDominance.addEdge(6, 2);
    expectedStrictPostDominance.addEdge(2, 3);
    expectedStrictPostDominance.addEdge(5, 3);
    expectedStrictPostDominance.addEdge(6, 3);
    expectedStrictPostDominance.addEdge(2, 4);
    expectedStrictPostDominance.addEdge(5, 4);
    expectedStrictPostDominance.addEdge(6, 4);
    expectedStrictPostDominance.addEdge(2, 5);
    expectedStrictPostDominance.addEdge(6, 5);
    compareGraphs(strictPostDominance.getGraph(), expectedStrictPostDominance);

    // Test ImmediateDominance
    Graph<Integer, DefaultEdge> expectedImmediateDominance =
        new DefaultDirectedGraph<Integer, DefaultEdge>(DefaultEdge.class);
    IntStream.rangeClosed(1, 6).forEach(i -> expectedImmediateDominance.addVertex(i));
    expectedImmediateDominance.addEdge(1, 2);
    expectedImmediateDominance.addEdge(2, 3);
    expectedImmediateDominance.addEdge(2, 4);
    expectedImmediateDominance.addEdge(2, 5);
    expectedImmediateDominance.addEdge(2, 6);
    compareGraphs(immediateDominance.getGraph(), expectedImmediateDominance);

    // Test ImmediatePostDominance
    Graph<Integer, DefaultEdge> expectedImmediatePostDominance =
        new DefaultDirectedGraph<Integer, DefaultEdge>(DefaultEdge.class);
    IntStream.rangeClosed(1, 6).forEach(i -> expectedImmediatePostDominance.addVertex(i));
    expectedImmediatePostDominance.addEdge(2, 1);
    expectedImmediatePostDominance.addEdge(6, 2);
    expectedImmediatePostDominance.addEdge(5, 3);
    expectedImmediatePostDominance.addEdge(5, 4);
    expectedImmediatePostDominance.addEdge(2, 5);
    compareGraphs(immediatePostDominance.getGraph(), expectedImmediatePostDominance);

    // Test ControlDependency
    Graph<Integer, DefaultEdge> expectedControlDependency =
        new DefaultDirectedGraph<Integer, DefaultEdge>(DefaultEdge.class);
    expectedControlDependency.addVertex(ControlDependency.ROOT_INDEX);
    IntStream.rangeClosed(1, 6).forEach(i -> expectedControlDependency.addVertex(i));
    expectedControlDependency.addEdge(2, 3);
    expectedControlDependency.addEdge(2, 4);
    expectedControlDependency.addEdge(2, 5);
    expectedControlDependency.addEdge(-1, 1);
    expectedControlDependency.addEdge(-1, 2);
    expectedControlDependency.addEdge(-1, 6);
    compareGraphs(controlDependency.getGraph(), expectedControlDependency);
  }

  private static void plot(SlicerGraph<Integer> slicerGraph)
      throws IOException, InterruptedException, InvalidClassFileException, ExportException {
    slicerGraph.writePlot(Path.of("/tmp/slicer"), slicerGraph.getClass().getSimpleName() + ".png");
  }

  private static void plot(Graph<Integer, DefaultEdge> graph)
      throws IOException, InterruptedException {
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
    dotExporter.exportGraph(graph, writer);
    Utilities.dotWriteToFile("/tmp/slicer/Graph.png", writer.toString());
  }

  // Used to generate tests from graphs. DO NOT REMOVE
  @SuppressWarnings("unused")
  private static String javaSourceFromGraph(SlicerGraph<Integer> slicerGraph)
      throws IOException, InvalidClassFileException {
    StringWriter writer = new StringWriter();

    String objectVarName = slicerGraph.getClass().getSimpleName();
    objectVarName = objectVarName.substring(0, 1).toLowerCase() + objectVarName.substring(1);

    String expectedObjectVarName = "expected" + slicerGraph.getClass().getSimpleName();

    writer.append(String.format("// Test %s\n", slicerGraph.getClass().getSimpleName()));
    writer.append(
        String.format(
            "Graph<Integer, DefaultEdge> %s = new DefaultDirectedGraph<Integer, DefaultEdge>(DefaultEdge.class);\n",
            expectedObjectVarName));

    if (slicerGraph instanceof ControlDependency) {
      writer.append(
          String.format("%s.addVertex(ControlDependency.ROOT_INDEX);\n", expectedObjectVarName));
    }

    final Graph<Integer, DefaultEdge> graph = slicerGraph.getGraph();
    final Set<Integer> vertexSet = new HashSet<>(graph.vertexSet());
    if (slicerGraph instanceof ControlDependency) {
      vertexSet.remove(ControlDependency.ROOT_INDEX);
    }
    final Integer min = vertexSet.stream().mapToInt(i -> i).boxed().min(Integer::compareTo).get();
    final Integer max = vertexSet.stream().mapToInt(i -> i).boxed().max(Integer::compareTo).get();
    writer.append(
        String.format(
            "IntStream.rangeClosed(%s, %s).forEach(i -> %s.addVertex(i));\n",
            min, max, expectedObjectVarName));

    for (DefaultEdge edge : graph.edgeSet()) {
      final Integer edgeSource = graph.getEdgeSource(edge);
      final Integer edgeTarget = graph.getEdgeTarget(edge);
      writer.append(
          String.format("%s.addEdge(%s, %s);\n", expectedObjectVarName, edgeSource, edgeTarget));
    }
    writer.append(
        String.format("compareGraphs(%s.getGraph(), %s);\n", objectVarName, expectedObjectVarName));

    return writer.toString();
  }

  private static void compareGraphs(
      Graph<Integer, DefaultEdge> graph1, Graph<Integer, DefaultEdge> graph2) {
    assertTrue(graph1.vertexSet().equals(graph2.vertexSet()));
    assertEquals(graph1.edgeSet().size(), graph2.edgeSet().size());

    for (DefaultEdge edge : graph1.edgeSet()) {
      final Integer edgeSource = graph1.getEdgeSource(edge);
      final Integer edgeTarget = graph1.getEdgeTarget(edge);

      assertTrue(graph2.containsEdge(edgeSource, edgeTarget));
    }
  }
}
