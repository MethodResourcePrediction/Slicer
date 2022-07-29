package de.uniks.vs.methodresourceprediction.slicer.test;

import com.ibm.wala.shrike.shrikeBT.*;
import com.ibm.wala.shrike.shrikeBT.IInvokeInstruction.Dispatch;
import com.ibm.wala.shrike.shrikeCT.InvalidClassFileException;
import de.uniks.vs.methodresourceprediction.slicer.SliceResult;
import de.uniks.vs.methodresourceprediction.slicer.Slicer;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;
import java.util.Map.Entry;

import org.apache.commons.codec.DecoderException;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.jgrapht.io.ExportException;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertTrue;

public class SlicerTest {
    private Slicer slicer;

    @Before
    public void setUp() throws Exception {
        // Get path to java class
        String classFilePath =
                SlicerTest.class.getProtectionDomain().getCodeSource().getLocation().getPath();
        String path = URLDecoder.decode(classFilePath, StandardCharsets.UTF_8);

        String jvmPackageName = SlicerValidation.class.getPackageName();
        String packageName = jvmPackageName.replaceAll("\\.", File.separator) + File.separator;

        String slicerValidationClassPath =
                FilenameUtils.concat(path, packageName + SlicerValidation.class.getSimpleName() + ".class");
        String slicerValidationJarPath =
                FilenameUtils.concat(path, packageName + SlicerValidation.class.getSimpleName() + ".jar");

        Process process =
                new ProcessBuilder("jar", "-cfv", slicerValidationJarPath, slicerValidationClassPath)
                        .start();
        process.waitFor();
        if (process.exitValue() != 0) {
            System.err.println("Some error occured during jar compilation\n");
            System.err.println("\nStderr:\n" + readInputStream(process.getErrorStream()));
            System.out.println("\nStdout:\n" + readInputStream(process.getInputStream()));
            throw new IOException("Process exited with exit code " + process.exitValue());
        }

        slicer = new Slicer();
        slicer.setInputJar(slicerValidationJarPath);
    }

    @Test
    public void testSimpleMethodCall() throws InvalidClassFileException, IOException {
        slicer.setMethodSignature(
                "Lde.uniks.vs.methodresourceprediction.slicer.test.SlicerValidation;.simpleMethodCall()V");
        System.out.println(slicer.getMethodSummary());

        Map<Set<Integer>, List<IInstruction>> slicerCriterionResultMap = new HashMap<>();

        slicerCriterionResultMap.put(
                Set.of(0),
                Arrays.asList(
                        InvokeInstruction.make(
                                "()J", "Ljava/lang/System;", "currentTimeMillis", Dispatch.STATIC),
                        PopInstruction.make(2),
                        ReturnInstruction.make(Constants.TYPE_void)));
        slicerCriterionResultMap.put(
                Set.of(1),
                Arrays.asList(
                        InvokeInstruction.make(
                                "()J", "Ljava/lang/System;", "currentTimeMillis", Dispatch.STATIC),
                        PopInstruction.make(2),
                        ReturnInstruction.make(Constants.TYPE_void)));
        slicerCriterionResultMap.put(
                Set.of(2), Arrays.asList(ReturnInstruction.make(Constants.TYPE_void)));

        validateSliceResults(slicerCriterionResultMap);
    }

    @Test
    public void testSimpleMethodCallWithParameter()
            throws IOException, InvalidClassFileException, InterruptedException, IllegalStateException,
            DecoderException {
        slicer.setMethodSignature(
                "Lde.uniks.vs.methodresourceprediction.slicer.test.SlicerValidation;.simpleMethodCallWithParameter(J)V");
        System.out.println(slicer.getMethodSummary());

        Map<Set<Integer>, List<IInstruction>> slicerCriterionResultMap = new HashMap<>();

        slicerCriterionResultMap.put(
                Set.of(0),
                Arrays.asList(
                        LoadInstruction.make(Constants.TYPE_long, 1),
                        PopInstruction.make(2),
                        ReturnInstruction.make(Constants.TYPE_void)));
        slicerCriterionResultMap.put(
                Set.of(1),
                Arrays.asList(
                        LoadInstruction.make(Constants.TYPE_long, 1),
                        InvokeInstruction.make("(J)V", "Ljava/lang/Thread;", "sleep", Dispatch.STATIC),
                        ReturnInstruction.make(Constants.TYPE_void)));
        slicerCriterionResultMap.put(
                Set.of(2), Arrays.asList(ReturnInstruction.make(Constants.TYPE_void)));

        validateSliceResults(slicerCriterionResultMap);
    }

    @Test
    public void testSimpleMethodCallAndLoopWithParameter()
            throws IOException, InvalidClassFileException, InterruptedException, IllegalStateException,
            DecoderException, ExportException {
        slicer.setMethodSignature(
                "Lde.uniks.vs.methodresourceprediction.slicer.test.SlicerValidation;.simpleMethodCallAndLoopWithParameter(J)V");
        System.out.println(slicer.getMethodSummary());

        //		slicer.getControlFlow().showPlot();
        //		slicer.getControlDependency().showPlot();

        Map<Set<Integer>, List<IInstruction>> slicerCriterionResultMap = new HashMap<>();

        slicerCriterionResultMap.put(
                Set.of(0),
                Arrays.asList(
                        ConstantInstruction.make(0),
                        PopInstruction.make(1),
                        ReturnInstruction.make(Constants.TYPE_void)));
        slicerCriterionResultMap.put(
                Set.of(1),
                Arrays.asList(
                        ConstantInstruction.make(0),
                        StoreInstruction.make(Constants.TYPE_int, 3),
                        ReturnInstruction.make(Constants.TYPE_void)));
        slicerCriterionResultMap.put(
                Set.of(2),
                Arrays.asList(
                        ConstantInstruction.make(0),
                        StoreInstruction.make(Constants.TYPE_int, 3),
                        LoadInstruction.make(Constants.TYPE_int, 3),
                        ConstantInstruction.make(5),
                        ConditionalBranchInstruction.make(
                                Constants.TYPE_int, ConditionalBranchInstruction.Operator.GE, 12),
                        LoadInstruction.make(Constants.TYPE_int, 3),
                        ConstantInstruction.make(1),
                        BinaryOpInstruction.make(Constants.TYPE_int, IBinaryOpInstruction.Operator.ADD),
                        StoreInstruction.make(Constants.TYPE_int, 3),
                        GotoInstruction.make(2),
                        ReturnInstruction.make(Constants.TYPE_void)));
        slicerCriterionResultMap.put(
                Set.of(3),
                Arrays.asList(
                        ConstantInstruction.make(5),
                        PopInstruction.make(1),
                        ReturnInstruction.make(Constants.TYPE_void)));
        slicerCriterionResultMap.put(
                Set.of(4),
                Arrays.asList(
                        ConstantInstruction.make(0),
                        StoreInstruction.make(Constants.TYPE_int, 3),
                        LoadInstruction.make(Constants.TYPE_int, 3),
                        ConstantInstruction.make(5),
                        ConditionalBranchInstruction.make(
                                Constants.TYPE_int, ConditionalBranchInstruction.Operator.GE, 12),
                        LoadInstruction.make(Constants.TYPE_int, 3),
                        ConstantInstruction.make(1),
                        BinaryOpInstruction.make(Constants.TYPE_int, IBinaryOpInstruction.Operator.ADD),
                        StoreInstruction.make(Constants.TYPE_int, 3),
                        GotoInstruction.make(2),
                        ReturnInstruction.make(Constants.TYPE_void)));
        slicerCriterionResultMap.put(
                Set.of(5),
                Arrays.asList(
                        ConstantInstruction.make(0),
                        StoreInstruction.make(Constants.TYPE_int, 3),
                        LoadInstruction.make(Constants.TYPE_int, 3),
                        ConstantInstruction.make(5),
                        ConditionalBranchInstruction.make(
                                Constants.TYPE_int, ConditionalBranchInstruction.Operator.GE, 12),
                        LoadInstruction.make(Constants.TYPE_long, 1),
                        PopInstruction.make(2),
                        LoadInstruction.make(Constants.TYPE_int, 3),
                        ConstantInstruction.make(1),
                        BinaryOpInstruction.make(Constants.TYPE_int, IBinaryOpInstruction.Operator.ADD),
                        StoreInstruction.make(Constants.TYPE_int, 3),
                        GotoInstruction.make(2),
                        ReturnInstruction.make(Constants.TYPE_void)));
        slicerCriterionResultMap.put(
                Set.of(6),
                Arrays.asList(
                        ConstantInstruction.make(0),
                        StoreInstruction.make(Constants.TYPE_int, 3),
                        LoadInstruction.make(Constants.TYPE_int, 3),
                        ConstantInstruction.make(5),
                        ConditionalBranchInstruction.make(
                                Constants.TYPE_int, ConditionalBranchInstruction.Operator.GE, 12),
                        LoadInstruction.make(Constants.TYPE_long, 1),
                        InvokeInstruction.make("(J)V", "Ljava/lang/Thread;", "sleep", Dispatch.STATIC),
                        LoadInstruction.make(Constants.TYPE_int, 3),
                        ConstantInstruction.make(1),
                        BinaryOpInstruction.make(Constants.TYPE_int, IBinaryOpInstruction.Operator.ADD),
                        StoreInstruction.make(Constants.TYPE_int, 3),
                        GotoInstruction.make(2),
                        ReturnInstruction.make(Constants.TYPE_void)));
        slicerCriterionResultMap.put(
                Set.of(7),
                Arrays.asList(
                        ConstantInstruction.make(0),
                        StoreInstruction.make(Constants.TYPE_int, 3),
                        LoadInstruction.make(Constants.TYPE_int, 3),
                        ConstantInstruction.make(5),
                        ConditionalBranchInstruction.make(
                                Constants.TYPE_int, ConditionalBranchInstruction.Operator.GE, 12),
                        LoadInstruction.make(Constants.TYPE_int, 3),
                        ConstantInstruction.make(1),
                        BinaryOpInstruction.make(Constants.TYPE_int, IBinaryOpInstruction.Operator.ADD),
                        StoreInstruction.make(Constants.TYPE_int, 3),
                        GotoInstruction.make(2),
                        ReturnInstruction.make(Constants.TYPE_void)));
        slicerCriterionResultMap.put(
                Set.of(8),
                Arrays.asList(
                        ConstantInstruction.make(1),
                        PopInstruction.make(1),
                        ReturnInstruction.make(Constants.TYPE_void)));
        slicerCriterionResultMap.put(
                Set.of(9),
                Arrays.asList(
                        ConstantInstruction.make(0),
                        StoreInstruction.make(Constants.TYPE_int, 3),
                        LoadInstruction.make(Constants.TYPE_int, 3),
                        ConstantInstruction.make(5),
                        ConditionalBranchInstruction.make(
                                Constants.TYPE_int, ConditionalBranchInstruction.Operator.GE, 12),
                        LoadInstruction.make(Constants.TYPE_int, 3),
                        ConstantInstruction.make(1),
                        BinaryOpInstruction.make(Constants.TYPE_int, IBinaryOpInstruction.Operator.ADD),
                        StoreInstruction.make(Constants.TYPE_int, 3),
                        GotoInstruction.make(2),
                        ReturnInstruction.make(Constants.TYPE_void)));
        slicerCriterionResultMap.put(
                Set.of(10),
                Arrays.asList(
                        ConstantInstruction.make(0),
                        StoreInstruction.make(Constants.TYPE_int, 3),
                        LoadInstruction.make(Constants.TYPE_int, 3),
                        ConstantInstruction.make(5),
                        ConditionalBranchInstruction.make(
                                Constants.TYPE_int, ConditionalBranchInstruction.Operator.GE, 12),
                        LoadInstruction.make(Constants.TYPE_int, 3),
                        ConstantInstruction.make(1),
                        BinaryOpInstruction.make(Constants.TYPE_int, IBinaryOpInstruction.Operator.ADD),
                        StoreInstruction.make(Constants.TYPE_int, 3),
                        GotoInstruction.make(2),
                        ReturnInstruction.make(Constants.TYPE_void)));
        slicerCriterionResultMap.put(
                Set.of(11),
                Arrays.asList(
                        ConstantInstruction.make(0),
                        StoreInstruction.make(Constants.TYPE_int, 3),
                        LoadInstruction.make(Constants.TYPE_int, 3),
                        ConstantInstruction.make(5),
                        ConditionalBranchInstruction.make(
                                Constants.TYPE_int, ConditionalBranchInstruction.Operator.GE, 12),
                        LoadInstruction.make(Constants.TYPE_int, 3),
                        ConstantInstruction.make(1),
                        BinaryOpInstruction.make(Constants.TYPE_int, IBinaryOpInstruction.Operator.ADD),
                        StoreInstruction.make(Constants.TYPE_int, 3),
                        GotoInstruction.make(2),
                        ReturnInstruction.make(Constants.TYPE_void)));
        slicerCriterionResultMap.put(
                Set.of(12), Arrays.asList(ReturnInstruction.make(Constants.TYPE_void)));

        validateSliceResults(slicerCriterionResultMap);
    }

