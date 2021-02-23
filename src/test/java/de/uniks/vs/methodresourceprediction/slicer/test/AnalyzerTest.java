package de.uniks.vs.methodresourceprediction.slicer.test;

import com.ibm.wala.shrikeBT.IInstruction;
import com.ibm.wala.shrikeBT.MethodData;
import com.ibm.wala.shrikeBT.ReturnInstruction;
import com.ibm.wala.shrikeBT.ThrowInstruction;
import com.ibm.wala.shrikeBT.shrikeCT.ClassInstrumenter;
import com.ibm.wala.shrikeCT.InvalidClassFileException;
import de.uniks.vs.methodresourceprediction.slicer.Analyzer;
import de.uniks.vs.methodresourceprediction.slicer.SliceResult;
import de.uniks.vs.methodresourceprediction.slicer.Slicer;
import org.jgrapht.io.ExportException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class AnalyzerTest {
  @Test
  public void testAnalyzer()
      throws IOException, InvalidClassFileException, ExportException, InterruptedException {
    final String jar = "src/test/resources/mariadb-java-client-2.7.2.jar";
    Set<String> skippedPrefixes =
        Set.of(
            "com/google/gson/internal/bind/util/ISO8601Utils.parse(Ljava/lang/String;Ljava/text/ParsePosition;)Ljava/util/Date;",
            "com/google/gson/stream/JsonReader.doPeek()I",
            "com/google/gson/stream/JsonReader.peekNumber()I");

    //    SliceResult sliceResult3 = Slicer.getSliceResult(jar,
    // "org/mariadb/jdbc/util/DefaultOptions.<clinit>()V", Set.of(8));
    //    sliceResult3.getSlice();

    ExecutorService executor = Executors.newSingleThreadExecutor();

    for (ClassInstrumenter classInstrumenter : Analyzer.getClassInstrumenters(new File(jar))) {
      for (MethodData method : Analyzer.getMethods(classInstrumenter)) {
        String methodSignature =
            classInstrumenter.getReader().getName()
                + "."
                + method.getName()
                + method.getSignature();
        //        methodSignature = "org/mariadb/jdbc/util/DefaultOptions.<clinit>()V";

        if (skippedPrefixes.stream().anyMatch(methodSignature::startsWith)) {
          System.out.println("Skipped: " + methodSignature);
          continue;
        }

        System.out.println(methodSignature);

        Slicer slicer = new Slicer();
        slicer.setInputJar(jar);
        slicer.setMethodSignature(methodSignature);

        List<SliceResult> sliceResults = new ArrayList<>();
        IInstruction[] instructions = slicer.getControlFlow().getMethodData().getInstructions();
        int instructionCount = instructions.length;
        System.out.print("SliceCriterion: ");
        for (int sliceCriterion = 0; sliceCriterion < instructionCount; sliceCriterion++) {
          System.out.print(sliceCriterion);
          System.out.flush();
          slicer.setInstructionIndexes(Set.of(sliceCriterion));

          Future<SliceResult> submit =
              executor.submit(
                  () -> {
                    try {
                      return slicer.getSliceResult();
                    } catch (IOException | InvalidClassFileException e) {
                      e.printStackTrace();
                    }
                    return null;
                  });
          try {
            SliceResult sliceResult = submit.get(10, TimeUnit.SECONDS);
            if (!Objects.isNull(sliceResult)) {
              sliceResults.add(sliceResult);
            }
          } catch (ExecutionException | TimeoutException e) {
            System.out.print(": ");
            System.out.print("Timeout or Error");
          }
          System.out.print(", ");
          System.out.flush();
        }
        System.out.println();

        for (SliceResult sliceResult1 : sliceResults) {
          for (SliceResult sliceResult2 : sliceResults) {
            if (sliceResult1.equals(sliceResult2)) {
              continue;
            }
            Set<Integer> instructionsToKeep1 = sliceResult1.getInstructionsToKeep();
            Set<Integer> instructionsToKeep2 = sliceResult2.getInstructionsToKeep();

            Set<Integer> intersectionSet = new HashSet<>(instructionsToKeep1);
            intersectionSet.retainAll(instructionsToKeep2);

            boolean containsOtherInstructionThanExit =
                intersectionSet.stream()
                    .anyMatch(
                        i -> {
                          if (instructions[i] instanceof ReturnInstruction
                              || instructions[i] instanceof ThrowInstruction) {
                            return false;
                          }
                          return true;
                        });

            if (intersectionSet.isEmpty() || !containsOtherInstructionThanExit) {
              System.out.println(
                  "Independent slices from " + instructionsToKeep1 + " and " + instructionsToKeep2);
              System.out.println(sliceResult1.getSlice());
              System.out.println(sliceResult2.getSlice());
              System.out.println();
              System.out.flush();
            }
          }
        }

        //        break;
      }
      //      break;
    }
  }
}
