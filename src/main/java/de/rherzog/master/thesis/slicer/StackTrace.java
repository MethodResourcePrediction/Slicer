package de.rherzog.master.thesis.slicer;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Stack;
import java.util.function.BiConsumer;
import java.util.stream.IntStream;

import com.ibm.wala.shrikeBT.IInstruction;

import de.rherzog.master.thesis.utils.Utilities;

public class StackTrace implements Iterable<Entry<Integer, Stack<Integer>>> {
	private IInstruction[] instructions;

	private Map<Integer, Stack<Integer>> stackTrace;
	private Map<Integer, Stack<Integer>> poppedStackTrace;
	private Map<Integer, Stack<Integer>> pushedStackTrace;

	public StackTrace(IInstruction[] instructions) {
		this.instructions = instructions;

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

	public Map<Integer, Stack<Integer>> getPoppedStackTrace() {
		return poppedStackTrace;
	}

	public Map<Integer, Stack<Integer>> getPushedStackTrace() {
		return pushedStackTrace;
	}

	private void createStackTraces() {
		stackTrace = new HashMap<>();
		poppedStackTrace = new HashMap<>();
		pushedStackTrace = new HashMap<>();

		Stack<Integer> stack = new Stack<>();
		// Iterate all instructions and build the control flow
		for (int index = 0; index < instructions.length - 1; index++) {
			IInstruction instruction = instructions[index];

			int pushedElementCount = Utilities.getPushedElementCount(instruction);
			int poppedElementCount = Utilities.getPoppedElementCount(instruction);

			// Simulate POP stack execution
			Stack<Integer> poppedStack = new Stack<Integer>();
			for (int popIteration = 0; popIteration < poppedElementCount; popIteration++) {
				Integer poppedIndex = stack.pop();
				poppedStack.insertElementAt(poppedIndex, 0);
			}
			poppedStackTrace.put(index, poppedStack);

			// Simulate PUSH stack execution
			Stack<Integer> pushedStack = new Stack<Integer>();
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
		IntStream.range(0, stack.size()).forEach(i -> {
			stackCopy.push(stack.get(i));
		});
		return stackCopy;
	}

	@Override
	public Iterator<Entry<Integer, Stack<Integer>>> iterator() {
		return stackTrace.entrySet().iterator();
	}
}