    @Test
    public void testSliceReuseVariableWithReinitialization()
            throws IOException, InvalidClassFileException, InterruptedException {
        slicer.setMethodSignature(
                "Lde.uniks.vs.methodresourceprediction.slicer.test.SlicerValidation;.reuseVariableWithReinitialization()V");
        System.out.println(slicer.getMethodSummary());

        Map<Set<Integer>, List<IInstruction>> slicerCriterionResultMap = new HashMap<>();

        slicerCriterionResultMap.put(
                Set.of(0),
                Arrays.asList(
                        ConstantInstruction.make(0),
                        PopInstruction.make(1),
                        ReturnInstruction.make(Constants.TYPE_void)));
        slicerCriterionResultMap.put(
                Set.of(1),
                Arrays.asList(
                        ConstantInstruction.make(0),
                        StoreInstruction.make(Constants.TYPE_int, 1),
                        ReturnInstruction.make(Constants.TYPE_void)));
        slicerCriterionResultMap.put(
                Set.of(2),
                Arrays.asList(
                        ConstantInstruction.make(0),
                        StoreInstruction.make(Constants.TYPE_int, 1),
                        LoadInstruction.make(Constants.TYPE_int, 1),
                        ConstantInstruction.make(2),
                        ConditionalBranchInstruction.make(
                                Constants.TYPE_int, ConditionalBranchInstruction.Operator.GE, 12),
                        LoadInstruction.make(Constants.TYPE_int, 1),
                        ConstantInstruction.make(1),
                        BinaryOpInstruction.make(Constants.TYPE_int, IBinaryOpInstruction.Operator.ADD),
                        StoreInstruction.make(Constants.TYPE_int, 1),
                        GotoInstruction.make(2),
                        InvokeInstruction.make(
                                "()V",
                                "Lde/uniks/vs/methodresourceprediction/slicer/export/Nothing;",
                                "doNothing",
                                Dispatch.STATIC),
                        ReturnInstruction.make(Constants.TYPE_void)));
        slicerCriterionResultMap.put(
                Set.of(3),
                Arrays.asList(
                        ConstantInstruction.make(2),
                        PopInstruction.make(1),
                        ReturnInstruction.make(Constants.TYPE_void)));
        slicerCriterionResultMap.put(
                Set.of(4),
                Arrays.asList(
                        ConstantInstruction.make(0),
                        StoreInstruction.make(Constants.TYPE_int, 1),
                        LoadInstruction.make(Constants.TYPE_int, 1),
                        ConstantInstruction.make(2),
                        ConditionalBranchInstruction.make(
                                Constants.TYPE_int, ConditionalBranchInstruction.Operator.GE, 12),
                        LoadInstruction.make(Constants.TYPE_int, 1),
                        ConstantInstruction.make(1),
                        BinaryOpInstruction.make(Constants.TYPE_int, IBinaryOpInstruction.Operator.ADD),
                        StoreInstruction.make(Constants.TYPE_int, 1),
                        GotoInstruction.make(2),
                        InvokeInstruction.make(
                                "()V",
                                "Lde/uniks/vs/methodresourceprediction/slicer/export/Nothing;",
                                "doNothing",
                                Dispatch.STATIC),
                        ReturnInstruction.make(Constants.TYPE_void)));
        slicerCriterionResultMap.put(
                Set.of(5),
                Arrays.asList(
                        ConstantInstruction.make(0),
                        StoreInstruction.make(Constants.TYPE_int, 1),
                        LoadInstruction.make(Constants.TYPE_int, 1),
                        ConstantInstruction.make(2),
                        ConditionalBranchInstruction.make(
                                Constants.TYPE_int, ConditionalBranchInstruction.Operator.GE, 12),
                        InvokeInstruction.make(
                                "()J", "Ljava/lang/System;", "currentTimeMillis", Dispatch.STATIC),
                        PopInstruction.make(2),
                        LoadInstruction.make(Constants.TYPE_int, 1),
                        ConstantInstruction.make(1),
                        BinaryOpInstruction.make(Constants.TYPE_int, IBinaryOpInstruction.Operator.ADD),
                        StoreInstruction.make(Constants.TYPE_int, 1),
                        GotoInstruction.make(2),
                        InvokeInstruction.make(
                                "()V",
                                "Lde/uniks/vs/methodresourceprediction/slicer/export/Nothing;",
                                "doNothing",
                                Dispatch.STATIC),
                        ReturnInstruction.make(Constants.TYPE_void)));
        slicerCriterionResultMap.put(
                Set.of(6),
                Arrays.asList(
                        ConstantInstruction.make(0),
                        StoreInstruction.make(Constants.TYPE_int, 1),
                        LoadInstruction.make(Constants.TYPE_int, 1),
                        ConstantInstruction.make(2),
                        ConditionalBranchInstruction.make(
                                Constants.TYPE_int, ConditionalBranchInstruction.Operator.GE, 12),
                        InvokeInstruction.make(
                                "()J", "Ljava/lang/System;", "currentTimeMillis", Dispatch.STATIC),
                        PopInstruction.make(2),
                        LoadInstruction.make(Constants.TYPE_int, 1),
                        ConstantInstruction.make(1),
                        BinaryOpInstruction.make(Constants.TYPE_int, IBinaryOpInstruction.Operator.ADD),
                        StoreInstruction.make(Constants.TYPE_int, 1),
                        GotoInstruction.make(2),
                        InvokeInstruction.make(
                                "()V",
                                "Lde/uniks/vs/methodresourceprediction/slicer/export/Nothing;",
                                "doNothing",
                                Dispatch.STATIC),
                        ReturnInstruction.make(Constants.TYPE_void)));
        slicerCriterionResultMap.put(
                Set.of(7),
                Arrays.asList(
                        ConstantInstruction.make(0),
                        StoreInstruction.make(Constants.TYPE_int, 1),
                        LoadInstruction.make(Constants.TYPE_int, 1),
                        ConstantInstruction.make(2),
                        ConditionalBranchInstruction.make(
                                Constants.TYPE_int, ConditionalBranchInstruction.Operator.GE, 12),
                        LoadInstruction.make(Constants.TYPE_int, 1),
                        ConstantInstruction.make(1),
                        BinaryOpInstruction.make(Constants.TYPE_int, IBinaryOpInstruction.Operator.ADD),
                        StoreInstruction.make(Constants.TYPE_int, 1),
                        GotoInstruction.make(2),
                        InvokeInstruction.make(
                                "()V",
                                "Lde/uniks/vs/methodresourceprediction/slicer/export/Nothing;",
                                "doNothing",
                                Dispatch.STATIC),
                        ReturnInstruction.make(Constants.TYPE_void)));
        slicerCriterionResultMap.put(
                Set.of(8),
                Arrays.asList(
                        ConstantInstruction.make(1),
                        PopInstruction.make(1),
                        ReturnInstruction.make(Constants.TYPE_void)));
        slicerCriterionResultMap.put(
                Set.of(9),
                Arrays.asList(
                        ConstantInstruction.make(0),
                        StoreInstruction.make(Constants.TYPE_int, 1),
                        LoadInstruction.make(Constants.TYPE_int, 1),
                        ConstantInstruction.make(2),
                        ConditionalBranchInstruction.make(
                                Constants.TYPE_int, ConditionalBranchInstruction.Operator.GE, 12),
                        LoadInstruction.make(Constants.TYPE_int, 1),
                        ConstantInstruction.make(1),
                        BinaryOpInstruction.make(Constants.TYPE_int, IBinaryOpInstruction.Operator.ADD),
                        StoreInstruction.make(Constants.TYPE_int, 1),
                        GotoInstruction.make(2),
                        InvokeInstruction.make(
                                "()V",
                                "Lde/uniks/vs/methodresourceprediction/slicer/export/Nothing;",
                                "doNothing",
                                Dispatch.STATIC),
                        ReturnInstruction.make(Constants.TYPE_void)));
        slicerCriterionResultMap.put(
                Set.of(10),
                Arrays.asList(
                        ConstantInstruction.make(0),
                        StoreInstruction.make(Constants.TYPE_int, 1),
                        LoadInstruction.make(Constants.TYPE_int, 1),
                        ConstantInstruction.make(2),
                        ConditionalBranchInstruction.make(
                                Constants.TYPE_int, ConditionalBranchInstruction.Operator.GE, 12),
                        LoadInstruction.make(Constants.TYPE_int, 1),
                        ConstantInstruction.make(1),
                        BinaryOpInstruction.make(Constants.TYPE_int, IBinaryOpInstruction.Operator.ADD),
                        StoreInstruction.make(Constants.TYPE_int, 1),
                        GotoInstruction.make(2),
                        InvokeInstruction.make(
                                "()V",
                                "Lde/uniks/vs/methodresourceprediction/slicer/export/Nothing;",
                                "doNothing",
                                Dispatch.STATIC),
                        ReturnInstruction.make(Constants.TYPE_void)));
        slicerCriterionResultMap.put(
                Set.of(11),
                Arrays.asList(
                        ConstantInstruction.make(0),
                        StoreInstruction.make(Constants.TYPE_int, 1),
                        LoadInstruction.make(Constants.TYPE_int, 1),
                        ConstantInstruction.make(2),
                        ConditionalBranchInstruction.make(
                                Constants.TYPE_int, ConditionalBranchInstruction.Operator.GE, 12),
                        LoadInstruction.make(Constants.TYPE_int, 1),
                        ConstantInstruction.make(1),
                        BinaryOpInstruction.make(Constants.TYPE_int, IBinaryOpInstruction.Operator.ADD),
                        StoreInstruction.make(Constants.TYPE_int, 1),
                        GotoInstruction.make(2),
                        InvokeInstruction.make(
                                "()V",
                                "Lde/uniks/vs/methodresourceprediction/slicer/export/Nothing;",
                                "doNothing",
                                Dispatch.STATIC),
                        ReturnInstruction.make(Constants.TYPE_void)));
        slicerCriterionResultMap.put(
                Set.of(12),
                Arrays.asList(
                        ConstantInstruction.make(0),
                        PopInstruction.make(1),
                        ReturnInstruction.make(Constants.TYPE_void)));
        slicerCriterionResultMap.put(
                Set.of(13),
                Arrays.asList(
                        ConstantInstruction.make(0),
                        StoreInstruction.make(Constants.TYPE_int, 1),
                        ReturnInstruction.make(Constants.TYPE_void)));
        slicerCriterionResultMap.put(
                Set.of(14),
                Arrays.asList(
                        ConstantInstruction.make(0),
                        StoreInstruction.make(Constants.TYPE_int, 1),
                        LoadInstruction.make(Constants.TYPE_int, 1),
                        ConstantInstruction.make(2),
                        ConditionalBranchInstruction.make(
                                Constants.TYPE_int, ConditionalBranchInstruction.Operator.GE, 12),
                        LoadInstruction.make(Constants.TYPE_int, 1),
                        ConstantInstruction.make(1),
                        BinaryOpInstruction.make(Constants.TYPE_int, IBinaryOpInstruction.Operator.ADD),
                        StoreInstruction.make(Constants.TYPE_int, 1),
                        GotoInstruction.make(2),
                        ConstantInstruction.make(0),
                        StoreInstruction.make(Constants.TYPE_int, 1),
                        LoadInstruction.make(Constants.TYPE_int, 1),
                        ConstantInstruction.make(5),
                        ConditionalBranchInstruction.make(
                                Constants.TYPE_int, ConditionalBranchInstruction.Operator.GE, 24),
                        LoadInstruction.make(Constants.TYPE_int, 1),
                        ConstantInstruction.make(1),
                        BinaryOpInstruction.make(Constants.TYPE_int, IBinaryOpInstruction.Operator.ADD),
                        StoreInstruction.make(Constants.TYPE_int, 1),
                        GotoInstruction.make(14),
                        ReturnInstruction.make(Constants.TYPE_void)));
        slicerCriterionResultMap.put(
                Set.of(15),
                Arrays.asList(
                        ConstantInstruction.make(5),
                        PopInstruction.make(1),
                        ReturnInstruction.make(Constants.TYPE_void)));
        slicerCriterionResultMap.put(
                Set.of(16),
                Arrays.asList(
                        ConstantInstruction.make(0),
                        StoreInstruction.make(Constants.TYPE_int, 1),
                        LoadInstruction.make(Constants.TYPE_int, 1),
                        ConstantInstruction.make(2),
                        ConditionalBranchInstruction.make(
                                Constants.TYPE_int, ConditionalBranchInstruction.Operator.GE, 12),
                        LoadInstruction.make(Constants.TYPE_int, 1),
                        ConstantInstruction.make(1),
                        BinaryOpInstruction.make(Constants.TYPE_int, IBinaryOpInstruction.Operator.ADD),
                        StoreInstruction.make(Constants.TYPE_int, 1),
                        GotoInstruction.make(2),
                        ConstantInstruction.make(0),
                        StoreInstruction.make(Constants.TYPE_int, 1),
                        LoadInstruction.make(Constants.TYPE_int, 1),
                        ConstantInstruction.make(5),
                        ConditionalBranchInstruction.make(
                                Constants.TYPE_int, ConditionalBranchInstruction.Operator.GE, 24),
                        LoadInstruction.make(Constants.TYPE_int, 1),
                        ConstantInstruction.make(1),
                        BinaryOpInstruction.make(Constants.TYPE_int, IBinaryOpInstruction.Operator.ADD),
                        StoreInstruction.make(Constants.TYPE_int, 1),
                        GotoInstruction.make(14),
                        ReturnInstruction.make(Constants.TYPE_void)));
        slicerCriterionResultMap.put(
                Set.of(17),
                Arrays.asList(
                        ConstantInstruction.make(0),
                        StoreInstruction.make(Constants.TYPE_int, 1),
                        LoadInstruction.make(Constants.TYPE_int, 1),
                        ConstantInstruction.make(2),
                        ConditionalBranchInstruction.make(
                                Constants.TYPE_int, ConditionalBranchInstruction.Operator.GE, 12),
                        LoadInstruction.make(Constants.TYPE_int, 1),
                        ConstantInstruction.make(1),
                        BinaryOpInstruction.make(Constants.TYPE_int, IBinaryOpInstruction.Operator.ADD),
                        StoreInstruction.make(Constants.TYPE_int, 1),
                        GotoInstruction.make(2),
                        ConstantInstruction.make(0),
                        StoreInstruction.make(Constants.TYPE_int, 1),
                        LoadInstruction.make(Constants.TYPE_int, 1),
                        ConstantInstruction.make(5),
                        ConditionalBranchInstruction.make(
                                Constants.TYPE_int, ConditionalBranchInstruction.Operator.GE, 24),
                        InvokeInstruction.make(
                                "()J", "Ljava/lang/System;", "currentTimeMillis", Dispatch.STATIC),
                        PopInstruction.make(2),
                        LoadInstruction.make(Constants.TYPE_int, 1),
                        ConstantInstruction.make(1),
                        BinaryOpInstruction.make(Constants.TYPE_int, IBinaryOpInstruction.Operator.ADD),
                        StoreInstruction.make(Constants.TYPE_int, 1),
                        GotoInstruction.make(14),
                        ReturnInstruction.make(Constants.TYPE_void)));
        slicerCriterionResultMap.put(
                Set.of(18),
                Arrays.asList(
                        ConstantInstruction.make(0),
                        StoreInstruction.make(Constants.TYPE_int, 1),
                        LoadInstruction.make(Constants.TYPE_int, 1),
                        ConstantInstruction.make(2),
                        ConditionalBranchInstruction.make(
                                Constants.TYPE_int, ConditionalBranchInstruction.Operator.GE, 12),
                        LoadInstruction.make(Constants.TYPE_int, 1),
                        ConstantInstruction.make(1),
                        BinaryOpInstruction.make(Constants.TYPE_int, IBinaryOpInstruction.Operator.ADD),
                        StoreInstruction.make(Constants.TYPE_int, 1),
                        GotoInstruction.make(2),
                        ConstantInstruction.make(0),
                        StoreInstruction.make(Constants.TYPE_int, 1),
                        LoadInstruction.make(Constants.TYPE_int, 1),
                        ConstantInstruction.make(5),
                        ConditionalBranchInstruction.make(
                                Constants.TYPE_int, ConditionalBranchInstruction.Operator.GE, 24),
                        InvokeInstruction.make(
                                "()J", "Ljava/lang/System;", "currentTimeMillis", Dispatch.STATIC),
                        PopInstruction.make(2),
                        LoadInstruction.make(Constants.TYPE_int, 1),
                        ConstantInstruction.make(1),
                        BinaryOpInstruction.make(Constants.TYPE_int, IBinaryOpInstruction.Operator.ADD),
                        StoreInstruction.make(Constants.TYPE_int, 1),
                        GotoInstruction.make(14),
                        ReturnInstruction.make(Constants.TYPE_void)));
        slicerCriterionResultMap.put(
                Set.of(19),
                Arrays.asList(
                        ConstantInstruction.make(0),
                        StoreInstruction.make(Constants.TYPE_int, 1),
                        LoadInstruction.make(Constants.TYPE_int, 1),
                        ConstantInstruction.make(2),
                        ConditionalBranchInstruction.make(
                                Constants.TYPE_int, ConditionalBranchInstruction.Operator.GE, 12),
                        LoadInstruction.make(Constants.TYPE_int, 1),
                        ConstantInstruction.make(1),
                        BinaryOpInstruction.make(Constants.TYPE_int, IBinaryOpInstruction.Operator.ADD),
                        StoreInstruction.make(Constants.TYPE_int, 1),
                        GotoInstruction.make(2),
                        ConstantInstruction.make(0),
                        StoreInstruction.make(Constants.TYPE_int, 1),
                        LoadInstruction.make(Constants.TYPE_int, 1),
                        ConstantInstruction.make(5),
                        ConditionalBranchInstruction.make(
                                Constants.TYPE_int, ConditionalBranchInstruction.Operator.GE, 24),
                        LoadInstruction.make(Constants.TYPE_int, 1),
                        ConstantInstruction.make(1),
                        BinaryOpInstruction.make(Constants.TYPE_int, IBinaryOpInstruction.Operator.ADD),
                        StoreInstruction.make(Constants.TYPE_int, 1),
                        GotoInstruction.make(14),
                        ReturnInstruction.make(Constants.TYPE_void)));
        slicerCriterionResultMap.put(
                Set.of(20),
                Arrays.asList(
                        ConstantInstruction.make(1),
                        PopInstruction.make(1),
                        ReturnInstruction.make(Constants.TYPE_void)));
        slicerCriterionResultMap.put(
                Set.of(21),
                Arrays.asList(
                        ConstantInstruction.make(0),
                        StoreInstruction.make(Constants.TYPE_int, 1),
                        LoadInstruction.make(Constants.TYPE_int, 1),
                        ConstantInstruction.make(2),
                        ConditionalBranchInstruction.make(
                                Constants.TYPE_int, ConditionalBranchInstruction.Operator.GE, 12),
                        LoadInstruction.make(Constants.TYPE_int, 1),
                        ConstantInstruction.make(1),
                        BinaryOpInstruction.make(Constants.TYPE_int, IBinaryOpInstruction.Operator.ADD),
                        StoreInstruction.make(Constants.TYPE_int, 1),
                        GotoInstruction.make(2),
                        ConstantInstruction.make(0),
                        StoreInstruction.make(Constants.TYPE_int, 1),
                        LoadInstruction.make(Constants.TYPE_int, 1),
                        ConstantInstruction.make(5),
                        ConditionalBranchInstruction.make(
                                Constants.TYPE_int, ConditionalBranchInstruction.Operator.GE, 24),
                        LoadInstruction.make(Constants.TYPE_int, 1),
                        ConstantInstruction.make(1),
                        BinaryOpInstruction.make(Constants.TYPE_int, IBinaryOpInstruction.Operator.ADD),
                        StoreInstruction.make(Constants.TYPE_int, 1),
                        GotoInstruction.make(14),
                        ReturnInstruction.make(Constants.TYPE_void)));
        slicerCriterionResultMap.put(
                Set.of(22),
                Arrays.asList(
                        ConstantInstruction.make(0),
                        StoreInstruction.make(Constants.TYPE_int, 1),
                        LoadInstruction.make(Constants.TYPE_int, 1),
                        ConstantInstruction.make(2),
                        ConditionalBranchInstruction.make(
                                Constants.TYPE_int, ConditionalBranchInstruction.Operator.GE, 12),
                        LoadInstruction.make(Constants.TYPE_int, 1),
                        ConstantInstruction.make(1),
                        BinaryOpInstruction.make(Constants.TYPE_int, IBinaryOpInstruction.Operator.ADD),
                        StoreInstruction.make(Constants.TYPE_int, 1),
                        GotoInstruction.make(2),
                        ConstantInstruction.make(0),
                        StoreInstruction.make(Constants.TYPE_int, 1),
                        LoadInstruction.make(Constants.TYPE_int, 1),
                        ConstantInstruction.make(5),
                        ConditionalBranchInstruction.make(
                                Constants.TYPE_int, ConditionalBranchInstruction.Operator.GE, 24),
                        LoadInstruction.make(Constants.TYPE_int, 1),
                        ConstantInstruction.make(1),
                        BinaryOpInstruction.make(Constants.TYPE_int, IBinaryOpInstruction.Operator.ADD),
                        StoreInstruction.make(Constants.TYPE_int, 1),
                        GotoInstruction.make(14),
                        ReturnInstruction.make(Constants.TYPE_void)));
        slicerCriterionResultMap.put(
                Set.of(23),
                Arrays.asList(
                        ConstantInstruction.make(0),
                        StoreInstruction.make(Constants.TYPE_int, 1),
                        LoadInstruction.make(Constants.TYPE_int, 1),
                        ConstantInstruction.make(2),
                        ConditionalBranchInstruction.make(
                                Constants.TYPE_int, ConditionalBranchInstruction.Operator.GE, 12),
                        LoadInstruction.make(Constants.TYPE_int, 1),
                        ConstantInstruction.make(1),
                        BinaryOpInstruction.make(Constants.TYPE_int, IBinaryOpInstruction.Operator.ADD),
                        StoreInstruction.make(Constants.TYPE_int, 1),
                        GotoInstruction.make(2),
                        ConstantInstruction.make(0),
                        StoreInstruction.make(Constants.TYPE_int, 1),
                        LoadInstruction.make(Constants.TYPE_int, 1),
                        ConstantInstruction.make(5),
                        ConditionalBranchInstruction.make(
                                Constants.TYPE_int, ConditionalBranchInstruction.Operator.GE, 24),
                        LoadInstruction.make(Constants.TYPE_int, 1),
                        ConstantInstruction.make(1),
                        BinaryOpInstruction.make(Constants.TYPE_int, IBinaryOpInstruction.Operator.ADD),
                        StoreInstruction.make(Constants.TYPE_int, 1),
                        GotoInstruction.make(14),
                        ReturnInstruction.make(Constants.TYPE_void)));
        slicerCriterionResultMap.put(
                Set.of(24), Arrays.asList(ReturnInstruction.make(Constants.TYPE_void)));

        validateSliceResults(slicerCriterionResultMap);
    }

