package de.rherzog.master.thesis.slicer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.Stack;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.codec.DecoderException;
import org.jgrapht.graph.DefaultEdge;

import com.ibm.wala.shrikeBT.GotoInstruction;
import com.ibm.wala.shrikeBT.IConditionalBranchInstruction;
import com.ibm.wala.shrikeBT.IInstruction;
import com.ibm.wala.shrikeBT.IInvokeInstruction;
import com.ibm.wala.shrikeBT.LoadInstruction;
import com.ibm.wala.shrikeBT.ReturnInstruction;
import com.ibm.wala.shrikeBT.StoreInstruction;
import com.ibm.wala.shrikeCT.InvalidClassFileException;

import de.rherzog.master.thesis.slicer.instrumenter.export.SliceWriter.ExportFormat;
import de.rherzog.master.thesis.utils.Utilities;

public class Slicer {
	private String inputJar;
	private String outputJar;
	private String methodSignature;
	private String mainClass;
	private String resultFilePath;
	private Set<Integer> instructionIndexes;
	private ExportFormat exportFormat;
	private String additionalJarsPath;

	// Internal graphs
	private ControlFlow controlFlow;
	private ControlDependency controlDependency;
	private BlockDependency blockDependency;
	private DataDependency dataDependency;
	private ArgumentDependency argumentDependency;

	private boolean verbose = false;

	public Slicer() {
		this.instructionIndexes = new HashSet<>();
	}

	public static void main(String[] args) throws ParseException, IllegalStateException, IOException,
			InvalidClassFileException, DecoderException, InterruptedException {
		Slicer mySlicer = new Slicer();
		mySlicer.setVerbose(true);
		mySlicer.parseArgs(args);
//		mySlicer.setExportFormat(null);
//		mySlicer.makeSlicedFile(true);
		mySlicer.makeSlicedFile(false);
	}

	public String makeSlicedFile() throws IOException, InvalidClassFileException, IllegalStateException,
			DecoderException, InterruptedException {
		return makeSlicedFile(false);
	}

	public ControlFlow getControlFlow() throws IOException, InvalidClassFileException {
		if (controlFlow != null) {
			return controlFlow;
		}
		controlFlow = new ControlFlow(inputJar, methodSignature);
		// Optional
		controlFlow.renumberVarIndexes();
		return controlFlow;
	}

