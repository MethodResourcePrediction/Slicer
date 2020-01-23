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

import com.ibm.wala.shrikeBT.ArrayLoadInstruction;
import com.ibm.wala.shrikeBT.BinaryOpInstruction;
import com.ibm.wala.shrikeBT.ConditionalBranchInstruction;
import com.ibm.wala.shrikeBT.ConstantInstruction;
import com.ibm.wala.shrikeBT.Constants;
import com.ibm.wala.shrikeBT.ConversionInstruction;
import com.ibm.wala.shrikeBT.GetInstruction;
import com.ibm.wala.shrikeBT.GotoInstruction;
import com.ibm.wala.shrikeBT.IInstruction;
import com.ibm.wala.shrikeBT.IInvokeInstruction;
import com.ibm.wala.shrikeBT.ILoadInstruction;
import com.ibm.wala.shrikeBT.IStoreInstruction;
import com.ibm.wala.shrikeBT.InvokeInstruction;
import com.ibm.wala.shrikeBT.LoadInstruction;
import com.ibm.wala.shrikeBT.MethodData;
import com.ibm.wala.shrikeBT.MethodEditor;
import com.ibm.wala.shrikeBT.MethodEditor.Output;
import com.ibm.wala.shrikeBT.MethodEditor.Patch;
import com.ibm.wala.shrikeBT.ReturnInstruction;
import com.ibm.wala.shrikeBT.StoreInstruction;
import com.ibm.wala.shrikeBT.Util;
import com.ibm.wala.shrikeBT.shrikeCT.CTCompiler;
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
import de.rherzog.master.thesis.slicer.instrumenter.export.Nothing;
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
			Set<Integer> instructionIndexesToIgnore, Map<Integer, Set<Integer>> varIndexesToRenumber)
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
					instructionIndexesToIgnore, varIndexesToRenumber);
			instrumentedMethod.getMethodEditor().endPass();

			// Write no matter if there are changes
			ClassWriter cw = ci.emitClass();
			instrumenter.outputModifiedClass(ci, cw);
		}
	}

	protected InstrumentedMethod instrumentMethod(MethodData methodData, Set<Integer> instructionIndexes,
			Set<Integer> instructionIndexesToKeep, Set<Integer> instructionIndexesToIgnore,
			Map<Integer, Set<Integer>> varIndexesToRenumber) {
		return instrumentMethod(methodData, instructionIndexes, instructionIndexesToKeep, instructionIndexesToIgnore,
				Collections.emptyMap(), varIndexesToRenumber);
	}

	private enum PatchAction {
		AT_START, BEFORE, AFTER, AFTER_BODY, REPLACE
	}

	protected InstrumentedMethod instrumentMethod(MethodData methodData, Set<Integer> instructionIndexes,
			Set<Integer> instructionIndexesToKeep, Set<Integer> instructionIndexesToIgnore,
			Map<Integer, Patch> featurePatchMap, Map<Integer, Set<Integer>> varIndexesToRenumber) {
		MethodEditor methodEditor = new MethodEditor(methodData);
		methodEditor.beginPass();

		// Build a List out of the IInstruction array.
		// This list can be used to select the instruction from the report features
		IInstruction[] instructions = methodEditor.getInstructions();

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
		final int resultVarIndex = maxVarIndex += 1;
		final int endTimeVarIndex = maxVarIndex += 1;

		List<Patch> atStartPatches = instructionPatchesMap.get(0).get(PatchAction.AT_START);
		if (exportFormat != null) {
			// Store time at the beginning so that we can calculate the duration later
			atStartPatches.add(Utilities.getStoreTimePatch(startTimeVarIndex));
		}

		// Clear feature state in SliceWriter on method start. After the method
		// execution, other code can use the feature information from the executed slice
		final String loggerType = Util.makeType(FeatureLogger.class);
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

					IInstruction instruction = instructions[instructionIndex];
					if (instruction instanceof IInvokeInstruction) {
						// We got an invoke instruction, check if its recursive
						if (Utilities.isRecursiveInvokeInstruction(methodData, (IInvokeInstruction) instruction)) {
							// For a recursive invoke instruction, we count the number of invocations.
							// Initialize its default value with 0 (no invocations so far)
							w.emit(ConstantInstruction.make(0));
							Utilities.convertIfNecessary(w, TypeSignature.make(Constants.TYPE_int));
							w.emit(Util.makeInvoke(FeatureLogger.class, "initializeFeature",
									new Class[] { int.class, Object.class }));
						} else {
							w.emit(Util.makeInvoke(FeatureLogger.class, "initializeFeature",
									new Class[] { int.class }));
						}
					} else {
						w.emit(Util.makeInvoke(FeatureLogger.class, "initializeFeature", new Class[] { int.class }));
					}
				});
			}
		});

		// Some instructions will leave an element on the stack without being processed
		// by others (because they are removed for the slice). For example:
		// _____ 5: BinaryOp(I,mul)
		// _____ 6: SKIPPED Conversion(I,J)
		// _____ 7: SKIPPED Invoke(STATIC,Ljava/lang/Thread;,sleep,(J)V)
		// _____ 8: Goto(10)
		// The result of instruction 5 would normally consumed by instruction 7 (but
		// which is skipped and therefore not in the slice).
		// For that, we first need to remember which instruction pushed how many
		// elements. Second, as long as there are skipped instructions, count a stack
		// variable to find out how many elements were left "behind" (1 or 2 are only
		// possible). As a third and last step, after the "skipped" instructions end,
		// the previously kept instruction index is stored together with the number of
		// elements to pop.
		// Like that, we can archive a consistent stack size slice
		final Map<Integer, Integer> instructionPopMap = new HashMap<>();
		int lastStackSize = 0, lastInstructionIndex = 0;
		Integer stackSize = null;

		// Iterate all instructions and replace those we wont keep
		for (int index = 0; index < instructions.length; index++) {
			IInstruction instruction = instructions[index];

			List<Patch> replacePatches = instructionPatchesMap.get(index).get(PatchAction.REPLACE);
			if (instructionIndexesToIgnore.contains(index)) {
				// An instruction which should be ignored is necessary to keep but not
				// relevant to the output. We can simply ignore the instruction but cannot
				// delete it. In most cases an ignored instruction is a jump target.
				// Unfortunately, WALA does not allow to instrument NOOP instructions.
				replacePatches.add(new Patch() {
					@Override
					public void emitTo(Output w) {
						w.emit(Util.makeInvoke(Nothing.class, "doNothing"));
					}
				});
//				System.out.println(i + ": IGNORED " + instruction);
			}

			if (!instructionIndexesToKeep.contains(index)) {
				replacePatches.add(Utilities.getEmptyPatch());

				if (stackSize == null) {
					stackSize = lastStackSize;
				}
//				System.out.println(i + ": SKIPPED " + instruction);
			} else {
				// The instruction should be kept

				// Change variable indexes if necessary
				Patch varRenumberPatch = getVarRenumberPatch(varIndexesToRenumber, instruction, index);
				if (varRenumberPatch != null) {
					replacePatches.add(varRenumberPatch);
				}

				// If the stack size was 0 before, we don't need to correct anything
				if (stackSize != null && stackSize != 0) {
					instructionPopMap.put(lastInstructionIndex, lastStackSize);
//					System.out.println("  " + stackSize + " element(s) on stack to correct");
					stackSize = null;
					lastStackSize = 0;
				}
//				System.out.println(i + ": " + instruction);

				lastInstructionIndex = index;
				lastStackSize += Utilities.getPushedSize(instruction) - Utilities.getPoppedSize(instruction);
			}
		}

		// Patch every feature to get the value
		for (int instructionIndex : instructionIndexes) {
			IInstruction featureInstruction = instructions[instructionIndex];

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

				List<Patch> patches = instructionPatchesMap.get(instructionIndex - 2).get(PatchAction.BEFORE);
				patches.add(new Patch() {
					@Override
					public void emitTo(Output w) {
						w.emit(ConstantInstruction.make(0));
						getFeatureLoggerLogPatch(instructionIndex, resultVarIndex, instruction.getType(),
								loggerVarIndex).emitTo(w);
					}
				});
			}

			// Replace the original feature if a patch is given
			if (featurePatchMap.containsKey(instructionIndex)) {
				List<Patch> patches = instructionPatchesMap.get(instructionIndex).get(PatchAction.REPLACE);
				patches.add(featurePatchMap.get(instructionIndex));
			}

			// Logging patch for instruction value
			Patch resultPatch = getResultAfterPatch(methodData, featureInstruction, instructionIndex, resultVarIndex,
					instructionPopMap, loggerVarIndex);
			List<Patch> afterPatches = instructionPatchesMap.get(instructionIndex).get(PatchAction.AFTER);
			afterPatches.add(resultPatch);

			// TODO Pop remaining elements on the stack for instruction
			// TODO TODO TODO Why do I NOT need to remove remaining elements on the stack??? WTF?
			// TODO If I do so, the WALA stack validation fails
