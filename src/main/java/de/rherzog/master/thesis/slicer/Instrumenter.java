package de.rherzog.master.thesis.slicer;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import org.apache.commons.codec.DecoderException;
import org.apache.commons.io.FilenameUtils;

import com.ibm.wala.shrikeBT.ArrayLengthInstruction;
import com.ibm.wala.shrikeBT.ArrayLoadInstruction;
import com.ibm.wala.shrikeBT.BinaryOpInstruction;
import com.ibm.wala.shrikeBT.ConditionalBranchInstruction;
import com.ibm.wala.shrikeBT.ConstantInstruction;
import com.ibm.wala.shrikeBT.Constants;
import com.ibm.wala.shrikeBT.ConversionInstruction;
import com.ibm.wala.shrikeBT.DupInstruction;
import com.ibm.wala.shrikeBT.GetInstruction;
import com.ibm.wala.shrikeBT.GotoInstruction;
import com.ibm.wala.shrikeBT.IInstruction;
import com.ibm.wala.shrikeBT.ILoadInstruction;
import com.ibm.wala.shrikeBT.IStoreInstruction;
import com.ibm.wala.shrikeBT.InvokeDynamicInstruction;
import com.ibm.wala.shrikeBT.InvokeInstruction;
import com.ibm.wala.shrikeBT.LoadInstruction;
import com.ibm.wala.shrikeBT.MethodData;
import com.ibm.wala.shrikeBT.MethodEditor;
import com.ibm.wala.shrikeBT.NewInstruction;
import com.ibm.wala.shrikeBT.MethodEditor.Output;
import com.ibm.wala.shrikeBT.MethodEditor.Patch;
import com.ibm.wala.shrikeBT.PopInstruction;
import com.ibm.wala.shrikeBT.ReturnInstruction;
import com.ibm.wala.shrikeBT.StoreInstruction;
import com.ibm.wala.shrikeBT.Util;
import com.ibm.wala.shrikeBT.shrikeCT.ClassInstrumenter;
import com.ibm.wala.shrikeBT.shrikeCT.OfflineInstrumenter;
import com.ibm.wala.shrikeCT.ClassReader;
import com.ibm.wala.shrikeCT.ClassWriter;
import com.ibm.wala.shrikeCT.InvalidClassFileException;
import com.ibm.wala.types.TypeName;
import com.ibm.wala.types.generics.TypeSignature;
import com.ibm.wala.util.collections.Iterator2Iterable;
import com.ibm.wala.util.strings.StringStuff;

import de.rherzog.master.thesis.slicer.instrumenter.export.FeatureLogger;
import de.rherzog.master.thesis.slicer.instrumenter.export.FeatureLoggerExecution;
import de.rherzog.master.thesis.slicer.instrumenter.export.SliceWriter;
import de.rherzog.master.thesis.slicer.instrumenter.export.SliceWriter.ExportFormat;
import de.rherzog.master.thesis.utils.InstrumenterComparator;
import de.rherzog.master.thesis.utils.Utilities;

public class Instrumenter {
	private final static String ADDITIONAL_JARS_PATH = "extra_libs/";

	private OfflineInstrumenter instrumenter;
	private String inputPath;
	private String outputPath;
	private String tempOutputPath;
	private File baseDirFile;
	private String additionalJarsPath;
	private String methodSignature;
	private String mainClass;
	private String resultFilePath;
	private ExportFormat exportFormat;

	// Filter for duplicate entries.
	private Set<String> duplicateEntrySet = new HashSet<>();
	private String[] exportJars = new String[] { "SlicerExport.jar", "Utils.jar" };
	private boolean verbose = false;

	/**
	 * Constructor for an OfflineInstrumenter
	 * 
	 * @param inputPath       - path to input jar which should be profiled
	 * @param outputPath      - path to output jar where the profiled program is
	 *                        stored
	 * @param pipePath        - path to pipe for data send back communication
	 * @param methodSignature - signature for method to profile
	 * @param exportFormat
	 * @throws IOException
	 */
	public Instrumenter(String additionalJarsPath, String inputPath, String outputPath, String methodSignature,
			String mainClass, String resultFilePath, ExportFormat exportFormat) throws IOException {
		this.additionalJarsPath = additionalJarsPath;
		this.inputPath = inputPath;
		this.outputPath = outputPath;
		this.methodSignature = methodSignature;
		this.mainClass = mainClass;
		this.resultFilePath = resultFilePath;
		this.exportFormat = exportFormat;

		baseDirFile = new File("");

		instrumenter = new OfflineInstrumenter();
//		instrumenter.addInputJar(new File(inputPath));
		addJar(inputPath);

		tempOutputPath = outputPath + "_";
		instrumenter.setOutputJar(new File(tempOutputPath));
		instrumenter.setPassUnmodifiedClasses(true);
	}

