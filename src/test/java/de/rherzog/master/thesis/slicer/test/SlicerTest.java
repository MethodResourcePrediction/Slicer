package de.rherzog.master.thesis.slicer.test;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.net.URLDecoder;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.apache.commons.codec.DecoderException;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import com.ibm.wala.shrikeBT.BinaryOpInstruction;
import com.ibm.wala.shrikeBT.ComparisonInstruction;
import com.ibm.wala.shrikeBT.ConditionalBranchInstruction;
import com.ibm.wala.shrikeBT.ConstantInstruction;
import com.ibm.wala.shrikeBT.Constants;
import com.ibm.wala.shrikeBT.GotoInstruction;
import com.ibm.wala.shrikeBT.IBinaryOpInstruction;
import com.ibm.wala.shrikeBT.IComparisonInstruction;
import com.ibm.wala.shrikeBT.IInstruction;
import com.ibm.wala.shrikeBT.IInvokeInstruction.Dispatch;
import com.ibm.wala.shrikeBT.InvokeInstruction;
import com.ibm.wala.shrikeBT.LoadInstruction;
import com.ibm.wala.shrikeBT.PopInstruction;
import com.ibm.wala.shrikeBT.ReturnInstruction;
import com.ibm.wala.shrikeBT.StoreInstruction;
import com.ibm.wala.shrikeCT.InvalidClassFileException;

import de.rherzog.master.thesis.slicer.SliceResult;
import de.rherzog.master.thesis.slicer.Slicer;

public class SlicerTest {
	private Slicer slicer;
	private String slicerValidationJarPath;

	@Before
	public void setUp() throws Exception {
		// Get path to java class
		String classFilePath = SlicerTest.class.getProtectionDomain().getCodeSource().getLocation().getPath();
		String path = URLDecoder.decode(classFilePath, "UTF-8");

		String jvmPackageName = SlicerValidation.class.getPackageName();
		String packageName = jvmPackageName.replaceAll("\\.", File.separator) + File.separator;

		String slicerValidationClassPath = FilenameUtils.concat(path,
				packageName + SlicerValidation.class.getSimpleName() + ".class");
		slicerValidationJarPath = FilenameUtils.concat(path,
				packageName + SlicerValidation.class.getSimpleName() + ".jar");

		Process process = new ProcessBuilder("jar", "-cfv", slicerValidationJarPath, slicerValidationClassPath).start();
		process.waitFor();
		if (process.exitValue() != 0) {
			System.err.println("Some error occured during jar compilation\n");
			System.err.println("\nStderr:\n" + readInputStream(process.getErrorStream()));
			System.out.println("\nStdout:\n" + readInputStream(process.getInputStream()));
			throw new IOException("Process exited with exit code " + process.exitValue());
		}

		slicer = new Slicer();
	}

	@Test
	public void testSimpleSlice() throws IOException, InvalidClassFileException, InterruptedException,
			IllegalStateException, DecoderException {
		slicer.setInputJar(slicerValidationJarPath); // Path to your application.jar
		slicer.setMethodSignature(
				"Lde.rherzog.master.thesis.slicer.test.SlicerValidation;.simpleMethodCallAndLoopWithParameter(J)V");
		System.out.println(slicer.getMethodSummary());

		// Define you criterion's from some instruction indexes
		// Index ... here is:
		List<Integer> instructionIndexes = Arrays.asList(5);
		Set<Integer> criterionSet = new HashSet<>(instructionIndexes);

		slicer.setInstructionIndexes(criterionSet);

		// Variant 1: Get the instruction slice as a list of instructions
		SliceResult sliceResult = slicer.getSliceResult();
		List<IInstruction> slice = sliceResult.getSlice();
		System.out.println("=== Sliced method ===");

		int index = 0;
		for (IInstruction instruction : slice) {
			System.out.println(index + ": " + instruction.toString());
			index++;
		}

		// Variant 2: Generate a new jar with all dependencies from your original one,
		// but with a sliced method specified above.
		// The parameter is false by default. Given true shows all method graphs
//		String slicedOutputJar = slicer.makeSlicedFile(true);
//		System.out.println("Sliced output jar path" + slicedOutputJar);
	}

