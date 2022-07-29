package de.uniks.vs.methodresourceprediction.slicer;

import com.ibm.wala.shrike.shrikeBT.ConditionalBranchInstruction;
import com.ibm.wala.shrike.shrikeBT.GotoInstruction;
import com.ibm.wala.shrike.shrikeBT.IInstruction;
import com.ibm.wala.shrike.shrikeBT.MethodData;
import com.ibm.wala.shrike.shrikeBT.shrikeCT.ClassInstrumenter;
import com.ibm.wala.shrike.shrikeBT.shrikeCT.OfflineInstrumenter;
import com.ibm.wala.shrike.shrikeCT.ClassReader;
import com.ibm.wala.shrike.shrikeCT.InvalidClassFileException;
import com.ibm.wala.util.collections.Pair;
import de.uniks.vs.methodresourceprediction.utils.InstrumenterComparator;
import org.jgrapht.alg.cycle.JohnsonSimpleCycles;
import org.jgrapht.graph.DefaultDirectedGraph;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.io.ComponentNameProvider;
import org.jgrapht.io.ExportException;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.stream.IntStream;

public class ControlFlow extends SlicerGraph<Integer> {
  private String inputPath;
  private String classPath;
  private String methodSignature;

  private MethodData methodData;

  private DefaultDirectedGraph<Integer, DefaultEdge> graph;
  private List<List<Integer>> simpleCycles;
  private StackTrace stackTrace;
  private List<Pair<Integer, Integer>> loopPairs;
  private Integer startNode;

  public ControlFlow(String inputPath, String methodSignature) {
    this.inputPath = inputPath;
    this.methodSignature = methodSignature;
  }

  public ControlFlow(MethodData methodData) {
    this.methodData = methodData;
  }

  public StackTrace getStackTrace() throws IOException, InvalidClassFileException {
    if (stackTrace != null) {
      return stackTrace;
    }

    stackTrace = new StackTrace(getMethodData());
    return stackTrace;
  }

  @Override
  public DefaultDirectedGraph<Integer, DefaultEdge> getGraph() throws IOException, InvalidClassFileException {
    if (graph != null) {
      return graph;
    }

    MethodData methodData = getMethodData();
    IInstruction[] instructions = methodData.getInstructions();

    startNode = 0;

    graph = new DefaultDirectedGraph<>(DefaultEdge.class);

    // Add all instruction indexes as vertices first
    for (int index = 0; index < instructions.length; index++) {
      graph.addVertex(index);
    }

    // Iterate all instructions and build the control flow
    for (int index = 0; index < instructions.length - 1; index++) {
      IInstruction a = instructions[index];

      int b = index + 1;
      // If the next instruction is a Goto-/ConditionalBranchInstruction, the flow can
      // differ to realize if's and loops
      if (a instanceof GotoInstruction) {
        int target = a.getBranchTargets()[0];
        b = target;
      }
      if (a instanceof ConditionalBranchInstruction) {
        graph.addEdge(index, b);
        int target = ((ConditionalBranchInstruction) a).getTarget();
        b = target;
      }
      graph.addEdge(index, b);
    }
    return graph;
  }

  public List<List<Integer>> getSimpleCycles() throws IOException, InvalidClassFileException {
    if (simpleCycles != null) {
      return simpleCycles;
    }

    JohnsonSimpleCycles<Integer, DefaultEdge> johnsonSimpleCycles =
        new JohnsonSimpleCycles<>(getGraph());
    simpleCycles = johnsonSimpleCycles.findSimpleCycles();
    return simpleCycles;
  }

  public List<Pair<Integer, Integer>> getLoopPairs() throws IOException, InvalidClassFileException {
    if (loopPairs != null) {
      return loopPairs;
    }

    loopPairs = new ArrayList<>();
    for (List<Integer> simpleCycle : getSimpleCycles()) {
      int min = simpleCycle.stream().mapToInt(Integer::intValue).min().getAsInt();
      int max = simpleCycle.stream().mapToInt(Integer::intValue).max().getAsInt();

      Pair<Integer, Integer> loopPair = Pair.make(min, max);
      if (!loopPairs.stream()
          .filter(loopIndex -> loopIndex.equals(loopPair))
          .findAny()
          .isPresent()) {
        loopPairs.add(loopPair);
      }
    }
    return loopPairs;
  }

  public boolean isPartOfCycle(int instructionIndex) throws IOException, InvalidClassFileException {
    for (List<Integer> cycle : getSimpleCycles()) {
      if (cycle.contains(instructionIndex)) {
        return true;
      }
    }
    return false;
  }

  public Map<Integer, IInstruction> getInstructionMap() throws IOException, InvalidClassFileException {
    final IInstruction[] instructions = getMethodData().getInstructions();
    final Map<Integer, IInstruction> instructionMap = new LinkedHashMap<>();
    IntStream.range(0, instructions.length).forEach(i -> instructionMap.put(i, instructions[i]));
    return instructionMap;
  }

