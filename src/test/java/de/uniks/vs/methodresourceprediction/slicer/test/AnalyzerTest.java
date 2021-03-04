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
import java.util.stream.IntStream;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class AnalyzerTest {
  public boolean randomBoolean() {
    Random random = new Random();
    double d = random.nextDouble();
    if (d < 0.5) {
      d += 0.1;
    }
    double e = random.nextDouble();
    return e > 0.5;
  }

  @Test
  public void testRandomSlice()
      throws IOException, InvalidClassFileException, ExportException, InterruptedException {
    Slicer slicer = new Slicer();
    slicer.setInputJar("build/libs/slicer-1.0.0-SNAPSHOT-tests.jar");
    slicer.setMethodSignature(
        "Lde/uniks/vs/methodresourceprediction/slicer/test/AnalyzerTest;.randomBoolean()Z");
    slicer.setInstructionIndexes(Set.of(21));
    SliceResult sliceResult = slicer.getSliceResult();
    System.out.println(sliceResult);
  }

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

    for (ClassInstrumenter classInstrumenter : Analyzer.getClassInstrumenters(new File(jar))) {
      //      if (!classInstrumenter.getReader().getName().equals("org/mariadb/jdbc/UrlParser")) {
      //        continue;
      //      }
      for (MethodData method : Analyzer.getMethods(classInstrumenter)) {
        String methodSignature =
            classInstrumenter.getReader().getName()
                + "."
                + method.getName()
                + method.getSignature();
        //                methodSignature =
        // "org/mariadb/jdbc/util/DefaultOptions.getOptionName()Ljava/lang/String;";

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

          ExecutorService executor = Executors.newSingleThreadExecutor();
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
            SliceResult sliceResult = submit.get(3, TimeUnit.SECONDS);
            //            SliceResult sliceResult = submit.get(Long.MAX_VALUE, TimeUnit.SECONDS);
            //            slicer.getArgumentDependency().showPlot();
            if (!Objects.isNull(sliceResult)) {
              sliceResults.add(sliceResult);
            }
          } catch (ExecutionException | TimeoutException e) {
            e.printStackTrace();
            System.out.print(": Timeout or Error");
            System.out.flush();
            break;
          }
          System.out.print(", ");
          System.out.flush();
        }
        System.out.println();

        Set<Integer> allInstructionIndexSet = new HashSet<>();
        IntStream.range(0, instructionCount).forEach(allInstructionIndexSet::add);

        for (SliceResult sliceResult1 : sliceResults) {
          for (SliceResult sliceResult2 : sliceResults) {
            if (sliceResult1.equals(sliceResult2)) {
              continue;
            }
            Set<Integer> instructionsToKeep1 = sliceResult1.getInstructionsToKeep();
            Set<Integer> instructionsToKeep2 = sliceResult2.getInstructionsToKeep();

            Set<Integer> intersectionSet = new HashSet<>(instructionsToKeep1);
            intersectionSet.retainAll(instructionsToKeep2);

            Set<Integer> unionSet = new HashSet<>(instructionsToKeep1);
            unionSet.addAll(instructionsToKeep2);

            boolean containsOnlyExitInstructions =
                intersectionSet.stream()
                    .allMatch(
                        i ->
                            instructions[i] instanceof ReturnInstruction
                                || instructions[i] instanceof ThrowInstruction);
            boolean slice1containsOnlyExitInstructions =
                instructionsToKeep1.stream()
                    .allMatch(
                        i ->
                            instructions[i] instanceof ReturnInstruction
                                || instructions[i] instanceof ThrowInstruction);
            boolean slice2containsOnlyExitInstructions =
                instructionsToKeep2.stream()
                    .allMatch(
                        i ->
                            instructions[i] instanceof ReturnInstruction
                                || instructions[i] instanceof ThrowInstruction);

            // Slices have the complete set of method instructions
            if (unionSet.equals(allInstructionIndexSet)) {

              // Slice intersection should only consist out of exit instructions
              if (containsOnlyExitInstructions
                  && !slice1containsOnlyExitInstructions
                  && !slice2containsOnlyExitInstructions) {
                System.out.println(
                    "Independent slices from "
                        + instructionsToKeep1
                        + " and "
                        + instructionsToKeep2);
                System.out.println(sliceResult1.getSlice());
                System.out.println(sliceResult2.getSlice());
                System.out.println();
                System.out.flush();
              }
            }
          }
        }
        System.gc();
      }
      //      break;
    }
  }
}
