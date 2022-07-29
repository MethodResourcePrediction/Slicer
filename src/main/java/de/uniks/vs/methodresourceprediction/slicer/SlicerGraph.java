package de.uniks.vs.methodresourceprediction.slicer;

import com.ibm.wala.shrike.shrikeCT.InvalidClassFileException;
import de.uniks.vs.methodresourceprediction.utils.Utilities;
import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import org.jgrapht.Graph;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.io.ComponentNameProvider;
import org.jgrapht.io.DOTExporter;
import org.jgrapht.io.ExportException;
import org.jgrapht.io.GraphExporter;

// TODO Rewrite this into an abstract class with a method generating the exporter for graph creation
public abstract class SlicerGraph<T> {
  public abstract Graph<T, DefaultEdge> getGraph() throws IOException, InvalidClassFileException;

  protected GraphExporter<T, DefaultEdge> getExporter(
      ComponentNameProvider<T> vertexIdProvider, ComponentNameProvider<T> vertexLabelProvider) {
    DOTExporter<T, DefaultEdge> exporter =
        new DOTExporter<>(vertexIdProvider, vertexLabelProvider, null);
    exporter.putGraphAttribute("label", this.getClass().getSimpleName());
    exporter.putGraphAttribute("labelloc", "t");
    exporter.putGraphAttribute("fontsize", "30");
    return exporter;
  }

  protected String getExporterGraphString(
      ComponentNameProvider<T> vertexIdProvider, ComponentNameProvider<T> vertexLabelProvider)
      throws ExportException, IOException, InvalidClassFileException {
    GraphExporter<T, DefaultEdge> exporter = getExporter(vertexIdProvider, vertexLabelProvider);
    Writer writer = new StringWriter();
    exporter.exportGraph(getGraph(), writer);
    return writer.toString();
  }

  public void writePlot(Path dir, String fileName)
      throws IOException, InterruptedException, InvalidClassFileException, ExportException {
    final Path path = Path.of(dir.toString(), fileName);
    Utilities.dotWriteToFile(path.toString(), dotPrint());
  }

  public void showPlot(Path dir, String fileName)
      throws IOException, InterruptedException, InvalidClassFileException, ExportException {
    Utilities.dotShow(dir, fileName, dotPrint());
  }

  public void showPlot(Path dir)
      throws IOException, InterruptedException, InvalidClassFileException, ExportException {
    Utilities.dotShow(dir, dotPrint());
  }

  public void showPlot()
      throws IOException, InterruptedException, InvalidClassFileException, ExportException {
    final Path dir = Files.createTempDirectory("slicer-");
    showPlot(dir);
  }

  protected abstract String dotPrint()
      throws IOException, InvalidClassFileException, ExportException;
}
