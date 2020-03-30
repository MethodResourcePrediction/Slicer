package de.rherzog.master.thesis.slicer.test;

import static org.junit.Assert.*;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

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
	public void testSliceForAllInstructions() throws IOException, InvalidClassFileException {
		slicer.setInputJar("../EvaluationPrograms.jar");
		slicer.setMethodSignature("LSleep;.q([Ljava/lang/String;)V");
		slicer.setInstructionIndexes(new HashSet<>(Arrays.asList(1)));

		int instructionCount = slicer.getControlFlow().getMethodData().getInstructions().length;
		for (int instructionIndex = 0; instructionIndex < instructionCount; instructionIndex++) {
			slicer.setInstructionIndexes(new HashSet<>(Arrays.asList(instructionIndex)));
			SliceResult sliceResult = slicer.getSliceResult();

			System.out.println(sliceResult);
			Assert.assertTrue(sliceResult.getInstructionIndex().size() > 0);
		}
	}
}
