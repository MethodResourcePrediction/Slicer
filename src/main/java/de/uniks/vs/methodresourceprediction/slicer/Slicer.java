package de.uniks.vs.methodresourceprediction.slicer;

import com.ibm.wala.shrikeBT.*;
import com.ibm.wala.shrikeBT.Util;
import com.ibm.wala.shrikeCT.InvalidClassFileException;
import de.uniks.vs.methodresourceprediction.slicer.dominance.*;
import de.uniks.vs.methodresourceprediction.slicer.export.SliceWriter.ExportFormat;
import de.uniks.vs.methodresourceprediction.utils.Utilities;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import org.apache.commons.cli.*;
import org.apache.commons.codec.DecoderException;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.io.ExportException;

public class Slicer {
  private String inputJar;
  private String outputJar;
  private String methodSignature;
  private String mainClass;
  private String resultFilePath;
  private Set<Integer> instructionIndexes;
  private ExportFormat exportFormat;
  private String additionalJarsPath;
  private boolean showPlots;

  // Internal graphs
  private ControlFlow controlFlow;
  private ControlDependency controlDependency;
  private BlockDependency blockDependency;
  private DataDependency dataDependency;
  private ArgumentDependency argumentDependency;
  private InitializerDependency initializerDependency;

  // Dominance
  private Dominance dominance;
  private StrictDominance strictDominance;
  private ImmediateDominance immediateDominance;
  // Post Dominance
  private ImmediatePostDominance immediatePostDominance;
  private PostDominance postDominance;
  private StrictPostDominance strictPostDominance;

  private boolean verbose = false;

  public Slicer() {
    this.instructionIndexes = new HashSet<>();
  }

  public static void main(String[] args)
      throws ParseException, IllegalStateException, IOException, InvalidClassFileException,
          DecoderException, InterruptedException, ExportException {
    Slicer mySlicer = new Slicer();
    mySlicer.setVerbose(true);
    mySlicer.parseArgs(args);
    //		mySlicer.setExportFormat(null);
    mySlicer.makeSlicedFile();
  }

  public String makeSlicedFile()
      throws IOException, InvalidClassFileException, IllegalStateException, DecoderException,
          InterruptedException, ExportException {
    ControlFlow controlFlow = getControlFlow();
    ControlDependency controlDependency = getControlDependency();
    BlockDependency blockDependency = getBlockDependency();
    DataDependency dataDependency = getDataDependency();
    ArgumentDependency argumentDependency = getArgumentDependency();
    InitializerDependency initializerDependency = getClassObjectDependency();

    //    Dominance dominance = getDominance();
    //    StrictDominance strictDominance = getStrictDominance();
    //    ImmediateDominance immediateDominance = getImmediateDominance();
    //
    //    PostDominance postDominance = getPostDominance();
    //    StrictPostDominance strictPostDominance = getStrictPostDominance();
    //    ImmediatePostDominance immediatePostDominance = getImmediatePostDominance();

    Map<Integer, Set<Integer>> varIndexesToRenumber = argumentDependency.getVarIndexesToRenumber();
    Set<Integer> instructionsInCycles = controlFlow.getInstructionsInCycles();

    if (showPlots) {
      showPlots();
    }

    Set<Integer> instructionIndexesToKeep =
        getInstructionIndexesToKeep(
            controlFlow,
            controlDependency,
            blockDependency,
            argumentDependency,
            dataDependency,
            initializerDependency);
    Set<Integer> instructionIndexesToIgnore =
        getInstructionIndexesToIgnore(
            controlFlow,
            controlDependency,
            argumentDependency,
            dataDependency,
            initializerDependency);
    Map<Integer, Integer> instructionPopMap = getInstructionPopMap();

    //		System.out.println(getSliceResult());

    // Instrument a new program with a modified method which we analyze
    Instrumenter instrumenter =
        new Instrumenter(
            additionalJarsPath,
            inputJar,
            outputJar,
            methodSignature,
            mainClass,
            resultFilePath,
            exportFormat);
    instrumenter.setVerbose(verbose);
    instrumenter.instrument(
        instructionIndexes,
        instructionIndexesToKeep,
        instructionIndexesToIgnore,
        instructionPopMap,
        varIndexesToRenumber,
        instructionsInCycles);
    instrumenter.finalize();

    return outputJar;
  }

