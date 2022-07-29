package de.uniks.vs.methodresourceprediction.slicer.test;

import com.ibm.wala.shrike.shrikeCT.InvalidClassFileException;
import de.uniks.vs.methodresourceprediction.slicer.Slicer;
import java.io.IOException;
import org.apache.commons.cli.ParseException;
import org.apache.commons.codec.DecoderException;
import org.jgrapht.io.ExportException;

public class MyInstrumenter {
  //	@Test
  public void instrumenter()
      throws IOException, ParseException, IllegalStateException, InvalidClassFileException,
          DecoderException, InterruptedException, ExportException {
    //		// --inputJar ../EvaluationPrograms.jar --methodSignature --mainClass
    //		// --instructionIndexes 8 --outputJar generated/2_50/sliced.jar
    //		Instrumenter instrumenter = new Instrumenter("../", "../EvaluationPrograms.jar",
    // "generated/sliced.jar",
    //				"LSleep;.f([Ljava/lang/String;)V", "Sleep", "generated/", null);
    //
    //		ControlFlow controlFlow = new ControlFlow("../EvaluationPrograms.jar",
    // "LSleep;.f([Ljava/lang/String;)V");
    //
    //		instrumenter.instrument(, Arrays.asList(0), Arrays.asList(0), Collections.emptyMap(),
    //				Collections.emptySet());
    //		instrumenter.finalize();

    String argString =
        "--inputJar ../EvaluationPrograms.jar --methodSignature \"LSleep;.f([Ljava/lang/String;)V\" --mainClass \"Sleep\" --instructionIndexes 8 --outputJar generated/2_50/sliced.jar";
    String[] args = argString.split(" ");

    Slicer slicer = new Slicer();
    slicer.parseArgs(args);
    //		mySlicer.setExportFormat(null);
    slicer.setVerbose(true);
    slicer.makeSlicedFile();
  }
}