	@Test
	public void testSliceReuseVariableWithReinitialization()
			throws IOException, InvalidClassFileException, InterruptedException {
		slicer.setInputJar(slicerValidationJarPath);
		slicer.setMethodSignature(
				"Lde.rherzog.master.thesis.slicer.test.SlicerValidation;.reuseVariableWithReinitialization()V");
		System.out.println(slicer.getMethodSummary());

		Map<Set<Integer>, List<IInstruction>> slicerCriterionResultMap = new HashMap<>();

		slicerCriterionResultMap.put(Set.of(0), Arrays.asList(ConstantInstruction.make(0), PopInstruction.make(1),
				ReturnInstruction.make(Constants.TYPE_void)));
		slicerCriterionResultMap.put(Set.of(1), Arrays.asList(ConstantInstruction.make(0),
				StoreInstruction.make(Constants.TYPE_int, 1), ReturnInstruction.make(Constants.TYPE_void)));
		slicerCriterionResultMap.put(Set.of(2),
				Arrays.asList(GotoInstruction.make(9), InvokeInstruction.make("()V",
						"Lde/rherzog/master/thesis/slicer/instrumenter/export/Nothing;", "doNothing", Dispatch.STATIC),
						ReturnInstruction.make(Constants.TYPE_void)));
		slicerCriterionResultMap.put(Set.of(3),
				Arrays.asList(ConstantInstruction.make(0), StoreInstruction.make(Constants.TYPE_int, 1),
						GotoInstruction.make(9),
						InvokeInstruction.make("()J", "Ljava/lang/System;", "currentTimeMillis", Dispatch.STATIC),
						PopInstruction.make(2), LoadInstruction.make(Constants.TYPE_int, 1),
						ConstantInstruction.make(1),
						BinaryOpInstruction.make(Constants.TYPE_int, IBinaryOpInstruction.Operator.ADD),
						StoreInstruction.make(Constants.TYPE_int, 1), LoadInstruction.make(Constants.TYPE_int, 1),
						ConstantInstruction.make(2), ConditionalBranchInstruction.make(Constants.TYPE_int,
								ConditionalBranchInstruction.Operator.LT, 3),
						ReturnInstruction.make(Constants.TYPE_void)));
		slicerCriterionResultMap.put(Set.of(4),
				Arrays.asList(ConstantInstruction.make(0), StoreInstruction.make(Constants.TYPE_int, 1),
						GotoInstruction.make(9),
						InvokeInstruction.make("()J", "Ljava/lang/System;", "currentTimeMillis", Dispatch.STATIC),
						PopInstruction.make(2), LoadInstruction.make(Constants.TYPE_int, 1),
						ConstantInstruction.make(1),
						BinaryOpInstruction.make(Constants.TYPE_int, IBinaryOpInstruction.Operator.ADD),
						StoreInstruction.make(Constants.TYPE_int, 1), LoadInstruction.make(Constants.TYPE_int, 1),
						ConstantInstruction.make(2), ConditionalBranchInstruction.make(Constants.TYPE_int,
								ConditionalBranchInstruction.Operator.LT, 3),
						ReturnInstruction.make(Constants.TYPE_void)));
		slicerCriterionResultMap.put(Set.of(5),
				Arrays.asList(ConstantInstruction.make(0), StoreInstruction.make(Constants.TYPE_int, 1),
						GotoInstruction.make(9),
						InvokeInstruction.make("()V", "Lde/rherzog/master/thesis/slicer/instrumenter/export/Nothing;",
								"doNothing", Dispatch.STATIC),
						LoadInstruction.make(Constants.TYPE_int, 1), ConstantInstruction.make(1),
						BinaryOpInstruction.make(Constants.TYPE_int, IBinaryOpInstruction.Operator.ADD),
						StoreInstruction.make(Constants.TYPE_int, 1), LoadInstruction.make(Constants.TYPE_int, 1),
						ConstantInstruction.make(2), ConditionalBranchInstruction.make(Constants.TYPE_int,
								ConditionalBranchInstruction.Operator.LT, 3),
						ReturnInstruction.make(Constants.TYPE_void)));
		slicerCriterionResultMap.put(Set.of(6), Arrays.asList(ConstantInstruction.make(1), PopInstruction.make(1),
				ReturnInstruction.make(Constants.TYPE_void)));
		slicerCriterionResultMap.put(Set.of(7),
				Arrays.asList(ConstantInstruction.make(0), StoreInstruction.make(Constants.TYPE_int, 1),
						GotoInstruction.make(9),
						InvokeInstruction.make("()V", "Lde/rherzog/master/thesis/slicer/instrumenter/export/Nothing;",
								"doNothing", Dispatch.STATIC),
						LoadInstruction.make(Constants.TYPE_int, 1), ConstantInstruction.make(1),
						BinaryOpInstruction.make(Constants.TYPE_int, IBinaryOpInstruction.Operator.ADD),
						StoreInstruction.make(Constants.TYPE_int, 1), LoadInstruction.make(Constants.TYPE_int, 1),
						ConstantInstruction.make(2), ConditionalBranchInstruction.make(Constants.TYPE_int,
								ConditionalBranchInstruction.Operator.LT, 3),
						ReturnInstruction.make(Constants.TYPE_void)));
		slicerCriterionResultMap.put(Set.of(8),
				Arrays.asList(ConstantInstruction.make(0), StoreInstruction.make(Constants.TYPE_int, 1),
						GotoInstruction.make(9),
						InvokeInstruction.make("()V", "Lde/rherzog/master/thesis/slicer/instrumenter/export/Nothing;",
								"doNothing", Dispatch.STATIC),
						LoadInstruction.make(Constants.TYPE_int, 1), ConstantInstruction.make(1),
						BinaryOpInstruction.make(Constants.TYPE_int, IBinaryOpInstruction.Operator.ADD),
						StoreInstruction.make(Constants.TYPE_int, 1), LoadInstruction.make(Constants.TYPE_int, 1),
						ConstantInstruction.make(2), ConditionalBranchInstruction.make(Constants.TYPE_int,
								ConditionalBranchInstruction.Operator.LT, 3),
						ReturnInstruction.make(Constants.TYPE_void)));
		slicerCriterionResultMap.put(Set.of(9),
				Arrays.asList(ConstantInstruction.make(0), StoreInstruction.make(Constants.TYPE_int, 1),
						GotoInstruction.make(9),
						InvokeInstruction.make("()V", "Lde/rherzog/master/thesis/slicer/instrumenter/export/Nothing;",
								"doNothing", Dispatch.STATIC),
						LoadInstruction.make(Constants.TYPE_int, 1), ConstantInstruction.make(1),
						BinaryOpInstruction.make(Constants.TYPE_int, IBinaryOpInstruction.Operator.ADD),
						StoreInstruction.make(Constants.TYPE_int, 1), LoadInstruction.make(Constants.TYPE_int, 1),
						ConstantInstruction.make(2), ConditionalBranchInstruction.make(Constants.TYPE_int,
								ConditionalBranchInstruction.Operator.LT, 3),
						ReturnInstruction.make(Constants.TYPE_void)));
		slicerCriterionResultMap.put(Set.of(10), Arrays.asList(ConstantInstruction.make(2), PopInstruction.make(1),
				ReturnInstruction.make(Constants.TYPE_void)));
		slicerCriterionResultMap.put(Set.of(11),
				Arrays.asList(ConstantInstruction.make(0), StoreInstruction.make(Constants.TYPE_int, 1),
						GotoInstruction.make(9),
						InvokeInstruction.make("()V", "Lde/rherzog/master/thesis/slicer/instrumenter/export/Nothing;",
								"doNothing", Dispatch.STATIC),
						LoadInstruction.make(Constants.TYPE_int, 1), ConstantInstruction.make(1),
						BinaryOpInstruction.make(Constants.TYPE_int, IBinaryOpInstruction.Operator.ADD),
						StoreInstruction.make(Constants.TYPE_int, 1), LoadInstruction.make(Constants.TYPE_int, 1),
						ConstantInstruction.make(2), ConditionalBranchInstruction.make(Constants.TYPE_int,
								ConditionalBranchInstruction.Operator.LT, 3),
						ReturnInstruction.make(Constants.TYPE_void)));
		slicerCriterionResultMap.put(Set.of(12),
				Arrays.asList(ConstantInstruction.make(0), StoreInstruction.make(Constants.TYPE_int, 1),
						GotoInstruction.make(9),
						InvokeInstruction.make("()V", "Lde/rherzog/master/thesis/slicer/instrumenter/export/Nothing;",
								"doNothing", Dispatch.STATIC),
						LoadInstruction.make(Constants.TYPE_int, 1), ConstantInstruction.make(1),
						BinaryOpInstruction.make(Constants.TYPE_int, IBinaryOpInstruction.Operator.ADD),
						StoreInstruction.make(Constants.TYPE_int, 1), LoadInstruction.make(Constants.TYPE_int, 1),
						ConstantInstruction.make(2),
						ConditionalBranchInstruction.make(Constants.TYPE_int, ConditionalBranchInstruction.Operator.LT,
								3),
						LoadInstruction.make(Constants.TYPE_int, 1), PopInstruction.make(1),
						ReturnInstruction.make(Constants.TYPE_void)));
		slicerCriterionResultMap.put(Set.of(13), Arrays.asList(ConstantInstruction.make(1), PopInstruction.make(1),
				ReturnInstruction.make(Constants.TYPE_void)));
		slicerCriterionResultMap.put(Set.of(14),
				Arrays.asList(ConstantInstruction.make(0), StoreInstruction.make(Constants.TYPE_int, 1),
						GotoInstruction.make(9),
						InvokeInstruction.make("()V", "Lde/rherzog/master/thesis/slicer/instrumenter/export/Nothing;",
								"doNothing", Dispatch.STATIC),
						LoadInstruction.make(Constants.TYPE_int, 1), ConstantInstruction.make(1),
						BinaryOpInstruction.make(Constants.TYPE_int, IBinaryOpInstruction.Operator.ADD),
						StoreInstruction.make(Constants.TYPE_int, 1), LoadInstruction.make(Constants.TYPE_int, 1),
						ConstantInstruction.make(2),
						ConditionalBranchInstruction.make(Constants.TYPE_int, ConditionalBranchInstruction.Operator.LT,
								3),
						LoadInstruction.make(Constants.TYPE_int, 1), ConstantInstruction.make(1),
						BinaryOpInstruction.make(Constants.TYPE_int, IBinaryOpInstruction.Operator.ADD),
						PopInstruction.make(1), ReturnInstruction.make(Constants.TYPE_void)));
		slicerCriterionResultMap.put(Set.of(15),
				Arrays.asList(ConstantInstruction.make(0), StoreInstruction.make(Constants.TYPE_int, 1),
						GotoInstruction.make(9),
						InvokeInstruction.make("()V", "Lde/rherzog/master/thesis/slicer/instrumenter/export/Nothing;",
								"doNothing", Dispatch.STATIC),
						LoadInstruction.make(Constants.TYPE_int, 1), ConstantInstruction.make(1),
						BinaryOpInstruction.make(Constants.TYPE_int, IBinaryOpInstruction.Operator.ADD),
						StoreInstruction.make(Constants.TYPE_int, 1), LoadInstruction.make(Constants.TYPE_int, 1),
						ConstantInstruction.make(2),
						ConditionalBranchInstruction.make(Constants.TYPE_int, ConditionalBranchInstruction.Operator.LT,
								3),
						LoadInstruction.make(Constants.TYPE_int, 1), ConstantInstruction.make(1),
						BinaryOpInstruction.make(Constants.TYPE_int, IBinaryOpInstruction.Operator.ADD),
						StoreInstruction.make(Constants.TYPE_int, 1), ReturnInstruction.make(Constants.TYPE_void)));
		slicerCriterionResultMap.put(Set.of(16),
				Arrays.asList(GotoInstruction.make(23), InvokeInstruction.make("()V",
						"Lde/rherzog/master/thesis/slicer/instrumenter/export/Nothing;", "doNothing", Dispatch.STATIC),
						ReturnInstruction.make(Constants.TYPE_void)));
		slicerCriterionResultMap.put(Set.of(17),
				Arrays.asList(ConstantInstruction.make(0), StoreInstruction.make(Constants.TYPE_int, 1),
						GotoInstruction.make(9),
						InvokeInstruction.make("()V", "Lde/rherzog/master/thesis/slicer/instrumenter/export/Nothing;",
								"doNothing", Dispatch.STATIC),
						LoadInstruction.make(Constants.TYPE_int, 1), ConstantInstruction.make(1),
						BinaryOpInstruction.make(Constants.TYPE_int, IBinaryOpInstruction.Operator.ADD),
						StoreInstruction.make(Constants.TYPE_int, 1), LoadInstruction.make(Constants.TYPE_int, 1),
						ConstantInstruction.make(2),
						ConditionalBranchInstruction.make(Constants.TYPE_int, ConditionalBranchInstruction.Operator.LT,
								3),
						LoadInstruction.make(Constants.TYPE_int, 1), ConstantInstruction.make(1),
						BinaryOpInstruction.make(Constants.TYPE_int, IBinaryOpInstruction.Operator.ADD),
						StoreInstruction.make(Constants.TYPE_int, 1), GotoInstruction.make(23),
						InvokeInstruction.make("()J", "Ljava/lang/System;", "currentTimeMillis", Dispatch.STATIC),
						PopInstruction.make(2), LoadInstruction.make(Constants.TYPE_int, 1),
						ConstantInstruction.make(1),
						BinaryOpInstruction.make(Constants.TYPE_int, IBinaryOpInstruction.Operator.ADD),
						StoreInstruction.make(Constants.TYPE_int, 1), LoadInstruction.make(Constants.TYPE_int, 1),
						ConstantInstruction.make(5), ConditionalBranchInstruction.make(Constants.TYPE_int,
								ConditionalBranchInstruction.Operator.LT, 17),
						ReturnInstruction.make(Constants.TYPE_void)));
		slicerCriterionResultMap.put(Set.of(18),
				Arrays.asList(ConstantInstruction.make(0), StoreInstruction.make(Constants.TYPE_int, 1),
						GotoInstruction.make(9),
						InvokeInstruction.make("()V", "Lde/rherzog/master/thesis/slicer/instrumenter/export/Nothing;",
								"doNothing", Dispatch.STATIC),
						LoadInstruction.make(Constants.TYPE_int, 1), ConstantInstruction.make(1),
						BinaryOpInstruction.make(Constants.TYPE_int, IBinaryOpInstruction.Operator.ADD),
						StoreInstruction.make(Constants.TYPE_int, 1), LoadInstruction.make(Constants.TYPE_int, 1),
						ConstantInstruction.make(2),
						ConditionalBranchInstruction.make(Constants.TYPE_int, ConditionalBranchInstruction.Operator.LT,
								3),
						LoadInstruction.make(Constants.TYPE_int, 1), ConstantInstruction.make(1),
						BinaryOpInstruction.make(Constants.TYPE_int, IBinaryOpInstruction.Operator.ADD),
						StoreInstruction.make(Constants.TYPE_int, 1), GotoInstruction.make(23),
						InvokeInstruction.make("()J", "Ljava/lang/System;", "currentTimeMillis", Dispatch.STATIC),
						PopInstruction.make(2), LoadInstruction.make(Constants.TYPE_int, 1),
						ConstantInstruction.make(1),
						BinaryOpInstruction.make(Constants.TYPE_int, IBinaryOpInstruction.Operator.ADD),
						StoreInstruction.make(Constants.TYPE_int, 1), LoadInstruction.make(Constants.TYPE_int, 1),
						ConstantInstruction.make(5), ConditionalBranchInstruction.make(Constants.TYPE_int,
								ConditionalBranchInstruction.Operator.LT, 17),
						ReturnInstruction.make(Constants.TYPE_void)));
		slicerCriterionResultMap.put(Set.of(19),
				Arrays.asList(ConstantInstruction.make(0), StoreInstruction.make(Constants.TYPE_int, 1),
						GotoInstruction.make(9),
						InvokeInstruction.make("()V", "Lde/rherzog/master/thesis/slicer/instrumenter/export/Nothing;",
								"doNothing", Dispatch.STATIC),
						LoadInstruction.make(Constants.TYPE_int, 1), ConstantInstruction.make(1),
						BinaryOpInstruction.make(Constants.TYPE_int, IBinaryOpInstruction.Operator.ADD),
						StoreInstruction.make(Constants.TYPE_int, 1), LoadInstruction.make(Constants.TYPE_int, 1),
						ConstantInstruction.make(2),
						ConditionalBranchInstruction.make(Constants.TYPE_int, ConditionalBranchInstruction.Operator.LT,
								3),
						LoadInstruction.make(Constants.TYPE_int, 1), ConstantInstruction.make(1),
						BinaryOpInstruction.make(Constants.TYPE_int, IBinaryOpInstruction.Operator.ADD),
						StoreInstruction.make(Constants.TYPE_int, 1), GotoInstruction.make(23),
						InvokeInstruction.make("()V", "Lde/rherzog/master/thesis/slicer/instrumenter/export/Nothing;",
								"doNothing", Dispatch.STATIC),
						LoadInstruction.make(Constants.TYPE_int, 1), ConstantInstruction.make(1),
						BinaryOpInstruction.make(Constants.TYPE_int, IBinaryOpInstruction.Operator.ADD),
						StoreInstruction.make(Constants.TYPE_int, 1), LoadInstruction.make(Constants.TYPE_int, 1),
						ConstantInstruction.make(5), ConditionalBranchInstruction.make(Constants.TYPE_int,
								ConditionalBranchInstruction.Operator.LT, 17),
						ReturnInstruction.make(Constants.TYPE_void)));
		slicerCriterionResultMap.put(Set.of(20), Arrays.asList(ConstantInstruction.make(1), PopInstruction.make(1),
				ReturnInstruction.make(Constants.TYPE_void)));
		slicerCriterionResultMap.put(Set.of(21),
				Arrays.asList(ConstantInstruction.make(0), StoreInstruction.make(Constants.TYPE_int, 1),
						GotoInstruction.make(9),
						InvokeInstruction.make("()V", "Lde/rherzog/master/thesis/slicer/instrumenter/export/Nothing;",
								"doNothing", Dispatch.STATIC),
						LoadInstruction.make(Constants.TYPE_int, 1), ConstantInstruction.make(1),
						BinaryOpInstruction.make(Constants.TYPE_int, IBinaryOpInstruction.Operator.ADD),
						StoreInstruction.make(Constants.TYPE_int, 1), LoadInstruction.make(Constants.TYPE_int, 1),
						ConstantInstruction.make(2),
						ConditionalBranchInstruction.make(Constants.TYPE_int, ConditionalBranchInstruction.Operator.LT,
								3),
						LoadInstruction.make(Constants.TYPE_int, 1), ConstantInstruction.make(1),
						BinaryOpInstruction.make(Constants.TYPE_int, IBinaryOpInstruction.Operator.ADD),
						StoreInstruction.make(Constants.TYPE_int, 1), GotoInstruction.make(23),
						InvokeInstruction.make("()V", "Lde/rherzog/master/thesis/slicer/instrumenter/export/Nothing;",
								"doNothing", Dispatch.STATIC),
						LoadInstruction.make(Constants.TYPE_int, 1), ConstantInstruction.make(1),
						BinaryOpInstruction.make(Constants.TYPE_int, IBinaryOpInstruction.Operator.ADD),
						StoreInstruction.make(Constants.TYPE_int, 1), LoadInstruction.make(Constants.TYPE_int, 1),
						ConstantInstruction.make(5), ConditionalBranchInstruction.make(Constants.TYPE_int,
								ConditionalBranchInstruction.Operator.LT, 17),
						ReturnInstruction.make(Constants.TYPE_void)));
		slicerCriterionResultMap.put(Set.of(22),
				Arrays.asList(ConstantInstruction.make(0), StoreInstruction.make(Constants.TYPE_int, 1),
						GotoInstruction.make(9),
						InvokeInstruction.make("()V", "Lde/rherzog/master/thesis/slicer/instrumenter/export/Nothing;",
								"doNothing", Dispatch.STATIC),
						LoadInstruction.make(Constants.TYPE_int, 1), ConstantInstruction.make(1),
						BinaryOpInstruction.make(Constants.TYPE_int, IBinaryOpInstruction.Operator.ADD),
						StoreInstruction.make(Constants.TYPE_int, 1), LoadInstruction.make(Constants.TYPE_int, 1),
						ConstantInstruction.make(2),
						ConditionalBranchInstruction.make(Constants.TYPE_int, ConditionalBranchInstruction.Operator.LT,
								3),
						LoadInstruction.make(Constants.TYPE_int, 1), ConstantInstruction.make(1),
						BinaryOpInstruction.make(Constants.TYPE_int, IBinaryOpInstruction.Operator.ADD),
						StoreInstruction.make(Constants.TYPE_int, 1), GotoInstruction.make(23),
						InvokeInstruction.make("()V", "Lde/rherzog/master/thesis/slicer/instrumenter/export/Nothing;",
								"doNothing", Dispatch.STATIC),
						LoadInstruction.make(Constants.TYPE_int, 1), ConstantInstruction.make(1),
						BinaryOpInstruction.make(Constants.TYPE_int, IBinaryOpInstruction.Operator.ADD),
						StoreInstruction.make(Constants.TYPE_int, 1), LoadInstruction.make(Constants.TYPE_int, 1),
						ConstantInstruction.make(5), ConditionalBranchInstruction.make(Constants.TYPE_int,
								ConditionalBranchInstruction.Operator.LT, 17),
						ReturnInstruction.make(Constants.TYPE_void)));
		slicerCriterionResultMap.put(Set.of(23),
				Arrays.asList(ConstantInstruction.make(0), StoreInstruction.make(Constants.TYPE_int, 1),
						GotoInstruction.make(9),
						InvokeInstruction.make("()V", "Lde/rherzog/master/thesis/slicer/instrumenter/export/Nothing;",
								"doNothing", Dispatch.STATIC),
						LoadInstruction.make(Constants.TYPE_int, 1), ConstantInstruction.make(1),
						BinaryOpInstruction.make(Constants.TYPE_int, IBinaryOpInstruction.Operator.ADD),
						StoreInstruction.make(Constants.TYPE_int, 1), LoadInstruction.make(Constants.TYPE_int, 1),
						ConstantInstruction.make(2),
						ConditionalBranchInstruction.make(Constants.TYPE_int, ConditionalBranchInstruction.Operator.LT,
								3),
						LoadInstruction.make(Constants.TYPE_int, 1), ConstantInstruction.make(1),
						BinaryOpInstruction.make(Constants.TYPE_int, IBinaryOpInstruction.Operator.ADD),
						StoreInstruction.make(Constants.TYPE_int, 1), GotoInstruction.make(23),
						InvokeInstruction.make("()V", "Lde/rherzog/master/thesis/slicer/instrumenter/export/Nothing;",
								"doNothing", Dispatch.STATIC),
						LoadInstruction.make(Constants.TYPE_int, 1), ConstantInstruction.make(1),
						BinaryOpInstruction.make(Constants.TYPE_int, IBinaryOpInstruction.Operator.ADD),
						StoreInstruction.make(Constants.TYPE_int, 1), LoadInstruction.make(Constants.TYPE_int, 1),
						ConstantInstruction.make(5), ConditionalBranchInstruction.make(Constants.TYPE_int,
								ConditionalBranchInstruction.Operator.LT, 17),
						ReturnInstruction.make(Constants.TYPE_void)));
		slicerCriterionResultMap.put(Set.of(24), Arrays.asList(ConstantInstruction.make(5), PopInstruction.make(1),
				ReturnInstruction.make(Constants.TYPE_void)));
		slicerCriterionResultMap.put(Set.of(25),
				Arrays.asList(ConstantInstruction.make(0), StoreInstruction.make(Constants.TYPE_int, 1),
						GotoInstruction.make(9),
						InvokeInstruction.make("()V", "Lde/rherzog/master/thesis/slicer/instrumenter/export/Nothing;",
								"doNothing", Dispatch.STATIC),
						LoadInstruction.make(Constants.TYPE_int, 1), ConstantInstruction.make(1),
						BinaryOpInstruction.make(Constants.TYPE_int, IBinaryOpInstruction.Operator.ADD),
						StoreInstruction.make(Constants.TYPE_int, 1), LoadInstruction.make(Constants.TYPE_int, 1),
						ConstantInstruction.make(2),
						ConditionalBranchInstruction.make(Constants.TYPE_int, ConditionalBranchInstruction.Operator.LT,
								3),
						LoadInstruction.make(Constants.TYPE_int, 1), ConstantInstruction.make(1),
						BinaryOpInstruction.make(Constants.TYPE_int, IBinaryOpInstruction.Operator.ADD),
						StoreInstruction.make(Constants.TYPE_int, 1), GotoInstruction.make(23),
						InvokeInstruction.make("()V", "Lde/rherzog/master/thesis/slicer/instrumenter/export/Nothing;",
								"doNothing", Dispatch.STATIC),
						LoadInstruction.make(Constants.TYPE_int, 1), ConstantInstruction.make(1),
						BinaryOpInstruction.make(Constants.TYPE_int, IBinaryOpInstruction.Operator.ADD),
						StoreInstruction.make(Constants.TYPE_int, 1), LoadInstruction.make(Constants.TYPE_int, 1),
						ConstantInstruction.make(5), ConditionalBranchInstruction.make(Constants.TYPE_int,
								ConditionalBranchInstruction.Operator.LT, 17),
						ReturnInstruction.make(Constants.TYPE_void)));
		slicerCriterionResultMap.put(Set.of(26), Arrays.asList(ReturnInstruction.make(Constants.TYPE_void)));

		validateSliceResults(slicerCriterionResultMap);
	}

