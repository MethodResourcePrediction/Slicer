package de.rherzog.master.thesis.slicer.test;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.net.URLDecoder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import com.ibm.wala.shrikeBT.BinaryOpInstruction;
import com.ibm.wala.shrikeBT.ConditionalBranchInstruction;
import com.ibm.wala.shrikeBT.ConstantInstruction;
import com.ibm.wala.shrikeBT.Constants;
import com.ibm.wala.shrikeBT.GotoInstruction;
import com.ibm.wala.shrikeBT.IBinaryOpInstruction;
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

	@Before
	public void setUp() throws Exception {
		slicer = new Slicer();
	}

	@Test
	public void testSlice() throws IOException, InvalidClassFileException, InterruptedException {
		String classFilePath = SlicerTest.class.getProtectionDomain().getCodeSource().getLocation().getPath();
		String path = URLDecoder.decode(classFilePath, "UTF-8");

		String jvmPackageName = SlicerValidation.class.getPackageName();
		String packageName = jvmPackageName.replaceAll("\\.", File.separator) + File.separator;

		String slicerValidationClassPath = FilenameUtils.concat(path,
				packageName + SlicerValidation.class.getSimpleName() + ".class");
		String slicerValidationJarPath = FilenameUtils.concat(path,
				packageName + SlicerValidation.class.getSimpleName() + ".jar");

		Process process = new ProcessBuilder("jar", "-cfv", slicerValidationJarPath, slicerValidationClassPath).start();
		process.waitFor();
		if (process.exitValue() != 0) {
			System.err.println("Some error occured during jar compilation\n");
			System.err.println("\nStderr:\n" + readInputStream(process.getErrorStream()));
			System.out.println("\nStdout:\n" + readInputStream(process.getInputStream()));
			throw new IOException("Process exited with exit code " + process.exitValue());
		}

		slicer.setInputJar(slicerValidationJarPath);
		slicer.setMethodSignature(
				"Lde.rherzog.master.thesis.slicer.test.SlicerValidation;.reuseVariableWithoutReinitialization()V");
//		slicer.setMethodSignature(
//				"Lde.rherzog.master.thesis.slicer.test.SlicerValidation;.twoWordPop()V");
		System.out.println(slicer.getMethodSummary());

		Map<Set<Integer>, List<IInstruction>> slicerCriterionResultMap = new HashMap<>();
//		slicerCriterionResultMap.put(Set.of(0), Arrays.asList(ConstantInstruction.make(0), PopInstruction.make(1),
//				ReturnInstruction.make(Constants.TYPE_void)));
//		slicerCriterionResultMap.put(Set.of(1), Arrays.asList(ConstantInstruction.make(0),
//				StoreInstruction.make(Constants.TYPE_int, 1), ReturnInstruction.make(Constants.TYPE_void)));
//		slicerCriterionResultMap.put(Set.of(2),
//				Arrays.asList(GotoInstruction.make(9), InvokeInstruction.make("()V",
//						"Lde/rherzog/master/thesis/slicer/instrumenter/export/Nothing;", "doNothing", Dispatch.STATIC),
//						ReturnInstruction.make(Constants.TYPE_void)));
//		slicerCriterionResultMap.put(Set.of(3),
//				Arrays.asList(ConstantInstruction.make(0), StoreInstruction.make(Constants.TYPE_int, 1),
//						InvokeInstruction.make("()J", "Ljava/lang/System;", "currentTimeMillis", Dispatch.STATIC),
//						PopInstruction.make(1), LoadInstruction.make(Constants.TYPE_int, 1),
//						ConstantInstruction.make(1),
//						BinaryOpInstruction.make(Constants.TYPE_int, IBinaryOpInstruction.Operator.ADD),
//						StoreInstruction.make(Constants.TYPE_int, 1), LoadInstruction.make(Constants.TYPE_int, 1),
//						ConstantInstruction.make(5), ConditionalBranchInstruction.make(Constants.TYPE_int,
//								ConditionalBranchInstruction.Operator.LT, 3),
//						ReturnInstruction.make(Constants.TYPE_void)));
//		slicerCriterionResultMap.put(Set.of(4),
//				Arrays.asList(ConstantInstruction.make(0), StoreInstruction.make(Constants.TYPE_int, 1),
//						InvokeInstruction.make("()J", "Ljava/lang/System;", "currentTimeMillis", Dispatch.STATIC),
//						PopInstruction.make(1), LoadInstruction.make(Constants.TYPE_int, 1),
//						ConstantInstruction.make(1),
//						BinaryOpInstruction.make(Constants.TYPE_int, IBinaryOpInstruction.Operator.ADD),
//						StoreInstruction.make(Constants.TYPE_int, 1), LoadInstruction.make(Constants.TYPE_int, 1),
//						ConstantInstruction.make(5), ConditionalBranchInstruction.make(Constants.TYPE_int,
//								ConditionalBranchInstruction.Operator.LT, 3),
//						ReturnInstruction.make(Constants.TYPE_void)));
		slicerCriterionResultMap.put(Set.of(5),
				Arrays.asList(ConstantInstruction.make(0), StoreInstruction.make(Constants.TYPE_int, 1),
						LoadInstruction.make(Constants.TYPE_int, 1), ConstantInstruction.make(1),
						BinaryOpInstruction.make(Constants.TYPE_int, IBinaryOpInstruction.Operator.ADD),
						StoreInstruction.make(Constants.TYPE_int, 1), LoadInstruction.make(Constants.TYPE_int, 1),
						ConstantInstruction.make(5), ConditionalBranchInstruction.make(Constants.TYPE_int,
								ConditionalBranchInstruction.Operator.LT, 3),
						ReturnInstruction.make(Constants.TYPE_void)));
//		slicerCriterionResultMap.put(Set.of(6), Arrays.asList(ConstantInstruction.make(1), PopInstruction.make(1),
//				ReturnInstruction.make(Constants.TYPE_void)));
//		slicerCriterionResultMap.put(Set.of(7),
//				Arrays.asList(ConstantInstruction.make(0), StoreInstruction.make(Constants.TYPE_int, 1),
//						InvokeInstruction.make("()J", "Ljava/lang/System;", "currentTimeMillis", Dispatch.STATIC),
//						PopInstruction.make(1), LoadInstruction.make(Constants.TYPE_int, 1),
//						ConstantInstruction.make(1),
//						BinaryOpInstruction.make(Constants.TYPE_int, IBinaryOpInstruction.Operator.ADD),
//						StoreInstruction.make(Constants.TYPE_int, 1), LoadInstruction.make(Constants.TYPE_int, 1),
//						ConstantInstruction.make(5), ConditionalBranchInstruction.make(Constants.TYPE_int,
//								ConditionalBranchInstruction.Operator.LT, 3),
//						ReturnInstruction.make(Constants.TYPE_void)));
//		slicerCriterionResultMap.put(Set.of(8),
//				Arrays.asList(ConstantInstruction.make(0), StoreInstruction.make(Constants.TYPE_int, 1),
//						InvokeInstruction.make("()J", "Ljava/lang/System;", "currentTimeMillis", Dispatch.STATIC),
//						PopInstruction.make(1), LoadInstruction.make(Constants.TYPE_int, 1),
//						ConstantInstruction.make(1),
//						BinaryOpInstruction.make(Constants.TYPE_int, IBinaryOpInstruction.Operator.ADD),
//						StoreInstruction.make(Constants.TYPE_int, 1), LoadInstruction.make(Constants.TYPE_int, 1),
//						ConstantInstruction.make(5), ConditionalBranchInstruction.make(Constants.TYPE_int,
//								ConditionalBranchInstruction.Operator.LT, 3),
//						ReturnInstruction.make(Constants.TYPE_void)));
//		slicerCriterionResultMap.put(Set.of(9),
//				Arrays.asList(ConstantInstruction.make(0), StoreInstruction.make(Constants.TYPE_int, 1),
//						InvokeInstruction.make("()J", "Ljava/lang/System;", "currentTimeMillis", Dispatch.STATIC),
//						PopInstruction.make(1), LoadInstruction.make(Constants.TYPE_int, 1),
//						ConstantInstruction.make(1),
//						BinaryOpInstruction.make(Constants.TYPE_int, IBinaryOpInstruction.Operator.ADD),
//						StoreInstruction.make(Constants.TYPE_int, 1), LoadInstruction.make(Constants.TYPE_int, 1),
//						ConstantInstruction.make(5), ConditionalBranchInstruction.make(Constants.TYPE_int,
//								ConditionalBranchInstruction.Operator.LT, 3),
//						ReturnInstruction.make(Constants.TYPE_void)));
//		slicerCriterionResultMap.put(Set.of(10), Arrays.asList(ConstantInstruction.make(5), PopInstruction.make(1),
//				ReturnInstruction.make(Constants.TYPE_void)));
//		slicerCriterionResultMap.put(Set.of(11),
//				Arrays.asList(ConstantInstruction.make(0), StoreInstruction.make(Constants.TYPE_int, 1),
//						InvokeInstruction.make("()J", "Ljava/lang/System;", "currentTimeMillis", Dispatch.STATIC),
//						PopInstruction.make(1), LoadInstruction.make(Constants.TYPE_int, 1),
//						ConstantInstruction.make(1),
//						BinaryOpInstruction.make(Constants.TYPE_int, IBinaryOpInstruction.Operator.ADD),
//						StoreInstruction.make(Constants.TYPE_int, 1), LoadInstruction.make(Constants.TYPE_int, 1),
//						ConstantInstruction.make(5), ConditionalBranchInstruction.make(Constants.TYPE_int,
//								ConditionalBranchInstruction.Operator.LT, 3),
//						ReturnInstruction.make(Constants.TYPE_void)));
//		slicerCriterionResultMap.put(Set.of(6), Arrays.asList(ConstantInstruction.make(1), PopInstruction.make(1),
//		ReturnInstruction.make(Constants.TYPE_void)));
//		slicerCriterionResultMap.put(Set.of(6), Arrays.asList(ConstantInstruction.make(1), PopInstruction.make(1),
//		ReturnInstruction.make(Constants.TYPE_void)));
//		slicerCriterionResultMap.put(Set.of(6), Arrays.asList(ConstantInstruction.make(1), PopInstruction.make(1),
//		ReturnInstruction.make(Constants.TYPE_void)));
//		slicerCriterionResultMap.put(Set.of(6), Arrays.asList(ConstantInstruction.make(1), PopInstruction.make(1),
//		ReturnInstruction.make(Constants.TYPE_void)));
//		slicerCriterionResultMap.put(Set.of(6), Arrays.asList(ConstantInstruction.make(1), PopInstruction.make(1),
//		ReturnInstruction.make(Constants.TYPE_void)));
//		slicerCriterionResultMap.put(Set.of(6), Arrays.asList(ConstantInstruction.make(1), PopInstruction.make(1),
//		ReturnInstruction.make(Constants.TYPE_void)));
//		slicerCriterionResultMap.put(Set.of(6), Arrays.asList(ConstantInstruction.make(1), PopInstruction.make(1),
//		ReturnInstruction.make(Constants.TYPE_void)));
//		slicerCriterionResultMap.put(Set.of(6), Arrays.asList(ConstantInstruction.make(1), PopInstruction.make(1),
//		ReturnInstruction.make(Constants.TYPE_void)));
//		slicerCriterionResultMap.put(Set.of(6), Arrays.asList(ConstantInstruction.make(1), PopInstruction.make(1),
//		ReturnInstruction.make(Constants.TYPE_void)));
//		slicerCriterionResultMap.put(Set.of(6), Arrays.asList(ConstantInstruction.make(1), PopInstruction.make(1),
//		ReturnInstruction.make(Constants.TYPE_void)));
//		slicerCriterionResultMap.put(Set.of(6), Arrays.asList(ConstantInstruction.make(1), PopInstruction.make(1),
//		ReturnInstruction.make(Constants.TYPE_void)));
//		slicerCriterionResultMap.put(Set.of(6), Arrays.asList(ConstantInstruction.make(1), PopInstruction.make(1),
//		ReturnInstruction.make(Constants.TYPE_void)));

		Path dir = Files.createTempDirectory("slicer-");
		for (Entry<Set<Integer>, List<IInstruction>> slicerCriterionResultEntry : slicerCriterionResultMap.entrySet()) {
			Set<Integer> criterionSet = slicerCriterionResultEntry.getKey();
			List<IInstruction> resultList = slicerCriterionResultEntry.getValue();

			slicer.setInstructionIndexes(criterionSet);
			SliceResult sliceResult = slicer.getSliceResult();

			System.out.println(sliceResult);
			System.out.println(sliceResult.toJavaSource());

//			ControlFlow controlFlow = sliceResult.getControlFlow();
//			BlockDependency blockDependency = new BlockDependency(controlFlow);
//			ArgumentDependency argumentDependency = new ArgumentDependency(controlFlow);
//			Utilities.dotShow(dir, blockDependency.dotPrint());

			Assert.assertTrue("Expected slice\n  " + resultList + "\nbut is\n  " + sliceResult.getSlice(),
					resultList.equals(sliceResult.getSlice()));
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