	public static IInstruction[] getInstructions(File inputJar, String methodSignature)
			throws IOException, InvalidClassFileException {
		InstrumenterComparator comparator = InstrumenterComparator.of(methodSignature);

		OfflineInstrumenter inst = new OfflineInstrumenter();
		inst.addInputJar(inputJar);
		inst.beginTraversal();

		// Iterate each class in the input program and instrument it
		ClassInstrumenter ci;
		MethodData md = null;
		while ((ci = inst.nextClass()) != null) {
			// Search for the correct method (MethodData)
			ClassReader reader = ci.getReader();

			for (int methodIndex = 0; methodIndex < reader.getMethodCount(); methodIndex++) {
				md = ci.visitMethod(methodIndex);
				if (md == null) {
					continue;
				}

				if (!comparator.equals(md)) {
					md = null;
					continue;
				}
				break;
			}

			// Check if method was not found in this class
			if (md != null) {
				break;
			}
		}
		if (md == null) {
			return null;
		}
		return md.getInstructions();
	}

	public void instrument(Set<Integer> instructionIndexes, Set<Integer> instructionIndexesToKeep,
			Set<Integer> instructionIndexesToIgnore, Map<Integer, Integer> instructionPopMap,
			Map<Integer, Set<Integer>> varIndexesToRenumber, Set<Integer> instructionsInCycles)
			throws InvalidClassFileException, IllegalStateException, IOException {
		InstrumenterComparator comparator = InstrumenterComparator.of(methodSignature);

		instrumenter.beginTraversal();

		// Iterate each class in the input program and instrument it
		ClassInstrumenter ci;
		while ((ci = instrumenter.nextClass()) != null) {
			// Search for the correct method (MethodData)
			ClassReader reader = ci.getReader();
			MethodData md = null;

			for (int methodIndex = 0; methodIndex < reader.getMethodCount(); methodIndex++) {
				md = ci.visitMethod(methodIndex);
				if (md == null) {
					continue;
				}

				if (!comparator.equals(md)) {
					md = null;
					continue;
				}
				break;
			}

			// Check if method was not found in this class
			if (md == null) {
				continue;
			}

			InstrumentedMethod instrumentedMethod = instrumentMethod(md, instructionIndexes, instructionIndexesToKeep,
					instructionIndexesToIgnore, instructionPopMap, varIndexesToRenumber, instructionsInCycles);
			instrumentedMethod.getMethodEditor().endPass();

			// Write no matter if there are changes
			ClassWriter cw = ci.emitClass();
			instrumenter.outputModifiedClass(ci, cw);
		}
	}

	protected InstrumentedMethod instrumentMethod(MethodData methodData, Set<Integer> instructionIndexes,
			Set<Integer> instructionIndexesToKeep, Set<Integer> instructionIndexesToIgnore,
			Map<Integer, Integer> instructionPopMap, Map<Integer, Set<Integer>> varIndexesToRenumber,
			Set<Integer> instructionsInCycles) {
		// No feature patches here
		return instrumentMethod(methodData, instructionIndexes, instructionIndexesToKeep, instructionIndexesToIgnore,
				instructionPopMap, Collections.emptyMap(), varIndexesToRenumber, instructionsInCycles);
	}

	private enum PatchAction {
		AT_START, BEFORE, AFTER, AFTER_BODY, REPLACE
	}