	@Test
	public void testSliceReuseVariableWithReinitializationDoubleSized()
			throws IOException, InvalidClassFileException, InterruptedException {
		slicer.setInputJar(slicerValidationJarPath);
		slicer.setMethodSignature(
				"Lde.rherzog.master.thesis.slicer.test.SlicerValidation;.reuseVariableWithReinitializationDoubleSized()V");
		System.out.println(slicer.getMethodSummary());

		Map<Set<Integer>, List<IInstruction>> slicerCriterionResultMap = new HashMap<>();

		slicerCriterionResultMap.put(Set.of(0), Arrays.asList(ConstantInstruction.make(0L), PopInstruction.make(2),
				ReturnInstruction.make(Constants.TYPE_void)));
		slicerCriterionResultMap.put(Set.of(1), Arrays.asList(ConstantInstruction.make(0L),
				StoreInstruction.make(Constants.TYPE_long, 1), ReturnInstruction.make(Constants.TYPE_void)));
		slicerCriterionResultMap.put(Set.of(2),
				Arrays.asList(GotoInstruction.make(9), InvokeInstruction.make("()V",
						"Lde/rherzog/master/thesis/slicer/instrumenter/export/Nothing;", "doNothing", Dispatch.STATIC),
						ReturnInstruction.make(Constants.TYPE_void)));
		slicerCriterionResultMap.put(Set.of(3),
				Arrays.asList(ConstantInstruction.make(0L), StoreInstruction.make(Constants.TYPE_long, 1),
						GotoInstruction.make(9),
						InvokeInstruction.make("()J", "Ljava/lang/System;", "currentTimeMillis", Dispatch.STATIC),
						PopInstruction.make(2), LoadInstruction.make(Constants.TYPE_long, 1),
						ConstantInstruction.make(1L),
						BinaryOpInstruction.make(Constants.TYPE_long, IBinaryOpInstruction.Operator.ADD),
						StoreInstruction.make(Constants.TYPE_long, 1), LoadInstruction.make(Constants.TYPE_long, 1),
						ConstantInstruction.make(2L),
						ComparisonInstruction.make(Constants.TYPE_long, IComparisonInstruction.Operator.CMP),
						ConstantInstruction.make(0), ConditionalBranchInstruction.make(Constants.TYPE_int,
								ConditionalBranchInstruction.Operator.LT, 3),
						ReturnInstruction.make(Constants.TYPE_void)));
		slicerCriterionResultMap.put(Set.of(4),
				Arrays.asList(ConstantInstruction.make(0L), StoreInstruction.make(Constants.TYPE_long, 1),
						GotoInstruction.make(9),
						InvokeInstruction.make("()J", "Ljava/lang/System;", "currentTimeMillis", Dispatch.STATIC),
						PopInstruction.make(2), LoadInstruction.make(Constants.TYPE_long, 1),
						ConstantInstruction.make(1L),
						BinaryOpInstruction.make(Constants.TYPE_long, IBinaryOpInstruction.Operator.ADD),
						StoreInstruction.make(Constants.TYPE_long, 1), LoadInstruction.make(Constants.TYPE_long, 1),
						ConstantInstruction.make(2L),
						ComparisonInstruction.make(Constants.TYPE_long, IComparisonInstruction.Operator.CMP),
						ConstantInstruction.make(0), ConditionalBranchInstruction.make(Constants.TYPE_int,
								ConditionalBranchInstruction.Operator.LT, 3),
						ReturnInstruction.make(Constants.TYPE_void)));
		slicerCriterionResultMap.put(Set.of(5),
				Arrays.asList(ConstantInstruction.make(0L), StoreInstruction.make(Constants.TYPE_long, 1),
						GotoInstruction.make(9),
						InvokeInstruction.make("()V", "Lde/rherzog/master/thesis/slicer/instrumenter/export/Nothing;",
								"doNothing", Dispatch.STATIC),
						LoadInstruction.make(Constants.TYPE_long, 1), ConstantInstruction.make(1L),
						BinaryOpInstruction.make(Constants.TYPE_long, IBinaryOpInstruction.Operator.ADD),
						StoreInstruction.make(Constants.TYPE_long, 1), LoadInstruction.make(Constants.TYPE_long, 1),
						ConstantInstruction.make(2L),
						ComparisonInstruction.make(Constants.TYPE_long, IComparisonInstruction.Operator.CMP),
						ConstantInstruction.make(0), ConditionalBranchInstruction.make(Constants.TYPE_int,
								ConditionalBranchInstruction.Operator.LT, 3),
						ReturnInstruction.make(Constants.TYPE_void)));
		slicerCriterionResultMap.put(Set.of(6), Arrays.asList(ConstantInstruction.make(1L), PopInstruction.make(2),
				ReturnInstruction.make(Constants.TYPE_void)));
		slicerCriterionResultMap.put(Set.of(7),
				Arrays.asList(ConstantInstruction.make(0L), StoreInstruction.make(Constants.TYPE_long, 1),
						GotoInstruction.make(9),
						InvokeInstruction.make("()V", "Lde/rherzog/master/thesis/slicer/instrumenter/export/Nothing;",
								"doNothing", Dispatch.STATIC),
						LoadInstruction.make(Constants.TYPE_long, 1), ConstantInstruction.make(1L),
						BinaryOpInstruction.make(Constants.TYPE_long, IBinaryOpInstruction.Operator.ADD),
						StoreInstruction.make(Constants.TYPE_long, 1), LoadInstruction.make(Constants.TYPE_long, 1),
						ConstantInstruction.make(2L),
						ComparisonInstruction.make(Constants.TYPE_long, IComparisonInstruction.Operator.CMP),
						ConstantInstruction.make(0), ConditionalBranchInstruction.make(Constants.TYPE_int,
								ConditionalBranchInstruction.Operator.LT, 3),
						ReturnInstruction.make(Constants.TYPE_void)));
		slicerCriterionResultMap.put(Set.of(8),
				Arrays.asList(ConstantInstruction.make(0L), StoreInstruction.make(Constants.TYPE_long, 1),
						GotoInstruction.make(9),
						InvokeInstruction.make("()V", "Lde/rherzog/master/thesis/slicer/instrumenter/export/Nothing;",
								"doNothing", Dispatch.STATIC),
						LoadInstruction.make(Constants.TYPE_long, 1), ConstantInstruction.make(1L),
						BinaryOpInstruction.make(Constants.TYPE_long, IBinaryOpInstruction.Operator.ADD),
						StoreInstruction.make(Constants.TYPE_long, 1), LoadInstruction.make(Constants.TYPE_long, 1),
						ConstantInstruction.make(2L),
						ComparisonInstruction.make(Constants.TYPE_long, IComparisonInstruction.Operator.CMP),
						ConstantInstruction.make(0), ConditionalBranchInstruction.make(Constants.TYPE_int,
								ConditionalBranchInstruction.Operator.LT, 3),
						ReturnInstruction.make(Constants.TYPE_void)));
		slicerCriterionResultMap.put(Set.of(9),
				Arrays.asList(ConstantInstruction.make(0L), StoreInstruction.make(Constants.TYPE_long, 1),
						GotoInstruction.make(9),
						InvokeInstruction.make("()V", "Lde/rherzog/master/thesis/slicer/instrumenter/export/Nothing;",
								"doNothing", Dispatch.STATIC),
						LoadInstruction.make(Constants.TYPE_long, 1), ConstantInstruction.make(1L),
						BinaryOpInstruction.make(Constants.TYPE_long, IBinaryOpInstruction.Operator.ADD),
						StoreInstruction.make(Constants.TYPE_long, 1), LoadInstruction.make(Constants.TYPE_long, 1),
						ConstantInstruction.make(2L),
						ComparisonInstruction.make(Constants.TYPE_long, IComparisonInstruction.Operator.CMP),
						ConstantInstruction.make(0), ConditionalBranchInstruction.make(Constants.TYPE_int,
								ConditionalBranchInstruction.Operator.LT, 3),
						ReturnInstruction.make(Constants.TYPE_void)));
		slicerCriterionResultMap.put(Set.of(10), Arrays.asList(ConstantInstruction.make(2L), PopInstruction.make(2),
				ReturnInstruction.make(Constants.TYPE_void)));
		slicerCriterionResultMap.put(Set.of(11),
				Arrays.asList(ConstantInstruction.make(0L), StoreInstruction.make(Constants.TYPE_long, 1),
						GotoInstruction.make(9),
						InvokeInstruction.make("()V", "Lde/rherzog/master/thesis/slicer/instrumenter/export/Nothing;",
								"doNothing", Dispatch.STATIC),
						LoadInstruction.make(Constants.TYPE_long, 1), ConstantInstruction.make(1L),
						BinaryOpInstruction.make(Constants.TYPE_long, IBinaryOpInstruction.Operator.ADD),
						StoreInstruction.make(Constants.TYPE_long, 1), LoadInstruction.make(Constants.TYPE_long, 1),
						ConstantInstruction.make(2L),
						ComparisonInstruction.make(Constants.TYPE_long, IComparisonInstruction.Operator.CMP),
						ConstantInstruction.make(0), ConditionalBranchInstruction.make(Constants.TYPE_int,
								ConditionalBranchInstruction.Operator.LT, 3),
						ReturnInstruction.make(Constants.TYPE_void)));
		slicerCriterionResultMap.put(Set.of(12), Arrays.asList(ConstantInstruction.make(0), PopInstruction.make(1),
				ReturnInstruction.make(Constants.TYPE_void)));
		slicerCriterionResultMap.put(Set.of(13),
				Arrays.asList(ConstantInstruction.make(0L), StoreInstruction.make(Constants.TYPE_long, 1),
						GotoInstruction.make(9),
						InvokeInstruction.make("()V", "Lde/rherzog/master/thesis/slicer/instrumenter/export/Nothing;",
								"doNothing", Dispatch.STATIC),
						LoadInstruction.make(Constants.TYPE_long, 1), ConstantInstruction.make(1L),
						BinaryOpInstruction.make(Constants.TYPE_long, IBinaryOpInstruction.Operator.ADD),
						StoreInstruction.make(Constants.TYPE_long, 1), LoadInstruction.make(Constants.TYPE_long, 1),
						ConstantInstruction.make(2L),
						ComparisonInstruction.make(Constants.TYPE_long, IComparisonInstruction.Operator.CMP),
						ConstantInstruction.make(0), ConditionalBranchInstruction.make(Constants.TYPE_int,
								ConditionalBranchInstruction.Operator.LT, 3),
						ReturnInstruction.make(Constants.TYPE_void)));
		slicerCriterionResultMap.put(Set.of(14),
				Arrays.asList(ConstantInstruction.make(0L), StoreInstruction.make(Constants.TYPE_long, 1),
						GotoInstruction.make(9),
						InvokeInstruction.make("()V", "Lde/rherzog/master/thesis/slicer/instrumenter/export/Nothing;",
								"doNothing", Dispatch.STATIC),
						LoadInstruction.make(Constants.TYPE_long, 1), ConstantInstruction.make(1L),
						BinaryOpInstruction.make(Constants.TYPE_long, IBinaryOpInstruction.Operator.ADD),
						StoreInstruction.make(Constants.TYPE_long, 1), LoadInstruction.make(Constants.TYPE_long, 1),
						ConstantInstruction.make(2L),
						ComparisonInstruction.make(Constants.TYPE_long, IComparisonInstruction.Operator.CMP),
						ConstantInstruction.make(0),
						ConditionalBranchInstruction.make(Constants.TYPE_int, ConditionalBranchInstruction.Operator.LT,
								3),
						LoadInstruction.make(Constants.TYPE_long, 1), PopInstruction.make(2),
						ReturnInstruction.make(Constants.TYPE_void)));
		slicerCriterionResultMap.put(Set.of(15), Arrays.asList(ConstantInstruction.make(1L), PopInstruction.make(2),
				ReturnInstruction.make(Constants.TYPE_void)));
		slicerCriterionResultMap.put(Set.of(16),
				Arrays.asList(ConstantInstruction.make(0L), StoreInstruction.make(Constants.TYPE_long, 1),
						GotoInstruction.make(9),
						InvokeInstruction.make("()V", "Lde/rherzog/master/thesis/slicer/instrumenter/export/Nothing;",
								"doNothing", Dispatch.STATIC),
						LoadInstruction.make(Constants.TYPE_long, 1), ConstantInstruction.make(1L),
						BinaryOpInstruction.make(Constants.TYPE_long, IBinaryOpInstruction.Operator.ADD),
						StoreInstruction.make(Constants.TYPE_long, 1), LoadInstruction.make(Constants.TYPE_long, 1),
						ConstantInstruction.make(2L),
						ComparisonInstruction.make(Constants.TYPE_long, IComparisonInstruction.Operator.CMP),
						ConstantInstruction.make(0),
						ConditionalBranchInstruction.make(Constants.TYPE_int, ConditionalBranchInstruction.Operator.LT,
								3),
						LoadInstruction.make(Constants.TYPE_long, 1), ConstantInstruction.make(1L),
						BinaryOpInstruction.make(Constants.TYPE_long, IBinaryOpInstruction.Operator.ADD),
						PopInstruction.make(2), ReturnInstruction.make(Constants.TYPE_void)));
		slicerCriterionResultMap.put(Set.of(17),
				Arrays.asList(ConstantInstruction.make(0L), StoreInstruction.make(Constants.TYPE_long, 1),
						GotoInstruction.make(9),
						InvokeInstruction.make("()V", "Lde/rherzog/master/thesis/slicer/instrumenter/export/Nothing;",
								"doNothing", Dispatch.STATIC),
						LoadInstruction.make(Constants.TYPE_long, 1), ConstantInstruction.make(1L),
						BinaryOpInstruction.make(Constants.TYPE_long, IBinaryOpInstruction.Operator.ADD),
						StoreInstruction.make(Constants.TYPE_long, 1), LoadInstruction.make(Constants.TYPE_long, 1),
						ConstantInstruction.make(2L),
						ComparisonInstruction.make(Constants.TYPE_long, IComparisonInstruction.Operator.CMP),
						ConstantInstruction.make(0),
						ConditionalBranchInstruction.make(Constants.TYPE_int, ConditionalBranchInstruction.Operator.LT,
								3),
						LoadInstruction.make(Constants.TYPE_long, 1), ConstantInstruction.make(1L),
						BinaryOpInstruction.make(Constants.TYPE_long, IBinaryOpInstruction.Operator.ADD),
						StoreInstruction.make(Constants.TYPE_long, 1), ReturnInstruction.make(Constants.TYPE_void)));
		slicerCriterionResultMap.put(Set.of(18),
				Arrays.asList(GotoInstruction.make(25), InvokeInstruction.make("()V",
						"Lde/rherzog/master/thesis/slicer/instrumenter/export/Nothing;", "doNothing", Dispatch.STATIC),
						ReturnInstruction.make(Constants.TYPE_void)));
		slicerCriterionResultMap.put(Set.of(19),
				Arrays.asList(ConstantInstruction.make(0L), StoreInstruction.make(Constants.TYPE_long, 1),
						GotoInstruction.make(9),
						InvokeInstruction.make("()V", "Lde/rherzog/master/thesis/slicer/instrumenter/export/Nothing;",
								"doNothing", Dispatch.STATIC),
						LoadInstruction.make(Constants.TYPE_long, 1), ConstantInstruction.make(1L),
						BinaryOpInstruction.make(Constants.TYPE_long, IBinaryOpInstruction.Operator.ADD),
						StoreInstruction.make(Constants.TYPE_long, 1), LoadInstruction.make(Constants.TYPE_long, 1),
						ConstantInstruction.make(2L),
						ComparisonInstruction.make(Constants.TYPE_long, IComparisonInstruction.Operator.CMP),
						ConstantInstruction.make(0),
						ConditionalBranchInstruction.make(Constants.TYPE_int, ConditionalBranchInstruction.Operator.LT,
								3),
						LoadInstruction.make(Constants.TYPE_long, 1), ConstantInstruction.make(1L),
						BinaryOpInstruction.make(Constants.TYPE_long, IBinaryOpInstruction.Operator.ADD),
						StoreInstruction.make(Constants.TYPE_long, 1), GotoInstruction.make(25),
						InvokeInstruction.make("()J", "Ljava/lang/System;", "currentTimeMillis", Dispatch.STATIC),
						PopInstruction.make(2), LoadInstruction.make(Constants.TYPE_long, 1),
						ConstantInstruction.make(1L),
						BinaryOpInstruction.make(Constants.TYPE_long, IBinaryOpInstruction.Operator.ADD),
						StoreInstruction.make(Constants.TYPE_long, 1), LoadInstruction.make(Constants.TYPE_long, 1),
						ConstantInstruction.make(5L),
						ComparisonInstruction.make(Constants.TYPE_long, IComparisonInstruction.Operator.CMP),
						ConstantInstruction.make(0), ConditionalBranchInstruction.make(Constants.TYPE_int,
								ConditionalBranchInstruction.Operator.LT, 19),
						ReturnInstruction.make(Constants.TYPE_void)));
		slicerCriterionResultMap.put(Set.of(20),
				Arrays.asList(ConstantInstruction.make(0L), StoreInstruction.make(Constants.TYPE_long, 1),
						GotoInstruction.make(9),
						InvokeInstruction.make("()V", "Lde/rherzog/master/thesis/slicer/instrumenter/export/Nothing;",
								"doNothing", Dispatch.STATIC),
						LoadInstruction.make(Constants.TYPE_long, 1), ConstantInstruction.make(1L),
						BinaryOpInstruction.make(Constants.TYPE_long, IBinaryOpInstruction.Operator.ADD),
						StoreInstruction.make(Constants.TYPE_long, 1), LoadInstruction.make(Constants.TYPE_long, 1),
						ConstantInstruction.make(2L),
						ComparisonInstruction.make(Constants.TYPE_long, IComparisonInstruction.Operator.CMP),
						ConstantInstruction.make(0),
						ConditionalBranchInstruction.make(Constants.TYPE_int, ConditionalBranchInstruction.Operator.LT,
								3),
						LoadInstruction.make(Constants.TYPE_long, 1), ConstantInstruction.make(1L),
						BinaryOpInstruction.make(Constants.TYPE_long, IBinaryOpInstruction.Operator.ADD),
						StoreInstruction.make(Constants.TYPE_long, 1), GotoInstruction.make(25),
						InvokeInstruction.make("()J", "Ljava/lang/System;", "currentTimeMillis", Dispatch.STATIC),
						PopInstruction.make(2), LoadInstruction.make(Constants.TYPE_long, 1),
						ConstantInstruction.make(1L),
						BinaryOpInstruction.make(Constants.TYPE_long, IBinaryOpInstruction.Operator.ADD),
						StoreInstruction.make(Constants.TYPE_long, 1), LoadInstruction.make(Constants.TYPE_long, 1),
						ConstantInstruction.make(5L),
						ComparisonInstruction.make(Constants.TYPE_long, IComparisonInstruction.Operator.CMP),
						ConstantInstruction.make(0), ConditionalBranchInstruction.make(Constants.TYPE_int,
								ConditionalBranchInstruction.Operator.LT, 19),
						ReturnInstruction.make(Constants.TYPE_void)));
		slicerCriterionResultMap.put(Set.of(21),
				Arrays.asList(ConstantInstruction.make(0L), StoreInstruction.make(Constants.TYPE_long, 1),
						GotoInstruction.make(9),
						InvokeInstruction.make("()V", "Lde/rherzog/master/thesis/slicer/instrumenter/export/Nothing;",
								"doNothing", Dispatch.STATIC),
						LoadInstruction.make(Constants.TYPE_long, 1), ConstantInstruction.make(1L),
						BinaryOpInstruction.make(Constants.TYPE_long, IBinaryOpInstruction.Operator.ADD),
						StoreInstruction.make(Constants.TYPE_long, 1), LoadInstruction.make(Constants.TYPE_long, 1),
						ConstantInstruction.make(2L),
						ComparisonInstruction.make(Constants.TYPE_long, IComparisonInstruction.Operator.CMP),
						ConstantInstruction.make(0),
						ConditionalBranchInstruction.make(Constants.TYPE_int, ConditionalBranchInstruction.Operator.LT,
								3),
						LoadInstruction.make(Constants.TYPE_long, 1), ConstantInstruction.make(1L),
						BinaryOpInstruction.make(Constants.TYPE_long, IBinaryOpInstruction.Operator.ADD),
						StoreInstruction.make(Constants.TYPE_long, 1), GotoInstruction.make(25),
						InvokeInstruction.make("()V", "Lde/rherzog/master/thesis/slicer/instrumenter/export/Nothing;",
								"doNothing", Dispatch.STATIC),
						LoadInstruction.make(Constants.TYPE_long, 1), ConstantInstruction.make(1L),
						BinaryOpInstruction.make(Constants.TYPE_long, IBinaryOpInstruction.Operator.ADD),
						StoreInstruction.make(Constants.TYPE_long, 1), LoadInstruction.make(Constants.TYPE_long, 1),
						ConstantInstruction.make(5L),
						ComparisonInstruction.make(Constants.TYPE_long, IComparisonInstruction.Operator.CMP),
						ConstantInstruction.make(0), ConditionalBranchInstruction.make(Constants.TYPE_int,
								ConditionalBranchInstruction.Operator.LT, 19),
						ReturnInstruction.make(Constants.TYPE_void)));
		slicerCriterionResultMap.put(Set.of(22), Arrays.asList(ConstantInstruction.make(1L), PopInstruction.make(2),
				ReturnInstruction.make(Constants.TYPE_void)));
		slicerCriterionResultMap.put(Set.of(23),
				Arrays.asList(ConstantInstruction.make(0L), StoreInstruction.make(Constants.TYPE_long, 1),
						GotoInstruction.make(9),
						InvokeInstruction.make("()V", "Lde/rherzog/master/thesis/slicer/instrumenter/export/Nothing;",
								"doNothing", Dispatch.STATIC),
						LoadInstruction.make(Constants.TYPE_long, 1), ConstantInstruction.make(1L),
						BinaryOpInstruction.make(Constants.TYPE_long, IBinaryOpInstruction.Operator.ADD),
						StoreInstruction.make(Constants.TYPE_long, 1), LoadInstruction.make(Constants.TYPE_long, 1),
						ConstantInstruction.make(2L),
						ComparisonInstruction.make(Constants.TYPE_long, IComparisonInstruction.Operator.CMP),
						ConstantInstruction.make(0),
						ConditionalBranchInstruction.make(Constants.TYPE_int, ConditionalBranchInstruction.Operator.LT,
								3),
						LoadInstruction.make(Constants.TYPE_long, 1), ConstantInstruction.make(1L),
						BinaryOpInstruction.make(Constants.TYPE_long, IBinaryOpInstruction.Operator.ADD),
						StoreInstruction.make(Constants.TYPE_long, 1), GotoInstruction.make(25),
						InvokeInstruction.make("()V", "Lde/rherzog/master/thesis/slicer/instrumenter/export/Nothing;",
								"doNothing", Dispatch.STATIC),
						LoadInstruction.make(Constants.TYPE_long, 1), ConstantInstruction.make(1L),
						BinaryOpInstruction.make(Constants.TYPE_long, IBinaryOpInstruction.Operator.ADD),
						StoreInstruction.make(Constants.TYPE_long, 1), LoadInstruction.make(Constants.TYPE_long, 1),
						ConstantInstruction.make(5L),
						ComparisonInstruction.make(Constants.TYPE_long, IComparisonInstruction.Operator.CMP),
						ConstantInstruction.make(0), ConditionalBranchInstruction.make(Constants.TYPE_int,
								ConditionalBranchInstruction.Operator.LT, 19),
						ReturnInstruction.make(Constants.TYPE_void)));
		slicerCriterionResultMap.put(Set.of(24),
				Arrays.asList(ConstantInstruction.make(0L), StoreInstruction.make(Constants.TYPE_long, 1),
						GotoInstruction.make(9),
						InvokeInstruction.make("()V", "Lde/rherzog/master/thesis/slicer/instrumenter/export/Nothing;",
								"doNothing", Dispatch.STATIC),
						LoadInstruction.make(Constants.TYPE_long, 1), ConstantInstruction.make(1L),
						BinaryOpInstruction.make(Constants.TYPE_long, IBinaryOpInstruction.Operator.ADD),
						StoreInstruction.make(Constants.TYPE_long, 1), LoadInstruction.make(Constants.TYPE_long, 1),
						ConstantInstruction.make(2L),
						ComparisonInstruction.make(Constants.TYPE_long, IComparisonInstruction.Operator.CMP),
						ConstantInstruction.make(0),
						ConditionalBranchInstruction.make(Constants.TYPE_int, ConditionalBranchInstruction.Operator.LT,
								3),
						LoadInstruction.make(Constants.TYPE_long, 1), ConstantInstruction.make(1L),
						BinaryOpInstruction.make(Constants.TYPE_long, IBinaryOpInstruction.Operator.ADD),
						StoreInstruction.make(Constants.TYPE_long, 1), GotoInstruction.make(25),
						InvokeInstruction.make("()V", "Lde/rherzog/master/thesis/slicer/instrumenter/export/Nothing;",
								"doNothing", Dispatch.STATIC),
						LoadInstruction.make(Constants.TYPE_long, 1), ConstantInstruction.make(1L),
						BinaryOpInstruction.make(Constants.TYPE_long, IBinaryOpInstruction.Operator.ADD),
						StoreInstruction.make(Constants.TYPE_long, 1), LoadInstruction.make(Constants.TYPE_long, 1),
						ConstantInstruction.make(5L),
						ComparisonInstruction.make(Constants.TYPE_long, IComparisonInstruction.Operator.CMP),
						ConstantInstruction.make(0), ConditionalBranchInstruction.make(Constants.TYPE_int,
								ConditionalBranchInstruction.Operator.LT, 19),
						ReturnInstruction.make(Constants.TYPE_void)));
		slicerCriterionResultMap.put(Set.of(25),
				Arrays.asList(ConstantInstruction.make(0L), StoreInstruction.make(Constants.TYPE_long, 1),
						GotoInstruction.make(9),
						InvokeInstruction.make("()V", "Lde/rherzog/master/thesis/slicer/instrumenter/export/Nothing;",
								"doNothing", Dispatch.STATIC),
						LoadInstruction.make(Constants.TYPE_long, 1), ConstantInstruction.make(1L),
						BinaryOpInstruction.make(Constants.TYPE_long, IBinaryOpInstruction.Operator.ADD),
						StoreInstruction.make(Constants.TYPE_long, 1), LoadInstruction.make(Constants.TYPE_long, 1),
						ConstantInstruction.make(2L),
						ComparisonInstruction.make(Constants.TYPE_long, IComparisonInstruction.Operator.CMP),
						ConstantInstruction.make(0),
						ConditionalBranchInstruction.make(Constants.TYPE_int, ConditionalBranchInstruction.Operator.LT,
								3),
						LoadInstruction.make(Constants.TYPE_long, 1), ConstantInstruction.make(1L),
						BinaryOpInstruction.make(Constants.TYPE_long, IBinaryOpInstruction.Operator.ADD),
						StoreInstruction.make(Constants.TYPE_long, 1), GotoInstruction.make(25),
						InvokeInstruction.make("()V", "Lde/rherzog/master/thesis/slicer/instrumenter/export/Nothing;",
								"doNothing", Dispatch.STATIC),
						LoadInstruction.make(Constants.TYPE_long, 1), ConstantInstruction.make(1L),
						BinaryOpInstruction.make(Constants.TYPE_long, IBinaryOpInstruction.Operator.ADD),
						StoreInstruction.make(Constants.TYPE_long, 1), LoadInstruction.make(Constants.TYPE_long, 1),
						ConstantInstruction.make(5L),
						ComparisonInstruction.make(Constants.TYPE_long, IComparisonInstruction.Operator.CMP),
						ConstantInstruction.make(0), ConditionalBranchInstruction.make(Constants.TYPE_int,
								ConditionalBranchInstruction.Operator.LT, 19),
						ReturnInstruction.make(Constants.TYPE_void)));
		slicerCriterionResultMap.put(Set.of(26), Arrays.asList(ConstantInstruction.make(5L), PopInstruction.make(2),
				ReturnInstruction.make(Constants.TYPE_void)));
		slicerCriterionResultMap.put(Set.of(27),
				Arrays.asList(ConstantInstruction.make(0L), StoreInstruction.make(Constants.TYPE_long, 1),
						GotoInstruction.make(9),
						InvokeInstruction.make("()V", "Lde/rherzog/master/thesis/slicer/instrumenter/export/Nothing;",
								"doNothing", Dispatch.STATIC),
						LoadInstruction.make(Constants.TYPE_long, 1), ConstantInstruction.make(1L),
						BinaryOpInstruction.make(Constants.TYPE_long, IBinaryOpInstruction.Operator.ADD),
						StoreInstruction.make(Constants.TYPE_long, 1), LoadInstruction.make(Constants.TYPE_long, 1),
						ConstantInstruction.make(2L),
						ComparisonInstruction.make(Constants.TYPE_long, IComparisonInstruction.Operator.CMP),
						ConstantInstruction.make(0),
						ConditionalBranchInstruction.make(Constants.TYPE_int, ConditionalBranchInstruction.Operator.LT,
								3),
						LoadInstruction.make(Constants.TYPE_long, 1), ConstantInstruction.make(1L),
						BinaryOpInstruction.make(Constants.TYPE_long, IBinaryOpInstruction.Operator.ADD),
						StoreInstruction.make(Constants.TYPE_long, 1), GotoInstruction.make(25),
						InvokeInstruction.make("()V", "Lde/rherzog/master/thesis/slicer/instrumenter/export/Nothing;",
								"doNothing", Dispatch.STATIC),
						LoadInstruction.make(Constants.TYPE_long, 1), ConstantInstruction.make(1L),
						BinaryOpInstruction.make(Constants.TYPE_long, IBinaryOpInstruction.Operator.ADD),
						StoreInstruction.make(Constants.TYPE_long, 1), LoadInstruction.make(Constants.TYPE_long, 1),
						ConstantInstruction.make(5L),
						ComparisonInstruction.make(Constants.TYPE_long, IComparisonInstruction.Operator.CMP),
						ConstantInstruction.make(0), ConditionalBranchInstruction.make(Constants.TYPE_int,
								ConditionalBranchInstruction.Operator.LT, 19),
						ReturnInstruction.make(Constants.TYPE_void)));
		slicerCriterionResultMap.put(Set.of(28), Arrays.asList(ConstantInstruction.make(0), PopInstruction.make(1),
				ReturnInstruction.make(Constants.TYPE_void)));
		slicerCriterionResultMap.put(Set.of(29),
				Arrays.asList(ConstantInstruction.make(0L), StoreInstruction.make(Constants.TYPE_long, 1),
						GotoInstruction.make(9),
						InvokeInstruction.make("()V", "Lde/rherzog/master/thesis/slicer/instrumenter/export/Nothing;",
								"doNothing", Dispatch.STATIC),
						LoadInstruction.make(Constants.TYPE_long, 1), ConstantInstruction.make(1L),
						BinaryOpInstruction.make(Constants.TYPE_long, IBinaryOpInstruction.Operator.ADD),
						StoreInstruction.make(Constants.TYPE_long, 1), LoadInstruction.make(Constants.TYPE_long, 1),
						ConstantInstruction.make(2L),
						ComparisonInstruction.make(Constants.TYPE_long, IComparisonInstruction.Operator.CMP),
						ConstantInstruction.make(0),
						ConditionalBranchInstruction.make(Constants.TYPE_int, ConditionalBranchInstruction.Operator.LT,
								3),
						LoadInstruction.make(Constants.TYPE_long, 1), ConstantInstruction.make(1L),
						BinaryOpInstruction.make(Constants.TYPE_long, IBinaryOpInstruction.Operator.ADD),
						StoreInstruction.make(Constants.TYPE_long, 1), GotoInstruction.make(25),
						InvokeInstruction.make("()V", "Lde/rherzog/master/thesis/slicer/instrumenter/export/Nothing;",
								"doNothing", Dispatch.STATIC),
						LoadInstruction.make(Constants.TYPE_long, 1), ConstantInstruction.make(1L),
						BinaryOpInstruction.make(Constants.TYPE_long, IBinaryOpInstruction.Operator.ADD),
						StoreInstruction.make(Constants.TYPE_long, 1), LoadInstruction.make(Constants.TYPE_long, 1),
						ConstantInstruction.make(5L),
						ComparisonInstruction.make(Constants.TYPE_long, IComparisonInstruction.Operator.CMP),
						ConstantInstruction.make(0), ConditionalBranchInstruction.make(Constants.TYPE_int,
								ConditionalBranchInstruction.Operator.LT, 19),
						ReturnInstruction.make(Constants.TYPE_void)));
		slicerCriterionResultMap.put(Set.of(30), Arrays.asList(ReturnInstruction.make(Constants.TYPE_void)));

		validateSliceResults(slicerCriterionResultMap);
	}