//			Integer elementPopSize = instructionPopMap.get(instructionIndex);
//			if (elementPopSize != null) {
//				for (int i = 0; i < elementPopSize - 1; i++) {
//					afterPatches.add(new Patch() {
//						@Override
//						public void emitTo(Output w) {
//							w.emit(PopInstruction.make(1));
//						}
//					});
//				}
//			}
		}

		// At the end of the method, we save the slicer results
		if (exportFormat != null) {
			List<Patch> patches = instructionPatchesMap.get(lastInstructionIndex).get(PatchAction.BEFORE);
			patches.add(Utilities.getStoreTimePatch(endTimeVarIndex));
			patches.add(getWriteFilePatch(startTimeVarIndex, endTimeVarIndex, loggerVarIndex));
		}

		List<Patch> patches = instructionPatchesMap.get(lastInstructionIndex).get(PatchAction.BEFORE);
		patches.add(new Patch() {
			@Override
			public void emitTo(Output w) {
				// Per convention we return void at the end
				w.emit(ReturnInstruction.make(CTCompiler.TYPE_void));
			}
		});

		// Apply patches from map
		applyPatches(methodEditor, instructionPatchesMap);

		methodEditor.applyPatches();

		return new InstrumentedMethod(methodEditor, loggerVarIndex);
	}

	private static void applyPatches(MethodEditor methodEditor,
			Map<Integer, Map<PatchAction, List<Patch>>> instructionPatchesMap) {
		for (Entry<Integer, Map<PatchAction, List<Patch>>> entry : instructionPatchesMap.entrySet()) {
			int instructionIndex = entry.getKey();
			Map<PatchAction, List<Patch>> patchMap = entry.getValue();

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
					Collections.reverse(patchListCopy);
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

	protected Patch getResultAfterPatch(MethodData methodData, final IInstruction instruction, int instructionIndex,
			final int resultVarIndex, Map<Integer, Integer> instructionPopMap, int loggerVarIndex) {
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
					type = instruction2.getType();
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
					type = CTCompiler.TYPE_void;
				}

				if (type == null) {
					throw new NullPointerException(
							"type of instruction '" + instruction + "' with index " + instructionIndex + " is null");
				}

				if (type.equals(CTCompiler.TYPE_void)) {
					// The return value of the instruction is void
					if (instruction instanceof InvokeInstruction) {
						// Check if the instruction is a recursive invoke instruction. If so, we can
						// count the number of invocations.
						InvokeInstruction instruction2 = (InvokeInstruction) instruction;
						if (Utilities.isRecursiveInvokeInstruction(methodData, instruction2)) {
							// Increment the last value by 1
							w.emit(LoadInstruction.make(Util.makeType(FeatureLogger.class), loggerVarIndex));
							w.emit(ConstantInstruction.make(instructionIndex));
							w.emit(ConstantInstruction.make(1));
							Utilities.convertIfNecessary(w, TypeSignature.make(Constants.TYPE_int));
							w.emit(Util.makeInvoke(FeatureLogger.class, "incrementLastBy",
									new Class[] { int.class, Object.class }));
						}
						return;
					} else {
						// If the type is void we cannot log anything and so does not influence
						return;
					}
				}

				// Special handling for boolean (Z) since the bytecode only knows int (I)
				if (CTCompiler.TYPE_boolean.equals(type)) {
					type = CTCompiler.TYPE_int;
				}

				// Log the value on the stack in local variable "resultVarIndex"
				getFeatureLoggerLogPatch(instructionIndex, resultVarIndex, type, loggerVarIndex).emitTo(w);

				// Restore the feature value if there was any
				if (instruction instanceof LoadInstruction) {
					// Restore what we just saved
//					if (!instructionPopMap.containsKey(instructionIndex)) {
					w.emit(LoadInstruction.make(type, resultVarIndex));
//					}
				} else if (instruction instanceof StoreInstruction) {
					// Nothing to restore (there was no element pushed before)
				} else if (instruction instanceof BinaryOpInstruction) {
					// Restore what we just saved
//					if (!instructionPopMap.containsKey(instructionIndex)) {
					w.emit(LoadInstruction.make(type, resultVarIndex));
//					}
				} else if (instruction instanceof ConstantInstruction) {
					ConstantInstruction instruction2 = (ConstantInstruction) instruction;
//					if (!instructionPopMap.containsKey(instructionIndex)) {
					w.emit(instruction2);
//					}
				} else if (instruction instanceof GetInstruction) {
					// Restore what we just saved
//					if (!instructionPopMap.containsKey(instructionIndex)) {
					w.emit(LoadInstruction.make(type, resultVarIndex));
//					}
				} else if (instruction instanceof InvokeInstruction) {
					// Restore what we just saved
//					if (!instructionPopMap.containsKey(instructionIndex)) {
					w.emit(LoadInstruction.make(type, resultVarIndex));
//					}
				} else if (instruction instanceof ConversionInstruction) {
					// Restore what we just saved
//					if (!instructionPopMap.containsKey(instructionIndex)) {
					w.emit(LoadInstruction.make(type, resultVarIndex));
//					}
				} else if (instruction instanceof ArrayLoadInstruction) {
					// Restore what we just saved
//					if (!instructionPopMap.containsKey(instructionIndex)) {
					w.emit(LoadInstruction.make(type, resultVarIndex));
//					}
				} else if (instruction instanceof GotoInstruction) {
					// Nothing to restore (there was no element pushed before)
				}
			}
		};
	}

	protected Patch getFeatureLoggerLogPatch(int instructionIndex, final int resultVarIndex, String type,
			int loggerVarIndex) {

		return new Patch() {
			@Override
			public void emitTo(Output w) {
				// Special handling for boolean (Z) since the bytecode only knows int (I)
				String type2 = type;
				if (CTCompiler.TYPE_boolean.equals(type)) {
					type2 = CTCompiler.TYPE_int;
				}

				// Result of slice is on the top of the stack save it from where we can load it
				// again later
				w.emit(StoreInstruction.make(type2, resultVarIndex));

				// featureLogger.log(instructionIndex, value)
				w.emit(LoadInstruction.make(Util.makeType(FeatureLogger.class), loggerVarIndex));

				// Export the feature value and time (prepare the parameters first)
				w.emit(ConstantInstruction.make(instructionIndex));
				w.emit(LoadInstruction.make(type2, resultVarIndex));
				Utilities.convertIfNecessary(w, TypeSignature.make(type2));

				w.emit(Util.makeInvoke(FeatureLogger.class, "log", new Class[] { int.class, Object.class }));
			}
		};
	}

	protected Patch getWriteFilePatch(int startTimeVarIndex, int endTimeVarIndex, int loggerVarIndex) {
		return new Patch() {
			@Override
			public void emitTo(Output w) {
				// FeatureValueWriter.writeCSV(path)
				w.emit(ConstantInstruction.makeString(resultFilePath));
				w.emit(LoadInstruction.make(CTCompiler.TYPE_long, startTimeVarIndex));
				w.emit(LoadInstruction.make(CTCompiler.TYPE_long, endTimeVarIndex));
				w.emit(LoadInstruction.make(Util.makeType(FeatureLogger.class), loggerVarIndex));
				switch (exportFormat) {
				case CSV:
					w.emit(Util.makeInvoke(SliceWriter.class, "writeCSV",
							new Class[] { String.class, long.class, long.class, FeatureLogger.class }));
					break;
				case XML:
					w.emit(Util.makeInvoke(SliceWriter.class, "writeXML",
							new Class[] { String.class, long.class, long.class, FeatureLogger.class }));
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
//				w.emit(LoadInstruction.make(CTCompiler.TYPE_long, timeDurationVarIndex));
//				w.emit(LoadInstruction.make(CTCompiler.TYPE_long, timeStartVarIndex));
//				w.emit(BinaryOpInstruction.make(CTCompiler.TYPE_long, Operator.SUB));
//				w.emit(StoreInstruction.make(CTCompiler.TYPE_long, timeDurationVarIndex));
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

		// Add additional jars to instrumented executable.
		for (String exportJar : exportJars) {
//			String exportJarPath = FilenameUtils.concat(additionalJarsBasePath, exportJar);
			String exportJarPath = additionalJarsBasePath + exportJar;

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
}