	protected InstrumentedMethod instrumentMethod(MethodData methodData, Set<Integer> instructionIndexes,
			Set<Integer> instructionIndexesToKeep, Set<Integer> instructionIndexesToIgnore,
			Map<Integer, Integer> instructionPopMap, Map<Integer, Patch> featurePatchMap,
			Map<Integer, Set<Integer>> varIndexesToRenumber, Set<Integer> instructionsInCycles) {
		MethodEditor methodEditor = new MethodEditor(methodData);
		methodEditor.beginPass();

		// Build a List out of the IInstruction array.
		// This list can be used to select the instruction from the report features
		IInstruction[] instructions = methodEditor.getInstructions();
		final int lastInstructionIndex = instructions.length - 1;

		// Calculate the maximum local variable index
		int maxVarIndex = Utilities.getMaxLocalVarIndex(methodData);
		// Renumbering of variable indexes can shift the maxVarIndex up
		for (int varIndex : varIndexesToRenumber.keySet()) {
			maxVarIndex = Math.max(maxVarIndex, varIndex);
		}

		// There is a chance of multiple patches, for example if a load instruction
		// should be renumbered but is still in the "instructionIndexes" list to query
		// the value for. Therefore, we save a list of patches to each instruction and
		// apply it later.
		Map<Integer, Map<PatchAction, List<Patch>>> instructionPatchesMap = new HashMap<>();

		// Initialize all patch actions for each instruction
		for (int instructionIndex = 0; instructionIndex < instructions.length; instructionIndex++) {
			Map<PatchAction, List<Patch>> patchesMap = new HashMap<>();
			for (PatchAction patchAction : PatchAction.values()) {
				List<Patch> instructionPatches = new ArrayList<>();
				patchesMap.put(patchAction, instructionPatches);
			}
			instructionPatchesMap.put(instructionIndex, patchesMap);
		}

		// NOTE: Variables consume 1 or 2 stack elements (2 for long and double, 1
		// otherwise). Since the times here are saved as a "long" data type, we need to
		// reserve 2 elements after allocation. The addition to maxVarIndex is done in
		// the subsequent lines. So we need to reserve the space for the previous
		// instruction in the current line.
		// This is why loggerVarIndex gets incremented by 2, because startTimeVarIndex
		// needs 2 elements on the stack.
		final int startTimeVarIndex = maxVarIndex += 1;
		final int loggerVarIndex = maxVarIndex += 2;
		final int executionLoggerVarIndex = maxVarIndex += 1;
		final int resultVarIndex = maxVarIndex += 1;
		final int endTimeVarIndex = maxVarIndex += 2;

		List<Patch> atStartPatches = instructionPatchesMap.get(0).get(PatchAction.AT_START);
		if (exportFormat != null) {
			// Store time at the beginning so that we can calculate the duration later
			atStartPatches.add(Utilities.getStoreTimePatch(startTimeVarIndex));
		}

		// Clear feature state in SliceWriter on method start. After the method
		// execution, other code can use the feature information from the executed slice
		final String loggerType = Util.makeType(FeatureLogger.class);
		final String executionLoggerType = Util.makeType(FeatureLoggerExecution.class);
		atStartPatches.add(new Patch() {
			@Override
			public void emitTo(Output w) {
				// Create the new FeatureLogger-Object
				w.emit(Util.makeInvoke(FeatureLogger.class, "getInstance", new Class[] {}));
				w.emit(StoreInstruction.make(loggerType, loggerVarIndex));

				// Initialize every feature at the beginning
				instructionIndexes.forEach(instructionIndex -> {
					w.emit(LoadInstruction.make(loggerType, loggerVarIndex));
					w.emit(ConstantInstruction.make(instructionIndex));
					w.emit(ConstantInstruction.make(0)); // do not throw an exception
					w.emit(Util.makeInvoke(FeatureLogger.class, "initializeFeature",
							new Class[] { int.class, boolean.class }));

					// Add instruction default values
					// If there is a specified patch for the feature, use the patch instead of the
					// default value
					if (featurePatchMap.containsKey(instructionIndex)) {
						// TODO Handle more instruction types here
						if (instructions[instructionIndex] instanceof ConstantInstruction) {
							ConstantInstruction instruction = (ConstantInstruction) instructions[instructionIndex];
							w.emit(LoadInstruction.make(loggerType, loggerVarIndex));
							w.emit(ConstantInstruction.make(instructionIndex));
							Patch patch = featurePatchMap.get(instructionIndex);
							patch.emitTo(w);
							Utilities.convertIfNecessary(w, TypeSignature.make(instruction.getType()));
							w.emit(Util.makeInvoke(FeatureLogger.class, "setFeatureDefaultValue",
									new Class[] { int.class, Object.class }));
						}
					} else {
						// Handle all other instructions were no explicit patch is given
						// We can be sure that a not modified constant has the constant value
						if (instructions[instructionIndex] instanceof ConstantInstruction) {
							ConstantInstruction instruction = (ConstantInstruction) instructions[instructionIndex];
							w.emit(LoadInstruction.make(loggerType, loggerVarIndex));
							w.emit(ConstantInstruction.make(instructionIndex));
							w.emit(ConstantInstruction.make(instruction.getType(), instruction.getValue()));
							Utilities.convertIfNecessary(w, TypeSignature.make(instruction.getType()));
							w.emit(Util.makeInvoke(FeatureLogger.class, "setFeatureDefaultValue",
									new Class[] { int.class, Object.class }));
						}
					}
				});

				// Create an execution object
				w.emit(LoadInstruction.make(loggerType, loggerVarIndex));
				w.emit(Util.makeInvoke(FeatureLogger.class, "createExecution", new Class[] {}));
				w.emit(StoreInstruction.make(executionLoggerType, executionLoggerVarIndex));
			}
		});

		// Patch every feature to get the value
		for (int instructionIndex : instructionIndexes) {
			IInstruction featureInstruction = instructions[instructionIndex];

			boolean allowValueOverwrite = instructionsInCycles.contains(instructionIndex);

			if (featureInstruction instanceof ConditionalBranchInstruction) {
				// Special handing before a conditional jump.
				// The ConditionalBranchInstruction consumes two elements from the stack. To
				// notice what truth value was evaluated we have to assume "false" beforehand.
				// If the condition was not met, the instruction directly after the
				// ConditionalBranchInstruction is set to save the truth value "true".

				// 0 log truth value "false" <=== Thats what we are doing here
				// 1 operand 1
				// 2 operand 2
				// 3 ConditionalBranchInstruction with jump target 6
				// 4 log truth value "true" <=== We do that later
				// 5 {whatever was in the body of the ConditionalBranchInstruction}
				// 6 first instruction after the conditional
				ConditionalBranchInstruction instruction = (ConditionalBranchInstruction) featureInstruction;

				List<Patch> beforeCBIpatches = instructionPatchesMap.get(instructionIndex - 2).get(PatchAction.BEFORE);
				Patch patch = new Patch() {
					@Override
					public void emitTo(Output w) {
						w.emit(ConstantInstruction.make(0));
						Patch featureLoggerLogPatch = getFeatureLoggerLogPatch(instructionIndex, resultVarIndex,
								instruction.getType(), executionLoggerVarIndex);
						featureLoggerLogPatch.emitTo(w);
						w.emit(PopInstruction.make(1));
					}
				};
				beforeCBIpatches.add(patch);

				allowValueOverwrite = true;
//				List<Patch> beforeNextCBIpatches = instructionPatchesMap.get(instructionIndex).get(PatchAction.AFTER);
//				patch = new Patch() {
//					@Override
//					public void emitTo(Output w) {
//						w.emit(ConstantInstruction.make(1));
//						Patch featureLoggerLogPatch = getFeatureLoggerLogPatch(instructionIndex, resultVarIndex,
//								instruction.getType(), executionLoggerVarIndex, true);
//						featureLoggerLogPatch.emitTo(w);
//						w.emit(PopInstruction.make(1));
//					}
//				};
//				beforeNextCBIpatches.add(patch);
			}

			// Replace the original feature if a patch is given
			if (featurePatchMap.containsKey(instructionIndex)) {
				List<Patch> patches = instructionPatchesMap.get(instructionIndex).get(PatchAction.REPLACE);
				patches.add(featurePatchMap.get(instructionIndex));
			}

			// Logging patch for instruction value
			Patch resultPatch = getResultAfterPatch(methodData, featureInstruction, instructionIndex, resultVarIndex,
					instructionPopMap, executionLoggerVarIndex, allowValueOverwrite);
			List<Patch> afterPatches = instructionPatchesMap.get(instructionIndex).get(PatchAction.AFTER);
			afterPatches.add(resultPatch);

			if (featureInstruction instanceof ConditionalBranchInstruction) {
				// We just pushed a "true" on the stack and since it is not consumed by any
				// other instruction, we need to pop it ourself
				afterPatches.add(new Patch() {
					@Override
					public void emitTo(Output w) {
						w.emit(PopInstruction.make(1));
					}
				});
			}

			// Pop remaining elements on the stack for instruction
			Integer elementPopSize = instructionPopMap.get(instructionIndex);
			if (elementPopSize != null) {
				// TODO Why do I not need to remove multiple elements here? Stack size is
				// definitively bigger than 1 (for example for a long data type) or does the
				// 2-sized elements only belong to local variables????
//				for (int i = 0; i < elementPopSize; i++) {
				afterPatches.add(new Patch() {
					@Override
					public void emitTo(Output w) {
						w.emit(PopInstruction.make(1));
					}
				});
//				}
			}
		}

		// Add AFTER-Patches (PopInstruction)
		for (Entry<Integer, Integer> entry : instructionPopMap.entrySet()) {
			int instructionIndex = entry.getKey();
			int elementsToPop = entry.getValue();

			Map<PatchAction, List<Patch>> patchList = instructionPatchesMap.get(instructionIndex);
			patchList.get(PatchAction.AFTER).add(new Patch() {
				@Override
				public void emitTo(Output w) {
					for (int popIteration = 0; popIteration < elementsToPop; popIteration++) {
						w.emit(PopInstruction.make(1));
					}
				}
			});
		}

		// Instrument all method endings
		for (int instructionIndex : instructionIndexesToKeep) {
			IInstruction instruction = instructions[instructionIndex];
			if (instructionIndex != lastInstructionIndex && instruction instanceof ReturnInstruction) {
				List<Patch> patches = instructionPatchesMap.get(instructionIndex).get(PatchAction.BEFORE);
				patches.add(Utilities.getStoreTimePatch(endTimeVarIndex));
				patches.add(getExecutionEndPatch(executionLoggerVarIndex, startTimeVarIndex, endTimeVarIndex));

				if (exportFormat != null) {
					patches.add(getWriteFilePatch(loggerVarIndex));
				}
			}
		}

		// At the end of the method, we save the slicer results
		if (exportFormat != null) {
			List<Patch> patches = instructionPatchesMap.get(lastInstructionIndex).get(PatchAction.BEFORE);
			patches.add(Utilities.getStoreTimePatch(endTimeVarIndex));
			patches.add(getExecutionEndPatch(executionLoggerVarIndex, startTimeVarIndex, endTimeVarIndex));
			patches.add(getWriteFilePatch(loggerVarIndex));
		}

		// Add a return statement to the end of the method if there is not any
		if (!instructionIndexesToKeep.contains(lastInstructionIndex)) {
			List<Patch> patches = instructionPatchesMap.get(lastInstructionIndex).get(PatchAction.BEFORE);
			patches.add(new Patch() {
				@Override
				public void emitTo(Output w) {
					// Per convention we return void at the end
					w.emit(ReturnInstruction.make(Constants.TYPE_void));
				}
			});
		}

		// Apply patches from map
		applyPatches(methodEditor, instructionIndexesToKeep, instructionPatchesMap);

		methodEditor.applyPatches();

		return new InstrumentedMethod(methodEditor, loggerVarIndex);
	}