	public ControlDependency getControlDependency() throws IOException, InvalidClassFileException {
		if (controlDependency != null) {
			return controlDependency;
		}
		controlDependency = new ControlDependency(getControlFlow());
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

	public String makeSlicedFile(boolean showDotPlots) throws IOException, InvalidClassFileException,
			IllegalStateException, DecoderException, InterruptedException {
		ControlFlow controlFlow = getControlFlow();
		ControlDependency controlDependency = getControlDependency();
		BlockDependency blockDependency = getBlockDependency();
		DataDependency dataDependency = getDataDependency();
		ArgumentDependency argumentDependency = getArgumentDependency();

		Map<Integer, Set<Integer>> varIndexesToRenumber = controlFlow.getVarIndexesToRenumber();
		Set<Integer> instructionsInCycles = controlFlow.getInstructionsInCycles();

		if (showDotPlots) {
			final Path dir = Files.createTempDirectory("slicer-");
//			Utilities.dotShow(dir, controlFlow.dotPrint());
//			Utilities.dotShow(dir, controlDependency.dotPrint());
//			Utilities.dotShow(dir, blockDependency.dotPrint());
//			Utilities.dotShow(dir, dataDependency.dotPrint());
			Utilities.dotShow(dir, argumentDependency.dotPrint());
		}

		Set<Integer> instructionIndexesToKeep = getInstructionIndexesToKeep(controlFlow, controlDependency,
				blockDependency, argumentDependency, dataDependency);
		Set<Integer> instructionIndexesToIgnore = getInstructionIndexesToIgnore(controlFlow, controlDependency,
				argumentDependency, dataDependency);
		Map<Integer, Integer> instructionPopMap = getInstructionPopMap();

		// Instrument a new program with a modified method which we analyze
		Instrumenter instrumenter = new Instrumenter(additionalJarsPath, inputJar, outputJar, methodSignature,
				mainClass, resultFilePath, exportFormat);
		instrumenter.setVerbose(verbose);
		instrumenter.instrument(instructionIndexes, instructionIndexesToKeep, instructionIndexesToIgnore,
				instructionPopMap, varIndexesToRenumber, instructionsInCycles);
		instrumenter.finalize();

		return outputJar;
	}

	public Set<Integer> getInstructionIndexesToIgnore() throws IOException, InvalidClassFileException {
		return getInstructionIndexesToIgnore(getControlFlow(), getControlDependency(), getArgumentDependency(),
				getDataDependency());

	}

	public Set<Integer> getInstructionIndexesToIgnore(ControlFlow controlFlow, ControlDependency controlDependency,
			ArgumentDependency argumentDependency, DataDependency dataDependency)
			throws IOException, InvalidClassFileException {
		Set<Integer> instructionIndexesToKeep = getInstructionIndexesToKeep(controlFlow, controlDependency,
				blockDependency, argumentDependency, dataDependency);
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
		return getInstructionIndexesToKeep(getControlFlow(), getControlDependency(), getBlockDependency(),
				getArgumentDependency(), getDataDependency());
	}

	public Set<Integer> getInstructionIndexesToKeep(ControlFlow controlFlow, ControlDependency controlDependency,
			BlockDependency blockDependency, ArgumentDependency argumentDependency, DataDependency dataDependency)
			throws IOException, InvalidClassFileException {
		IInstruction[] instructions = controlFlow.getMethodData().getInstructions();

		// Add all provided indexes to the slice
		Set<Integer> indexesToKeep = new HashSet<>();
		for (int instructionIndex : getInstructionIndexes()) {
			slice(controlFlow, controlDependency, blockDependency, argumentDependency, dataDependency, indexesToKeep,
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
				slice(controlFlow, controlDependency, blockDependency, argumentDependency, dataDependency,
						indexesToKeep, recursiveInvokeInstructionIndex);

				// Include all return statements that are reachable from any instruction if
				// they are already kept in "indexesToKeep"
				for (int index = 0; index < recursiveInvokeInstructionIndex; index++) {
					IInstruction instruction = instructions[index];
					if (!(instruction instanceof ReturnInstruction)) {
						continue;
					}
					for (DefaultEdge incomingEdge : controlFlow.getGraph().incomingEdgesOf(index)) {
						Integer instructionSourceIndex = controlFlow.getGraph().getEdgeSource(incomingEdge);
						if (!indexesToKeep.contains(instructionSourceIndex)) {
							continue;
						}
						slice(controlFlow, controlDependency, blockDependency, argumentDependency, dataDependency,
								indexesToKeep, index);
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
				slice(controlFlow, controlDependency, blockDependency, argumentDependency, dataDependency,
						indexesToKeep2, recursiveInvokeInstructionIndex);
			}
		}
		indexesToKeep.addAll(indexesToKeep2);

		// Add last return instruction
		indexesToKeep.add(instructions.length - 1);
		return indexesToKeep;
	}

	public Map<Integer, Integer> getInstructionPopMap() throws IOException, InvalidClassFileException {
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

				int pushedElementCount = Utilities.getPushedElementCount(instruction);
				int poppedElementCount = Utilities.getPoppedElementCount(instruction);

				// Simulate stack execution
				for (int popIteration = 0; popIteration < poppedElementCount; popIteration++) {
					stack.pop();
				}
				for (int pushIteration = 0; pushIteration < pushedElementCount; pushIteration++) {
					stack.push(blockInstructionIndex);
				}
			}

			// The remaining elements on the stack are the ones to be popped
			if (stack.size() > 0) {
				instructionPopAfterMap.put(block.getHighestIndex(), stack.size());
			}
		}
		return instructionPopAfterMap;
	}

	private void slice(ControlFlow controlFlow, ControlDependency controlDependency, BlockDependency blockDependency,
			ArgumentDependency argumentDependency, DataDependency dataDependency, Set<Integer> dependendInstructions,
			int index) throws IOException, InvalidClassFileException {
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
			slice(controlFlow, controlDependency, blockDependency, argumentDependency, dataDependency,
					dependendInstructions, argumentIndex);
		}

		// Add cycle (goto)
		List<List<Integer>> cycles = controlFlow.getCyclesForInstruction(index);
		for (List<Integer> cycle : cycles) {
			// Keep start block of loop
			Integer cycleStartIndex = cycle.get(0);
			Integer highestIndex = blockDependency.getBlockForIndex(cycleStartIndex).getHighestIndex();
			slice(controlFlow, controlDependency, blockDependency, argumentDependency, dataDependency,
					dependendInstructions, highestIndex);

			// Keep end block of loop
			// NOTE: This is usually a GOTO-Instruction. Is it required to slice the index?
			Integer cycleEndIndex = cycle.get(cycle.size() - 1);
			highestIndex = blockDependency.getBlockForIndex(cycleEndIndex).getHighestIndex();
			slice(controlFlow, controlDependency, blockDependency, argumentDependency, dataDependency,
					dependendInstructions, highestIndex);
		}

		// Consider data dependencies
		for (Integer dataDependentIndex : dataDependency.getDataDependencyInstructions(index)) {
			// TODO Filter for lower data dependencies is not correct in a loop. Try to find
			// another solution.
			// Example: LSleep;.p([Ljava/lang/String;)V with InstructionIndexes: [12]
			if (dataDependentIndex >= 0) {
				IInstruction instruction = getControlFlow().getMethodData().getInstructions()[dataDependentIndex];
				if ((instruction instanceof LoadInstruction || instruction instanceof StoreInstruction)
						&& dataDependentIndex > index) {
					continue;
				}
			}
			slice(controlFlow, controlDependency, blockDependency, argumentDependency, dataDependency,
					dependendInstructions, dataDependentIndex);
		}

//		// Consider control dependencies
//		for (Integer controlDependentIndex : controlDependency.getControlDependencyInstructions(index)) {
//			if (controlDependentIndex == ControlDependency.ROOT_INDEX) {
//				continue;
//			}
//			slice(controlFlow, controlDependency, blockDependency, argumentDependency, dataDependency,
//					dependendInstructions, controlDependentIndex);
//		}
	}