    @Test
    public void testSliceReuseVariableWithReinitializationDoubleSized()
            throws IOException, InvalidClassFileException, InterruptedException {
        slicer.setMethodSignature(
                "Lde.uniks.vs.methodresourceprediction.slicer.test.SlicerValidation;.reuseVariableWithReinitializationDoubleSized()V");
        System.out.println(slicer.getMethodSummary());

        Map<Set<Integer>, List<IInstruction>> slicerCriterionResultMap = new HashMap<>();

        slicerCriterionResultMap.put(
                Set.of(0),
                Arrays.asList(
                        ConstantInstruction.make(0L),
                        PopInstruction.make(2),
                        ReturnInstruction.make(Constants.TYPE_void)));
        slicerCriterionResultMap.put(
                Set.of(1),
                Arrays.asList(
                        ConstantInstruction.make(0L),
                        StoreInstruction.make(Constants.TYPE_long, 1),
                        ReturnInstruction.make(Constants.TYPE_void)));
        slicerCriterionResultMap.put(
                Set.of(2),
                Arrays.asList(
                        ConstantInstruction.make(0L),
                        StoreInstruction.make(Constants.TYPE_long, 1),
                        LoadInstruction.make(Constants.TYPE_long, 1),
                        ConstantInstruction.make(2L),
                        ComparisonInstruction.make(Constants.TYPE_long, IComparisonInstruction.Operator.CMP),
                        ConstantInstruction.make(0),
                        ConditionalBranchInstruction.make(
                                Constants.TYPE_int, ConditionalBranchInstruction.Operator.GE, 14),
                        LoadInstruction.make(Constants.TYPE_long, 1),
                        ConstantInstruction.make(1L),
                        BinaryOpInstruction.make(Constants.TYPE_long, IBinaryOpInstruction.Operator.ADD),
                        StoreInstruction.make(Constants.TYPE_long, 1),
                        GotoInstruction.make(2),
                        InvokeInstruction.make(
                                "()V",
                                "Lde/uniks/vs/methodresourceprediction/slicer/export/Nothing;",
                                "doNothing",
                                Dispatch.STATIC),
                        ReturnInstruction.make(Constants.TYPE_void)));
        slicerCriterionResultMap.put(
                Set.of(3),
                Arrays.asList(
                        ConstantInstruction.make(2L),
                        PopInstruction.make(2),
                        ReturnInstruction.make(Constants.TYPE_void)));
        slicerCriterionResultMap.put(
                Set.of(4),
                Arrays.asList(
                        ConstantInstruction.make(0L),
                        StoreInstruction.make(Constants.TYPE_long, 1),
                        LoadInstruction.make(Constants.TYPE_long, 1),
                        ConstantInstruction.make(2L),
                        ComparisonInstruction.make(Constants.TYPE_long, IComparisonInstruction.Operator.CMP),
                        ConstantInstruction.make(0),
                        ConditionalBranchInstruction.make(
                                Constants.TYPE_int, ConditionalBranchInstruction.Operator.GE, 14),
                        LoadInstruction.make(Constants.TYPE_long, 1),
                        ConstantInstruction.make(1L),
                        BinaryOpInstruction.make(Constants.TYPE_long, IBinaryOpInstruction.Operator.ADD),
                        StoreInstruction.make(Constants.TYPE_long, 1),
                        GotoInstruction.make(2),
                        InvokeInstruction.make(
                                "()V",
                                "Lde/uniks/vs/methodresourceprediction/slicer/export/Nothing;",
                                "doNothing",
                                Dispatch.STATIC),
                        ReturnInstruction.make(Constants.TYPE_void)));
        slicerCriterionResultMap.put(
                Set.of(5),
                Arrays.asList(
                        ConstantInstruction.make(0),
                        PopInstruction.make(1),
                        ReturnInstruction.make(Constants.TYPE_void)));
        slicerCriterionResultMap.put(
                Set.of(6),
                Arrays.asList(
                        ConstantInstruction.make(0L),
                        StoreInstruction.make(Constants.TYPE_long, 1),
                        LoadInstruction.make(Constants.TYPE_long, 1),
                        ConstantInstruction.make(2L),
                        ComparisonInstruction.make(Constants.TYPE_long, IComparisonInstruction.Operator.CMP),
                        ConstantInstruction.make(0),
                        ConditionalBranchInstruction.make(
                                Constants.TYPE_int, ConditionalBranchInstruction.Operator.GE, 14),
                        LoadInstruction.make(Constants.TYPE_long, 1),
                        ConstantInstruction.make(1L),
                        BinaryOpInstruction.make(Constants.TYPE_long, IBinaryOpInstruction.Operator.ADD),
                        StoreInstruction.make(Constants.TYPE_long, 1),
                        GotoInstruction.make(2),
                        InvokeInstruction.make(
                                "()V",
                                "Lde/uniks/vs/methodresourceprediction/slicer/export/Nothing;",
                                "doNothing",
                                Dispatch.STATIC),
                        ReturnInstruction.make(Constants.TYPE_void)));
        slicerCriterionResultMap.put(
                Set.of(7),
                Arrays.asList(
                        ConstantInstruction.make(0L),
                        StoreInstruction.make(Constants.TYPE_long, 1),
                        LoadInstruction.make(Constants.TYPE_long, 1),
                        ConstantInstruction.make(2L),
                        ComparisonInstruction.make(Constants.TYPE_long, IComparisonInstruction.Operator.CMP),
                        ConstantInstruction.make(0),
                        ConditionalBranchInstruction.make(
                                Constants.TYPE_int, ConditionalBranchInstruction.Operator.GE, 14),
                        InvokeInstruction.make(
                                "()J", "Ljava/lang/System;", "currentTimeMillis", Dispatch.STATIC),
                        PopInstruction.make(2),
                        LoadInstruction.make(Constants.TYPE_long, 1),
                        ConstantInstruction.make(1L),
                        BinaryOpInstruction.make(Constants.TYPE_long, IBinaryOpInstruction.Operator.ADD),
                        StoreInstruction.make(Constants.TYPE_long, 1),
                        GotoInstruction.make(2),
                        InvokeInstruction.make(
                                "()V",
                                "Lde/uniks/vs/methodresourceprediction/slicer/export/Nothing;",
                                "doNothing",
                                Dispatch.STATIC),
                        ReturnInstruction.make(Constants.TYPE_void)));
        slicerCriterionResultMap.put(
                Set.of(8),
                Arrays.asList(
                        ConstantInstruction.make(0L),
                        StoreInstruction.make(Constants.TYPE_long, 1),
                        LoadInstruction.make(Constants.TYPE_long, 1),
                        ConstantInstruction.make(2L),
                        ComparisonInstruction.make(Constants.TYPE_long, IComparisonInstruction.Operator.CMP),
                        ConstantInstruction.make(0),
                        ConditionalBranchInstruction.make(
                                Constants.TYPE_int, ConditionalBranchInstruction.Operator.GE, 14),
                        InvokeInstruction.make(
                                "()J", "Ljava/lang/System;", "currentTimeMillis", Dispatch.STATIC),
                        PopInstruction.make(2),
                        LoadInstruction.make(Constants.TYPE_long, 1),
                        ConstantInstruction.make(1L),
                        BinaryOpInstruction.make(Constants.TYPE_long, IBinaryOpInstruction.Operator.ADD),
                        StoreInstruction.make(Constants.TYPE_long, 1),
                        GotoInstruction.make(2),
                        InvokeInstruction.make(
                                "()V",
                                "Lde/uniks/vs/methodresourceprediction/slicer/export/Nothing;",
                                "doNothing",
                                Dispatch.STATIC),
                        ReturnInstruction.make(Constants.TYPE_void)));
        slicerCriterionResultMap.put(
                Set.of(9),
                Arrays.asList(
                        ConstantInstruction.make(0L),
                        StoreInstruction.make(Constants.TYPE_long, 1),
                        LoadInstruction.make(Constants.TYPE_long, 1),
                        ConstantInstruction.make(2L),
                        ComparisonInstruction.make(Constants.TYPE_long, IComparisonInstruction.Operator.CMP),
                        ConstantInstruction.make(0),
                        ConditionalBranchInstruction.make(
                                Constants.TYPE_int, ConditionalBranchInstruction.Operator.GE, 14),
                        LoadInstruction.make(Constants.TYPE_long, 1),
                        ConstantInstruction.make(1L),
                        BinaryOpInstruction.make(Constants.TYPE_long, IBinaryOpInstruction.Operator.ADD),
                        StoreInstruction.make(Constants.TYPE_long, 1),
                        GotoInstruction.make(2),
                        InvokeInstruction.make(
                                "()V",
                                "Lde/uniks/vs/methodresourceprediction/slicer/export/Nothing;",
                                "doNothing",
                                Dispatch.STATIC),
                        ReturnInstruction.make(Constants.TYPE_void)));
        slicerCriterionResultMap.put(
                Set.of(10),
                Arrays.asList(
                        ConstantInstruction.make(1L),
                        PopInstruction.make(2),
                        ReturnInstruction.make(Constants.TYPE_void)));
        slicerCriterionResultMap.put(
                Set.of(11),
                Arrays.asList(
                        ConstantInstruction.make(0L),
                        StoreInstruction.make(Constants.TYPE_long, 1),
                        LoadInstruction.make(Constants.TYPE_long, 1),
                        ConstantInstruction.make(2L),
                        ComparisonInstruction.make(Constants.TYPE_long, IComparisonInstruction.Operator.CMP),
                        ConstantInstruction.make(0),
                        ConditionalBranchInstruction.make(
                                Constants.TYPE_int, ConditionalBranchInstruction.Operator.GE, 14),
                        LoadInstruction.make(Constants.TYPE_long, 1),
                        ConstantInstruction.make(1L),
                        BinaryOpInstruction.make(Constants.TYPE_long, IBinaryOpInstruction.Operator.ADD),
                        StoreInstruction.make(Constants.TYPE_long, 1),
                        GotoInstruction.make(2),
                        InvokeInstruction.make(
                                "()V",
                                "Lde/uniks/vs/methodresourceprediction/slicer/export/Nothing;",
                                "doNothing",
                                Dispatch.STATIC),
                        ReturnInstruction.make(Constants.TYPE_void)));
        slicerCriterionResultMap.put(
                Set.of(12),
                Arrays.asList(
                        ConstantInstruction.make(0L),
                        StoreInstruction.make(Constants.TYPE_long, 1),
                        LoadInstruction.make(Constants.TYPE_long, 1),
                        ConstantInstruction.make(2L),
                        ComparisonInstruction.make(Constants.TYPE_long, IComparisonInstruction.Operator.CMP),
                        ConstantInstruction.make(0),
                        ConditionalBranchInstruction.make(
                                Constants.TYPE_int, ConditionalBranchInstruction.Operator.GE, 14),
                        LoadInstruction.make(Constants.TYPE_long, 1),
                        ConstantInstruction.make(1L),
                        BinaryOpInstruction.make(Constants.TYPE_long, IBinaryOpInstruction.Operator.ADD),
                        StoreInstruction.make(Constants.TYPE_long, 1),
                        GotoInstruction.make(2),
                        InvokeInstruction.make(
                                "()V",
                                "Lde/uniks/vs/methodresourceprediction/slicer/export/Nothing;",
                                "doNothing",
                                Dispatch.STATIC),
                        ReturnInstruction.make(Constants.TYPE_void)));
        slicerCriterionResultMap.put(
                Set.of(13),
                Arrays.asList(
                        ConstantInstruction.make(0L),
                        StoreInstruction.make(Constants.TYPE_long, 1),
                        LoadInstruction.make(Constants.TYPE_long, 1),
                        ConstantInstruction.make(2L),
                        ComparisonInstruction.make(Constants.TYPE_long, IComparisonInstruction.Operator.CMP),
                        ConstantInstruction.make(0),
                        ConditionalBranchInstruction.make(
                                Constants.TYPE_int, ConditionalBranchInstruction.Operator.GE, 14),
                        LoadInstruction.make(Constants.TYPE_long, 1),
                        ConstantInstruction.make(1L),
                        BinaryOpInstruction.make(Constants.TYPE_long, IBinaryOpInstruction.Operator.ADD),
                        StoreInstruction.make(Constants.TYPE_long, 1),
                        GotoInstruction.make(2),
                        InvokeInstruction.make(
                                "()V",
                                "Lde/uniks/vs/methodresourceprediction/slicer/export/Nothing;",
                                "doNothing",
                                Dispatch.STATIC),
                        ReturnInstruction.make(Constants.TYPE_void)));
        slicerCriterionResultMap.put(
                Set.of(14),
                Arrays.asList(
                        ConstantInstruction.make(0L),
                        StoreInstruction.make(Constants.TYPE_long, 1),
                        LoadInstruction.make(Constants.TYPE_long, 1),
                        ConstantInstruction.make(2L),
                        ComparisonInstruction.make(Constants.TYPE_long, IComparisonInstruction.Operator.CMP),
                        ConstantInstruction.make(0),
                        ConditionalBranchInstruction.make(
                                Constants.TYPE_int, ConditionalBranchInstruction.Operator.GE, 14),
                        LoadInstruction.make(Constants.TYPE_long, 1),
                        ConstantInstruction.make(1L),
                        BinaryOpInstruction.make(Constants.TYPE_long, IBinaryOpInstruction.Operator.ADD),
                        StoreInstruction.make(Constants.TYPE_long, 1),
                        GotoInstruction.make(2),
                        LoadInstruction.make(Constants.TYPE_long, 1),
                        PopInstruction.make(2),
                        ReturnInstruction.make(Constants.TYPE_void)));
        slicerCriterionResultMap.put(
                Set.of(15),
                Arrays.asList(
                        ConstantInstruction.make(1L),
                        PopInstruction.make(2),
                        ReturnInstruction.make(Constants.TYPE_void)));
        slicerCriterionResultMap.put(
                Set.of(16),
                Arrays.asList(
                        ConstantInstruction.make(0L),
                        StoreInstruction.make(Constants.TYPE_long, 1),
                        LoadInstruction.make(Constants.TYPE_long, 1),
                        ConstantInstruction.make(2L),
                        ComparisonInstruction.make(Constants.TYPE_long, IComparisonInstruction.Operator.CMP),
                        ConstantInstruction.make(0),
                        ConditionalBranchInstruction.make(
                                Constants.TYPE_int, ConditionalBranchInstruction.Operator.GE, 14),
                        LoadInstruction.make(Constants.TYPE_long, 1),
                        ConstantInstruction.make(1L),
                        BinaryOpInstruction.make(Constants.TYPE_long, IBinaryOpInstruction.Operator.ADD),
                        StoreInstruction.make(Constants.TYPE_long, 1),
                        GotoInstruction.make(2),
                        LoadInstruction.make(Constants.TYPE_long, 1),
                        ConstantInstruction.make(1L),
                        BinaryOpInstruction.make(Constants.TYPE_long, IBinaryOpInstruction.Operator.ADD),
                        PopInstruction.make(2),
                        ReturnInstruction.make(Constants.TYPE_void)));
        slicerCriterionResultMap.put(
                Set.of(17),
                Arrays.asList(
                        ConstantInstruction.make(0L),
                        StoreInstruction.make(Constants.TYPE_long, 1),
                        LoadInstruction.make(Constants.TYPE_long, 1),
                        ConstantInstruction.make(2L),
                        ComparisonInstruction.make(Constants.TYPE_long, IComparisonInstruction.Operator.CMP),
                        ConstantInstruction.make(0),
                        ConditionalBranchInstruction.make(
                                Constants.TYPE_int, ConditionalBranchInstruction.Operator.GE, 14),
                        LoadInstruction.make(Constants.TYPE_long, 1),
                        ConstantInstruction.make(1L),
                        BinaryOpInstruction.make(Constants.TYPE_long, IBinaryOpInstruction.Operator.ADD),
                        StoreInstruction.make(Constants.TYPE_long, 1),
                        GotoInstruction.make(2),
                        LoadInstruction.make(Constants.TYPE_long, 1),
                        ConstantInstruction.make(1L),
                        BinaryOpInstruction.make(Constants.TYPE_long, IBinaryOpInstruction.Operator.ADD),
                        StoreInstruction.make(Constants.TYPE_long, 1),
                        ReturnInstruction.make(Constants.TYPE_void)));
        slicerCriterionResultMap.put(
                Set.of(18),
                Arrays.asList(
                        ConstantInstruction.make(0L),
                        StoreInstruction.make(Constants.TYPE_long, 1),
                        LoadInstruction.make(Constants.TYPE_long, 1),
                        ConstantInstruction.make(2L),
                        ComparisonInstruction.make(Constants.TYPE_long, IComparisonInstruction.Operator.CMP),
                        ConstantInstruction.make(0),
                        ConditionalBranchInstruction.make(
                                Constants.TYPE_int, ConditionalBranchInstruction.Operator.GE, 14),
                        LoadInstruction.make(Constants.TYPE_long, 1),
                        ConstantInstruction.make(1L),
                        BinaryOpInstruction.make(Constants.TYPE_long, IBinaryOpInstruction.Operator.ADD),
                        StoreInstruction.make(Constants.TYPE_long, 1),
                        GotoInstruction.make(2),
                        LoadInstruction.make(Constants.TYPE_long, 1),
                        ConstantInstruction.make(1L),
                        BinaryOpInstruction.make(Constants.TYPE_long, IBinaryOpInstruction.Operator.ADD),
                        StoreInstruction.make(Constants.TYPE_long, 1),
                        LoadInstruction.make(Constants.TYPE_long, 1),
                        ConstantInstruction.make(5L),
                        ComparisonInstruction.make(Constants.TYPE_long, IComparisonInstruction.Operator.CMP),
                        ConstantInstruction.make(0),
                        ConditionalBranchInstruction.make(
                                Constants.TYPE_int, ConditionalBranchInstruction.Operator.GE, 30),
                        LoadInstruction.make(Constants.TYPE_long, 1),
                        ConstantInstruction.make(1L),
                        BinaryOpInstruction.make(Constants.TYPE_long, IBinaryOpInstruction.Operator.ADD),
                        StoreInstruction.make(Constants.TYPE_long, 1),
                        GotoInstruction.make(18),
                        ReturnInstruction.make(Constants.TYPE_void)));
        slicerCriterionResultMap.put(
                Set.of(19),
                Arrays.asList(
                        ConstantInstruction.make(5L),
                        PopInstruction.make(2),
                        ReturnInstruction.make(Constants.TYPE_void)));
        slicerCriterionResultMap.put(
                Set.of(20),
                Arrays.asList(
                        ConstantInstruction.make(0L),
                        StoreInstruction.make(Constants.TYPE_long, 1),
                        LoadInstruction.make(Constants.TYPE_long, 1),
                        ConstantInstruction.make(2L),
                        ComparisonInstruction.make(Constants.TYPE_long, IComparisonInstruction.Operator.CMP),
                        ConstantInstruction.make(0),
                        ConditionalBranchInstruction.make(
                                Constants.TYPE_int, ConditionalBranchInstruction.Operator.GE, 14),
                        LoadInstruction.make(Constants.TYPE_long, 1),
                        ConstantInstruction.make(1L),
                        BinaryOpInstruction.make(Constants.TYPE_long, IBinaryOpInstruction.Operator.ADD),
                        StoreInstruction.make(Constants.TYPE_long, 1),
                        GotoInstruction.make(2),
                        LoadInstruction.make(Constants.TYPE_long, 1),
                        ConstantInstruction.make(1L),
                        BinaryOpInstruction.make(Constants.TYPE_long, IBinaryOpInstruction.Operator.ADD),
                        StoreInstruction.make(Constants.TYPE_long, 1),
                        LoadInstruction.make(Constants.TYPE_long, 1),
                        ConstantInstruction.make(5L),
                        ComparisonInstruction.make(Constants.TYPE_long, IComparisonInstruction.Operator.CMP),
                        ConstantInstruction.make(0),
                        ConditionalBranchInstruction.make(
                                Constants.TYPE_int, ConditionalBranchInstruction.Operator.GE, 30),
                        LoadInstruction.make(Constants.TYPE_long, 1),
                        ConstantInstruction.make(1L),
                        BinaryOpInstruction.make(Constants.TYPE_long, IBinaryOpInstruction.Operator.ADD),
                        StoreInstruction.make(Constants.TYPE_long, 1),
                        GotoInstruction.make(18),
                        ReturnInstruction.make(Constants.TYPE_void)));
        slicerCriterionResultMap.put(
                Set.of(21),
                Arrays.asList(
                        ConstantInstruction.make(0),
                        PopInstruction.make(1),
                        ReturnInstruction.make(Constants.TYPE_void)));
        slicerCriterionResultMap.put(
                Set.of(22),
                Arrays.asList(
                        ConstantInstruction.make(0L),
                        StoreInstruction.make(Constants.TYPE_long, 1),
                        LoadInstruction.make(Constants.TYPE_long, 1),
                        ConstantInstruction.make(2L),
                        ComparisonInstruction.make(Constants.TYPE_long, IComparisonInstruction.Operator.CMP),
                        ConstantInstruction.make(0),
                        ConditionalBranchInstruction.make(
                                Constants.TYPE_int, ConditionalBranchInstruction.Operator.GE, 14),
                        LoadInstruction.make(Constants.TYPE_long, 1),
                        ConstantInstruction.make(1L),
                        BinaryOpInstruction.make(Constants.TYPE_long, IBinaryOpInstruction.Operator.ADD),
                        StoreInstruction.make(Constants.TYPE_long, 1),
                        GotoInstruction.make(2),
                        LoadInstruction.make(Constants.TYPE_long, 1),
                        ConstantInstruction.make(1L),
                        BinaryOpInstruction.make(Constants.TYPE_long, IBinaryOpInstruction.Operator.ADD),
                        StoreInstruction.make(Constants.TYPE_long, 1),
                        LoadInstruction.make(Constants.TYPE_long, 1),
                        ConstantInstruction.make(5L),
                        ComparisonInstruction.make(Constants.TYPE_long, IComparisonInstruction.Operator.CMP),
                        ConstantInstruction.make(0),
                        ConditionalBranchInstruction.make(
                                Constants.TYPE_int, ConditionalBranchInstruction.Operator.GE, 30),
                        LoadInstruction.make(Constants.TYPE_long, 1),
                        ConstantInstruction.make(1L),
                        BinaryOpInstruction.make(Constants.TYPE_long, IBinaryOpInstruction.Operator.ADD),
                        StoreInstruction.make(Constants.TYPE_long, 1),
                        GotoInstruction.make(18),
                        ReturnInstruction.make(Constants.TYPE_void)));
        slicerCriterionResultMap.put(
                Set.of(23),
                Arrays.asList(
                        ConstantInstruction.make(0L),
                        StoreInstruction.make(Constants.TYPE_long, 1),
                        LoadInstruction.make(Constants.TYPE_long, 1),
                        ConstantInstruction.make(2L),
                        ComparisonInstruction.make(Constants.TYPE_long, IComparisonInstruction.Operator.CMP),
                        ConstantInstruction.make(0),
                        ConditionalBranchInstruction.make(
                                Constants.TYPE_int, ConditionalBranchInstruction.Operator.GE, 14),
                        LoadInstruction.make(Constants.TYPE_long, 1),
                        ConstantInstruction.make(1L),
                        BinaryOpInstruction.make(Constants.TYPE_long, IBinaryOpInstruction.Operator.ADD),
                        StoreInstruction.make(Constants.TYPE_long, 1),
                        GotoInstruction.make(2),
                        LoadInstruction.make(Constants.TYPE_long, 1),
                        ConstantInstruction.make(1L),
                        BinaryOpInstruction.make(Constants.TYPE_long, IBinaryOpInstruction.Operator.ADD),
                        StoreInstruction.make(Constants.TYPE_long, 1),
                        LoadInstruction.make(Constants.TYPE_long, 1),
                        ConstantInstruction.make(5L),
                        ComparisonInstruction.make(Constants.TYPE_long, IComparisonInstruction.Operator.CMP),
                        ConstantInstruction.make(0),
                        ConditionalBranchInstruction.make(
                                Constants.TYPE_int, ConditionalBranchInstruction.Operator.GE, 30),
                        InvokeInstruction.make(
                                "()J", "Ljava/lang/System;", "currentTimeMillis", Dispatch.STATIC),
                        PopInstruction.make(2),
                        LoadInstruction.make(Constants.TYPE_long, 1),
                        ConstantInstruction.make(1L),
                        BinaryOpInstruction.make(Constants.TYPE_long, IBinaryOpInstruction.Operator.ADD),
                        StoreInstruction.make(Constants.TYPE_long, 1),
                        GotoInstruction.make(18),
                        ReturnInstruction.make(Constants.TYPE_void)));
        slicerCriterionResultMap.put(
                Set.of(24),
                Arrays.asList(
                        ConstantInstruction.make(0L),
                        StoreInstruction.make(Constants.TYPE_long, 1),
                        LoadInstruction.make(Constants.TYPE_long, 1),
                        ConstantInstruction.make(2L),
                        ComparisonInstruction.make(Constants.TYPE_long, IComparisonInstruction.Operator.CMP),
                        ConstantInstruction.make(0),
                        ConditionalBranchInstruction.make(
                                Constants.TYPE_int, ConditionalBranchInstruction.Operator.GE, 14),
                        LoadInstruction.make(Constants.TYPE_long, 1),
                        ConstantInstruction.make(1L),
                        BinaryOpInstruction.make(Constants.TYPE_long, IBinaryOpInstruction.Operator.ADD),
                        StoreInstruction.make(Constants.TYPE_long, 1),
                        GotoInstruction.make(2),
                        LoadInstruction.make(Constants.TYPE_long, 1),
                        ConstantInstruction.make(1L),
                        BinaryOpInstruction.make(Constants.TYPE_long, IBinaryOpInstruction.Operator.ADD),
                        StoreInstruction.make(Constants.TYPE_long, 1),
                        LoadInstruction.make(Constants.TYPE_long, 1),
                        ConstantInstruction.make(5L),
                        ComparisonInstruction.make(Constants.TYPE_long, IComparisonInstruction.Operator.CMP),
                        ConstantInstruction.make(0),
                        ConditionalBranchInstruction.make(
                                Constants.TYPE_int, ConditionalBranchInstruction.Operator.GE, 30),
                        InvokeInstruction.make(
                                "()J", "Ljava/lang/System;", "currentTimeMillis", Dispatch.STATIC),
                        PopInstruction.make(2),
                        LoadInstruction.make(Constants.TYPE_long, 1),
                        ConstantInstruction.make(1L),
                        BinaryOpInstruction.make(Constants.TYPE_long, IBinaryOpInstruction.Operator.ADD),
                        StoreInstruction.make(Constants.TYPE_long, 1),
                        GotoInstruction.make(18),
                        ReturnInstruction.make(Constants.TYPE_void)));
        slicerCriterionResultMap.put(
                Set.of(25),
                Arrays.asList(
                        ConstantInstruction.make(0L),
                        StoreInstruction.make(Constants.TYPE_long, 1),
                        LoadInstruction.make(Constants.TYPE_long, 1),
                        ConstantInstruction.make(2L),
                        ComparisonInstruction.make(Constants.TYPE_long, IComparisonInstruction.Operator.CMP),
                        ConstantInstruction.make(0),
                        ConditionalBranchInstruction.make(
                                Constants.TYPE_int, ConditionalBranchInstruction.Operator.GE, 14),
                        LoadInstruction.make(Constants.TYPE_long, 1),
                        ConstantInstruction.make(1L),
                        BinaryOpInstruction.make(Constants.TYPE_long, IBinaryOpInstruction.Operator.ADD),
                        StoreInstruction.make(Constants.TYPE_long, 1),
                        GotoInstruction.make(2),
                        LoadInstruction.make(Constants.TYPE_long, 1),
                        ConstantInstruction.make(1L),
                        BinaryOpInstruction.make(Constants.TYPE_long, IBinaryOpInstruction.Operator.ADD),
                        StoreInstruction.make(Constants.TYPE_long, 1),
                        LoadInstruction.make(Constants.TYPE_long, 1),
                        ConstantInstruction.make(5L),
                        ComparisonInstruction.make(Constants.TYPE_long, IComparisonInstruction.Operator.CMP),
                        ConstantInstruction.make(0),
                        ConditionalBranchInstruction.make(
                                Constants.TYPE_int, ConditionalBranchInstruction.Operator.GE, 30),
                        LoadInstruction.make(Constants.TYPE_long, 1),
                        ConstantInstruction.make(1L),
                        BinaryOpInstruction.make(Constants.TYPE_long, IBinaryOpInstruction.Operator.ADD),
                        StoreInstruction.make(Constants.TYPE_long, 1),
                        GotoInstruction.make(18),
                        ReturnInstruction.make(Constants.TYPE_void)));
        slicerCriterionResultMap.put(
                Set.of(26),
                Arrays.asList(
                        ConstantInstruction.make(1L),
                        PopInstruction.make(2),
                        ReturnInstruction.make(Constants.TYPE_void)));
        slicerCriterionResultMap.put(
                Set.of(27),
                Arrays.asList(
                        ConstantInstruction.make(0L),
                        StoreInstruction.make(Constants.TYPE_long, 1),
                        LoadInstruction.make(Constants.TYPE_long, 1),
                        ConstantInstruction.make(2L),
                        ComparisonInstruction.make(Constants.TYPE_long, IComparisonInstruction.Operator.CMP),
                        ConstantInstruction.make(0),
                        ConditionalBranchInstruction.make(
                                Constants.TYPE_int, ConditionalBranchInstruction.Operator.GE, 14),
                        LoadInstruction.make(Constants.TYPE_long, 1),
                        ConstantInstruction.make(1L),
                        BinaryOpInstruction.make(Constants.TYPE_long, IBinaryOpInstruction.Operator.ADD),
                        StoreInstruction.make(Constants.TYPE_long, 1),
                        GotoInstruction.make(2),
                        LoadInstruction.make(Constants.TYPE_long, 1),
                        ConstantInstruction.make(1L),
                        BinaryOpInstruction.make(Constants.TYPE_long, IBinaryOpInstruction.Operator.ADD),
                        StoreInstruction.make(Constants.TYPE_long, 1),
                        LoadInstruction.make(Constants.TYPE_long, 1),
                        ConstantInstruction.make(5L),
                        ComparisonInstruction.make(Constants.TYPE_long, IComparisonInstruction.Operator.CMP),
                        ConstantInstruction.make(0),
                        ConditionalBranchInstruction.make(
                                Constants.TYPE_int, ConditionalBranchInstruction.Operator.GE, 30),
                        LoadInstruction.make(Constants.TYPE_long, 1),
                        ConstantInstruction.make(1L),
                        BinaryOpInstruction.make(Constants.TYPE_long, IBinaryOpInstruction.Operator.ADD),
                        StoreInstruction.make(Constants.TYPE_long, 1),
                        GotoInstruction.make(18),
                        ReturnInstruction.make(Constants.TYPE_void)));
        slicerCriterionResultMap.put(
                Set.of(28),
                Arrays.asList(
                        ConstantInstruction.make(0L),
                        StoreInstruction.make(Constants.TYPE_long, 1),
                        LoadInstruction.make(Constants.TYPE_long, 1),
                        ConstantInstruction.make(2L),
                        ComparisonInstruction.make(Constants.TYPE_long, IComparisonInstruction.Operator.CMP),
                        ConstantInstruction.make(0),
                        ConditionalBranchInstruction.make(
                                Constants.TYPE_int, ConditionalBranchInstruction.Operator.GE, 14),
                        LoadInstruction.make(Constants.TYPE_long, 1),
                        ConstantInstruction.make(1L),
                        BinaryOpInstruction.make(Constants.TYPE_long, IBinaryOpInstruction.Operator.ADD),
                        StoreInstruction.make(Constants.TYPE_long, 1),
                        GotoInstruction.make(2),
                        LoadInstruction.make(Constants.TYPE_long, 1),
                        ConstantInstruction.make(1L),
                        BinaryOpInstruction.make(Constants.TYPE_long, IBinaryOpInstruction.Operator.ADD),
                        StoreInstruction.make(Constants.TYPE_long, 1),
                        LoadInstruction.make(Constants.TYPE_long, 1),
                        ConstantInstruction.make(5L),
                        ComparisonInstruction.make(Constants.TYPE_long, IComparisonInstruction.Operator.CMP),
                        ConstantInstruction.make(0),
                        ConditionalBranchInstruction.make(
                                Constants.TYPE_int, ConditionalBranchInstruction.Operator.GE, 30),
                        LoadInstruction.make(Constants.TYPE_long, 1),
                        ConstantInstruction.make(1L),
                        BinaryOpInstruction.make(Constants.TYPE_long, IBinaryOpInstruction.Operator.ADD),
                        StoreInstruction.make(Constants.TYPE_long, 1),
                        GotoInstruction.make(18),
                        ReturnInstruction.make(Constants.TYPE_void)));
        slicerCriterionResultMap.put(
                Set.of(29),
                Arrays.asList(
                        ConstantInstruction.make(0L),
                        StoreInstruction.make(Constants.TYPE_long, 1),
                        LoadInstruction.make(Constants.TYPE_long, 1),
                        ConstantInstruction.make(2L),
                        ComparisonInstruction.make(Constants.TYPE_long, IComparisonInstruction.Operator.CMP),
                        ConstantInstruction.make(0),
                        ConditionalBranchInstruction.make(
                                Constants.TYPE_int, ConditionalBranchInstruction.Operator.GE, 14),
                        LoadInstruction.make(Constants.TYPE_long, 1),
                        ConstantInstruction.make(1L),
                        BinaryOpInstruction.make(Constants.TYPE_long, IBinaryOpInstruction.Operator.ADD),
                        StoreInstruction.make(Constants.TYPE_long, 1),
                        GotoInstruction.make(2),
                        LoadInstruction.make(Constants.TYPE_long, 1),
                        ConstantInstruction.make(1L),
                        BinaryOpInstruction.make(Constants.TYPE_long, IBinaryOpInstruction.Operator.ADD),
                        StoreInstruction.make(Constants.TYPE_long, 1),
                        LoadInstruction.make(Constants.TYPE_long, 1),
                        ConstantInstruction.make(5L),
                        ComparisonInstruction.make(Constants.TYPE_long, IComparisonInstruction.Operator.CMP),
                        ConstantInstruction.make(0),
                        ConditionalBranchInstruction.make(
                                Constants.TYPE_int, ConditionalBranchInstruction.Operator.GE, 30),
                        LoadInstruction.make(Constants.TYPE_long, 1),
                        ConstantInstruction.make(1L),
                        BinaryOpInstruction.make(Constants.TYPE_long, IBinaryOpInstruction.Operator.ADD),
                        StoreInstruction.make(Constants.TYPE_long, 1),
                        GotoInstruction.make(18),
                        ReturnInstruction.make(Constants.TYPE_void)));
        slicerCriterionResultMap.put(
                Set.of(30), Arrays.asList(ReturnInstruction.make(Constants.TYPE_void)));

        validateSliceResults(slicerCriterionResultMap);
    }