	protected Patch getResultAfterPatch(MethodData methodData, final IInstruction instruction, int instructionIndex,
			final int resultVarIndex, Map<Integer, Integer> instructionPopMap, int executionLoggerVarIndex,
			boolean allowValueOverwrite) {
		return new Patch() {
			@Override
			public void emitTo(Output w) {
				// Prepare the feature value to appear on the stack
				String type = null;
				if (instruction instanceof LoadInstruction) {
					// Nothing to push on stack (value is already there)
					LoadInstruction instruction2 = (LoadInstruction) instruction;
					type = instruction2.getType();
				} else if (instruction instanceof StoreInstruction) {
					// A StoreFeature consumes the top-most element and saves it into a variable
					// since we are interested in the value, we have to load (with a
					// LoadInstruction) it again
					StoreInstruction instruction2 = (StoreInstruction) instruction;
					type = instruction2.getType();
					w.emit(LoadInstruction.make(type, instruction2.getVarIndex()));
				} else if (instruction instanceof BinaryOpInstruction) {
					// Nothing to push on stack (value is already there)
					BinaryOpInstruction instruction2 = (BinaryOpInstruction) instruction;
					type = instruction2.getType();
				} else if (instruction instanceof ConditionalBranchInstruction) {
					// A ConditionalBranchInstruction jumps if the condition is met. Directly after
					// the instruction, we only can be sure that the result was positive (true)
					// because the instruction does not instruct a jump.
					ConditionalBranchInstruction instruction2 = (ConditionalBranchInstruction) instruction;
					type = Constants.TYPE_boolean;
					// Push a true (1) since following the normal program call flow means not
					// jumping anywhere
					w.emit(ConstantInstruction.make(1));
				} else if (instruction instanceof GetInstruction) {
					// A GetInstruction already pushed a value on the stack
					GetInstruction instruction2 = (GetInstruction) instruction;
					type = instruction2.getFieldType();
				} else if (instruction instanceof InvokeInstruction) {
					// A InvokeInstruction already pushed a value on the stack
					InvokeInstruction instruction2 = (InvokeInstruction) instruction;
//					type = instruction2.getPushedType(null);
					TypeName typeName = StringStuff.parseForReturnTypeName(instruction2.getMethodSignature());
					type = typeName.toString();
					if (!typeName.isPrimitiveType()) {
						type += ";";
					}
				} else if (instruction instanceof InvokeDynamicInstruction) {
					// A InvokeInstruction already pushed a value on the stack
					InvokeDynamicInstruction instruction2 = (InvokeDynamicInstruction) instruction;
//					type = instruction2.getPushedType(null);
					TypeName typeName = StringStuff.parseForReturnTypeName(instruction2.getMethodSignature());
					type = typeName.toString();
					if (!typeName.isPrimitiveType()) {
						type += ";";
					}
				} else if (instruction instanceof ConstantInstruction) {
					// A ConstantInstruction already pushed a value on the stack
					ConstantInstruction instruction2 = (ConstantInstruction) instruction;
					type = instruction2.getType();
				} else if (instruction instanceof ConversionInstruction) {
					// A ConversionInstruction already pushed a value on the stack
					ConversionInstruction instruction2 = (ConversionInstruction) instruction;
					type = instruction2.getToType();
				} else if (instruction instanceof ArrayLoadInstruction) {
					// A ArrayLoadInstruction already pushed an object on the stack
					ArrayLoadInstruction instruction2 = (ArrayLoadInstruction) instruction;
					type = instruction2.getType();
				} else if (instruction instanceof GotoInstruction) {
					// A GotoInstruction does not push anything and has no type
					type = Constants.TYPE_void;
				} else if (instruction instanceof ArrayLengthInstruction) {
//					ArrayLengthInstruction instruction2 = (ArrayLengthInstruction) instruction;
					// An ArrayLengthInstruction always pushes an int
					type = Constants.TYPE_int;
				} else if (instruction instanceof PopInstruction) {
//					PopInstruction instruction2 = (PopInstruction) instruction;
					// A PopInstruction never pushes anything
					type = Constants.TYPE_void;
				} else if (instruction instanceof ReturnInstruction) {
//					ReturnInstruction instruction2 = (ReturnInstruction) instruction;
					// A ReturnInstruction never pushes anything
					type = Constants.TYPE_void;
				} else if (instruction instanceof NewInstruction) {
					NewInstruction instruction2 = (NewInstruction) instruction;
					// A NewInstruction always pushes some element
					type = instruction2.getType();
				} else if (instruction instanceof DupInstruction) {
					DupInstruction instruction2 = (DupInstruction) instruction;
					// A DupInstruction always pushes two elements
					// TODO How to determine the type? Disabled logging for now
					type = Constants.TYPE_void;
				}

				if (type == null) {
					throw new NullPointerException(
							"type of instruction '" + instruction + "' with index " + instructionIndex + " is null");
				}

				if (type.equals(Constants.TYPE_void)) {
					// The return value of the instruction is void
					if (instruction instanceof InvokeInstruction) {
						// Check if the instruction is a recursive invoke instruction. If so, we can
						// count the number of invocations.
						InvokeInstruction instruction2 = (InvokeInstruction) instruction;
						if (Utilities.isRecursiveInvokeInstruction(methodData, instruction2)) {
							// Increment the last value by 1
							w.emit(LoadInstruction.make(Util.makeType(FeatureLoggerExecution.class),
									executionLoggerVarIndex));
							w.emit(ConstantInstruction.make(instructionIndex));
							w.emit(ConstantInstruction.make(1));
							Utilities.convertIfNecessary(w, TypeSignature.make(Constants.TYPE_int));
							w.emit(Util.makeInvoke(FeatureLoggerExecution.class, "log",
									new Class[] { int.class, Object.class }));
						}
						return;
					} else {
						// If the type is void we cannot log anything and so does not influence
						return;
					}
				}

				// Special handling for boolean (Z) since the bytecode only knows int (I)
				if (Constants.TYPE_boolean.equals(type)) {
					type = Constants.TYPE_int;
				}

				// Log the value on the stack in local variable "resultVarIndex"
				getFeatureLoggerLogPatch(instructionIndex, resultVarIndex, type, executionLoggerVarIndex,
						allowValueOverwrite).emitTo(w);
			}
		};
	}