	public SliceResult getSliceResult() throws IOException, InvalidClassFileException {
		return new SliceResult(getMethodSignature(), getInstructionIndexes(), getInstructionIndexesToKeep(),
				getInstructionIndexesToIgnore(), getInstructionPopMap(), getControlFlow());
	}

	public static SliceResult getSliceResult(String inputJar, String methodSignature, Set<Integer> instructionIndexes)
			throws IOException, InvalidClassFileException {
		Slicer slicer = new Slicer();
		slicer.setInputJar(inputJar);
		slicer.setMethodSignature(methodSignature);
		slicer.setInstructionIndexes(instructionIndexes);

		return slicer.getSliceResult();
	}

	public void parseArgs(String[] args) throws ParseException {
		Options options = new Options();
		options.addOption("in", "inputJar", true,
				"path to java application (jar) [Default: \"../exampleprogram.jar\"]");
		options.addOption("out", "outputJar", true,
				"path to the sliced java application (jar) [Default: \"sliced.jar\"]");
		options.addOption("ms", "methodSignature", true, "methodSignature");
		options.addOption("mc", "mainClass", true, "");
		options.addOption("ii", "instructionIndexes", true, "instructionIndexes [int,int,...]");
		options.addOption("ef", "exportFormat", true, "export format [CSV,XML,NONE] default: XML");
		options.addOption("rf", "resultFilePath", true, "path to saved result file [result.xml]");
		options.addOption("ajp", "additionalJarsPath", true,
				"path where the additional jars are stored [Default: ../]");

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
		instructionIndexes.removeIf(index -> {
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
