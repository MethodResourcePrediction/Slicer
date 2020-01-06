package de.rherzog.master.thesis.slicer;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import org.apache.commons.codec.DecoderException;
import org.apache.commons.io.FilenameUtils;

import com.ibm.wala.shrikeBT.ArrayLoadInstruction;
import com.ibm.wala.shrikeBT.BinaryOpInstruction;
import com.ibm.wala.shrikeBT.ConditionalBranchInstruction;
import com.ibm.wala.shrikeBT.ConstantInstruction;
import com.ibm.wala.shrikeBT.ConversionInstruction;
import com.ibm.wala.shrikeBT.DupInstruction;
import com.ibm.wala.shrikeBT.GetInstruction;
import com.ibm.wala.shrikeBT.GotoInstruction;
import com.ibm.wala.shrikeBT.IInstruction;
import com.ibm.wala.shrikeBT.InvokeInstruction;
import com.ibm.wala.shrikeBT.LoadInstruction;
import com.ibm.wala.shrikeBT.MethodData;
import com.ibm.wala.shrikeBT.MethodEditor;
import com.ibm.wala.shrikeBT.MethodEditor.Output;
import com.ibm.wala.shrikeBT.MethodEditor.Patch;
import com.ibm.wala.shrikeBT.NewInstruction;
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

import de.rherzog.master.thesis.slicer.MySlicer.ExportFormat;
import de.rherzog.master.thesis.slicer.instrumenter.export.FeatureLogger;
import de.rherzog.master.thesis.slicer.instrumenter.export.Nothing;
import de.rherzog.master.thesis.slicer.instrumenter.export.SliceWriter;
import de.rherzog.master.thesis.utils.InstrumenterComparator;
import de.rherzog.master.thesis.utils.Utilities;

public class Instrumenter {
	private final static String ADDITIONAL_JARS_PATH = "extra_libs/";

	private OfflineInstrumenter instrumenter;
	private String inputPath;
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
		this.methodSignature = methodSignature;
		this.mainClass = mainClass;
		this.resultFilePath = resultFilePath;
		this.exportFormat = exportFormat;

		File oFile = new File(outputPath);

		baseDirFile = new File("");

		instrumenter = new OfflineInstrumenter();
//		instrumenter.addInputJar(new File(inputPath));
		addJar(inputPath);
		instrumenter.setOutputJar(oFile);
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

	public void instrument(List<Integer> instructionIndexes, Set<Integer> instructionIndexesToKeep,
			Set<Integer> instructionIndexesToIgnore)
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

			SliceMethod sliceMethod = sliceMethod(md, instructionIndexes, instructionIndexesToKeep,
					instructionIndexesToIgnore);
			sliceMethod.getMethodEditor().endPass();