	protected Patch getFeatureLoggerLogPatch(int instructionIndex, final int resultVarIndex, String type,
			int executionLoggerVarIndex) {
		return getFeatureLoggerLogPatch(instructionIndex, resultVarIndex, type, executionLoggerVarIndex, false);
	}

	protected Patch getFeatureLoggerLogPatch(int instructionIndex, final int resultVarIndex, String type,
			int executionLoggerVarIndex, boolean allowValueOverwrite) {

		return new Patch() {
			@Override
			public void emitTo(Output w) {
				// Special handling for boolean (Z) since the bytecode only knows int (I)
				String type2 = type;
				if (Constants.TYPE_boolean.equals(type)) {
					type2 = Constants.TYPE_int;
				}

				// Result of slice is on the top of the stack save it from where we can load it
				// again later
				w.emit(StoreInstruction.make(type2, resultVarIndex));

				// featureLogger.log(instructionIndex, value)
				w.emit(LoadInstruction.make(Util.makeType(FeatureLoggerExecution.class), executionLoggerVarIndex));

				// Export the feature value and time (prepare the parameters first)
				w.emit(ConstantInstruction.make(instructionIndex));
				w.emit(LoadInstruction.make(type2, resultVarIndex));
				Utilities.convertIfNecessary(w, TypeSignature.make(type2));

				if (allowValueOverwrite) {
					w.emit(ConstantInstruction.make(1));
					w.emit(Util.makeInvoke(FeatureLoggerExecution.class, "log",
							new Class[] { int.class, Object.class, boolean.class }));
				} else {
					w.emit(Util.makeInvoke(FeatureLoggerExecution.class, "log",
							new Class[] { int.class, Object.class }));
				}

				w.emit(LoadInstruction.make(type2, resultVarIndex));
			}
		};
	}