  public void showPlots()
      throws IOException, InterruptedException, InvalidClassFileException, ExportException {
    showPlots(
        getControlFlow(),
        getControlDependency(),
        getBlockDependency(),
        getArgumentDependency(),
        getDataDependency(),
        getClassObjectDependency());
  }

  public static void showPlots(
      ControlFlow controlFlow,
      ControlDependency controlDependency,
      BlockDependency blockDependency,
      ArgumentDependency argumentDependency,
      DataDependency dataDependency,
      InitializerDependency initializerDependency)
      throws IOException, InterruptedException, InvalidClassFileException, ExportException {
    final Path dir = Files.createTempDirectory("slicer-");
    controlFlow.showPlot(dir);
    controlDependency.showPlot(dir);
    blockDependency.showPlot(dir);
    dataDependency.showPlot(dir);
    argumentDependency.showPlot(dir);
    initializerDependency.showPlot(dir);
  }

  public Set<Integer> getInstructionIndexesToIgnore()
      throws IOException, InvalidClassFileException {
    return getInstructionIndexesToIgnore(
        getControlFlow(),
        getControlDependency(),
        getArgumentDependency(),
        getDataDependency(),
        getClassObjectDependency());
  }

  public Set<Integer> getInstructionIndexesToIgnore(
      ControlFlow controlFlow,
      ControlDependency controlDependency,
      ArgumentDependency argumentDependency,
      DataDependency dataDependency,
      InitializerDependency initializerDependency)
      throws IOException, InvalidClassFileException {
    Set<Integer> instructionIndexesToKeep =
        getInstructionIndexesToKeep(
            controlFlow,
            controlDependency,
            blockDependency,
            argumentDependency,
            dataDependency,
            initializerDependency);
    Set<Integer> instructionIndexesToIgnore = new HashSet<>();

    IInstruction[] instructions = controlFlow.getMethodData().getInstructions();
    for (int instructionIndex : instructionIndexesToKeep) {
      IInstruction iInstruction = instructions[instructionIndex];
      if (iInstruction instanceof IConditionalBranchInstruction) {
        IConditionalBranchInstruction instruction = (IConditionalBranchInstruction) iInstruction;
        int targetIndex = instruction.getTarget();
        if (!instructionIndexesToKeep.contains(targetIndex)) {
          instructionIndexesToIgnore.add(targetIndex);
        }
      }
      if (iInstruction instanceof GotoInstruction) {
        GotoInstruction instruction = (GotoInstruction) iInstruction;
        int targetIndex = instruction.getLabel();
        if (!instructionIndexesToKeep.contains(targetIndex)) {
          instructionIndexesToIgnore.add(targetIndex);
        }
      }
    }

    return instructionIndexesToIgnore;
  }

  public Set<Integer> getInstructionIndexesToKeep() throws IOException, InvalidClassFileException {
    return getInstructionIndexesToKeep(
        getControlFlow(),
        getControlDependency(),
        getBlockDependency(),
        getArgumentDependency(),
        getDataDependency(),
        getClassObjectDependency());
  }