    @Test
    public void testSliceReuseVariableWithoutReinitialization()
            throws IOException, InvalidClassFileException, InterruptedException, ExportException {
        slicer.setMethodSignature(
                "Lde.uniks.vs.methodresourceprediction.slicer.test.SlicerValidation;.reuseVariableWithoutReinitialization()V");
        System.out.println(slicer.getMethodSummary());
        //		slicer.getControlFlow().showPlot();
        //		slicer.getDominance().showPlot();
        //		slicer.getImmediateDominance().showPlot();
        //		slicer.getControlDependency().showPlot();

        Map<Set<Integer>, List<IInstruction>> slicerCriterionResultMap = new HashMap<>();

        slicerCriterionResultMap.put(
                Set.of(0),
                Arrays.asList(
                        ConstantInstruction.make(0),
                        PopInstruction.make(1),
                        ReturnInstruction.make(Constants.TYPE_void)));
        slicerCriterionResultMap.put(
                Set.of(1),
                Arrays.asList(
                        ConstantInstruction.make(0),
                        StoreInstruction.make(Constants.TYPE_int, 1),
                        ReturnInstruction.make(Constants.TYPE_void)));
        slicerCriterionResultMap.put(
                Set.of(2),
                Arrays.asList(
                        ConstantInstruction.make(0),
                        StoreInstruction.make(Constants.TYPE_int, 1),
                        LoadInstruction.make(Constants.TYPE_int, 1),
                        ConstantInstruction.make(5),
                        ConditionalBranchInstruction.make(
                                Constants.TYPE_int, ConditionalBranchInstruction.Operator.GE, 12),
                        LoadInstruction.make(Constants.TYPE_int, 1),
                        ConstantInstruction.make(1),
                        BinaryOpInstruction.make(Constants.TYPE_int, IBinaryOpInstruction.Operator.ADD),
                        StoreInstruction.make(Constants.TYPE_int, 1),
                        GotoInstruction.make(2),
                        InvokeInstruction.make(
                                "()V",
                                "Lde/uniks/vs/methodresourceprediction/slicer/export/Nothing;",
                                "doNothing",
                                Dispatch.STATIC),
                        ReturnInstruction.make(Constants.TYPE_void)));
        slicerCriterionResultMap.put(
                Set.of(3),
                Arrays.asList(
                        ConstantInstruction.make(5),
                        PopInstruction.make(1),
                        ReturnInstruction.make(Constants.TYPE_void)));
        slicerCriterionResultMap.put(
                Set.of(4),
                Arrays.asList(
                        ConstantInstruction.make(0),
                        StoreInstruction.make(Constants.TYPE_int, 1),
                        LoadInstruction.make(Constants.TYPE_int, 1),
                        ConstantInstruction.make(5),
                        ConditionalBranchInstruction.make(
                                Constants.TYPE_int, ConditionalBranchInstruction.Operator.GE, 12),
                        LoadInstruction.make(Constants.TYPE_int, 1),
                        ConstantInstruction.make(1),
                        BinaryOpInstruction.make(Constants.TYPE_int, IBinaryOpInstruction.Operator.ADD),
                        StoreInstruction.make(Constants.TYPE_int, 1),
                        GotoInstruction.make(2),
                        InvokeInstruction.make(
                                "()V",
                                "Lde/uniks/vs/methodresourceprediction/slicer/export/Nothing;",
                                "doNothing",
                                Dispatch.STATIC),
                        ReturnInstruction.make(Constants.TYPE_void)));
        slicerCriterionResultMap.put(
                Set.of(5),
                Arrays.asList(
                        ConstantInstruction.make(0),
                        StoreInstruction.make(Constants.TYPE_int, 1),
                        LoadInstruction.make(Constants.TYPE_int, 1),
                        ConstantInstruction.make(5),
                        ConditionalBranchInstruction.make(
                                Constants.TYPE_int, ConditionalBranchInstruction.Operator.GE, 12),
                        InvokeInstruction.make(
                                "()J", "Ljava/lang/System;", "currentTimeMillis", Dispatch.STATIC),
                        PopInstruction.make(2),
                        LoadInstruction.make(Constants.TYPE_int, 1),
                        ConstantInstruction.make(1),
                        BinaryOpInstruction.make(Constants.TYPE_int, IBinaryOpInstruction.Operator.ADD),
                        StoreInstruction.make(Constants.TYPE_int, 1),
                        GotoInstruction.make(2),
                        InvokeInstruction.make(
                                "()V",
                                "Lde/uniks/vs/methodresourceprediction/slicer/export/Nothing;",
                                "doNothing",
                                Dispatch.STATIC),
                        ReturnInstruction.make(Constants.TYPE_void)));
        slicerCriterionResultMap.put(
                Set.of(6),
                Arrays.asList(
                        ConstantInstruction.make(0),
                        StoreInstruction.make(Constants.TYPE_int, 1),
                        LoadInstruction.make(Constants.TYPE_int, 1),
                        ConstantInstruction.make(5),
                        ConditionalBranchInstruction.make(
                                Constants.TYPE_int, ConditionalBranchInstruction.Operator.GE, 12),
                        InvokeInstruction.make(
                                "()J", "Ljava/lang/System;", "currentTimeMillis", Dispatch.STATIC),
                        PopInstruction.make(2),
                        LoadInstruction.make(Constants.TYPE_int, 1),
                        ConstantInstruction.make(1),
                        BinaryOpInstruction.make(Constants.TYPE_int, IBinaryOpInstruction.Operator.ADD),
                        StoreInstruction.make(Constants.TYPE_int, 1),
                        GotoInstruction.make(2),
                        InvokeInstruction.make(
                                "()V",
                                "Lde/uniks/vs/methodresourceprediction/slicer/export/Nothing;",
                                "doNothing",
                                Dispatch.STATIC),
                        ReturnInstruction.make(Constants.TYPE_void)));
        slicerCriterionResultMap.put(
                Set.of(7),
                Arrays.asList(
                        ConstantInstruction.make(0),
                        StoreInstruction.make(Constants.TYPE_int, 1),
                        LoadInstruction.make(Constants.TYPE_int, 1),
                        ConstantInstruction.make(5),
                        ConditionalBranchInstruction.make(
                                Constants.TYPE_int, ConditionalBranchInstruction.Operator.GE, 12),
                        LoadInstruction.make(Constants.TYPE_int, 1),
                        ConstantInstruction.make(1),
                        BinaryOpInstruction.make(Constants.TYPE_int, IBinaryOpInstruction.Operator.ADD),
                        StoreInstruction.make(Constants.TYPE_int, 1),
                        GotoInstruction.make(2),
                        InvokeInstruction.make(
                                "()V",
                                "Lde/uniks/vs/methodresourceprediction/slicer/export/Nothing;",
                                "doNothing",
                                Dispatch.STATIC),
                        ReturnInstruction.make(Constants.TYPE_void)));
        slicerCriterionResultMap.put(
                Set.of(8),
                Arrays.asList(
                        ConstantInstruction.make(1),
                        PopInstruction.make(1),
                        ReturnInstruction.make(Constants.TYPE_void)));
        slicerCriterionResultMap.put(
                Set.of(9),
                Arrays.asList(
                        ConstantInstruction.make(0),
                        StoreInstruction.make(Constants.TYPE_int, 1),
                        LoadInstruction.make(Constants.TYPE_int, 1),
                        ConstantInstruction.make(5),
                        ConditionalBranchInstruction.make(
                                Constants.TYPE_int, ConditionalBranchInstruction.Operator.GE, 12),
                        LoadInstruction.make(Constants.TYPE_int, 1),
                        ConstantInstruction.make(1),
                        BinaryOpInstruction.make(Constants.TYPE_int, IBinaryOpInstruction.Operator.ADD),
                        StoreInstruction.make(Constants.TYPE_int, 1),
                        GotoInstruction.make(2),
                        InvokeInstruction.make(
                                "()V",
                                "Lde/uniks/vs/methodresourceprediction/slicer/export/Nothing;",
                                "doNothing",
                                Dispatch.STATIC),
                        ReturnInstruction.make(Constants.TYPE_void)));
        slicerCriterionResultMap.put(
                Set.of(10),
                Arrays.asList(
                        ConstantInstruction.make(0),
                        StoreInstruction.make(Constants.TYPE_int, 1),
                        LoadInstruction.make(Constants.TYPE_int, 1),
                        ConstantInstruction.make(5),
                        ConditionalBranchInstruction.make(
                                Constants.TYPE_int, ConditionalBranchInstruction.Operator.GE, 12),
                        LoadInstruction.make(Constants.TYPE_int, 1),
                        ConstantInstruction.make(1),
                        BinaryOpInstruction.make(Constants.TYPE_int, IBinaryOpInstruction.Operator.ADD),
                        StoreInstruction.make(Constants.TYPE_int, 1),
                        GotoInstruction.make(2),
                        InvokeInstruction.make(
                                "()V",
                                "Lde/uniks/vs/methodresourceprediction/slicer/export/Nothing;",
                                "doNothing",
                                Dispatch.STATIC),
                        ReturnInstruction.make(Constants.TYPE_void)));
        slicerCriterionResultMap.put(
                Set.of(11),
                Arrays.asList(
                        ConstantInstruction.make(0),
                        StoreInstruction.make(Constants.TYPE_int, 1),
                        LoadInstruction.make(Constants.TYPE_int, 1),
                        ConstantInstruction.make(5),
                        ConditionalBranchInstruction.make(
                                Constants.TYPE_int, ConditionalBranchInstruction.Operator.GE, 12),
                        LoadInstruction.make(Constants.TYPE_int, 1),
                        ConstantInstruction.make(1),
                        BinaryOpInstruction.make(Constants.TYPE_int, IBinaryOpInstruction.Operator.ADD),
                        StoreInstruction.make(Constants.TYPE_int, 1),
                        GotoInstruction.make(2),
                        InvokeInstruction.make(
                                "()V",
                                "Lde/uniks/vs/methodresourceprediction/slicer/export/Nothing;",
                                "doNothing",
                                Dispatch.STATIC),
                        ReturnInstruction.make(Constants.TYPE_void)));
        slicerCriterionResultMap.put(
                Set.of(12),
                Arrays.asList(
                        ConstantInstruction.make(0),
                        StoreInstruction.make(Constants.TYPE_int, 1),
                        LoadInstruction.make(Constants.TYPE_int, 1),
                        ConstantInstruction.make(5),
                        ConditionalBranchInstruction.make(Constants.TYPE_int, ConditionalBranchInstruction.Operator.GE, 12),
                        LoadInstruction.make(Constants.TYPE_int, 1),
                        ConstantInstruction.make(1),
                        BinaryOpInstruction.make(Constants.TYPE_int, IBinaryOpInstruction.Operator.ADD),
                        StoreInstruction.make(Constants.TYPE_int, 1),
                        GotoInstruction.make(2),
                        LoadInstruction.make(Constants.TYPE_int, 1),
                        ConstantInstruction.make(10),
                        ConditionalBranchInstruction.make(Constants.TYPE_int, ConditionalBranchInstruction.Operator.GE, 22),
                        LoadInstruction.make(Constants.TYPE_int, 1),
                        ConstantInstruction.make(1),
                        BinaryOpInstruction.make(Constants.TYPE_int, IBinaryOpInstruction.Operator.ADD),
                        StoreInstruction.make(Constants.TYPE_int, 1),
                        GotoInstruction.make(12),
                        InvokeInstruction.make("()V", "Lde/uniks/vs/methodresourceprediction/slicer/export/Nothing;", "doNothing", Dispatch.STATIC),
                        ReturnInstruction.make(Constants.TYPE_void)
                ));
        slicerCriterionResultMap.put(
                Set.of(13),
                Arrays.asList(
                        ConstantInstruction.make(10),
                        PopInstruction.make(1),
                        ReturnInstruction.make(Constants.TYPE_void)));
        slicerCriterionResultMap.put(
                Set.of(14),
                Arrays.asList(
                        ConstantInstruction.make(0),
                        StoreInstruction.make(Constants.TYPE_int, 1),
                        LoadInstruction.make(Constants.TYPE_int, 1),
                        ConstantInstruction.make(5),
                        ConditionalBranchInstruction.make(
                                Constants.TYPE_int, ConditionalBranchInstruction.Operator.GE, 12),
                        LoadInstruction.make(Constants.TYPE_int, 1),
                        ConstantInstruction.make(1),
                        BinaryOpInstruction.make(Constants.TYPE_int, IBinaryOpInstruction.Operator.ADD),
                        StoreInstruction.make(Constants.TYPE_int, 1),
                        GotoInstruction.make(2),
                        LoadInstruction.make(Constants.TYPE_int, 1),
                        ConstantInstruction.make(10),
                        ConditionalBranchInstruction.make(
                                Constants.TYPE_int, ConditionalBranchInstruction.Operator.GE, 22),
                        LoadInstruction.make(Constants.TYPE_int, 1),
                        ConstantInstruction.make(1),
                        BinaryOpInstruction.make(Constants.TYPE_int, IBinaryOpInstruction.Operator.ADD),
                        StoreInstruction.make(Constants.TYPE_int, 1),
                        GotoInstruction.make(12),
                        InvokeInstruction.make(
                                "()V",
                                "Lde/uniks/vs/methodresourceprediction/slicer/export/Nothing;",
                                "doNothing",
                                Dispatch.STATIC),
                        ReturnInstruction.make(Constants.TYPE_void)));
        slicerCriterionResultMap.put(
                Set.of(15),
                Arrays.asList(
                        ConstantInstruction.make(0),
                        StoreInstruction.make(Constants.TYPE_int, 1),
                        LoadInstruction.make(Constants.TYPE_int, 1),
                        ConstantInstruction.make(5),
                        ConditionalBranchInstruction.make(
                                Constants.TYPE_int, ConditionalBranchInstruction.Operator.GE, 12),
                        LoadInstruction.make(Constants.TYPE_int, 1),
                        ConstantInstruction.make(1),
                        BinaryOpInstruction.make(Constants.TYPE_int, IBinaryOpInstruction.Operator.ADD),
                        StoreInstruction.make(Constants.TYPE_int, 1),
                        GotoInstruction.make(2),
                        LoadInstruction.make(Constants.TYPE_int, 1),
                        ConstantInstruction.make(10),
                        ConditionalBranchInstruction.make(
                                Constants.TYPE_int, ConditionalBranchInstruction.Operator.GE, 22),
                        InvokeInstruction.make(
                                "()J", "Ljava/lang/System;", "currentTimeMillis", Dispatch.STATIC),
                        PopInstruction.make(2),
                        LoadInstruction.make(Constants.TYPE_int, 1),
                        ConstantInstruction.make(1),
                        BinaryOpInstruction.make(Constants.TYPE_int, IBinaryOpInstruction.Operator.ADD),
                        StoreInstruction.make(Constants.TYPE_int, 1),
                        GotoInstruction.make(12),
                        InvokeInstruction.make(
                                "()V",
                                "Lde/uniks/vs/methodresourceprediction/slicer/export/Nothing;",
                                "doNothing",
                                Dispatch.STATIC),
                        ReturnInstruction.make(Constants.TYPE_void)));
        slicerCriterionResultMap.put(
                Set.of(16),
                Arrays.asList(
                        ConstantInstruction.make(0),
                        StoreInstruction.make(Constants.TYPE_int, 1),
                        LoadInstruction.make(Constants.TYPE_int, 1),
                        ConstantInstruction.make(5),
                        ConditionalBranchInstruction.make(
                                Constants.TYPE_int, ConditionalBranchInstruction.Operator.GE, 12),
                        LoadInstruction.make(Constants.TYPE_int, 1),
                        ConstantInstruction.make(1),
                        BinaryOpInstruction.make(Constants.TYPE_int, IBinaryOpInstruction.Operator.ADD),
                        StoreInstruction.make(Constants.TYPE_int, 1),
                        GotoInstruction.make(2),
                        LoadInstruction.make(Constants.TYPE_int, 1),
                        ConstantInstruction.make(10),
                        ConditionalBranchInstruction.make(
                                Constants.TYPE_int, ConditionalBranchInstruction.Operator.GE, 22),
                        InvokeInstruction.make(
                                "()J", "Ljava/lang/System;", "currentTimeMillis", Dispatch.STATIC),
                        PopInstruction.make(2),
                        LoadInstruction.make(Constants.TYPE_int, 1),
                        ConstantInstruction.make(1),
                        BinaryOpInstruction.make(Constants.TYPE_int, IBinaryOpInstruction.Operator.ADD),
                        StoreInstruction.make(Constants.TYPE_int, 1),
                        GotoInstruction.make(12),
                        InvokeInstruction.make(
                                "()V",
                                "Lde/uniks/vs/methodresourceprediction/slicer/export/Nothing;",
                                "doNothing",
                                Dispatch.STATIC),
                        ReturnInstruction.make(Constants.TYPE_void)));
        slicerCriterionResultMap.put(
                Set.of(17),
                Arrays.asList(
                        ConstantInstruction.make(0),
                        StoreInstruction.make(Constants.TYPE_int, 1),
                        LoadInstruction.make(Constants.TYPE_int, 1),
                        ConstantInstruction.make(5),
                        ConditionalBranchInstruction.make(
                                Constants.TYPE_int, ConditionalBranchInstruction.Operator.GE, 12),
                        LoadInstruction.make(Constants.TYPE_int, 1),
                        ConstantInstruction.make(1),
                        BinaryOpInstruction.make(Constants.TYPE_int, IBinaryOpInstruction.Operator.ADD),
                        StoreInstruction.make(Constants.TYPE_int, 1),
                        GotoInstruction.make(2),
                        LoadInstruction.make(Constants.TYPE_int, 1),
                        ConstantInstruction.make(10),
                        ConditionalBranchInstruction.make(
                                Constants.TYPE_int, ConditionalBranchInstruction.Operator.GE, 22),
                        LoadInstruction.make(Constants.TYPE_int, 1),
                        ConstantInstruction.make(1),
                        BinaryOpInstruction.make(Constants.TYPE_int, IBinaryOpInstruction.Operator.ADD),
                        StoreInstruction.make(Constants.TYPE_int, 1),
                        GotoInstruction.make(12),
                        InvokeInstruction.make(
                                "()V",
                                "Lde/uniks/vs/methodresourceprediction/slicer/export/Nothing;",
                                "doNothing",
                                Dispatch.STATIC),
                        ReturnInstruction.make(Constants.TYPE_void)));
        slicerCriterionResultMap.put(
                Set.of(18),
                Arrays.asList(
                        ConstantInstruction.make(1),
                        PopInstruction.make(1),
                        ReturnInstruction.make(Constants.TYPE_void)));
        slicerCriterionResultMap.put(
                Set.of(19),
                Arrays.asList(
                        ConstantInstruction.make(0),
                        StoreInstruction.make(Constants.TYPE_int, 1),
                        LoadInstruction.make(Constants.TYPE_int, 1),
                        ConstantInstruction.make(5),
                        ConditionalBranchInstruction.make(
                                Constants.TYPE_int, ConditionalBranchInstruction.Operator.GE, 12),
                        LoadInstruction.make(Constants.TYPE_int, 1),
                        ConstantInstruction.make(1),
                        BinaryOpInstruction.make(Constants.TYPE_int, IBinaryOpInstruction.Operator.ADD),
                        StoreInstruction.make(Constants.TYPE_int, 1),
                        GotoInstruction.make(2),
                        LoadInstruction.make(Constants.TYPE_int, 1),
                        ConstantInstruction.make(10),
                        ConditionalBranchInstruction.make(
                                Constants.TYPE_int, ConditionalBranchInstruction.Operator.GE, 22),
                        LoadInstruction.make(Constants.TYPE_int, 1),
                        ConstantInstruction.make(1),
                        BinaryOpInstruction.make(Constants.TYPE_int, IBinaryOpInstruction.Operator.ADD),
                        StoreInstruction.make(Constants.TYPE_int, 1),
                        GotoInstruction.make(12),
                        InvokeInstruction.make(
                                "()V",
                                "Lde/uniks/vs/methodresourceprediction/slicer/export/Nothing;",
                                "doNothing",
                                Dispatch.STATIC),
                        ReturnInstruction.make(Constants.TYPE_void)));
        slicerCriterionResultMap.put(
                Set.of(20),
                Arrays.asList(
                        ConstantInstruction.make(0),
                        StoreInstruction.make(Constants.TYPE_int, 1),
                        LoadInstruction.make(Constants.TYPE_int, 1),
                        ConstantInstruction.make(5),
                        ConditionalBranchInstruction.make(
                                Constants.TYPE_int, ConditionalBranchInstruction.Operator.GE, 12),
                        LoadInstruction.make(Constants.TYPE_int, 1),
                        ConstantInstruction.make(1),
                        BinaryOpInstruction.make(Constants.TYPE_int, IBinaryOpInstruction.Operator.ADD),
                        StoreInstruction.make(Constants.TYPE_int, 1),
                        GotoInstruction.make(2),
                        LoadInstruction.make(Constants.TYPE_int, 1),
                        ConstantInstruction.make(10),
                        ConditionalBranchInstruction.make(
                                Constants.TYPE_int, ConditionalBranchInstruction.Operator.GE, 22),
                        LoadInstruction.make(Constants.TYPE_int, 1),
                        ConstantInstruction.make(1),
                        BinaryOpInstruction.make(Constants.TYPE_int, IBinaryOpInstruction.Operator.ADD),
                        StoreInstruction.make(Constants.TYPE_int, 1),
                        GotoInstruction.make(12),
                        InvokeInstruction.make(
                                "()V",
                                "Lde/uniks/vs/methodresourceprediction/slicer/export/Nothing;",
                                "doNothing",
                                Dispatch.STATIC),
                        ReturnInstruction.make(Constants.TYPE_void)));
        slicerCriterionResultMap.put(
                Set.of(21),
                Arrays.asList(
                        ConstantInstruction.make(0),
                        StoreInstruction.make(Constants.TYPE_int, 1),
                        LoadInstruction.make(Constants.TYPE_int, 1),
                        ConstantInstruction.make(5),
                        ConditionalBranchInstruction.make(
                                Constants.TYPE_int, ConditionalBranchInstruction.Operator.GE, 12),
                        LoadInstruction.make(Constants.TYPE_int, 1),
                        ConstantInstruction.make(1),
                        BinaryOpInstruction.make(Constants.TYPE_int, IBinaryOpInstruction.Operator.ADD),
                        StoreInstruction.make(Constants.TYPE_int, 1),
                        GotoInstruction.make(2),
                        LoadInstruction.make(Constants.TYPE_int, 1),
                        ConstantInstruction.make(10),
                        ConditionalBranchInstruction.make(
                                Constants.TYPE_int, ConditionalBranchInstruction.Operator.GE, 22),
                        LoadInstruction.make(Constants.TYPE_int, 1),
                        ConstantInstruction.make(1),
                        BinaryOpInstruction.make(Constants.TYPE_int, IBinaryOpInstruction.Operator.ADD),
                        StoreInstruction.make(Constants.TYPE_int, 1),
                        GotoInstruction.make(12),
                        InvokeInstruction.make(
                                "()V",
                                "Lde/uniks/vs/methodresourceprediction/slicer/export/Nothing;",
                                "doNothing",
                                Dispatch.STATIC),
                        ReturnInstruction.make(Constants.TYPE_void)));
        slicerCriterionResultMap.put(
                Set.of(22),
                Arrays.asList(
                        InvokeInstruction.make(
                                "()V",
                                "Lde/uniks/vs/methodresourceprediction/slicer/export/Nothing;",
                                "doNothing",
                                Dispatch.STATIC),
                        ReturnInstruction.make(Constants.TYPE_void)));
        slicerCriterionResultMap.put(
                Set.of(23), Arrays.asList(ReturnInstruction.make(Constants.TYPE_void)));

        validateSliceResults(slicerCriterionResultMap);
    }