	private static void applyPatches(MethodEditor methodEditor, Set<Integer> instructionIndexesToKeep,
			Map<Integer, Map<PatchAction, List<Patch>>> instructionPatchesMap) {
		for (Entry<Integer, Map<PatchAction, List<Patch>>> entry : instructionPatchesMap.entrySet()) {
			int instructionIndex = entry.getKey();
			Map<PatchAction, List<Patch>> patchMap = entry.getValue();

			if (patchMap.get(PatchAction.REPLACE).isEmpty() && !instructionIndexesToKeep.contains(instructionIndex)) {
				methodEditor.replaceWith(instructionIndex, Utilities.getEmptyPatch());
			}

			for (Entry<PatchAction, List<Patch>> entry2 : patchMap.entrySet()) {
				PatchAction patchAction = entry2.getKey();
				List<Patch> patchList = entry2.getValue();
				if (patchList.isEmpty()) {
					continue;
				}

				List<Patch> patchListCopy = new ArrayList<>(patchList);

				switch (patchAction) {
				case BEFORE:
					methodEditor.insertBefore(instructionIndex, new Patch() {
						@Override
						public void emitTo(Output w) {
							patchList.forEach(patch -> patch.emitTo(w));
						}
					});
					break;
				case AFTER:
//					Collections.reverse(patchListCopy);
					methodEditor.insertAfter(instructionIndex, new Patch() {
						@Override
						public void emitTo(Output w) {
							patchListCopy.forEach(patch -> patch.emitTo(w));
						}
					});
					break;
				case REPLACE:
					methodEditor.replaceWith(instructionIndex, new Patch() {
						@Override
						public void emitTo(Output w) {
							patchList.forEach(patch -> patch.emitTo(w));
						}
					});
					break;
				case AT_START:
					Collections.reverse(patchListCopy);
					methodEditor.insertAtStart(new Patch() {
						@Override
						public void emitTo(Output w) {
							patchListCopy.forEach(patch -> patch.emitTo(w));
						}
					});
					break;
				case AFTER_BODY:
					methodEditor.insertAfterBody(new Patch() {
						@Override
						public void emitTo(Output w) {
							patchList.forEach(patch -> patch.emitTo(w));
						}
					});
					break;
				}
			}
		}
	}