	@Test
	public void testSliceReuseVariableWithoutReinitialization()
			throws IOException, InvalidClassFileException, InterruptedException {
		slicer.setInputJar(slicerValidationJarPath);
		slicer.setMethodSignature(
				"Lde.rherzog.master.thesis.slicer.test.SlicerValidation;.reuseVariableWithoutReinitialization()V");
		System.out.println(slicer.getMethodSummary());
//		Utilities.dotShow(slicer.getControlFlow().dotPrint());

		Map<Set<Integer>, List<IInstruction>> slicerCriterionResultMap = new HashMap<>();
		slicerCriterionResultMap.put(Set.of(0), Arrays.asList(ConstantInstruction.make(0), PopInstruction.make(1),
				ReturnInstruction.make(Constants.TYPE_void)));
		slicerCriterionResultMap.put(Set.of(1), Arrays.asList(ConstantInstruction.make(0),
				StoreInstruction.make(Constants.TYPE_int, 1), ReturnInstruction.make(Constants.TYPE_void)));
		slicerCriterionResultMap.put(Set.of(2),
				Arrays.asList(GotoInstruction.make(9), InvokeInstruction.make("()V",
						"Lde/rherzog/master/thesis/slicer/instrumenter/export/Nothing;", "doNothing", Dispatch.STATIC),
						ReturnInstruction.make(Constants.TYPE_void)));
		slicerCriterionResultMap.put(Set.of(3),
				Arrays.asList(ConstantInstruction.make(0), StoreInstruction.make(Constants.TYPE_int, 1),
						GotoInstruction.make(9),
						InvokeInstruction.make("()J", "Ljava/lang/System;", "currentTimeMillis", Dispatch.STATIC),
						PopInstruction.make(2), LoadInstruction.make(Constants.TYPE_int, 1),
						ConstantInstruction.make(1),
						BinaryOpInstruction.make(Constants.TYPE_int, IBinaryOpInstruction.Operator.ADD),
						StoreInstruction.make(Constants.TYPE_int, 1), LoadInstruction.make(Constants.TYPE_int, 1),
						ConstantInstruction.make(5), ConditionalBranchInstruction.make(Constants.TYPE_int,
								ConditionalBranchInstruction.Operator.LT, 3),
						ReturnInstruction.make(Constants.TYPE_void)));
		slicerCriterionResultMap.put(Set.of(4),
				Arrays.asList(ConstantInstruction.make(0), StoreInstruction.make(Constants.TYPE_int, 1),
						GotoInstruction.make(9),
						InvokeInstruction.make("()J", "Ljava/lang/System;", "currentTimeMillis", Dispatch.STATIC),
						PopInstruction.make(2), LoadInstruction.make(Constants.TYPE_int, 1),
						ConstantInstruction.make(1),
						BinaryOpInstruction.make(Constants.TYPE_int, IBinaryOpInstruction.Operator.ADD),
						StoreInstruction.make(Constants.TYPE_int, 1), LoadInstruction.make(Constants.TYPE_int, 1),
						ConstantInstruction.make(5), ConditionalBranchInstruction.make(Constants.TYPE_int,
								ConditionalBranchInstruction.Operator.LT, 3),
						ReturnInstruction.make(Constants.TYPE_void)));
		slicerCriterionResultMap.put(Set.of(5),
				Arrays.asList(ConstantInstruction.make(0), StoreInstruction.make(Constants.TYPE_int, 1),
						GotoInstruction.make(9),
						InvokeInstruction.make("()V", "Lde/rherzog/master/thesis/slicer/instrumenter/export/Nothing;",
								"doNothing", Dispatch.STATIC),
						LoadInstruction.make(Constants.TYPE_int, 1), ConstantInstruction.make(1),
						BinaryOpInstruction.make(Constants.TYPE_int, IBinaryOpInstruction.Operator.ADD),
						StoreInstruction.make(Constants.TYPE_int, 1), LoadInstruction.make(Constants.TYPE_int, 1),
						ConstantInstruction.make(5), ConditionalBranchInstruction.make(Constants.TYPE_int,
								ConditionalBranchInstruction.Operator.LT, 3),
						ReturnInstruction.make(Constants.TYPE_void)

				));
		slicerCriterionResultMap.put(Set.of(6), Arrays.asList(ConstantInstruction.make(1), PopInstruction.make(1),
				ReturnInstruction.make(Constants.TYPE_void)));
		slicerCriterionResultMap.put(Set.of(7),
				Arrays.asList(ConstantInstruction.make(0), StoreInstruction.make(Constants.TYPE_int, 1),
						GotoInstruction.make(9),
						InvokeInstruction.make("()V", "Lde/rherzog/master/thesis/slicer/instrumenter/export/Nothing;",
								"doNothing", Dispatch.STATIC),
						LoadInstruction.make(Constants.TYPE_int, 1), ConstantInstruction.make(1),
						BinaryOpInstruction.make(Constants.TYPE_int, IBinaryOpInstruction.Operator.ADD),
						StoreInstruction.make(Constants.TYPE_int, 1), LoadInstruction.make(Constants.TYPE_int, 1),
						ConstantInstruction.make(5), ConditionalBranchInstruction.make(Constants.TYPE_int,
								ConditionalBranchInstruction.Operator.LT, 3),
						ReturnInstruction.make(Constants.TYPE_void)));
		slicerCriterionResultMap.put(Set.of(8),
				Arrays.asList(ConstantInstruction.make(0), StoreInstruction.make(Constants.TYPE_int, 1),
						GotoInstruction.make(9),
						InvokeInstruction.make("()V", "Lde/rherzog/master/thesis/slicer/instrumenter/export/Nothing;",
								"doNothing", Dispatch.STATIC),
						LoadInstruction.make(Constants.TYPE_int, 1), ConstantInstruction.make(1),
						BinaryOpInstruction.make(Constants.TYPE_int, IBinaryOpInstruction.Operator.ADD),
						StoreInstruction.make(Constants.TYPE_int, 1), LoadInstruction.make(Constants.TYPE_int, 1),
						ConstantInstruction.make(5), ConditionalBranchInstruction.make(Constants.TYPE_int,
								ConditionalBranchInstruction.Operator.LT, 3),
						ReturnInstruction.make(Constants.TYPE_void)));
		slicerCriterionResultMap.put(Set.of(9),
				Arrays.asList(ConstantInstruction.make(0), StoreInstruction.make(Constants.TYPE_int, 1),
						GotoInstruction.make(9),
						InvokeInstruction.make("()V", "Lde/rherzog/master/thesis/slicer/instrumenter/export/Nothing;",
								"doNothing", Dispatch.STATIC),
						LoadInstruction.make(Constants.TYPE_int, 1), ConstantInstruction.make(1),
						BinaryOpInstruction.make(Constants.TYPE_int, IBinaryOpInstruction.Operator.ADD),
						StoreInstruction.make(Constants.TYPE_int, 1), LoadInstruction.make(Constants.TYPE_int, 1),
						ConstantInstruction.make(5), ConditionalBranchInstruction.make(Constants.TYPE_int,
								ConditionalBranchInstruction.Operator.LT, 3),
						ReturnInstruction.make(Constants.TYPE_void)));
		slicerCriterionResultMap.put(Set.of(10), Arrays.asList(ConstantInstruction.make(5), PopInstruction.make(1),
				ReturnInstruction.make(Constants.TYPE_void)));
		slicerCriterionResultMap.put(Set.of(11),
				Arrays.asList(ConstantInstruction.make(0), StoreInstruction.make(Constants.TYPE_int, 1),
						GotoInstruction.make(9),
						InvokeInstruction.make("()V", "Lde/rherzog/master/thesis/slicer/instrumenter/export/Nothing;",
								"doNothing", Dispatch.STATIC),
						LoadInstruction.make(Constants.TYPE_int, 1), ConstantInstruction.make(1),
						BinaryOpInstruction.make(Constants.TYPE_int, IBinaryOpInstruction.Operator.ADD),
						StoreInstruction.make(Constants.TYPE_int, 1), LoadInstruction.make(Constants.TYPE_int, 1),
						ConstantInstruction.make(5), ConditionalBranchInstruction.make(Constants.TYPE_int,
								ConditionalBranchInstruction.Operator.LT, 3),
						ReturnInstruction.make(Constants.TYPE_void)));
		slicerCriterionResultMap.put(Set.of(12),
				Arrays.asList(GotoInstruction.make(19), InvokeInstruction.make("()V",
						"Lde/rherzog/master/thesis/slicer/instrumenter/export/Nothing;", "doNothing", Dispatch.STATIC),
						ReturnInstruction.make(Constants.TYPE_void)));
		slicerCriterionResultMap.put(Set.of(13),
				Arrays.asList(ConstantInstruction.make(0), StoreInstruction.make(Constants.TYPE_int, 1),
						GotoInstruction.make(9),
						InvokeInstruction.make("()V", "Lde/rherzog/master/thesis/slicer/instrumenter/export/Nothing;",
								"doNothing", Dispatch.STATIC),
						LoadInstruction.make(Constants.TYPE_int, 1), ConstantInstruction.make(1),
						BinaryOpInstruction.make(Constants.TYPE_int, IBinaryOpInstruction.Operator.ADD),
						StoreInstruction.make(Constants.TYPE_int, 1), LoadInstruction.make(Constants.TYPE_int, 1),
						ConstantInstruction.make(5),
						ConditionalBranchInstruction.make(Constants.TYPE_int, ConditionalBranchInstruction.Operator.LT,
								3),
						GotoInstruction.make(19),
						InvokeInstruction.make("()J", "Ljava/lang/System;", "currentTimeMillis", Dispatch.STATIC),
						PopInstruction.make(2), LoadInstruction.make(Constants.TYPE_int, 1),
						ConstantInstruction.make(1),
						BinaryOpInstruction.make(Constants.TYPE_int, IBinaryOpInstruction.Operator.ADD),
						StoreInstruction.make(Constants.TYPE_int, 1), LoadInstruction.make(Constants.TYPE_int, 1),
						ConstantInstruction.make(10), ConditionalBranchInstruction.make(Constants.TYPE_int,
								ConditionalBranchInstruction.Operator.LT, 13),
						ReturnInstruction.make(Constants.TYPE_void)));
		slicerCriterionResultMap.put(Set.of(14),
				Arrays.asList(ConstantInstruction.make(0), StoreInstruction.make(Constants.TYPE_int, 1),
						GotoInstruction.make(9),
						InvokeInstruction.make("()V", "Lde/rherzog/master/thesis/slicer/instrumenter/export/Nothing;",
								"doNothing", Dispatch.STATIC),
						LoadInstruction.make(Constants.TYPE_int, 1), ConstantInstruction.make(1),
						BinaryOpInstruction.make(Constants.TYPE_int, IBinaryOpInstruction.Operator.ADD),
						StoreInstruction.make(Constants.TYPE_int, 1), LoadInstruction.make(Constants.TYPE_int, 1),
						ConstantInstruction.make(5),
						ConditionalBranchInstruction.make(Constants.TYPE_int, ConditionalBranchInstruction.Operator.LT,
								3),
						GotoInstruction.make(19),
						InvokeInstruction.make("()J", "Ljava/lang/System;", "currentTimeMillis", Dispatch.STATIC),
						PopInstruction.make(2), LoadInstruction.make(Constants.TYPE_int, 1),
						ConstantInstruction.make(1),
						BinaryOpInstruction.make(Constants.TYPE_int, IBinaryOpInstruction.Operator.ADD),
						StoreInstruction.make(Constants.TYPE_int, 1), LoadInstruction.make(Constants.TYPE_int, 1),
						ConstantInstruction.make(10), ConditionalBranchInstruction.make(Constants.TYPE_int,
								ConditionalBranchInstruction.Operator.LT, 13),
						ReturnInstruction.make(Constants.TYPE_void)));
		slicerCriterionResultMap.put(Set.of(15),
				Arrays.asList(ConstantInstruction.make(0), StoreInstruction.make(Constants.TYPE_int, 1),
						GotoInstruction.make(9),
						InvokeInstruction.make("()V", "Lde/rherzog/master/thesis/slicer/instrumenter/export/Nothing;",
								"doNothing", Dispatch.STATIC),
						LoadInstruction.make(Constants.TYPE_int, 1), ConstantInstruction.make(1),
						BinaryOpInstruction.make(Constants.TYPE_int, IBinaryOpInstruction.Operator.ADD),
						StoreInstruction.make(Constants.TYPE_int, 1), LoadInstruction.make(Constants.TYPE_int, 1),
						ConstantInstruction.make(5),
						ConditionalBranchInstruction
								.make(Constants.TYPE_int, ConditionalBranchInstruction.Operator.LT, 3),
						GotoInstruction.make(19),
						InvokeInstruction.make("()V", "Lde/rherzog/master/thesis/slicer/instrumenter/export/Nothing;",
								"doNothing", Dispatch.STATIC),
						LoadInstruction.make(Constants.TYPE_int, 1), ConstantInstruction.make(1),
						BinaryOpInstruction.make(Constants.TYPE_int, IBinaryOpInstruction.Operator.ADD),
						StoreInstruction.make(Constants.TYPE_int, 1), LoadInstruction.make(Constants.TYPE_int, 1),
						ConstantInstruction.make(10), ConditionalBranchInstruction.make(Constants.TYPE_int,
								ConditionalBranchInstruction.Operator.LT, 13),
						ReturnInstruction.make(Constants.TYPE_void)));
		slicerCriterionResultMap.put(Set.of(16), Arrays.asList(ConstantInstruction.make(1), PopInstruction.make(1),
				ReturnInstruction.make(Constants.TYPE_void)));
		slicerCriterionResultMap.put(Set.of(17),
				Arrays.asList(ConstantInstruction.make(0), StoreInstruction.make(Constants.TYPE_int, 1),
						GotoInstruction.make(9),
						InvokeInstruction.make("()V", "Lde/rherzog/master/thesis/slicer/instrumenter/export/Nothing;",
								"doNothing", Dispatch.STATIC),
						LoadInstruction.make(Constants.TYPE_int, 1), ConstantInstruction.make(1),
						BinaryOpInstruction.make(Constants.TYPE_int, IBinaryOpInstruction.Operator.ADD),
						StoreInstruction.make(Constants.TYPE_int, 1), LoadInstruction.make(Constants.TYPE_int, 1),
						ConstantInstruction.make(5),
						ConditionalBranchInstruction
								.make(Constants.TYPE_int, ConditionalBranchInstruction.Operator.LT, 3),
						GotoInstruction.make(19),
						InvokeInstruction.make("()V", "Lde/rherzog/master/thesis/slicer/instrumenter/export/Nothing;",
								"doNothing", Dispatch.STATIC),
						LoadInstruction.make(Constants.TYPE_int, 1), ConstantInstruction.make(1),
						BinaryOpInstruction.make(Constants.TYPE_int, IBinaryOpInstruction.Operator.ADD),
						StoreInstruction.make(Constants.TYPE_int, 1), LoadInstruction.make(Constants.TYPE_int, 1),
						ConstantInstruction.make(10), ConditionalBranchInstruction.make(Constants.TYPE_int,
								ConditionalBranchInstruction.Operator.LT, 13),
						ReturnInstruction.make(Constants.TYPE_void)));
		slicerCriterionResultMap.put(Set.of(18),
				Arrays.asList(ConstantInstruction.make(0), StoreInstruction.make(Constants.TYPE_int, 1),
						GotoInstruction.make(9),
						InvokeInstruction.make("()V", "Lde/rherzog/master/thesis/slicer/instrumenter/export/Nothing;",
								"doNothing", Dispatch.STATIC),
						LoadInstruction.make(Constants.TYPE_int, 1), ConstantInstruction.make(1),
						BinaryOpInstruction.make(Constants.TYPE_int, IBinaryOpInstruction.Operator.ADD),
						StoreInstruction.make(Constants.TYPE_int, 1), LoadInstruction.make(Constants.TYPE_int, 1),
						ConstantInstruction.make(5),
						ConditionalBranchInstruction
								.make(Constants.TYPE_int, ConditionalBranchInstruction.Operator.LT, 3),
						GotoInstruction.make(19),
						InvokeInstruction.make("()V", "Lde/rherzog/master/thesis/slicer/instrumenter/export/Nothing;",
								"doNothing", Dispatch.STATIC),
						LoadInstruction.make(Constants.TYPE_int, 1), ConstantInstruction.make(1),
						BinaryOpInstruction.make(Constants.TYPE_int, IBinaryOpInstruction.Operator.ADD),
						StoreInstruction.make(Constants.TYPE_int, 1), LoadInstruction.make(Constants.TYPE_int, 1),
						ConstantInstruction.make(10), ConditionalBranchInstruction.make(Constants.TYPE_int,
								ConditionalBranchInstruction.Operator.LT, 13),
						ReturnInstruction.make(Constants.TYPE_void)));
		slicerCriterionResultMap.put(Set.of(19),
				Arrays.asList(ConstantInstruction.make(0), StoreInstruction.make(Constants.TYPE_int, 1),
						GotoInstruction.make(9),
						InvokeInstruction.make("()V", "Lde/rherzog/master/thesis/slicer/instrumenter/export/Nothing;",
								"doNothing", Dispatch.STATIC),
						LoadInstruction.make(Constants.TYPE_int, 1), ConstantInstruction.make(1),
						BinaryOpInstruction.make(Constants.TYPE_int, IBinaryOpInstruction.Operator.ADD),
						StoreInstruction.make(Constants.TYPE_int, 1), LoadInstruction.make(Constants.TYPE_int, 1),
						ConstantInstruction.make(5),
						ConditionalBranchInstruction
								.make(Constants.TYPE_int, ConditionalBranchInstruction.Operator.LT, 3),
						GotoInstruction.make(19),
						InvokeInstruction.make("()V", "Lde/rherzog/master/thesis/slicer/instrumenter/export/Nothing;",
								"doNothing", Dispatch.STATIC),
						LoadInstruction.make(Constants.TYPE_int, 1), ConstantInstruction.make(1),
						BinaryOpInstruction.make(Constants.TYPE_int, IBinaryOpInstruction.Operator.ADD),
						StoreInstruction.make(Constants.TYPE_int, 1), LoadInstruction.make(Constants.TYPE_int, 1),
						ConstantInstruction.make(10), ConditionalBranchInstruction.make(Constants.TYPE_int,
								ConditionalBranchInstruction.Operator.LT, 13),
						ReturnInstruction.make(Constants.TYPE_void)));
		slicerCriterionResultMap.put(Set.of(20), Arrays.asList(ConstantInstruction.make(10), PopInstruction.make(1),
				ReturnInstruction.make(Constants.TYPE_void)));
		slicerCriterionResultMap.put(Set.of(21),
				Arrays.asList(ConstantInstruction.make(0), StoreInstruction.make(Constants.TYPE_int, 1),
						GotoInstruction.make(9),
						InvokeInstruction.make("()V", "Lde/rherzog/master/thesis/slicer/instrumenter/export/Nothing;",
								"doNothing", Dispatch.STATIC),
						LoadInstruction.make(Constants.TYPE_int, 1), ConstantInstruction.make(1),
						BinaryOpInstruction.make(Constants.TYPE_int, IBinaryOpInstruction.Operator.ADD),
						StoreInstruction.make(Constants.TYPE_int, 1), LoadInstruction.make(Constants.TYPE_int, 1),
						ConstantInstruction.make(5),
						ConditionalBranchInstruction
								.make(Constants.TYPE_int, ConditionalBranchInstruction.Operator.LT, 3),
						GotoInstruction.make(19),
						InvokeInstruction.make("()V", "Lde/rherzog/master/thesis/slicer/instrumenter/export/Nothing;",
								"doNothing", Dispatch.STATIC),
						LoadInstruction.make(Constants.TYPE_int, 1), ConstantInstruction.make(1),
						BinaryOpInstruction.make(Constants.TYPE_int, IBinaryOpInstruction.Operator.ADD),
						StoreInstruction.make(Constants.TYPE_int, 1), LoadInstruction.make(Constants.TYPE_int, 1),
						ConstantInstruction.make(10), ConditionalBranchInstruction.make(Constants.TYPE_int,
								ConditionalBranchInstruction.Operator.LT, 13),
						ReturnInstruction.make(Constants.TYPE_void)));
		slicerCriterionResultMap.put(Set.of(22),
				Arrays.asList(InvokeInstruction.make("()V",
						"Lde/rherzog/master/thesis/slicer/instrumenter/export/Nothing;", "doNothing", Dispatch.STATIC),
						ReturnInstruction.make(Constants.TYPE_void)));
		slicerCriterionResultMap.put(Set.of(23), Arrays.asList(ReturnInstruction.make(Constants.TYPE_void)));

		validateSliceResults(slicerCriterionResultMap);
	}