    @Test
    public void testSliceReuseVariableWithoutReinitializationDoubleSized()
            throws IOException, InvalidClassFileException, InterruptedException {
        slicer.setMethodSignature(
                "Lde.uniks.vs.methodresourceprediction.slicer.test.SlicerValidation;.reuseVariableWithoutReinitializationDoubleSized()V");
        System.out.println(slicer.getMethodSummary());
        //		Utilities.dotShow(slicer.getControlFlow().dotPrint());

        Map<Set<Integer>, List<IInstruction>> slicerCriterionResultMap = new HashMap<>();

        slicerCriterionResultMap.put(
                Set.of(0),
                Arrays.asList(
                        ConstantInstruction.make(0L),
                        PopInstruction.make(2),
                        ReturnInstruction.make(Constants.TYPE_void)));
        slicerCriterionResultMap.put(
                Set.of(1),
                Arrays.asList(
                        ConstantInstruction.make(0L),
                        StoreInstruction.make(Constants.TYPE_long, 1),
                        ReturnInstruction.make(Constants.TYPE_void)));
        slicerCriterionResultMap.put(
                Set.of(2),
                Arrays.asList(
                        ConstantInstruction.make(0L),
                        StoreInstruction.make(Constants.TYPE_long, 1),
                        LoadInstruction.make(Constants.TYPE_long, 1),
                        ConstantInstruction.make(5L),
                        ComparisonInstruction.make(Constants.TYPE_long, IComparisonInstruction.Operator.CMP),
                        ConstantInstruction.make(0),
                        ConditionalBranchInstruction.make(
                                Constants.TYPE_int, ConditionalBranchInstruction.Operator.GE, 14),
                        LoadInstruction.make(Constants.TYPE_long, 1),
                        ConstantInstruction.make(1L),
                        BinaryOpInstruction.make(Constants.TYPE_long, IBinaryOpInstruction.Operator.ADD),
                        StoreInstruction.make(Constants.TYPE_long, 1),
                        GotoInstruction.make(2),
                        InvokeInstruction.make(
                                "()V",
                                "Lde/uniks/vs/methodresourceprediction/slicer/export/Nothing;",
                                "doNothing",
                                Dispatch.STATIC),
                        ReturnInstruction.make(Constants.TYPE_void)));
        slicerCriterionResultMap.put(
                Set.of(3),
                Arrays.asList(
                        ConstantInstruction.make(5L),
                        PopInstruction.make(2),
                        ReturnInstruction.make(Constants.TYPE_void)));
        slicerCriterionResultMap.put(
                Set.of(4),
                Arrays.asList(
                        ConstantInstruction.make(0L),
                        StoreInstruction.make(Constants.TYPE_long, 1),
                        LoadInstruction.make(Constants.TYPE_long, 1),
                        ConstantInstruction.make(5L),
                        ComparisonInstruction.make(Constants.TYPE_long, IComparisonInstruction.Operator.CMP),
                        ConstantInstruction.make(0),
                        ConditionalBranchInstruction.make(
                                Constants.TYPE_int, ConditionalBranchInstruction.Operator.GE, 14),
                        LoadInstruction.make(Constants.TYPE_long, 1),
                        ConstantInstruction.make(1L),
                        BinaryOpInstruction.make(Constants.TYPE_long, IBinaryOpInstruction.Operator.ADD),
                        StoreInstruction.make(Constants.TYPE_long, 1),
                        GotoInstruction.make(2),
                        InvokeInstruction.make(
                                "()V",
                                "Lde/uniks/vs/methodresourceprediction/slicer/export/Nothing;",
                                "doNothing",
                                Dispatch.STATIC),
                        ReturnInstruction.make(Constants.TYPE_void)));
        slicerCriterionResultMap.put(
                Set.of(5),
                Arrays.asList(
                        ConstantInstruction.make(0),
                        PopInstruction.make(1),
                        ReturnInstruction.make(Constants.TYPE_void)));
        slicerCriterionResultMap.put(
                Set.of(6),
                Arrays.asList(
                        ConstantInstruction.make(0L),
                        StoreInstruction.make(Constants.TYPE_long, 1),
                        LoadInstruction.make(Constants.TYPE_long, 1),
                        ConstantInstruction.make(5L),
                        ComparisonInstruction.make(Constants.TYPE_long, IComparisonInstruction.Operator.CMP),
                        ConstantInstruction.make(0),
                        ConditionalBranchInstruction.make(
                                Constants.TYPE_int, ConditionalBranchInstruction.Operator.GE, 14),
                        LoadInstruction.make(Constants.TYPE_long, 1),
                        ConstantInstruction.make(1L),
                        BinaryOpInstruction.make(Constants.TYPE_long, IBinaryOpInstruction.Operator.ADD),
                        StoreInstruction.make(Constants.TYPE_long, 1),
                        GotoInstruction.make(2),
                        InvokeInstruction.make(
                                "()V",
                                "Lde/uniks/vs/methodresourceprediction/slicer/export/Nothing;",
                                "doNothing",
                                Dispatch.STATIC),
                        ReturnInstruction.make(Constants.TYPE_void)));
        slicerCriterionResultMap.put(
                Set.of(7),
                Arrays.asList(
                        ConstantInstruction.make(0L),
                        StoreInstruction.make(Constants.TYPE_long, 1),
                        LoadInstruction.make(Constants.TYPE_long, 1),
                        ConstantInstruction.make(5L),
                        ComparisonInstruction.make(Constants.TYPE_long, IComparisonInstruction.Operator.CMP),
                        ConstantInstruction.make(0),
                        ConditionalBranchInstruction.make(
                                Constants.TYPE_int, ConditionalBranchInstruction.Operator.GE, 14),
                        InvokeInstruction.make(
                                "()J", "Ljava/lang/System;", "currentTimeMillis", Dispatch.STATIC),
                        PopInstruction.make(2),
                        LoadInstruction.make(Constants.TYPE_long, 1),
                        ConstantInstruction.make(1L),
                        BinaryOpInstruction.make(Constants.TYPE_long, IBinaryOpInstruction.Operator.ADD),
                        StoreInstruction.make(Constants.TYPE_long, 1),
                        GotoInstruction.make(2),
                        InvokeInstruction.make(
                                "()V",
                                "Lde/uniks/vs/methodresourceprediction/slicer/export/Nothing;",
                                "doNothing",
                                Dispatch.STATIC),
                        ReturnInstruction.make(Constants.TYPE_void)));
        slicerCriterionResultMap.put(
                Set.of(8),
                Arrays.asList(
                        ConstantInstruction.make(0L),
                        StoreInstruction.make(Constants.TYPE_long, 1),
                        LoadInstruction.make(Constants.TYPE_long, 1),
                        ConstantInstruction.make(5L),
                        ComparisonInstruction.make(Constants.TYPE_long, IComparisonInstruction.Operator.CMP),
                        ConstantInstruction.make(0),
                        ConditionalBranchInstruction.make(
                                Constants.TYPE_int, ConditionalBranchInstruction.Operator.GE, 14),
                        InvokeInstruction.make(
                                "()J", "Ljava/lang/System;", "currentTimeMillis", Dispatch.STATIC),
                        PopInstruction.make(2),
                        LoadInstruction.make(Constants.TYPE_long, 1),
                        ConstantInstruction.make(1L),
                        BinaryOpInstruction.make(Constants.TYPE_long, IBinaryOpInstruction.Operator.ADD),
                        StoreInstruction.make(Constants.TYPE_long, 1),
                        GotoInstruction.make(2),
                        InvokeInstruction.make(
                                "()V",
                                "Lde/uniks/vs/methodresourceprediction/slicer/export/Nothing;",
                                "doNothing",
                                Dispatch.STATIC),
                        ReturnInstruction.make(Constants.TYPE_void)));
        slicerCriterionResultMap.put(
                Set.of(9),
                Arrays.asList(
                        ConstantInstruction.make(0L),
                        StoreInstruction.make(Constants.TYPE_long, 1),
                        LoadInstruction.make(Constants.TYPE_long, 1),
                        ConstantInstruction.make(5L),
                        ComparisonInstruction.make(Constants.TYPE_long, IComparisonInstruction.Operator.CMP),
                        ConstantInstruction.make(0),
                        ConditionalBranchInstruction.make(
                                Constants.TYPE_int, ConditionalBranchInstruction.Operator.GE, 14),
                        LoadInstruction.make(Constants.TYPE_long, 1),
                        ConstantInstruction.make(1L),
                        BinaryOpInstruction.make(Constants.TYPE_long, IBinaryOpInstruction.Operator.ADD),
                        StoreInstruction.make(Constants.TYPE_long, 1),
                        GotoInstruction.make(2),
                        InvokeInstruction.make(
                                "()V",
                                "Lde/uniks/vs/methodresourceprediction/slicer/export/Nothing;",
                                "doNothing",
                                Dispatch.STATIC),
                        ReturnInstruction.make(Constants.TYPE_void)));
        slicerCriterionResultMap.put(
                Set.of(10),
                Arrays.asList(
                        ConstantInstruction.make(1L),
                        PopInstruction.make(2),
                        ReturnInstruction.make(Constants.TYPE_void)));
        slicerCriterionResultMap.put(
                Set.of(11),
                Arrays.asList(
                        ConstantInstruction.make(0L),
                        StoreInstruction.make(Constants.TYPE_long, 1),
                        LoadInstruction.make(Constants.TYPE_long, 1),
                        ConstantInstruction.make(5L),
                        ComparisonInstruction.make(Constants.TYPE_long, IComparisonInstruction.Operator.CMP),
                        ConstantInstruction.make(0),
                        ConditionalBranchInstruction.make(
                                Constants.TYPE_int, ConditionalBranchInstruction.Operator.GE, 14),
                        LoadInstruction.make(Constants.TYPE_long, 1),
                        ConstantInstruction.make(1L),
                        BinaryOpInstruction.make(Constants.TYPE_long, IBinaryOpInstruction.Operator.ADD),
                        StoreInstruction.make(Constants.TYPE_long, 1),
                        GotoInstruction.make(2),
                        InvokeInstruction.make(
                                "()V",
                                "Lde/uniks/vs/methodresourceprediction/slicer/export/Nothing;",
                                "doNothing",
                                Dispatch.STATIC),
                        ReturnInstruction.make(Constants.TYPE_void)));
        slicerCriterionResultMap.put(
                Set.of(12),
                Arrays.asList(
                        ConstantInstruction.make(0L),
                        StoreInstruction.make(Constants.TYPE_long, 1),
                        LoadInstruction.make(Constants.TYPE_long, 1),
                        ConstantInstruction.make(5L),
                        ComparisonInstruction.make(Constants.TYPE_long, IComparisonInstruction.Operator.CMP),
                        ConstantInstruction.make(0),
                        ConditionalBranchInstruction.make(
                                Constants.TYPE_int, ConditionalBranchInstruction.Operator.GE, 14),
                        LoadInstruction.make(Constants.TYPE_long, 1),
                        ConstantInstruction.make(1L),
                        BinaryOpInstruction.make(Constants.TYPE_long, IBinaryOpInstruction.Operator.ADD),
                        StoreInstruction.make(Constants.TYPE_long, 1),
                        GotoInstruction.make(2),
                        InvokeInstruction.make(
                                "()V",
                                "Lde/uniks/vs/methodresourceprediction/slicer/export/Nothing;",
                                "doNothing",
                                Dispatch.STATIC),
                        ReturnInstruction.make(Constants.TYPE_void)));
        slicerCriterionResultMap.put(
                Set.of(13),
                Arrays.asList(
                        ConstantInstruction.make(0L),
                        StoreInstruction.make(Constants.TYPE_long, 1),
                        LoadInstruction.make(Constants.TYPE_long, 1),
                        ConstantInstruction.make(5L),
                        ComparisonInstruction.make(Constants.TYPE_long, IComparisonInstruction.Operator.CMP),
                        ConstantInstruction.make(0),
                        ConditionalBranchInstruction.make(
                                Constants.TYPE_int, ConditionalBranchInstruction.Operator.GE, 14),
                        LoadInstruction.make(Constants.TYPE_long, 1),
                        ConstantInstruction.make(1L),
                        BinaryOpInstruction.make(Constants.TYPE_long, IBinaryOpInstruction.Operator.ADD),
                        StoreInstruction.make(Constants.TYPE_long, 1),
                        GotoInstruction.make(2),
                        InvokeInstruction.make(
                                "()V",
                                "Lde/uniks/vs/methodresourceprediction/slicer/export/Nothing;",
                                "doNothing",
                                Dispatch.STATIC),
                        ReturnInstruction.make(Constants.TYPE_void)));
        slicerCriterionResultMap.put(
                Set.of(14),
                Arrays.asList(
                        ConstantInstruction.make(0L),
                        StoreInstruction.make(Constants.TYPE_long, 1),
                        LoadInstruction.make(Constants.TYPE_long, 1),
                        ConstantInstruction.make(5L),
                        ComparisonInstruction.make(Constants.TYPE_long, IComparisonInstruction.Operator.CMP),
                        ConstantInstruction.make(0),
                        ConditionalBranchInstruction.make(
                                Constants.TYPE_int, ConditionalBranchInstruction.Operator.GE, 14),
                        LoadInstruction.make(Constants.TYPE_long, 1),
                        ConstantInstruction.make(1L),
                        BinaryOpInstruction.make(Constants.TYPE_long, IBinaryOpInstruction.Operator.ADD),
                        StoreInstruction.make(Constants.TYPE_long, 1),
                        GotoInstruction.make(2),
                        LoadInstruction.make(Constants.TYPE_long, 1),
                        ConstantInstruction.make(10L),
                        ComparisonInstruction.make(Constants.TYPE_long, IComparisonInstruction.Operator.CMP),
                        ConstantInstruction.make(0),
                        ConditionalBranchInstruction.make(
                                Constants.TYPE_int, ConditionalBranchInstruction.Operator.GE, 26),
                        LoadInstruction.make(Constants.TYPE_long, 1),
                        ConstantInstruction.make(1L),
                        BinaryOpInstruction.make(Constants.TYPE_long, IBinaryOpInstruction.Operator.ADD),
                        StoreInstruction.make(Constants.TYPE_long, 1),
                        GotoInstruction.make(14),
                        InvokeInstruction.make(
                                "()V",
                                "Lde/uniks/vs/methodresourceprediction/slicer/export/Nothing;",
                                "doNothing",
                                Dispatch.STATIC),
                        ReturnInstruction.make(Constants.TYPE_void)));
        slicerCriterionResultMap.put(
                Set.of(15),
                Arrays.asList(
                        ConstantInstruction.make(10L),
                        PopInstruction.make(2),
                        ReturnInstruction.make(Constants.TYPE_void)));
        slicerCriterionResultMap.put(
                Set.of(16),
                Arrays.asList(
                        ConstantInstruction.make(0L),
                        StoreInstruction.make(Constants.TYPE_long, 1),
                        LoadInstruction.make(Constants.TYPE_long, 1),
                        ConstantInstruction.make(5L),
                        ComparisonInstruction.make(Constants.TYPE_long, IComparisonInstruction.Operator.CMP),
                        ConstantInstruction.make(0),
                        ConditionalBranchInstruction.make(
                                Constants.TYPE_int, ConditionalBranchInstruction.Operator.GE, 14),
                        LoadInstruction.make(Constants.TYPE_long, 1),
                        ConstantInstruction.make(1L),
                        BinaryOpInstruction.make(Constants.TYPE_long, IBinaryOpInstruction.Operator.ADD),
                        StoreInstruction.make(Constants.TYPE_long, 1),
                        GotoInstruction.make(2),
                        LoadInstruction.make(Constants.TYPE_long, 1),
                        ConstantInstruction.make(10L),
                        ComparisonInstruction.make(Constants.TYPE_long, IComparisonInstruction.Operator.CMP),
                        ConstantInstruction.make(0),
                        ConditionalBranchInstruction.make(
                                Constants.TYPE_int, ConditionalBranchInstruction.Operator.GE, 26),
                        LoadInstruction.make(Constants.TYPE_long, 1),
                        ConstantInstruction.make(1L),
                        BinaryOpInstruction.make(Constants.TYPE_long, IBinaryOpInstruction.Operator.ADD),
                        StoreInstruction.make(Constants.TYPE_long, 1),
                        GotoInstruction.make(14),
                        InvokeInstruction.make(
                                "()V",
                                "Lde/uniks/vs/methodresourceprediction/slicer/export/Nothing;",
                                "doNothing",
                                Dispatch.STATIC),
                        ReturnInstruction.make(Constants.TYPE_void)));
        slicerCriterionResultMap.put(
                Set.of(17),
                Arrays.asList(
                        ConstantInstruction.make(0),
                        PopInstruction.make(1),
                        ReturnInstruction.make(Constants.TYPE_void)));
        slicerCriterionResultMap.put(
                Set.of(18),
                Arrays.asList(
                        ConstantInstruction.make(0L),
                        StoreInstruction.make(Constants.TYPE_long, 1),
                        LoadInstruction.make(Constants.TYPE_long, 1),
                        ConstantInstruction.make(5L),
                        ComparisonInstruction.make(Constants.TYPE_long, IComparisonInstruction.Operator.CMP),
                        ConstantInstruction.make(0),
                        ConditionalBranchInstruction.make(
                                Constants.TYPE_int, ConditionalBranchInstruction.Operator.GE, 14),
                        LoadInstruction.make(Constants.TYPE_long, 1),
                        ConstantInstruction.make(1L),
                        BinaryOpInstruction.make(Constants.TYPE_long, IBinaryOpInstruction.Operator.ADD),
                        StoreInstruction.make(Constants.TYPE_long, 1),
                        GotoInstruction.make(2),
                        LoadInstruction.make(Constants.TYPE_long, 1),
                        ConstantInstruction.make(10L),
                        ComparisonInstruction.make(Constants.TYPE_long, IComparisonInstruction.Operator.CMP),
                        ConstantInstruction.make(0),
                        ConditionalBranchInstruction.make(
                                Constants.TYPE_int, ConditionalBranchInstruction.Operator.GE, 26),
                        LoadInstruction.make(Constants.TYPE_long, 1),
                        ConstantInstruction.make(1L),
                        BinaryOpInstruction.make(Constants.TYPE_long, IBinaryOpInstruction.Operator.ADD),
                        StoreInstruction.make(Constants.TYPE_long, 1),
                        GotoInstruction.make(14),
                        InvokeInstruction.make(
                                "()V",
                                "Lde/uniks/vs/methodresourceprediction/slicer/export/Nothing;",
                                "doNothing",
                                Dispatch.STATIC),
                        ReturnInstruction.make(Constants.TYPE_void)));
        slicerCriterionResultMap.put(
                Set.of(19),
                Arrays.asList(
                        ConstantInstruction.make(0L),
                        StoreInstruction.make(Constants.TYPE_long, 1),
                        LoadInstruction.make(Constants.TYPE_long, 1),
                        ConstantInstruction.make(5L),
                        ComparisonInstruction.make(Constants.TYPE_long, IComparisonInstruction.Operator.CMP),
                        ConstantInstruction.make(0),
                        ConditionalBranchInstruction.make(
                                Constants.TYPE_int, ConditionalBranchInstruction.Operator.GE, 14),
                        LoadInstruction.make(Constants.TYPE_long, 1),
                        ConstantInstruction.make(1L),
                        BinaryOpInstruction.make(Constants.TYPE_long, IBinaryOpInstruction.Operator.ADD),
                        StoreInstruction.make(Constants.TYPE_long, 1),
                        GotoInstruction.make(2),
                        LoadInstruction.make(Constants.TYPE_long, 1),
                        ConstantInstruction.make(10L),
                        ComparisonInstruction.make(Constants.TYPE_long, IComparisonInstruction.Operator.CMP),
                        ConstantInstruction.make(0),
                        ConditionalBranchInstruction.make(
                                Constants.TYPE_int, ConditionalBranchInstruction.Operator.GE, 26),
                        InvokeInstruction.make(
                                "()J", "Ljava/lang/System;", "currentTimeMillis", Dispatch.STATIC),
                        PopInstruction.make(2),
                        LoadInstruction.make(Constants.TYPE_long, 1),
                        ConstantInstruction.make(1L),
                        BinaryOpInstruction.make(Constants.TYPE_long, IBinaryOpInstruction.Operator.ADD),
                        StoreInstruction.make(Constants.TYPE_long, 1),
                        GotoInstruction.make(14),
                        InvokeInstruction.make(
                                "()V",
                                "Lde/uniks/vs/methodresourceprediction/slicer/export/Nothing;",
                                "doNothing",
                                Dispatch.STATIC),
                        ReturnInstruction.make(Constants.TYPE_void)));
        slicerCriterionResultMap.put(
                Set.of(20),
                Arrays.asList(
                        ConstantInstruction.make(0L),
                        StoreInstruction.make(Constants.TYPE_long, 1),
                        LoadInstruction.make(Constants.TYPE_long, 1),
                        ConstantInstruction.make(5L),
                        ComparisonInstruction.make(Constants.TYPE_long, IComparisonInstruction.Operator.CMP),
                        ConstantInstruction.make(0),
                        ConditionalBranchInstruction.make(
                                Constants.TYPE_int, ConditionalBranchInstruction.Operator.GE, 14),
                        LoadInstruction.make(Constants.TYPE_long, 1),
                        ConstantInstruction.make(1L),
                        BinaryOpInstruction.make(Constants.TYPE_long, IBinaryOpInstruction.Operator.ADD),
                        StoreInstruction.make(Constants.TYPE_long, 1),
                        GotoInstruction.make(2),
                        LoadInstruction.make(Constants.TYPE_long, 1),
                        ConstantInstruction.make(10L),
                        ComparisonInstruction.make(Constants.TYPE_long, IComparisonInstruction.Operator.CMP),
                        ConstantInstruction.make(0),
                        ConditionalBranchInstruction.make(
                                Constants.TYPE_int, ConditionalBranchInstruction.Operator.GE, 26),
                        InvokeInstruction.make(
                                "()J", "Ljava/lang/System;", "currentTimeMillis", Dispatch.STATIC),
                        PopInstruction.make(2),
                        LoadInstruction.make(Constants.TYPE_long, 1),
                        ConstantInstruction.make(1L),
                        BinaryOpInstruction.make(Constants.TYPE_long, IBinaryOpInstruction.Operator.ADD),
                        StoreInstruction.make(Constants.TYPE_long, 1),
                        GotoInstruction.make(14),
                        InvokeInstruction.make(
                                "()V",
                                "Lde/uniks/vs/methodresourceprediction/slicer/export/Nothing;",
                                "doNothing",
                                Dispatch.STATIC),
                        ReturnInstruction.make(Constants.TYPE_void)));
        slicerCriterionResultMap.put(
                Set.of(21),
                Arrays.asList(
                        ConstantInstruction.make(0L),
                        StoreInstruction.make(Constants.TYPE_long, 1),
                        LoadInstruction.make(Constants.TYPE_long, 1),
                        ConstantInstruction.make(5L),
                        ComparisonInstruction.make(Constants.TYPE_long, IComparisonInstruction.Operator.CMP),
                        ConstantInstruction.make(0),
                        ConditionalBranchInstruction.make(
                                Constants.TYPE_int, ConditionalBranchInstruction.Operator.GE, 14),
                        LoadInstruction.make(Constants.TYPE_long, 1),
                        ConstantInstruction.make(1L),
                        BinaryOpInstruction.make(Constants.TYPE_long, IBinaryOpInstruction.Operator.ADD),
                        StoreInstruction.make(Constants.TYPE_long, 1),
                        GotoInstruction.make(2),
                        LoadInstruction.make(Constants.TYPE_long, 1),
                        ConstantInstruction.make(10L),
                        ComparisonInstruction.make(Constants.TYPE_long, IComparisonInstruction.Operator.CMP),
                        ConstantInstruction.make(0),
                        ConditionalBranchInstruction.make(
                                Constants.TYPE_int, ConditionalBranchInstruction.Operator.GE, 26),
                        LoadInstruction.make(Constants.TYPE_long, 1),
                        ConstantInstruction.make(1L),
                        BinaryOpInstruction.make(Constants.TYPE_long, IBinaryOpInstruction.Operator.ADD),
                        StoreInstruction.make(Constants.TYPE_long, 1),
                        GotoInstruction.make(14),
                        InvokeInstruction.make(
                                "()V",
                                "Lde/uniks/vs/methodresourceprediction/slicer/export/Nothing;",
                                "doNothing",
                                Dispatch.STATIC),
                        ReturnInstruction.make(Constants.TYPE_void)));
        slicerCriterionResultMap.put(
                Set.of(22),
                Arrays.asList(
                        ConstantInstruction.make(1L),
                        PopInstruction.make(2),
                        ReturnInstruction.make(Constants.TYPE_void)));
        slicerCriterionResultMap.put(
                Set.of(23),
                Arrays.asList(
                        ConstantInstruction.make(0L),
                        StoreInstruction.make(Constants.TYPE_long, 1),
                        LoadInstruction.make(Constants.TYPE_long, 1),
                        ConstantInstruction.make(5L),
                        ComparisonInstruction.make(Constants.TYPE_long, IComparisonInstruction.Operator.CMP),
                        ConstantInstruction.make(0),
                        ConditionalBranchInstruction.make(
                                Constants.TYPE_int, ConditionalBranchInstruction.Operator.GE, 14),
                        LoadInstruction.make(Constants.TYPE_long, 1),
                        ConstantInstruction.make(1L),
                        BinaryOpInstruction.make(Constants.TYPE_long, IBinaryOpInstruction.Operator.ADD),
                        StoreInstruction.make(Constants.TYPE_long, 1),
                        GotoInstruction.make(2),
                        LoadInstruction.make(Constants.TYPE_long, 1),
                        ConstantInstruction.make(10L),
                        ComparisonInstruction.make(Constants.TYPE_long, IComparisonInstruction.Operator.CMP),
                        ConstantInstruction.make(0),
                        ConditionalBranchInstruction.make(
                                Constants.TYPE_int, ConditionalBranchInstruction.Operator.GE, 26),
                        LoadInstruction.make(Constants.TYPE_long, 1),
                        ConstantInstruction.make(1L),
                        BinaryOpInstruction.make(Constants.TYPE_long, IBinaryOpInstruction.Operator.ADD),
                        StoreInstruction.make(Constants.TYPE_long, 1),
                        GotoInstruction.make(14),
                        InvokeInstruction.make(
                                "()V",
                                "Lde/uniks/vs/methodresourceprediction/slicer/export/Nothing;",
                                "doNothing",
                                Dispatch.STATIC),
                        ReturnInstruction.make(Constants.TYPE_void)));
        slicerCriterionResultMap.put(
                Set.of(24),
                Arrays.asList(
                        ConstantInstruction.make(0L),
                        StoreInstruction.make(Constants.TYPE_long, 1),
                        LoadInstruction.make(Constants.TYPE_long, 1),
                        ConstantInstruction.make(5L),
                        ComparisonInstruction.make(Constants.TYPE_long, IComparisonInstruction.Operator.CMP),
                        ConstantInstruction.make(0),
                        ConditionalBranchInstruction.make(
                                Constants.TYPE_int, ConditionalBranchInstruction.Operator.GE, 14),
                        LoadInstruction.make(Constants.TYPE_long, 1),
                        ConstantInstruction.make(1L),
                        BinaryOpInstruction.make(Constants.TYPE_long, IBinaryOpInstruction.Operator.ADD),
                        StoreInstruction.make(Constants.TYPE_long, 1),
                        GotoInstruction.make(2),
                        LoadInstruction.make(Constants.TYPE_long, 1),
                        ConstantInstruction.make(10L),
                        ComparisonInstruction.make(Constants.TYPE_long, IComparisonInstruction.Operator.CMP),
                        ConstantInstruction.make(0),
                        ConditionalBranchInstruction.make(
                                Constants.TYPE_int, ConditionalBranchInstruction.Operator.GE, 26),
                        LoadInstruction.make(Constants.TYPE_long, 1),
                        ConstantInstruction.make(1L),
                        BinaryOpInstruction.make(Constants.TYPE_long, IBinaryOpInstruction.Operator.ADD),
                        StoreInstruction.make(Constants.TYPE_long, 1),
                        GotoInstruction.make(14),
                        InvokeInstruction.make(
                                "()V",
                                "Lde/uniks/vs/methodresourceprediction/slicer/export/Nothing;",
                                "doNothing",
                                Dispatch.STATIC),
                        ReturnInstruction.make(Constants.TYPE_void)));
        slicerCriterionResultMap.put(
                Set.of(25),
                Arrays.asList(
                        ConstantInstruction.make(0L),
                        StoreInstruction.make(Constants.TYPE_long, 1),
                        LoadInstruction.make(Constants.TYPE_long, 1),
                        ConstantInstruction.make(5L),
                        ComparisonInstruction.make(Constants.TYPE_long, IComparisonInstruction.Operator.CMP),
                        ConstantInstruction.make(0),
                        ConditionalBranchInstruction.make(
                                Constants.TYPE_int, ConditionalBranchInstruction.Operator.GE, 14),
                        LoadInstruction.make(Constants.TYPE_long, 1),
                        ConstantInstruction.make(1L),
                        BinaryOpInstruction.make(Constants.TYPE_long, IBinaryOpInstruction.Operator.ADD),
                        StoreInstruction.make(Constants.TYPE_long, 1),
                        GotoInstruction.make(2),
                        LoadInstruction.make(Constants.TYPE_long, 1),
                        ConstantInstruction.make(10L),
                        ComparisonInstruction.make(Constants.TYPE_long, IComparisonInstruction.Operator.CMP),
                        ConstantInstruction.make(0),
                        ConditionalBranchInstruction.make(
                                Constants.TYPE_int, ConditionalBranchInstruction.Operator.GE, 26),
                        LoadInstruction.make(Constants.TYPE_long, 1),
                        ConstantInstruction.make(1L),
                        BinaryOpInstruction.make(Constants.TYPE_long, IBinaryOpInstruction.Operator.ADD),
                        StoreInstruction.make(Constants.TYPE_long, 1),
                        GotoInstruction.make(14),
                        InvokeInstruction.make(
                                "()V",
                                "Lde/uniks/vs/methodresourceprediction/slicer/export/Nothing;",
                                "doNothing",
                                Dispatch.STATIC),
                        ReturnInstruction.make(Constants.TYPE_void)));
        slicerCriterionResultMap.put(
                Set.of(26),
                Arrays.asList(
                        InvokeInstruction.make(
                                "()V",
                                "Lde/uniks/vs/methodresourceprediction/slicer/export/Nothing;",
                                "doNothing",
                                Dispatch.STATIC),
                        ReturnInstruction.make(Constants.TYPE_void)));
        slicerCriterionResultMap.put(
                Set.of(27), Arrays.asList(ReturnInstruction.make(Constants.TYPE_void)));

        validateSliceResults(slicerCriterionResultMap);
    }