			// Write no matter if there are changes
			ClassWriter cw = ci.emitClass();
			instrumenter.outputModifiedClass(ci, cw);
		}
	}

	protected SliceMethod sliceMethod(MethodData methodData, List<Integer> instructionIndexes,
			Set<Integer> instructionIndexesToKeep, Set<Integer> instructionIndexesToIgnore) {
		return sliceMethod(methodData, instructionIndexes, instructionIndexesToKeep, instructionIndexesToIgnore,
				Collections.emptyMap());
	}

	protected SliceMethod sliceMethod(MethodData methodData, List<Integer> instructionIndexes,
			Set<Integer> instructionIndexesToKeep, Set<Integer> instructionIndexesToIgnore,
			Map<Integer, Patch> featurePatchMap) {
		MethodEditor methodEditor = new MethodEditor(methodData);
		methodEditor.beginPass();

		// Build a List out of the IInstruction array.
		// This list can be used to select the instruction from the report features
		IInstruction[] instructions = methodEditor.getInstructions();

		int maxVarIndex = Utilities.getMaxLocalVarIndex(methodData, instructions);

		int startTimeVarIndex = maxVarIndex;
		if (exportFormat != null) {
			// Store time at the beginning so that we can calculate the duration later
			methodEditor.insertAtStart(Utilities.getStoreTimePatch(startTimeVarIndex));
		}
		maxVarIndex += 2;

		// Clear feature state in SliceWriter on method start. After the method
		// execution, other code can use the feature information from the executed slice
		final int loggerVarIndex = maxVarIndex++;
		final String loggerType = Util.makeType(FeatureLogger.class);
		methodEditor.insertAtStart(new Patch() {
			@Override
			public void emitTo(Output w) {
				// Create the new FeatureLogger-Object
				w.emit(NewInstruction.make(loggerType, 0));
				w.emit(DupInstruction.make(0));
				w.emit(Util.makeInvoke(FeatureLogger.class, "<init>", new Class[] {}));
				w.emit(StoreInstruction.make(loggerType, loggerVarIndex));

				// Initialize every feature at the beginning
				instructionIndexes.forEach(instructionIndex -> {
					w.emit(LoadInstruction.make(loggerType, loggerVarIndex));
					w.emit(ConstantInstruction.make(instructionIndex));
					w.emit(Util.makeInvoke(FeatureLogger.class, "initializeFeature", new Class[] { int.class }));
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
		int lastPushedSize = 0, lastInstructionIndex = 0;
		Integer stackSize = null;

		// Iterate all instructions and replace those we wont keep
		for (int i = 0; i < instructions.length; i++) {
			IInstruction instruction = instructions[i];

			if (instructionIndexesToIgnore.contains(i)) {
				// An instruction which should be ignored is necessary to provide but not
				// relevant to the output. We can simply ignore the instruction but cannot
				// delete it. In most cases an ignored instruction is a jump target.
				methodEditor.replaceWith(i, new Patch() {
					@Override
					public void emitTo(Output w) {
						w.emit(Util.makeInvoke(Nothing.class, "doNothing"));
					}
				});
//				System.out.println(i + ": IGNORED " + instruction);
			}

			if (!instructionIndexesToKeep.contains(i)) {
				methodEditor.replaceWith(i, new Patch() {
					@Override
					public void emitTo(Output w) {
					}
				});

				if (stackSize == null) {
					stackSize = lastPushedSize;
				}
//				System.out.println(i + ": SKIPPED " + instruction);
			} else {
				if (stackSize != null) {
					// If the stack size was 0 before, we don't need to correct anything
					if (stackSize != 0) {
						instructionPopMap.put(lastInstructionIndex, lastPushedSize);
//						System.out.println("  " + stackSize + " element(s) on stack to correct");
					}
					stackSize = null;
				}
//				System.out.println(i + ": " + instruction);

				lastInstructionIndex = i;
				lastPushedSize = instruction.getPushedWordSize();
			}
		}

		// Patch every feature to get the value
		int resultVarIndex = maxVarIndex++;
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

				methodEditor.insertBefore(instructionIndex - 2, new Patch() {
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
				methodEditor.replaceWith(instructionIndex, featurePatchMap.get(instructionIndex));
			}

			Patch resultPatch = getResultAfterPatch(featureInstruction, instructionIndex, resultVarIndex,
					instructionPopMap, loggerVarIndex);
			methodEditor.insertAfter(instructionIndex, resultPatch);
		}

		// At the end of the method, we save the slicer results
		int endTimeVarIndex = maxVarIndex;
		if (exportFormat != null) {
			methodEditor.insertAfter(lastInstructionIndex, Utilities.getStoreTimePatch(endTimeVarIndex));
		}
		maxVarIndex += 2;

		if (exportFormat != null) {
			methodEditor.insertAfter(lastInstructionIndex,
					getWriteFilePatch(startTimeVarIndex, endTimeVarIndex, loggerVarIndex));
		}

		methodEditor.insertAfter(lastInstructionIndex, new Patch() {
			@Override
			public void emitTo(Output w) {
				// Per convention we return void at the end
				w.emit(ReturnInstruction.make(CTCompiler.TYPE_void));
			}
		});

		methodEditor.applyPatches();

		return new SliceMethod(methodEditor, loggerVarIndex);
	}

	protected Patch getResultAfterPatch(final IInstruction instruction, int instructionIndex, final int resultVarIndex,
			Map<Integer, Integer> instructionPopMap, int loggerVarIndex) {
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
					// If the type is void we cannot log anything and so does not influence
					return;
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
					if (!instructionPopMap.containsKey(instructionIndex)) {
						w.emit(LoadInstruction.make(type, resultVarIndex));
					}
				} else if (instruction instanceof StoreInstruction) {
					// Nothing to restore (there was no element pushed before)
				} else if (instruction instanceof BinaryOpInstruction) {
					// Restore what we just saved
					if (!instructionPopMap.containsKey(instructionIndex)) {
						w.emit(LoadInstruction.make(type, resultVarIndex));
					}
				} else if (instruction instanceof ConstantInstruction) {
					ConstantInstruction instruction2 = (ConstantInstruction) instruction;
					if (!instructionPopMap.containsKey(instructionIndex)) {
						w.emit(instruction2);
					}
				} else if (instruction instanceof GetInstruction) {
					// Restore what we just saved
					if (!instructionPopMap.containsKey(instructionIndex)) {
						w.emit(LoadInstruction.make(type, resultVarIndex));
					}
				} else if (instruction instanceof InvokeInstruction) {
					// Restore what we just saved
					if (!instructionPopMap.containsKey(instructionIndex)) {
						w.emit(LoadInstruction.make(type, resultVarIndex));
					}
				} else if (instruction instanceof ConversionInstruction) {
					// Restore what we just saved
					if (!instructionPopMap.containsKey(instructionIndex)) {
						w.emit(LoadInstruction.make(type, resultVarIndex));
					}
				} else if (instruction instanceof ArrayLoadInstruction) {
					// Restore what we just saved
					if (!instructionPopMap.containsKey(instructionIndex)) {
						w.emit(LoadInstruction.make(type, resultVarIndex));
					}
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
		String outputPath = instrumenter.getOutputFile().getPath();
		String tempOutputPath = outputPath + "_";
		File tempOutputFile = new File(tempOutputPath);
		File outputFile = new File(outputPath);
		outputFile.renameTo(tempOutputFile);
		Utilities.correctJarManifests(tempOutputPath, outputPath, mainClass);
		tempOutputFile.delete();
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