	@Test
	public void testSliceReuseVariableWithoutReinitializationDoubleSized()
			throws IOException, InvalidClassFileException, InterruptedException {
		slicer.setInputJar(slicerValidationJarPath);
		slicer.setMethodSignature(
				"Lde.rherzog.master.thesis.slicer.test.SlicerValidation;.reuseVariableWithoutReinitializationDoubleSized()V");
		System.out.println(slicer.getMethodSummary());
//		Utilities.dotShow(slicer.getControlFlow().dotPrint());

		Map<Set<Integer>, List<IInstruction>> slicerCriterionResultMap = new HashMap<>();
		slicerCriterionResultMap.put(Set.of(0), Arrays.asList(ConstantInstruction.make(0L), PopInstruction.make(2),
				ReturnInstruction.make(Constants.TYPE_void)));
		slicerCriterionResultMap.put(Set.of(1), Arrays.asList(ConstantInstruction.make(0L),
				StoreInstruction.make(Constants.TYPE_long, 1), ReturnInstruction.make(Constants.TYPE_void)));
		slicerCriterionResultMap.put(Set.of(2),
				Arrays.asList(GotoInstruction.make(9), InvokeInstruction.make("()V",
						"Lde/rherzog/master/thesis/slicer/instrumenter/export/Nothing;", "doNothing", Dispatch.STATIC),
						ReturnInstruction.make(Constants.TYPE_void)));
		slicerCriterionResultMap.put(Set.of(3),
				Arrays.asList(ConstantInstruction.make(0L), StoreInstruction.make(Constants.TYPE_long, 1),
						GotoInstruction.make(9),
						InvokeInstruction.make("()J", "Ljava/lang/System;", "currentTimeMillis", Dispatch.STATIC),
						PopInstruction.make(2), LoadInstruction.make(Constants.TYPE_long, 1),
						ConstantInstruction.make(1L),
						BinaryOpInstruction.make(Constants.TYPE_long, IBinaryOpInstruction.Operator.ADD),
						StoreInstruction.make(Constants.TYPE_long, 1), LoadInstruction.make(Constants.TYPE_long, 1),
						ConstantInstruction.make(5L),
						ComparisonInstruction.make(Constants.TYPE_long, IComparisonInstruction.Operator.CMP),
						ConstantInstruction.make(0), ConditionalBranchInstruction.make(Constants.TYPE_int,
								ConditionalBranchInstruction.Operator.LT, 3),
						ReturnInstruction.make(Constants.TYPE_void)));
		slicerCriterionResultMap.put(Set.of(4),
				Arrays.asList(ConstantInstruction.make(0L), StoreInstruction.make(Constants.TYPE_long, 1),
						GotoInstruction.make(9),
						InvokeInstruction.make("()J", "Ljava/lang/System;", "currentTimeMillis", Dispatch.STATIC),
						PopInstruction.make(2), LoadInstruction.make(Constants.TYPE_long, 1),
						ConstantInstruction.make(1L),
						BinaryOpInstruction.make(Constants.TYPE_long, IBinaryOpInstruction.Operator.ADD),
						StoreInstruction.make(Constants.TYPE_long, 1), LoadInstruction.make(Constants.TYPE_long, 1),
						ConstantInstruction.make(5L),
						ComparisonInstruction.make(Constants.TYPE_long, IComparisonInstruction.Operator.CMP),
						ConstantInstruction.make(0), ConditionalBranchInstruction.make(Constants.TYPE_int,
								ConditionalBranchInstruction.Operator.LT, 3),
						ReturnInstruction.make(Constants.TYPE_void)));
		slicerCriterionResultMap.put(Set.of(5),
				Arrays.asList(ConstantInstruction.make(0L), StoreInstruction.make(Constants.TYPE_long, 1),
						GotoInstruction.make(9),
						InvokeInstruction.make("()V", "Lde/rherzog/master/thesis/slicer/instrumenter/export/Nothing;",
								"doNothing", Dispatch.STATIC),
						LoadInstruction.make(Constants.TYPE_long, 1), ConstantInstruction.make(1L),
						BinaryOpInstruction.make(Constants.TYPE_long, IBinaryOpInstruction.Operator.ADD),
						StoreInstruction.make(Constants.TYPE_long, 1), LoadInstruction.make(Constants.TYPE_long, 1),
						ConstantInstruction.make(5L),
						ComparisonInstruction.make(Constants.TYPE_long, IComparisonInstruction.Operator.CMP),
						ConstantInstruction.make(0), ConditionalBranchInstruction.make(Constants.TYPE_int,
								ConditionalBranchInstruction.Operator.LT, 3),
						ReturnInstruction.make(Constants.TYPE_void)));
		slicerCriterionResultMap.put(Set.of(6), Arrays.asList(ConstantInstruction.make(1L), PopInstruction.make(2),
				ReturnInstruction.make(Constants.TYPE_void)));
		slicerCriterionResultMap.put(Set.of(7),
				Arrays.asList(ConstantInstruction.make(0L), StoreInstruction.make(Constants.TYPE_long, 1),
						GotoInstruction.make(9),
						InvokeInstruction.make("()V", "Lde/rherzog/master/thesis/slicer/instrumenter/export/Nothing;",
								"doNothing", Dispatch.STATIC),
						LoadInstruction.make(Constants.TYPE_long, 1), ConstantInstruction.make(1L),
						BinaryOpInstruction.make(Constants.TYPE_long, IBinaryOpInstruction.Operator.ADD),
						StoreInstruction.make(Constants.TYPE_long, 1), LoadInstruction.make(Constants.TYPE_long, 1),
						ConstantInstruction.make(5L),
						ComparisonInstruction.make(Constants.TYPE_long, IComparisonInstruction.Operator.CMP),
						ConstantInstruction.make(0), ConditionalBranchInstruction.make(Constants.TYPE_int,
								ConditionalBranchInstruction.Operator.LT, 3),
						ReturnInstruction.make(Constants.TYPE_void)));
		slicerCriterionResultMap.put(Set.of(8),
				Arrays.asList(ConstantInstruction.make(0L), StoreInstruction.make(Constants.TYPE_long, 1),
						GotoInstruction.make(9),
						InvokeInstruction.make("()V", "Lde/rherzog/master/thesis/slicer/instrumenter/export/Nothing;",
								"doNothing", Dispatch.STATIC),
						LoadInstruction.make(Constants.TYPE_long, 1), ConstantInstruction.make(1L),
						BinaryOpInstruction.make(Constants.TYPE_long, IBinaryOpInstruction.Operator.ADD),
						StoreInstruction.make(Constants.TYPE_long, 1), LoadInstruction.make(Constants.TYPE_long, 1),
						ConstantInstruction.make(5L),
						ComparisonInstruction.make(Constants.TYPE_long, IComparisonInstruction.Operator.CMP),
						ConstantInstruction.make(0), ConditionalBranchInstruction.make(Constants.TYPE_int,
								ConditionalBranchInstruction.Operator.LT, 3),
						ReturnInstruction.make(Constants.TYPE_void)));
		slicerCriterionResultMap.put(Set.of(9),
				Arrays.asList(ConstantInstruction.make(0L), StoreInstruction.make(Constants.TYPE_long, 1),
						GotoInstruction.make(9),
						InvokeInstruction.make("()V", "Lde/rherzog/master/thesis/slicer/instrumenter/export/Nothing;",
								"doNothing", Dispatch.STATIC),
						LoadInstruction.make(Constants.TYPE_long, 1), ConstantInstruction.make(1L),
						BinaryOpInstruction.make(Constants.TYPE_long, IBinaryOpInstruction.Operator.ADD),
						StoreInstruction.make(Constants.TYPE_long, 1), LoadInstruction.make(Constants.TYPE_long, 1),
						ConstantInstruction.make(5L),
						ComparisonInstruction.make(Constants.TYPE_long, IComparisonInstruction.Operator.CMP),
						ConstantInstruction.make(0), ConditionalBranchInstruction.make(Constants.TYPE_int,
								ConditionalBranchInstruction.Operator.LT, 3),
						ReturnInstruction.make(Constants.TYPE_void)));
		slicerCriterionResultMap.put(Set.of(10), Arrays.asList(ConstantInstruction.make(5L), PopInstruction.make(2),
				ReturnInstruction.make(Constants.TYPE_void)));
		slicerCriterionResultMap.put(Set.of(11),
				Arrays.asList(ConstantInstruction.make(0L), StoreInstruction.make(Constants.TYPE_long, 1),
						GotoInstruction.make(9),
						InvokeInstruction.make("()V", "Lde/rherzog/master/thesis/slicer/instrumenter/export/Nothing;",
								"doNothing", Dispatch.STATIC),
						LoadInstruction.make(Constants.TYPE_long, 1), ConstantInstruction.make(1L),
						BinaryOpInstruction.make(Constants.TYPE_long, IBinaryOpInstruction.Operator.ADD),
						StoreInstruction.make(Constants.TYPE_long, 1), LoadInstruction.make(Constants.TYPE_long, 1),
						ConstantInstruction.make(5L),
						ComparisonInstruction.make(Constants.TYPE_long, IComparisonInstruction.Operator.CMP),
						ConstantInstruction.make(0), ConditionalBranchInstruction.make(Constants.TYPE_int,
								ConditionalBranchInstruction.Operator.LT, 3),
						ReturnInstruction.make(Constants.TYPE_void)));
		slicerCriterionResultMap.put(Set.of(12), Arrays.asList(ConstantInstruction.make(0), PopInstruction.make(1),
				ReturnInstruction.make(Constants.TYPE_void)));
		slicerCriterionResultMap.put(Set.of(13),
				Arrays.asList(ConstantInstruction.make(0L), StoreInstruction.make(Constants.TYPE_long, 1),
						GotoInstruction.make(9),
						InvokeInstruction.make("()V", "Lde/rherzog/master/thesis/slicer/instrumenter/export/Nothing;",
								"doNothing", Dispatch.STATIC),
						LoadInstruction.make(Constants.TYPE_long, 1), ConstantInstruction.make(1L),
						BinaryOpInstruction.make(Constants.TYPE_long, IBinaryOpInstruction.Operator.ADD),
						StoreInstruction.make(Constants.TYPE_long, 1), LoadInstruction.make(Constants.TYPE_long, 1),
						ConstantInstruction.make(5L),
						ComparisonInstruction.make(Constants.TYPE_long, IComparisonInstruction.Operator.CMP),
						ConstantInstruction.make(0), ConditionalBranchInstruction.make(Constants.TYPE_int,
								ConditionalBranchInstruction.Operator.LT, 3),
						ReturnInstruction.make(Constants.TYPE_void)));
		slicerCriterionResultMap.put(Set.of(14),
				Arrays.asList(GotoInstruction.make(21), InvokeInstruction.make("()V",
						"Lde/rherzog/master/thesis/slicer/instrumenter/export/Nothing;", "doNothing", Dispatch.STATIC),
						ReturnInstruction.make(Constants.TYPE_void)));
		slicerCriterionResultMap.put(Set.of(15),
				Arrays.asList(ConstantInstruction.make(0L), StoreInstruction.make(Constants.TYPE_long, 1),
						GotoInstruction.make(9),
						InvokeInstruction.make("()V", "Lde/rherzog/master/thesis/slicer/instrumenter/export/Nothing;",
								"doNothing", Dispatch.STATIC),
						LoadInstruction.make(Constants.TYPE_long, 1), ConstantInstruction.make(1L),
						BinaryOpInstruction.make(Constants.TYPE_long, IBinaryOpInstruction.Operator.ADD),
						StoreInstruction.make(Constants.TYPE_long, 1), LoadInstruction.make(Constants.TYPE_long, 1),
						ConstantInstruction.make(5L),
						ComparisonInstruction.make(Constants.TYPE_long, IComparisonInstruction.Operator.CMP),
						ConstantInstruction.make(0),
						ConditionalBranchInstruction.make(Constants.TYPE_int, ConditionalBranchInstruction.Operator.LT,
								3),
						GotoInstruction.make(21),
						InvokeInstruction.make("()J", "Ljava/lang/System;", "currentTimeMillis", Dispatch.STATIC),
						PopInstruction.make(2), LoadInstruction.make(Constants.TYPE_long, 1),
						ConstantInstruction.make(1L),
						BinaryOpInstruction.make(Constants.TYPE_long, IBinaryOpInstruction.Operator.ADD),
						StoreInstruction.make(Constants.TYPE_long, 1), LoadInstruction.make(Constants.TYPE_long, 1),
						ConstantInstruction.make(10L),
						ComparisonInstruction.make(Constants.TYPE_long, IComparisonInstruction.Operator.CMP),
						ConstantInstruction.make(0), ConditionalBranchInstruction.make(Constants.TYPE_int,
								ConditionalBranchInstruction.Operator.LT, 15),
						ReturnInstruction.make(Constants.TYPE_void)));
		slicerCriterionResultMap.put(Set.of(16),
				Arrays.asList(ConstantInstruction.make(0L), StoreInstruction.make(Constants.TYPE_long, 1),
						GotoInstruction.make(9),
						InvokeInstruction.make("()V", "Lde/rherzog/master/thesis/slicer/instrumenter/export/Nothing;",
								"doNothing", Dispatch.STATIC),
						LoadInstruction.make(Constants.TYPE_long, 1), ConstantInstruction.make(1L),
						BinaryOpInstruction.make(Constants.TYPE_long, IBinaryOpInstruction.Operator.ADD),
						StoreInstruction.make(Constants.TYPE_long, 1), LoadInstruction.make(Constants.TYPE_long, 1),
						ConstantInstruction.make(5L),
						ComparisonInstruction.make(Constants.TYPE_long, IComparisonInstruction.Operator.CMP),
						ConstantInstruction.make(0),
						ConditionalBranchInstruction.make(Constants.TYPE_int, ConditionalBranchInstruction.Operator.LT,
								3),
						GotoInstruction.make(21),
						InvokeInstruction.make("()J", "Ljava/lang/System;", "currentTimeMillis", Dispatch.STATIC),
						PopInstruction.make(2), LoadInstruction.make(Constants.TYPE_long, 1),
						ConstantInstruction.make(1L),
						BinaryOpInstruction.make(Constants.TYPE_long, IBinaryOpInstruction.Operator.ADD),
						StoreInstruction.make(Constants.TYPE_long, 1), LoadInstruction.make(Constants.TYPE_long, 1),
						ConstantInstruction.make(10L),
						ComparisonInstruction.make(Constants.TYPE_long, IComparisonInstruction.Operator.CMP),
						ConstantInstruction.make(0), ConditionalBranchInstruction.make(Constants.TYPE_int,
								ConditionalBranchInstruction.Operator.LT, 15),
						ReturnInstruction.make(Constants.TYPE_void)));
		slicerCriterionResultMap.put(Set.of(17),
				Arrays.asList(ConstantInstruction.make(0L), StoreInstruction.make(Constants.TYPE_long, 1),
						GotoInstruction.make(9),
						InvokeInstruction.make("()V", "Lde/rherzog/master/thesis/slicer/instrumenter/export/Nothing;",
								"doNothing", Dispatch.STATIC),
						LoadInstruction.make(Constants.TYPE_long, 1), ConstantInstruction.make(1L),
						BinaryOpInstruction.make(Constants.TYPE_long, IBinaryOpInstruction.Operator.ADD),
						StoreInstruction.make(Constants.TYPE_long, 1), LoadInstruction.make(Constants.TYPE_long, 1),
						ConstantInstruction.make(5L),
						ComparisonInstruction.make(Constants.TYPE_long, IComparisonInstruction.Operator.CMP),
						ConstantInstruction.make(0),
						ConditionalBranchInstruction
								.make(Constants.TYPE_int, ConditionalBranchInstruction.Operator.LT, 3),
						GotoInstruction.make(21),
						InvokeInstruction.make("()V", "Lde/rherzog/master/thesis/slicer/instrumenter/export/Nothing;",
								"doNothing", Dispatch.STATIC),
						LoadInstruction.make(Constants.TYPE_long, 1), ConstantInstruction.make(1L),
						BinaryOpInstruction.make(Constants.TYPE_long, IBinaryOpInstruction.Operator.ADD),
						StoreInstruction.make(Constants.TYPE_long, 1), LoadInstruction.make(Constants.TYPE_long, 1),
						ConstantInstruction.make(10L),
						ComparisonInstruction.make(Constants.TYPE_long, IComparisonInstruction.Operator.CMP),
						ConstantInstruction.make(0), ConditionalBranchInstruction.make(Constants.TYPE_int,
								ConditionalBranchInstruction.Operator.LT, 15),
						ReturnInstruction.make(Constants.TYPE_void)));
		slicerCriterionResultMap.put(Set.of(18), Arrays.asList(ConstantInstruction.make(1L), PopInstruction.make(2),
				ReturnInstruction.make(Constants.TYPE_void)));
		slicerCriterionResultMap.put(Set.of(19),
				Arrays.asList(ConstantInstruction.make(0L), StoreInstruction.make(Constants.TYPE_long, 1),
						GotoInstruction.make(9),
						InvokeInstruction.make("()V", "Lde/rherzog/master/thesis/slicer/instrumenter/export/Nothing;",
								"doNothing", Dispatch.STATIC),
						LoadInstruction.make(Constants.TYPE_long, 1), ConstantInstruction.make(1L),
						BinaryOpInstruction.make(Constants.TYPE_long, IBinaryOpInstruction.Operator.ADD),
						StoreInstruction.make(Constants.TYPE_long, 1), LoadInstruction.make(Constants.TYPE_long, 1),
						ConstantInstruction.make(5L),
						ComparisonInstruction.make(Constants.TYPE_long, IComparisonInstruction.Operator.CMP),
						ConstantInstruction.make(0),
						ConditionalBranchInstruction
								.make(Constants.TYPE_int, ConditionalBranchInstruction.Operator.LT, 3),
						GotoInstruction.make(21),
						InvokeInstruction.make("()V", "Lde/rherzog/master/thesis/slicer/instrumenter/export/Nothing;",
								"doNothing", Dispatch.STATIC),
						LoadInstruction.make(Constants.TYPE_long, 1), ConstantInstruction.make(1L),
						BinaryOpInstruction.make(Constants.TYPE_long, IBinaryOpInstruction.Operator.ADD),
						StoreInstruction.make(Constants.TYPE_long, 1), LoadInstruction.make(Constants.TYPE_long, 1),
						ConstantInstruction.make(10L),
						ComparisonInstruction.make(Constants.TYPE_long, IComparisonInstruction.Operator.CMP),
						ConstantInstruction.make(0), ConditionalBranchInstruction.make(Constants.TYPE_int,
								ConditionalBranchInstruction.Operator.LT, 15),
						ReturnInstruction.make(Constants.TYPE_void)));
		slicerCriterionResultMap.put(Set.of(20),
				Arrays.asList(ConstantInstruction.make(0L), StoreInstruction.make(Constants.TYPE_long, 1),
						GotoInstruction.make(9),
						InvokeInstruction.make("()V", "Lde/rherzog/master/thesis/slicer/instrumenter/export/Nothing;",
								"doNothing", Dispatch.STATIC),
						LoadInstruction.make(Constants.TYPE_long, 1), ConstantInstruction.make(1L),
						BinaryOpInstruction.make(Constants.TYPE_long, IBinaryOpInstruction.Operator.ADD),
						StoreInstruction.make(Constants.TYPE_long, 1), LoadInstruction.make(Constants.TYPE_long, 1),
						ConstantInstruction.make(5L),
						ComparisonInstruction.make(Constants.TYPE_long, IComparisonInstruction.Operator.CMP),
						ConstantInstruction.make(0),
						ConditionalBranchInstruction
								.make(Constants.TYPE_int, ConditionalBranchInstruction.Operator.LT, 3),
						GotoInstruction.make(21),
						InvokeInstruction.make("()V", "Lde/rherzog/master/thesis/slicer/instrumenter/export/Nothing;",
								"doNothing", Dispatch.STATIC),
						LoadInstruction.make(Constants.TYPE_long, 1), ConstantInstruction.make(1L),
						BinaryOpInstruction.make(Constants.TYPE_long, IBinaryOpInstruction.Operator.ADD),
						StoreInstruction.make(Constants.TYPE_long, 1), LoadInstruction.make(Constants.TYPE_long, 1),
						ConstantInstruction.make(10L),
						ComparisonInstruction.make(Constants.TYPE_long, IComparisonInstruction.Operator.CMP),
						ConstantInstruction.make(0), ConditionalBranchInstruction.make(Constants.TYPE_int,
								ConditionalBranchInstruction.Operator.LT, 15),
						ReturnInstruction.make(Constants.TYPE_void)));
		slicerCriterionResultMap.put(Set.of(21),
				Arrays.asList(ConstantInstruction.make(0L), StoreInstruction.make(Constants.TYPE_long, 1),
						GotoInstruction.make(9),
						InvokeInstruction.make("()V", "Lde/rherzog/master/thesis/slicer/instrumenter/export/Nothing;",
								"doNothing", Dispatch.STATIC),
						LoadInstruction.make(Constants.TYPE_long, 1), ConstantInstruction.make(1L),
						BinaryOpInstruction.make(Constants.TYPE_long, IBinaryOpInstruction.Operator.ADD),
						StoreInstruction.make(Constants.TYPE_long, 1), LoadInstruction.make(Constants.TYPE_long, 1),
						ConstantInstruction.make(5L),
						ComparisonInstruction.make(Constants.TYPE_long, IComparisonInstruction.Operator.CMP),
						ConstantInstruction.make(0),
						ConditionalBranchInstruction
								.make(Constants.TYPE_int, ConditionalBranchInstruction.Operator.LT, 3),
						GotoInstruction.make(21),
						InvokeInstruction.make("()V", "Lde/rherzog/master/thesis/slicer/instrumenter/export/Nothing;",
								"doNothing", Dispatch.STATIC),
						LoadInstruction.make(Constants.TYPE_long, 1), ConstantInstruction.make(1L),
						BinaryOpInstruction.make(Constants.TYPE_long, IBinaryOpInstruction.Operator.ADD),
						StoreInstruction.make(Constants.TYPE_long, 1), LoadInstruction.make(Constants.TYPE_long, 1),
						ConstantInstruction.make(10L),
						ComparisonInstruction.make(Constants.TYPE_long, IComparisonInstruction.Operator.CMP),
						ConstantInstruction.make(0), ConditionalBranchInstruction.make(Constants.TYPE_int,
								ConditionalBranchInstruction.Operator.LT, 15),
						ReturnInstruction.make(Constants.TYPE_void)));
		slicerCriterionResultMap.put(Set.of(22), Arrays.asList(ConstantInstruction.make(10L), PopInstruction.make(2),
				ReturnInstruction.make(Constants.TYPE_void)));
		slicerCriterionResultMap.put(Set.of(23),
				Arrays.asList(ConstantInstruction.make(0L), StoreInstruction.make(Constants.TYPE_long, 1),
						GotoInstruction.make(9),
						InvokeInstruction.make("()V", "Lde/rherzog/master/thesis/slicer/instrumenter/export/Nothing;",
								"doNothing", Dispatch.STATIC),
						LoadInstruction.make(Constants.TYPE_long, 1), ConstantInstruction.make(1L),
						BinaryOpInstruction.make(Constants.TYPE_long, IBinaryOpInstruction.Operator.ADD),
						StoreInstruction.make(Constants.TYPE_long, 1), LoadInstruction.make(Constants.TYPE_long, 1),
						ConstantInstruction.make(5L),
						ComparisonInstruction.make(Constants.TYPE_long, IComparisonInstruction.Operator.CMP),
						ConstantInstruction.make(0),
						ConditionalBranchInstruction
								.make(Constants.TYPE_int, ConditionalBranchInstruction.Operator.LT, 3),
						GotoInstruction.make(21),
						InvokeInstruction.make("()V", "Lde/rherzog/master/thesis/slicer/instrumenter/export/Nothing;",
								"doNothing", Dispatch.STATIC),
						LoadInstruction.make(Constants.TYPE_long, 1), ConstantInstruction.make(1L),
						BinaryOpInstruction.make(Constants.TYPE_long, IBinaryOpInstruction.Operator.ADD),
						StoreInstruction.make(Constants.TYPE_long, 1), LoadInstruction.make(Constants.TYPE_long, 1),
						ConstantInstruction.make(10L),
						ComparisonInstruction.make(Constants.TYPE_long, IComparisonInstruction.Operator.CMP),
						ConstantInstruction.make(0), ConditionalBranchInstruction.make(Constants.TYPE_int,
								ConditionalBranchInstruction.Operator.LT, 15),
						ReturnInstruction.make(Constants.TYPE_void)));
		slicerCriterionResultMap.put(Set.of(23),
				Arrays.asList(ConstantInstruction.make(0L), StoreInstruction.make(Constants.TYPE_long, 1),
						GotoInstruction.make(9),
						InvokeInstruction.make("()V", "Lde/rherzog/master/thesis/slicer/instrumenter/export/Nothing;",
								"doNothing", Dispatch.STATIC),
						LoadInstruction.make(Constants.TYPE_long, 1), ConstantInstruction.make(1L),
						BinaryOpInstruction.make(Constants.TYPE_long, IBinaryOpInstruction.Operator.ADD),
						StoreInstruction.make(Constants.TYPE_long, 1), LoadInstruction.make(Constants.TYPE_long, 1),
						ConstantInstruction.make(5L),
						ComparisonInstruction.make(Constants.TYPE_long, IComparisonInstruction.Operator.CMP),
						ConstantInstruction.make(0),
						ConditionalBranchInstruction
								.make(Constants.TYPE_int, ConditionalBranchInstruction.Operator.LT, 3),
						GotoInstruction.make(21),
						InvokeInstruction.make("()V", "Lde/rherzog/master/thesis/slicer/instrumenter/export/Nothing;",
								"doNothing", Dispatch.STATIC),
						LoadInstruction.make(Constants.TYPE_long, 1), ConstantInstruction.make(1L),
						BinaryOpInstruction.make(Constants.TYPE_long, IBinaryOpInstruction.Operator.ADD),
						StoreInstruction.make(Constants.TYPE_long, 1), LoadInstruction.make(Constants.TYPE_long, 1),
						ConstantInstruction.make(10L),
						ComparisonInstruction.make(Constants.TYPE_long, IComparisonInstruction.Operator.CMP),
						ConstantInstruction.make(0), ConditionalBranchInstruction.make(Constants.TYPE_int,
								ConditionalBranchInstruction.Operator.LT, 15),
						ReturnInstruction.make(Constants.TYPE_void)));
		slicerCriterionResultMap.put(Set.of(24), Arrays.asList(ConstantInstruction.make(0), PopInstruction.make(1),
				ReturnInstruction.make(Constants.TYPE_void)));
		slicerCriterionResultMap.put(Set.of(25),
				Arrays.asList(ConstantInstruction.make(0L), StoreInstruction.make(Constants.TYPE_long, 1),
						GotoInstruction.make(9),
						InvokeInstruction.make("()V", "Lde/rherzog/master/thesis/slicer/instrumenter/export/Nothing;",
								"doNothing", Dispatch.STATIC),
						LoadInstruction.make(Constants.TYPE_long, 1), ConstantInstruction.make(1L),
						BinaryOpInstruction.make(Constants.TYPE_long, IBinaryOpInstruction.Operator.ADD),
						StoreInstruction.make(Constants.TYPE_long, 1), LoadInstruction.make(Constants.TYPE_long, 1),
						ConstantInstruction.make(5L),
						ComparisonInstruction.make(Constants.TYPE_long, IComparisonInstruction.Operator.CMP),
						ConstantInstruction.make(0),
						ConditionalBranchInstruction
								.make(Constants.TYPE_int, ConditionalBranchInstruction.Operator.LT, 3),
						GotoInstruction.make(21),
						InvokeInstruction.make("()V", "Lde/rherzog/master/thesis/slicer/instrumenter/export/Nothing;",
								"doNothing", Dispatch.STATIC),
						LoadInstruction.make(Constants.TYPE_long, 1), ConstantInstruction.make(1L),
						BinaryOpInstruction.make(Constants.TYPE_long, IBinaryOpInstruction.Operator.ADD),
						StoreInstruction.make(Constants.TYPE_long, 1), LoadInstruction.make(Constants.TYPE_long, 1),
						ConstantInstruction.make(10L),
						ComparisonInstruction.make(Constants.TYPE_long, IComparisonInstruction.Operator.CMP),
						ConstantInstruction.make(0), ConditionalBranchInstruction.make(Constants.TYPE_int,
								ConditionalBranchInstruction.Operator.LT, 15),
						ReturnInstruction.make(Constants.TYPE_void)));
		slicerCriterionResultMap.put(Set.of(26),
				Arrays.asList(InvokeInstruction.make("()V",
						"Lde/rherzog/master/thesis/slicer/instrumenter/export/Nothing;", "doNothing", Dispatch.STATIC),
						ReturnInstruction.make(Constants.TYPE_void)));
		slicerCriterionResultMap.put(Set.of(27), Arrays.asList(ReturnInstruction.make(Constants.TYPE_void)));

		validateSliceResults(slicerCriterionResultMap);
	}