	private Patch getVarRenumberPatch(Map<Integer, Set<Integer>> varIndexesToRenumber, IInstruction instruction,
			int index) {
		Integer newVarIndex = null;
		if (instruction instanceof ILoadInstruction || instruction instanceof IStoreInstruction) {
			for (Entry<Integer, Set<Integer>> entry : varIndexesToRenumber.entrySet()) {
				if (entry.getValue().contains(index)) {
					newVarIndex = entry.getKey();
				}
			}
		}

		if (newVarIndex != null) {
			final int finalNewVarIndex = newVarIndex;
			if (instruction instanceof ILoadInstruction) {
				ILoadInstruction loadInstruction = (ILoadInstruction) instruction;
				return new Patch() {
					@Override
					public void emitTo(Output w) {
						w.emit(LoadInstruction.make(loadInstruction.getType(), finalNewVarIndex));
					}
				};
			}
			if (instruction instanceof IStoreInstruction) {
				IStoreInstruction storeInstruction = (IStoreInstruction) instruction;
				return new Patch() {

					@Override
					public void emitTo(Output w) {
						w.emit(StoreInstruction.make(storeInstruction.getType(), finalNewVarIndex));
					}

				};
			}
		}
		return null;
	}

	protected Patch getExecutionEndPatch(int executionLoggerVarIndex, int startTimeVarIndex, int endTimeVarIndex) {
		return new Patch() {
			@Override
			public void emitTo(Output w) {
				w.emit(LoadInstruction.make(Util.makeType(FeatureLoggerExecution.class), executionLoggerVarIndex));
				w.emit(LoadInstruction.make(Constants.TYPE_long, startTimeVarIndex));
				w.emit(LoadInstruction.make(Constants.TYPE_long, endTimeVarIndex));
				w.emit(Util.makeInvoke(FeatureLoggerExecution.class, "end", new Class[] { long.class, long.class }));
			}
		};
	}