    @Test
    public void testSimpleReturnValue()
            throws IOException, InvalidClassFileException, InterruptedException, IllegalStateException,
            DecoderException {
        slicer.setMethodSignature(
                "Lde.uniks.vs.methodresourceprediction.slicer.test.SlicerValidation;.simpleReturnValue()I");
        System.out.println(slicer.getMethodSummary());

        Map<Set<Integer>, List<IInstruction>> slicerCriterionResultMap = new HashMap<>();

        slicerCriterionResultMap.put(
                Set.of(0),
                Arrays.asList(ConstantInstruction.make(0), ReturnInstruction.make(Constants.TYPE_int)));
        slicerCriterionResultMap.put(
                Set.of(1),
                Arrays.asList(ConstantInstruction.make(0), ReturnInstruction.make(Constants.TYPE_int)));

        validateSliceResults(slicerCriterionResultMap);
    }

    @Test
    public void testSimpleReturnValue2()
            throws IOException, InvalidClassFileException, InterruptedException, IllegalStateException,
            DecoderException {
        slicer.setMethodSignature(
                "Lde.uniks.vs.methodresourceprediction.slicer.test.SlicerValidation;.simpleReturnValue2()J");
        System.out.println(slicer.getMethodSummary());

        Map<Set<Integer>, List<IInstruction>> slicerCriterionResultMap = new HashMap<>();

        slicerCriterionResultMap.put(
                Set.of(0),
                Arrays.asList(
                        InvokeInstruction.make(
                                "()J", "Ljava/lang/System;", "currentTimeMillis", Dispatch.STATIC),
                        ReturnInstruction.make(Constants.TYPE_long)));
        slicerCriterionResultMap.put(
                Set.of(1),
                Arrays.asList(
                        InvokeInstruction.make(
                                "()J", "Ljava/lang/System;", "currentTimeMillis", Dispatch.STATIC),
                        ReturnInstruction.make(Constants.TYPE_long)));

        validateSliceResults(slicerCriterionResultMap);
    }