	private void validateSliceResults(Map<Set<Integer>, List<IInstruction>> slicerCriterionResultMap)
			throws IOException, InvalidClassFileException {
		for (Entry<Set<Integer>, List<IInstruction>> slicerCriterionResultEntry : slicerCriterionResultMap.entrySet()) {
			Set<Integer> criterionSet = slicerCriterionResultEntry.getKey();
			List<IInstruction> resultList = slicerCriterionResultEntry.getValue();

			slicer.setInstructionIndexes(criterionSet);
			SliceResult sliceResult = slicer.getSliceResult();

			System.out.println(sliceResult);
			System.out.println(sliceResult.toJavaSource());

			Assert.assertTrue("Expected slice from " + criterionSet + " \n  " + resultList + "\nbut is\n  "
					+ sliceResult.getSlice(), resultList.equals(sliceResult.getSlice()));
		}

		for (int instructionIndex = 0; instructionIndex < slicer.getControlFlow().getMethodData()
				.getInstructions().length; instructionIndex++) {
			Assert.assertTrue("slice instruction index test missing for instruction index: [" + instructionIndex + "]",
					slicerCriterionResultMap.keySet().contains(Set.of(instructionIndex)));
		}
	}

	private String readInputStream(InputStream inputStream) {
		// Copy the stream contents into a StringWriter
		StringWriter writer = new StringWriter();
		try {
			IOUtils.copy(inputStream, writer, "UTF-8");
		} catch (IOException e) {
			e.printStackTrace();
		}
		return writer.toString();
	}
}