	protected Patch getWriteFilePatch(int loggerVarIndex) {
		return new Patch() {
			@Override
			public void emitTo(Output w) {
				// FeatureValueWriter.writeCSV(path)
				w.emit(ConstantInstruction.makeString(resultFilePath));
				w.emit(LoadInstruction.make(Util.makeType(FeatureLogger.class), loggerVarIndex));
				switch (exportFormat) {
				case CSV:
					w.emit(Util.makeInvoke(SliceWriter.class, "writeCSV",
							new Class[] { String.class, FeatureLogger.class }));
					break;
				case XML:
					w.emit(Util.makeInvoke(SliceWriter.class, "writeXML",
							new Class[] { String.class, FeatureLogger.class }));
					break;
				}
			}
		};
	}

//	protected Patch getStoreTimeTakenPatch(int timeStartVarIndex, int timeDurationVarIndex) {
//		return new Patch() {
//			@Override
//			public void emitTo(Output w) {
//				// Java-Code
////				long timeDuration = System.currentTimeMillis() - timeMillis;
//
//				// Bytecode
////			    INVOKESTATIC java/lang/System.currentTimeMillis()J
////			    LLOAD 1 [timeStartVarIndex]
////			    LSUB
////				LSTORE 2 [timeDurationVarIndex]
//
//				w.emit(LoadInstruction.make(Constants.TYPE_long, timeDurationVarIndex));
//				w.emit(LoadInstruction.make(Constants.TYPE_long, timeStartVarIndex));
//				w.emit(BinaryOpInstruction.make(Constants.TYPE_long, Operator.SUB));
//				w.emit(StoreInstruction.make(Constants.TYPE_long, timeDurationVarIndex));
//			}
//		};
//	}

	/**
	 * Finalizes the instrumentalization. This consists out of adding additional
	 * required libraries and classes
	 * 
	 * @param mainClass
	 * 
	 * @throws DecoderException
	 */
	public void finalize() throws IllegalStateException, IOException, DecoderException {
		List<String> jars = new ArrayList<>();

		// Add external libraries
		File addJarLib = new File(baseDirFile.getAbsolutePath() + File.separatorChar + ADDITIONAL_JARS_PATH);
		File[] jarFiles = addJarLib.listFiles(f -> f.getName().endsWith(".jar"));
		if (jarFiles != null) {
			for (File file : jarFiles) {
				jars.add(file.getPath());
			}
		}

//		String exportJarPath = Config.getInstance().getExport().getExportJarPath();
		String additionalJarsBasePath = FilenameUtils.getFullPath(additionalJarsPath);
		if (additionalJarsBasePath == null) {
			additionalJarsBasePath = new File(".").getPath();
		}

		// Add additional jars to instrumented executable.
		for (String exportJar : exportJars) {
			String exportJarPath = FilenameUtils.concat(additionalJarsBasePath, exportJar);
//			String exportJarPath = additionalJarsBasePath + exportJar;

			jars.add(exportJarPath);
		}

		for (String jar : jars) {
			addJar(jar);
		}

		instrumenter.close();

		// Correct the META-INF/ files due to instrumentation
		String tempOutputPath = instrumenter.getOutputFile().getPath();
		Utilities.correctJarManifests(tempOutputPath, outputPath, mainClass);
		new File(tempOutputPath).delete();
	}

	protected void addJar(String jarPath) throws IOException {
//			System.out.println(jar);
		JarFile jarFile = new JarFile(jarPath);
		for (JarEntry entry : Iterator2Iterable.make(jarFile.entries().asIterator())) {
			String entryName = entry.getName();
			if (duplicateEntrySet.contains(entryName)) {
				continue;
			}
			if (!jarPath.equals(inputPath) && entryName.startsWith("META-INF/")) {
				continue;
			}
//				System.out.println(entryName);
			duplicateEntrySet.add(entryName);
			instrumenter.addInputJarEntry(new File(jarPath), entryName);
		}
		jarFile.close();
	}

	protected OfflineInstrumenter getInstrumenter() {
		return instrumenter;
	}

	protected String getMethodSignature() {
		return methodSignature;
	}

	public String getAdditionalJarsPath() {
		return additionalJarsPath;
	}

	public void setVerbose(boolean verbose) {
		this.verbose = verbose;
	}
}