    @Test
    public void testSimpleReturnValue3()
            throws IOException, InvalidClassFileException, InterruptedException, IllegalStateException,
            DecoderException {
        slicer.setMethodSignature(
                "Lde.uniks.vs.methodresourceprediction.slicer.test.SlicerValidation;.simpleReturnValue3()J");
        System.out.println(slicer.getMethodSummary());

        Map<Set<Integer>, List<IInstruction>> slicerCriterionResultMap = new HashMap<>();

        slicerCriterionResultMap.put(
                Set.of(0),
                Arrays.asList(
                        InvokeInstruction.make(
                                "()J", "Ljava/lang/System;", "currentTimeMillis", Dispatch.STATIC),
                        PopInstruction.make(2),
                        ConstantInstruction.make(0L),
                        ReturnInstruction.make(Constants.TYPE_long)));
        slicerCriterionResultMap.put(
                Set.of(1),
                Arrays.asList(
                        InvokeInstruction.make(
                                "()J", "Ljava/lang/System;", "currentTimeMillis", Dispatch.STATIC),
                        PopInstruction.make(2),
                        ConstantInstruction.make(0L),
                        ReturnInstruction.make(Constants.TYPE_long)));
        slicerCriterionResultMap.put(
                Set.of(2),
                Arrays.asList(ConstantInstruction.make(0L), ReturnInstruction.make(Constants.TYPE_long)));
        slicerCriterionResultMap.put(
                Set.of(3),
                Arrays.asList(ConstantInstruction.make(0L), ReturnInstruction.make(Constants.TYPE_long)));

        validateSliceResults(slicerCriterionResultMap);
    }

