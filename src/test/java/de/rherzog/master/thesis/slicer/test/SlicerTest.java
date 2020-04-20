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

import com.ibm.wala.shrikeBT.ConstantInstruction;
import com.ibm.wala.shrikeBT.Constants;
import com.ibm.wala.shrikeBT.IInstruction;
import com.ibm.wala.shrikeBT.PopInstruction;
import com.ibm.wala.shrikeBT.ReturnInstruction;
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

		Map<Set<Integer>, List<IInstruction>> slicerCriterionResultMap = new HashMap<>();
//		slicerCriterionResultMap.put(Set.of(0), Arrays.asList(ConstantInstruction.make(0), PopInstruction.make(1),
//				ReturnInstruction.make(Constants.TYPE_void)));
		slicerCriterionResultMap.put(Set.of(1), Arrays.asList(ConstantInstruction.make(0), PopInstruction.make(1),
				ReturnInstruction.make(Constants.TYPE_void)));

		Path dir = Files.createTempDirectory("slicer-");
		for (Entry<Set<Integer>, List<IInstruction>> slicerCriterionResultEntry : slicerCriterionResultMap.entrySet()) {
			Set<Integer> criterionSet = slicerCriterionResultEntry.getKey();
			List<IInstruction> resultList = slicerCriterionResultEntry.getValue();

			slicer.setInstructionIndexes(criterionSet);
			SliceResult sliceResult = slicer.getSliceResult();
			
			System.out.println(sliceResult.toJavaSource());

//			Assert.assertTrue("Expected slice\n  " + resultList + "\nbut is\n  " + sliceResult.getSlice(),
//					resultList.equals(sliceResult.getSlice()));

			System.out.println(sliceResult);

//			ControlFlow controlFlow = sliceResult.getControlFlow();
//			Utilities.dotShow(dir, controlFlow.dotPrint());
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