  public Set<Integer> getInstructionIndexesToKeep(
      ControlFlow controlFlow,
      ControlDependency controlDependency,
      BlockDependency blockDependency,
      ArgumentDependency argumentDependency,
      DataDependency dataDependency,
      InitializerDependency initializerDependency)
      throws IOException, InvalidClassFileException {
    IInstruction[] instructions = controlFlow.getMethodData().getInstructions();
    Set<Integer> indexesToKeep = new HashSet<>();

    // Keep the return values
    for (int index = 0; index < instructions.length; index++) {
      IInstruction instruction = instructions[index];
      if (instruction instanceof ReturnInstruction || instruction instanceof ThrowInstruction) {
        slice(
            controlFlow,
            controlDependency,
            blockDependency,
            argumentDependency,
            dataDependency,
            initializerDependency,
            indexesToKeep,
            index);
      }
    }

    // Add all provided indexes to the slice
    for (int instructionIndex : getInstructionIndexes()) {
      slice(
          controlFlow,
          controlDependency,
          blockDependency,
          argumentDependency,
          dataDependency,
          initializerDependency,
          indexesToKeep,
          instructionIndex);
    }

    // Check if the slice will depend on the parameters (can be affected by
    // recursive invocations)
    boolean dependendOnParameters = false;
    for (int indexToKeep : indexesToKeep) {
      dependendOnParameters |= dataDependency.hasDependencyToMethodParameter(indexToKeep);
    }

    // Search for recursive invoke instructions
    Set<Integer> recursiveInvokeInstructions = new HashSet<>();
    for (int instructionIndex = 0; instructionIndex < instructions.length; instructionIndex++) {
      IInstruction iInstruction = instructions[instructionIndex];
      if (iInstruction instanceof IInvokeInstruction) {
        IInvokeInstruction instruction = (IInvokeInstruction) iInstruction;
        if (Utilities.isRecursiveInvokeInstruction(controlFlow.getMethodData(), instruction)) {
          recursiveInvokeInstructions.add(instructionIndex);
          break;
        }
      }
    }

    // If there are recursive invoke instructions, add them to the instruction index
    // set and also keep all return statements (possible exit point for recursive
    // invoke instructions)
    if (dependendOnParameters && !recursiveInvokeInstructions.isEmpty()) {
      for (int recursiveInvokeInstructionIndex : recursiveInvokeInstructions) {
        // Slice the recursive invoke instruction
        slice(
            controlFlow,
            controlDependency,
            blockDependency,
            argumentDependency,
            dataDependency,
            initializerDependency,
            indexesToKeep,
            recursiveInvokeInstructionIndex);

        // Include all return statements that are reachable from any instruction if
        // they are already kept in "indexesToKeep"
        for (int index = 0; index < recursiveInvokeInstructionIndex; index++) {
          IInstruction instruction = instructions[index];
          if (!(instruction instanceof ReturnInstruction)
              && !(instruction instanceof ThrowInstruction)) {
            continue;
          }
          for (DefaultEdge incomingEdge : controlFlow.getGraph().incomingEdgesOf(index)) {
            Integer instructionSourceIndex = controlFlow.getGraph().getEdgeSource(incomingEdge);
            if (!indexesToKeep.contains(instructionSourceIndex)) {
              continue;
            }
            slice(
                controlFlow,
                controlDependency,
                blockDependency,
                argumentDependency,
                dataDependency,
                initializerDependency,
                indexesToKeep,
                index);
          }
        }
      }
    }

    // Concurrent modification handling since we iterate through indexesToKeep while
    // changing inside slice(...)
    Set<Integer> indexesToKeep2 = new HashSet<>();
    // Check if there is a data dependency to method parameters. If there is
    // further a recursive method call, include it into slicing
    if (dependendOnParameters) {
      // Add all indexes where recursive invoke instructions are performed
      for (int recursiveInvokeInstructionIndex : recursiveInvokeInstructions) {
        //				if (!indexesToKeep.contains(recursiveInvokeInstructionIndex)) {
        //					continue;
        //				}
        slice(
            controlFlow,
            controlDependency,
            blockDependency,
            argumentDependency,
            dataDependency,
            initializerDependency,
            indexesToKeep2,
            recursiveInvokeInstructionIndex);
      }
    }
    indexesToKeep.addAll(indexesToKeep2);
    return indexesToKeep;
  }

