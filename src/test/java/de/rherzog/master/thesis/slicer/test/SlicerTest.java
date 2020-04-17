package de.rherzog.master.thesis.slicer.test;

import static org.junit.Assert.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashSet;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import com.ibm.wala.shrikeCT.InvalidClassFileException;

import de.rherzog.master.thesis.slicer.ArgumentDependency;
import de.rherzog.master.thesis.slicer.BlockDependency;
import de.rherzog.master.thesis.slicer.ControlFlow;
import de.rherzog.master.thesis.slicer.DataDependency;
import de.rherzog.master.thesis.slicer.SliceResult;
import de.rherzog.master.thesis.slicer.Slicer;
import de.rherzog.master.thesis.utils.Utilities;

public class SlicerTest {
	private Slicer slicer;

	@Before
	public void setUp() throws Exception {
		slicer = new Slicer();
	}

	@Test
	public void testSliceForAllInstructions() throws IOException, InvalidClassFileException, InterruptedException {
		slicer.setInputJar("../EvaluationPrograms.jar");
//		slicer.setMethodSignature("LSleep;.w([Ljava/lang/String;)V");
		slicer.setMethodSignature("LBubbleSort;.bubble_srt([I)V");
		slicer.setInstructionIndexes(new HashSet<>(Arrays.asList(0)));

		SliceResult sliceResult = slicer.getSliceResult();
		ControlFlow controlFlow = sliceResult.getControlFlow();

		final Path dir = Files.createTempDirectory("slicer-");
		Utilities.dotShow(dir, controlFlow.dotPrint());
//		Utilities.dotShow(dir, new BlockDependency(controlFlow).dotPrint());
//		Utilities.dotShow(dir, new DataDependency(controlFlow).dotPrint());
//		Utilities.dotShow(dir, new ArgumentDependency(controlFlow).dotPrint());

		int instructionIndex = 11;
		slicer.setInstructionIndexes(new HashSet<>(Arrays.asList(instructionIndex)));
		sliceResult = slicer.getSliceResult();

		System.out.println(sliceResult);
		Assert.assertTrue(sliceResult.getInstructionIndex().size() > 0);

//		int instructionCount = slicer.getControlFlow().getMethodData().getInstructions().length;
//		for (int instructionIndex = 0; instructionIndex < instructionCount; instructionIndex++) {
//		}
	}
}
