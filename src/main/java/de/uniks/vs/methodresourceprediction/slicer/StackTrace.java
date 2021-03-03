package de.uniks.vs.methodresourceprediction.slicer;

import com.ibm.wala.shrikeBT.*;
import com.ibm.wala.shrikeCT.InvalidClassFileException;
import de.uniks.vs.methodresourceprediction.utils.Utilities;
import org.jgrapht.Graph;
import org.jgrapht.graph.DefaultEdge;

import java.io.IOException;
import java.util.*;
import java.util.Map.Entry;
import java.util.function.BiConsumer;
import java.util.stream.IntStream;

public class StackTrace implements Iterable<Entry<Integer, List<Stack<Integer>>>> {
  private final IInstruction[] instructions;
  private final ExceptionHandler[][] exceptionHandlers;
  private final ControlFlow controlFlow;

  private Map<Integer, List<Stack<Integer>>> stackTrace;
  private Map<Integer, List<Stack<Integer>>> exceptionStackTrace;
  private Map<Integer, List<Stack<Integer>>> poppedStackTrace;
  private Map<Integer, List<Stack<Integer>>> pushedStackTrace;

  public StackTrace(MethodData methodData) throws IOException, InvalidClassFileException {
    this.instructions = methodData.getInstructions();
    this.exceptionHandlers = methodData.getHandlers();
    this.controlFlow = new ControlFlow(methodData);

    createStackTraces();
  }

  public List<Stack<Integer>> getStackAtInstructionIndex(int instructionIndex) {
    return copyStacks(getStackTrace().get(instructionIndex));
  }

  public List<Stack<Integer>> getPoppedStackAtInstructionIndex(int instructionIndex) {
    return copyStacks(getPoppedStackTrace().get(instructionIndex));
  }

  public List<Stack<Integer>> getPushedStackAtInstructionIndex(int instructionIndex) {
    return copyStacks(getPushedStackTrace().get(instructionIndex));
  }

  public void forEachException(BiConsumer<Integer, List<Stack<Integer>>> consumer) {
    for (Entry<Integer, List<Stack<Integer>>> entry : getExceptionStackTrace().entrySet()) {
      List<Stack<Integer>> exceptionStacks = entry.getValue();
      consumer.accept(entry.getKey(), copyStacks(exceptionStacks));
    }
  }

  public void forEachPopped(BiConsumer<Integer, List<Stack<Integer>>> consumer) {
    for (Entry<Integer, List<Stack<Integer>>> entry : getPoppedStackTrace().entrySet()) {
      List<Stack<Integer>> poppedStacks = entry.getValue();
      consumer.accept(entry.getKey(), copyStacks(poppedStacks));
    }
  }

  public void forEachPushed(BiConsumer<Integer, List<Stack<Integer>>> consumer) {
    for (Entry<Integer, List<Stack<Integer>>> entry : getPushedStackTrace().entrySet()) {
      List<Stack<Integer>> pushedStacks = entry.getValue();
      consumer.accept(entry.getKey(), copyStacks(pushedStacks));
    }
  }

  public Map<Integer, List<Stack<Integer>>> getStackTrace() {
    return stackTrace;
  }

  public Map<Integer, List<Stack<Integer>>> getExceptionStackTrace() {
    return exceptionStackTrace;
  }

  public Map<Integer, List<Stack<Integer>>> getPoppedStackTrace() {
    return poppedStackTrace;
  }

  public Map<Integer, List<Stack<Integer>>> getPushedStackTrace() {
    return pushedStackTrace;
  }

  private void createStackTraceFor(
      Map<Integer, Set<String>> instructionExceptions, Stack<Integer> stack, Integer index)
      throws IOException, InvalidClassFileException {
    IInstruction instruction = instructions[index];

    // Exception handling
    Set<String> instructionException =
        instructionExceptions.getOrDefault(index, Collections.emptySet());
    int pushedExceptionCount = instructionException.size();

    Stack<Integer> exceptionStack = new Stack<>();
    for (int pushedException = 0; pushedException < pushedExceptionCount; pushedException++) {
      stack.push(-1 * index);
      exceptionStack.push(-1 * index);
    }
    List<Stack<Integer>> instructionExceptionStack =
        exceptionStackTrace.getOrDefault(index, new ArrayList<>());
    instructionExceptionStack.add(exceptionStack);
    exceptionStackTrace.putIfAbsent(index, instructionExceptionStack);

    int pushedElementCount = Utilities.getPushedElementCount(instruction);
    int poppedElementCount = Utilities.getPoppedElementCount(instruction);

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

      int poppedElementCountForDupInstruction =
          Utilities.getPoppedElementCountForDupInstruction(
              (DupInstruction) instruction, wordSize == 1);
      pushedElementCount = 2 * poppedElementCountForDupInstruction;
      poppedElementCount = poppedElementCountForDupInstruction;
    }

    // Simulate POP stack execution
    Stack<Integer> poppedStack = new Stack<>();
    for (int popIteration = 0; popIteration < poppedElementCount; popIteration++) {
      Integer poppedIndex = stack.pop();
      poppedStack.insertElementAt(poppedIndex, 0);
    }
    List<Stack<Integer>> instructionPoppedStack =
        poppedStackTrace.getOrDefault(index, new ArrayList<>());
    instructionPoppedStack.add(poppedStack);
    poppedStackTrace.putIfAbsent(index, instructionPoppedStack);

    // Simulate PUSH stack execution
    Stack<Integer> pushedStack = new Stack<>();
    for (int pushIteration = 0; pushIteration < pushedElementCount; pushIteration++) {
      stack.push(index);
      pushedStack.add(index);
    }
    List<Stack<Integer>> instructionPushedStack =
        pushedStackTrace.getOrDefault(index, new ArrayList<>());
    instructionPushedStack.add(pushedStack);
    pushedStackTrace.putIfAbsent(index, instructionPushedStack);

    Graph<Integer, DefaultEdge> cfg = controlFlow.getGraph();
    for (DefaultEdge edge : cfg.outgoingEdgesOf(index)) {
      createStackTraceFor(instructionExceptions, stack, cfg.getEdgeTarget(edge));
    }
  }

  private void createStackTraces() throws IOException, InvalidClassFileException {
    stackTrace = new HashMap<>();
    exceptionStackTrace = new HashMap<>();
    poppedStackTrace = new HashMap<>();
    pushedStackTrace = new HashMap<>();

    Map<Integer, Set<String>> instructionExceptions =
        Utilities.getInstructionExceptions(instructions, exceptionHandlers);

    createStackTraceFor(instructionExceptions, new Stack<>(), 0);

    assert pushedStackTrace.keySet().size() == instructions.length;
    assert poppedStackTrace.keySet().size() == instructions.length;
    assert exceptionStackTrace.keySet().size() == instructions.length;
  }

  public static List<Stack<Integer>> copyStacks(List<Stack<Integer>> stacks) {
    List<Stack<Integer>> stacksCopy = new ArrayList<>();
    IntStream.range(0, stacks.size())
        .forEach(
            i -> {
              stacksCopy.add(copyStack(stacks.get(i)));
            });
    return stacksCopy;
  }

  private static Stack<Integer> copyStack(Stack<Integer> stack) {
    Stack<Integer> stackCopy = new Stack<>();
    IntStream.range(0, stack.size())
        .forEach(
            i -> {
              stackCopy.push(stack.get(i));
            });
    return stackCopy;
  }

  @Override
  public Iterator<Entry<Integer, List<Stack<Integer>>>> iterator() {
    return stackTrace.entrySet().iterator();
  }
}