  public Map<Integer, Integer> getInstructionPopMap()
      throws IOException, InvalidClassFileException {
    // Some instructions will leave an element on the stack without being processed
    // by others (because they are removed for the slice). For example:
    // _____ 5: BinaryOp(I,mul)
    // _____ 6: SKIPPED Conversion(I,J)
    // _____ 7: SKIPPED Invoke(STATIC,Ljava/lang/Thread;,sleep,(J)V)
    // _____ 8: Goto(10)
    // The result of instruction 5 would normally consumed by instruction 7 (but
    // is skipped and therefore not in the slice).
    // For that, we first need to remember which instruction pushed how many
    // elements. Second, as long as there are skipped instructions, count a stack
    // variable to find out how many elements were left "behind" (1 or 2 are only
    // possible). As a third and last step, after the "skipped" instructions end,
    // the previously kept instruction index is stored together with the number of
    // elements to pop.
    // Like that, we can archive a consistent stack size slice

    Set<Integer> instructionIndexesToKeep = getInstructionIndexesToKeep();
    IInstruction[] instructions = getControlFlow().getMethodData().getInstructions();
    ExceptionHandler[][] exceptionHandlers = getControlFlow().getMethodData().getHandlers();
    Map<Integer, Set<String>> instructionExceptions =
        Utilities.getInstructionExceptions(instructions, exceptionHandlers);

    // If there is a partial block (not all instructions are kept), the stack
    // must be corrected.
    Map<Integer, Integer> instructionPopAfterMap = new HashMap<>();
    for (Block block : getBlockDependency().getBlocks()) {
      List<Integer> blockInstructionIndexes = block.getInstructionIndexes();
      blockInstructionIndexes.retainAll(instructionIndexesToKeep);

      // Simulate stack execution
      Stack<Integer> stack = new Stack<>();
      for (int blockInstructionIndex : blockInstructionIndexes) {
        // Iterate all instructions and build the control flow
        IInstruction instruction = instructions[blockInstructionIndex];

        // Exception handling
        Set<String> instructionException =
            instructionExceptions.getOrDefault(blockInstructionIndex, Collections.emptySet());
        int pushedExceptionCount = instructionException.size();

        for (int pushedException = 0; pushedException < pushedExceptionCount; pushedException++) {
          stack.push(-1 * blockInstructionIndex);
        }

        int pushedSize = Utilities.getPushedSize(instruction);
        int poppedSize = Utilities.getPoppedSize(instruction);

        // TODO That is maybe error-prone handling dup(2,0) is pain as hell
        if (instruction instanceof DupInstruction) {
          int topmostStackInstructionIndex = stack.elementAt(stack.size() - 1);
          IInstruction lastPushedInstruction = instructions[topmostStackInstructionIndex];
          String pushedType = lastPushedInstruction.getPushedType(null);
          int wordSize;
          if (lastPushedInstruction instanceof DupInstruction) {
            wordSize = ((DupInstruction) lastPushedInstruction).getSize();
          } else {
            wordSize = Util.getWordSize(pushedType);
          }
          int poppedSizeForDupInstruction =
              Utilities.getPoppedSizeForDupInstruction((DupInstruction) instruction, wordSize == 1);
          pushedSize = 2 * poppedSizeForDupInstruction;
          poppedSize = poppedSizeForDupInstruction;
        }

        // Simulate stack execution
        for (int popIteration = 0; popIteration < poppedSize; popIteration++) {
          stack.pop();
        }
        for (int pushIteration = 0; pushIteration < pushedSize; pushIteration++) {
          stack.push(blockInstructionIndex);
        }
      }

      // The remaining elements on the stack are the ones to be popped
      int popSize = 0;
      while (stack.size() > 0) {
        int pushedSize = stack.pop();
        // TODO There is at least one issue here when a remaining dup-instruction
        //  element should be popped. The instruction pushed size will not be the number
        //  of elements on the stack. Is 1 to use here always correct?
        popSize += 1;
        //				popSize += pushedSize;
        //				popSize += Utilities.getPushedSize(instructions[stackInstructionIndex]);
      }
      if (popSize > 0) {
        instructionPopAfterMap.put(block.getHighestIndex(), popSize);
      }
    }
    return instructionPopAfterMap;
  }