    @Test
    public void testReturnObject()
            throws IOException, InvalidClassFileException, IllegalStateException {
        slicer.setMethodSignature(
                "Lde.uniks.vs.methodresourceprediction.slicer.test.SlicerValidation;.returnObject()Ljava/io/PrintStream;");
        System.out.println(slicer.getMethodSummary());

        Map<Set<Integer>, List<IInstruction>> slicerCriterionResultMap = new HashMap<>();

        slicerCriterionResultMap.put(
                Set.of(0),
                Arrays.asList(
                        GetInstruction.make("Ljava/io/PrintStream;", "Ljava/lang/System;", "out", true),
                        ReturnInstruction.make(Constants.TYPE_Object)));
        slicerCriterionResultMap.put(
                Set.of(1),
                Arrays.asList(
                        GetInstruction.make("Ljava/io/PrintStream;", "Ljava/lang/System;", "out", true),
                        ReturnInstruction.make(Constants.TYPE_Object)));

        validateSliceResults(slicerCriterionResultMap);
    }

    @Test
    public void testSliceConditional()
            throws IOException, InvalidClassFileException, InterruptedException, ExportException,
            URISyntaxException {
        slicer.setMethodSignature(
                "Lde.uniks.vs.methodresourceprediction.slicer.test.SlicerValidation;.simpleConditional()V");
        System.out.println(slicer.getMethodSummary());
        //		slicer.showPlots();
        Path dir = Path.of(new URI("file:///tmp/slicer"));
        //		final Path dir = Files.createTempDirectory("slicer-");
        slicer.getControlFlow().writePlot(dir, "ControlFlow.png");
        slicer.getDominance().writePlot(dir, "ForwardDominanceTree.png");
        //		slicer.getControlDependency().writePlot(dir, "ControlDependency.png");
    }

    private void validateSliceResults(Map<Set<Integer>, List<IInstruction>> slicerCriterionResultMap)
            throws IOException, InvalidClassFileException {
        // Debug code generation output
        for (int instructionIndex = 0;
             instructionIndex < slicer.getControlFlow().getMethodData().getInstructions().length;
             instructionIndex++) {
            Set<Integer> criterionSet = Set.of(instructionIndex);

            slicer.setInstructionIndexes(criterionSet);
            SliceResult sliceResult = slicer.getSliceResult();

            System.out.println(
                    "slicerCriterionResultMap.put(Set.of("
                            + instructionIndex
                            + "), "
                            + sliceResult.toJavaSource()
                            + ");");
        }

        for (Entry<Set<Integer>, List<IInstruction>> slicerCriterionResultEntry :
                slicerCriterionResultMap.entrySet()) {
            Set<Integer> criterionSet = slicerCriterionResultEntry.getKey();
            List<IInstruction> resultList = slicerCriterionResultEntry.getValue();

            slicer.setInstructionIndexes(criterionSet);
            SliceResult sliceResult = slicer.getSliceResult();

            System.out.println(sliceResult);

            assertTrue(
                    "Expected slice from "
                            + criterionSet
                            + " \n  "
                            + resultList
                            + "\nbut is\n  "
                            + sliceResult.getSlice(),
                    resultList.equals(sliceResult.getSlice()));
        }

        for (int instructionIndex = 0;
             instructionIndex < slicer.getControlFlow().getMethodData().getInstructions().length;
             instructionIndex++) {
            assertTrue(
                    "slice instruction index test missing for instruction index: [" + instructionIndex + "]",
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