  public Set<Integer> getInstructionsInCycles() throws IOException, InvalidClassFileException {
    Set<Integer> instructionsInCycleSet = new HashSet<>();
    for (int index = 0; index < getMethodData().getInstructions().length; index++) {
      if (isPartOfCycle(index)) {
        instructionsInCycleSet.add(index);
      }
    }
    return instructionsInCycleSet;
  }

  @Override
  protected String dotPrint() throws IOException, InvalidClassFileException, ExportException {
    IInstruction[] instructions = getMethodData().getInstructions();

    // use helper classes to define how vertices should be rendered,
    // adhering to the DOT language restrictions
    ComponentNameProvider<Integer> vertexIdProvider =
        new ComponentNameProvider<Integer>() {
          public String getName(Integer index) {
            return String.valueOf(index);
          }
        };
    ComponentNameProvider<Integer> vertexLabelProvider =
        new ComponentNameProvider<Integer>() {
          public String getName(Integer index) {
            return index + ": " + instructions[index].toString();
          }
        };
    return getExporterGraphString(vertexIdProvider, vertexLabelProvider);
  }

  public MethodData getMethodData() throws IOException, InvalidClassFileException {
    if (methodData != null) {
      return methodData;
    }

    InstrumenterComparator comparator = InstrumenterComparator.of(methodSignature);

    OfflineInstrumenter inst = new OfflineInstrumenter();
    if (!Objects.isNull(inputPath)) {
      inst.addInputJar(new File(inputPath));
    }
    if (!Objects.isNull(classPath)) {
      File classPathFile = new File(classPath);
      File basePath = new File(classPathFile.getPath());
      File classFilename = new File(classPathFile.getName());
      inst.addInputClass(basePath, classFilename);
    }
    inst.beginTraversal();

    // Iterate each class in the input program and instrument it
    ClassInstrumenter ci;
    while ((ci = inst.nextClass()) != null) {
      // Search for the correct method (MethodData)
      ClassReader reader = ci.getReader();

      for (int methodIndex = 0; methodIndex < reader.getMethodCount(); methodIndex++) {
        methodData = ci.visitMethod(methodIndex);
        if (methodData == null) {
          continue;
        }

        if (!comparator.equals(methodData)) {
          methodData = null;
          continue;
        }
        break;
      }

      // Check if method was not found in this class
      if (methodData != null) {
        break;
      }
    }
    return methodData;
  }

  public List<List<Integer>> getCyclesForInstruction(int instructionIndex)
      throws IOException, InvalidClassFileException {
    List<List<Integer>> scs = getSimpleCycles();
    List<List<Integer>> scsWithInstructionIndex = new ArrayList<>();

    for (List<Integer> sc : scs) {
      if (sc.contains(instructionIndex)) {
        scsWithInstructionIndex.add(sc);
      }
    }
    return scsWithInstructionIndex;
  }

  public boolean inSameCycle(int instructionIndexA, int instructionIndexB)
      throws IOException, InvalidClassFileException {
    return inSameCycle(instructionIndexA, instructionIndexB, null);
  }

  public boolean inSameCycle(
      int instructionIndexA, int instructionIndexB, int... moreInstructionIndexes)
      throws IOException, InvalidClassFileException {
    // TODO What about sub-cycle handling?
    if (!getCyclesForInstruction(instructionIndexA)
        .equals(getCyclesForInstruction(instructionIndexB))) {
      return false;
    }
    if (moreInstructionIndexes != null) {
      for (int instructionIndex : moreInstructionIndexes) {
        if (!getCyclesForInstruction(instructionIndexA)
            .equals(getCyclesForInstruction(instructionIndex))) {
          return false;
        }
      }
    }
    return true;
  }

  public void renumberVarIndexes(Map<Integer, Set<Integer>> varIndexesToRenumber)
      throws IOException, InvalidClassFileException {
    //		Map<Integer, Set<Integer>> varIndexesToRenumber = getVarIndexesToRenumber();

    IInstruction[] instructions = getMethodData().getInstructions();
    for (int index = 0; index < instructions.length; index++) {
      IInstruction instruction = instructions[index];
      //			instructions[index] = Utilities.rewriteVarIndex(varIndexesToRenumber, instruction,
      // index);
    }
  }

  @Override
  public String toString() {
    try {
      return getGraph().toString();
    } catch (IOException e) {
      e.printStackTrace();
      return e.getMessage();
    } catch (InvalidClassFileException e) {
      e.printStackTrace();
      return e.getMessage();
    }
  }

  public Integer getStartNode() {
    return startNode;
  }

  public void setStartNode(Integer startNode) {
    this.startNode = startNode;
  }

  public void setClassPath(String classPath) {
    this.classPath = classPath;
  }
}