  private void slice(
      ControlFlow controlFlow,
      ControlDependency controlDependency,
      BlockDependency blockDependency,
      ArgumentDependency argumentDependency,
      DataDependency dataDependency,
      InitializerDependency initializerDependency,
      Set<Integer> dependendInstructions,
      int index)
      throws IOException, InvalidClassFileException {
    if (index < 0) {
      // We cannot slice indexes which represent optional "this" (-1) or method
      // arguments (-2, -3, ...)
      return;
    }

    boolean addedAny = dependendInstructions.add(index);
    if (!addedAny) {
      return;
    }

    // Add dependent argument instructions
    for (Integer argumentIndex : argumentDependency.getArgumentInstructionIndexes(index)) {
      slice(
          controlFlow,
          controlDependency,
          blockDependency,
          argumentDependency,
          dataDependency,
          initializerDependency,
          dependendInstructions,
          argumentIndex);
    }

    // Add cycle dependencies (goto)
    // If our current instruction is a ConstantInstruction, it cannot be affected by
    // any loop iteration count. This is the only exception for loops here.
    if (!(controlFlow.getMethodData().getInstructions()[index] instanceof ConstantInstruction)) {
      List<List<Integer>> cycles = controlFlow.getCyclesForInstruction(index);
      for (List<Integer> cycle : cycles) {
        // NOTE: Slicing a loop is a bit tricky. Usually, the start and the end of the
        // loop must be kept in order to preserve its functionality. Explicitly, keeping
        // the start of a loop can result in keeping instruction which are part of the
        // loop but expected to be sliced out. Therefore, we assume that the java
        // compiler always generates foot-controlled (is that the correct term?) loops,
        // meaning, that the condition for a loop iteration is realized at the highest
        // instruction index of the whole loop. Hope this will last for future java
        // releases.

        // TODO Usually a loop is compiled by jumping to the end (except for do-while)
        // for the evaluation of the condition. If the condition is not met, it jumps
        // back (in the control flow) to the start of the loop. If
        Integer cycleStartIndex = cycle.get(0);
        if (cycleStartIndex > 0
            && controlFlow.getMethodData().getInstructions()[cycleStartIndex - 1]
                instanceof GotoInstruction) {
          GotoInstruction gotoInstruction =
              (GotoInstruction) controlFlow.getMethodData().getInstructions()[cycleStartIndex - 1];
          // The jump target must definitively in between the loop begin and end.
          if (!controlFlow.inSameCycle(gotoInstruction.getLabel(), cycleStartIndex)) {
            // Coming here means there is a Goto-Instruction directly in front of the loop,
            // but the jump target is somewhere outside the loop. I cannot image any case
            // where this is possible.
            continue;
          }
          slice(
              controlFlow,
              controlDependency,
              blockDependency,
              argumentDependency,
              dataDependency,
              initializerDependency,
              dependendInstructions,
              cycleStartIndex - 1);
        }

        // So, keep end block of loop
        // NOTE: This is usually a GOTO- or ConditionalBranch-Instruction. Is it
        // required to slice the index?
        Integer cycleEndIndex = cycle.get(cycle.size() - 1);
        Integer highestIndex = blockDependency.getBlockForIndex(cycleEndIndex).getHighestIndex();
        slice(
            controlFlow,
            controlDependency,
            blockDependency,
            argumentDependency,
            dataDependency,
            initializerDependency,
            dependendInstructions,
            highestIndex);
      }
    }

    // Consider data dependencies
    for (Integer dataDependentIndex : dataDependency.getDataDependencyInstructions(index)) {
      // If the data dependency is higher and NOT part of the same loop (if any)
      // => ignore it
      if (dataDependentIndex > index && !controlFlow.inSameCycle(index, dataDependentIndex)) {
        continue;
      }
      slice(
          controlFlow,
          controlDependency,
          blockDependency,
          argumentDependency,
          dataDependency,
          initializerDependency,
          dependendInstructions,
          dataDependentIndex);

      // Check transitive data dependency for example thought this (-1)
      // This can be the cast if a class in inherited and the constructor <init> is called on the
      // parent class
      for (Integer transitiveDataDependentIndex :
          dataDependency.getAffectedDataDependencyInstructions(dataDependentIndex)) {
        if (transitiveDataDependentIndex < 0
            || transitiveDataDependentIndex > index
            || initializerDependency
                .getClassInitializerDependencyInstructions(transitiveDataDependentIndex)
                .isEmpty()) {
          continue;
        }
        slice(
            controlFlow,
            controlDependency,
            blockDependency,
            argumentDependency,
            dataDependency,
            initializerDependency,
            dependendInstructions,
            transitiveDataDependentIndex);
      }
    }

    // TODO Why do we not need to consider control dependencies? Implied by the
    // argument dependencies?
    // Consider control dependencies
    for (Integer controlDependentIndex :
        controlDependency.getControlDependencyInstructions(index)) {
      if (controlDependentIndex == ControlDependency.ROOT_INDEX) {
        continue;
      }
      if ((controlFlow.getMethodData().getInstructions()[index] instanceof ConstantInstruction)) {
        // Constant-Instructions are independent of any control dependency
        continue;
      }
      slice(
          controlFlow,
          controlDependency,
          blockDependency,
          argumentDependency,
          dataDependency,
          initializerDependency,
          dependendInstructions,
          controlDependentIndex);
    }

    // Class Object Dependencies
    for (Integer classObjectDependentIndex :
        initializerDependency.getClassInitializerDependencyInstructions(index)) {
      slice(
          controlFlow,
          controlDependency,
          blockDependency,
          argumentDependency,
          dataDependency,
          initializerDependency,
          dependendInstructions,
          classObjectDependentIndex);
    }
  }

  public ControlFlow getControlFlow() throws IOException, InvalidClassFileException {
    if (controlFlow != null) {
      return controlFlow;
    }
    controlFlow = new ControlFlow(inputJar, methodSignature);
    return controlFlow;
  }

