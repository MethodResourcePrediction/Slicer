package de.uniks.vs.methodresourceprediction.slicer;

import com.ibm.wala.shrikeBT.*;
import de.uniks.vs.methodresourceprediction.utils.Utilities;
import java.util.*;
import java.util.Map.Entry;
import java.util.function.BiConsumer;
import java.util.stream.IntStream;

public class StackTrace implements Iterable<Entry<Integer, Stack<Integer>>> {
  private final IInstruction[] instructions;
  private final ExceptionHandler[][] exceptionHandlers;

  private Map<Integer, Stack<Integer>> stackTrace;
  private Map<Integer, Stack<Integer>> exceptionStackTrace;
  private Map<Integer, Stack<Integer>> poppedStackTrace;
  private Map<Integer, Stack<Integer>> pushedStackTrace;

  public StackTrace(MethodData methodData) {
    this.instructions = methodData.getInstructions();
    this.exceptionHandlers = methodData.getHandlers();

    createStackTraces();
  }

  public StackTrace(IInstruction[] instructions, ExceptionHandler[][] exceptionHandlers) {
    this.instructions = instructions;
    this.exceptionHandlers = exceptionHandlers;

    createStackTraces();
  }

  public Stack<Integer> getStackAtInstructionIndex(int instructionIndex) {
    return copyStack(getStackTrace().get(instructionIndex));
  }

  public Stack<Integer> getPoppedStackAtInstructionIndex(int instructionIndex) {
    return copyStack(getPoppedStackTrace().get(instructionIndex));
  }

  public Stack<Integer> getPushedStackAtInstructionIndex(int instructionIndex) {
    return copyStack(getPushedStackTrace().get(instructionIndex));
  }

  public void forEachException(BiConsumer<Integer, Stack<Integer>> consumer) {
    for (Entry<Integer, Stack<Integer>> entry : getExceptionStackTrace().entrySet()) {
      Stack<Integer> pushedStack = entry.getValue();
      consumer.accept(entry.getKey(), copyStack(pushedStack));
    }
  }

  public void forEachPopped(BiConsumer<Integer, Stack<Integer>> consumer) {
    for (Entry<Integer, Stack<Integer>> entry : getPoppedStackTrace().entrySet()) {
      Stack<Integer> poppedStack = entry.getValue();
      consumer.accept(entry.getKey(), copyStack(poppedStack));
    }
  }

  public void forEachPushed(BiConsumer<Integer, Stack<Integer>> consumer) {
    for (Entry<Integer, Stack<Integer>> entry : getPushedStackTrace().entrySet()) {
      Stack<Integer> pushedStack = entry.getValue();
      consumer.accept(entry.getKey(), copyStack(pushedStack));
    }
  }

  public Map<Integer, Stack<Integer>> getStackTrace() {
    return stackTrace;
  }

  public Map<Integer, Stack<Integer>> getExceptionStackTrace() {
    return exceptionStackTrace;
  }

  public Map<Integer, Stack<Integer>> getPoppedStackTrace() {
    return poppedStackTrace;
  }

  public Map<Integer, Stack<Integer>> getPushedStackTrace() {
    return pushedStackTrace;
  }

  private void createStackTraces() {
    stackTrace = new HashMap<>();
    exceptionStackTrace = new HashMap<>();
    poppedStackTrace = new HashMap<>();
    pushedStackTrace = new HashMap<>();

    Stack<Integer> stack = new Stack<>();

    Map<Integer, Set<String>> instructionExceptions =
        Utilities.getInstructionExceptions(instructions, exceptionHandlers);

    // Iterate all instructions and build the control flow
    // TODO Can we simply go though all instructions from front to end
    //  or should it follow the control flow?
    for (int index = 0; index < instructions.length; index++) {
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
      exceptionStackTrace.put(index, exceptionStack);

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
      poppedStackTrace.put(index, poppedStack);

      // Simulate PUSH stack execution
      Stack<Integer> pushedStack = new Stack<>();
      for (int pushIteration = 0; pushIteration < pushedElementCount; pushIteration++) {
        stack.push(index);
        pushedStack.add(index);
      }
      pushedStackTrace.put(index, pushedStack);

      // Set a copy of the stack at current index
      stackTrace.put(index, copyStack(stack));
    }
  }

  public static Stack<Integer> copyStack(Stack<Integer> stack) {
    Stack<Integer> stackCopy = new Stack<>();
    IntStream.range(0, stack.size())
        .forEach(
            i -> {
              stackCopy.push(stack.get(i));
            });
    return stackCopy;
  }

  @Override
  public Iterator<Entry<Integer, Stack<Integer>>> iterator() {
    return stackTrace.entrySet().iterator();
  }
}