  public ControlDependency getControlDependency() throws IOException, InvalidClassFileException {
    if (controlDependency != null) {
      return controlDependency;
    }
    controlDependency = new ControlDependency(getControlFlow(), getImmediatePostDominance());
    return controlDependency;
  }

  public BlockDependency getBlockDependency() throws IOException, InvalidClassFileException {
    if (blockDependency != null) {
      return blockDependency;
    }
    blockDependency = new BlockDependency(getControlFlow());
    return blockDependency;
  }

  public DataDependency getDataDependency() throws IOException, InvalidClassFileException {
    if (dataDependency != null) {
      return dataDependency;
    }
    dataDependency = new DataDependency(getControlFlow());
    return dataDependency;
  }

  public ArgumentDependency getArgumentDependency() throws IOException, InvalidClassFileException {
    if (argumentDependency != null) {
      return argumentDependency;
    }
    argumentDependency = new ArgumentDependency(getControlFlow());
    return argumentDependency;
  }

  public InitializerDependency getClassObjectDependency()
      throws IOException, InvalidClassFileException {
    if (initializerDependency != null) {
      return initializerDependency;
    }
    initializerDependency = new InitializerDependency(getBlockDependency());
    return initializerDependency;
  }

  public Dominance getDominance() throws IOException, InvalidClassFileException {
    if (dominance != null) {
      return dominance;
    }
    dominance = new Dominance(getControlFlow(), 0);
    return dominance;
  }

  public PostDominance getPostDominance() throws IOException, InvalidClassFileException {
    if (postDominance != null) {
      return postDominance;
    }
    postDominance = new PostDominance(getControlFlow(), 0);
    return postDominance;
  }

  public StrictDominance getStrictDominance() throws IOException, InvalidClassFileException {
    if (strictDominance != null) {
      return strictDominance;
    }
    strictDominance = new StrictDominance(getDominance());
    return strictDominance;
  }

  public StrictPostDominance getStrictPostDominance()
      throws IOException, InvalidClassFileException {
    if (strictPostDominance != null) {
      return strictPostDominance;
    }
    strictPostDominance = new StrictPostDominance(getPostDominance());
    return strictPostDominance;
  }

  public ImmediateDominance getImmediateDominance() throws IOException, InvalidClassFileException {
    if (immediateDominance != null) {
      return immediateDominance;
    }
    immediateDominance = new ImmediateDominance(getStrictDominance());
    return immediateDominance;
  }

  public ImmediatePostDominance getImmediatePostDominance()
      throws IOException, InvalidClassFileException {
    if (immediatePostDominance != null) {
      return immediatePostDominance;
    }
    immediatePostDominance = new ImmediatePostDominance(getStrictPostDominance());
    return immediatePostDominance;
  }

  public Map<Integer, Set<Integer>> getVariableIndexesToRenumber()
      throws IOException, InvalidClassFileException {
    return getArgumentDependency().getVarIndexesToRenumber();
  }

  public String getMethodSummary() throws IOException, InvalidClassFileException {
    StringBuilder builder = new StringBuilder();
    IInstruction[] instructions = getControlFlow().getMethodData().getInstructions();
    int padding = (instructions.length / 10) + 1;

    builder.append("\n");
    builder.append("=== Method " + getMethodSignature() + " ===" + "\n");
    for (int index = 0; index < instructions.length; index++) {
      IInstruction instruction = instructions[index];

      String str = String.format("%" + padding + "s", index);
      builder.append(str + ": " + instruction + "\n");
    }
    return builder.toString();
  }

  public SliceResult getSliceResult() throws IOException, InvalidClassFileException {
    return new SliceResult(
        getMethodSignature(),
        getInstructionIndexes(),
        getInstructionIndexesToKeep(),
        getInstructionIndexesToIgnore(),
        getInstructionPopMap(),
        getControlFlow());
  }

  public static SliceResult getSliceResult(
      String inputJar, String methodSignature, Set<Integer> instructionIndexes)
      throws IOException, InvalidClassFileException {
    Slicer slicer = new Slicer();
    slicer.setInputJar(inputJar);
    slicer.setMethodSignature(methodSignature);
    slicer.setInstructionIndexes(instructionIndexes);

    return slicer.getSliceResult();
  }

  public void parseArgs(String[] args) throws ParseException {
    Options options = new Options();
    options.addOption(
        "in",
        "inputJar",
        true,
        "path to java application (jar) [Default: \"../exampleprogram.jar\"]");
    options.addOption(
        "out",
        "outputJar",
        true,
        "path to the sliced java application (jar) [Default: \"sliced.jar\"]");
    options.addOption("ms", "methodSignature", true, "methodSignature");
    options.addOption("mc", "mainClass", true, "");
    options.addOption("ii", "instructionIndexes", true, "instructionIndexes [int,int,...]");
    options.addOption("ef", "exportFormat", true, "export format [CSV,XML,NONE] default: XML");
    options.addOption("rf", "resultFilePath", true, "path to saved result file [result.xml]");
    options.addOption(
        "ajp",
        "additionalJarsPath",
        true,
        "path where the additional jars are stored [Default: ../]");
    options.addOption("sp", "showPlots", true, "show dot plots [Default: false]");

    CommandLineParser parser = new DefaultParser();
    CommandLine cmd = parser.parse(options, args);

    inputJar = cmd.getOptionValue("inputJar");
    Objects.requireNonNull(inputJar, "-in/--inputJar must be set");

    outputJar = cmd.getOptionValue("outputJar", "sliced.jar");
    resultFilePath = cmd.getOptionValue("resultFilePath", "result.xml");
    additionalJarsPath = cmd.getOptionValue("additionalJarsPath", "../");

    methodSignature = cmd.getOptionValue("methodSignature");
    Objects.requireNonNull(methodSignature, "-ms/--methodSignature must be set");

    mainClass = cmd.getOptionValue("mainClass");
    Objects.requireNonNull(mainClass, "-mc/--mainClass must be set");

    String exportFormatStr = cmd.getOptionValue("exportFormat", "XML");

    if (exportFormatStr.contentEquals("NONE")) {
      setExportFormat(null);
    } else {
      try {
        setExportFormat(ExportFormat.valueOf(exportFormatStr));
      } catch (IllegalArgumentException e) {
        throw new IllegalArgumentException("Unknown --exportFormat '" + exportFormatStr + "'");
      }
    }

    // Support multiple instruction indexes (a feature may consist out of many)
    String instructionIndexesStr = cmd.getOptionValue("instructionIndexes");
    Objects.requireNonNull(instructionIndexesStr, "-ii/--instructionIndexes must be set");
    for (String instructionIndexStr : instructionIndexesStr.split(",")) {
      int instructionIndex = Integer.parseInt(instructionIndexStr);
      if (instructionIndex < 0) {
        if (verbose) {
          System.out.println("NOTE: Removed instruction index: " + instructionIndex + " (below 0)");
        }
        continue;
      }
      instructionIndexes.add(instructionIndex);
    }

    showPlots = cmd.hasOption("showPlots");
  }

  public String getInputJar() {
    return inputJar;
  }

  public void setInputJar(String inputJar) {
    this.inputJar = inputJar;
  }

  public String getOutputJar() {
    return outputJar;
  }

  public void setOutputJar(String outputJar) {
    this.outputJar = outputJar;
  }

  public String getMethodSignature() {
    return methodSignature;
  }

  public void setMethodSignature(String methodSignature) {
    this.methodSignature = methodSignature;
  }

  public Set<Integer> getInstructionIndexes() {
    return instructionIndexes;
  }

  public void setInstructionIndexes(Set<Integer> instructionIndexes) {
    instructionIndexes = new HashSet<>(instructionIndexes);
    instructionIndexes.removeIf(
        index -> {
          if (index < 0) {
            if (verbose) {
              System.out.println("NOTE: Removed instruction index: " + index + " (below 0)");
            }
            return true;
          }
          return false;
        });
    this.instructionIndexes = instructionIndexes;
  }

  public String getResultFilePath() {
    return resultFilePath;
  }

  public void setResultFilePath(String resultFilePath) {
    this.resultFilePath = resultFilePath;
  }

  public ExportFormat getExportFormat() {
    return exportFormat;
  }

  public void setExportFormat(ExportFormat exportFormat) {
    this.exportFormat = exportFormat;
  }

  public String getAdditionalJarsPath() {
    return additionalJarsPath;
  }

  public void setAdditionalJarsPath(String additionalJarsPath) {
    this.additionalJarsPath = additionalJarsPath;
  }

  public String getMainClass() {
    return mainClass;
  }

  public void setMainClass(String mainClass) {
    this.mainClass = mainClass;
  }

  public void setVerbose(boolean verbose) {
    this.verbose = verbose;
  }
}
