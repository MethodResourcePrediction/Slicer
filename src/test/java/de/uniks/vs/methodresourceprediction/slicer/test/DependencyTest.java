package de.uniks.vs.methodresourceprediction.slicer.test;

import com.ibm.wala.shrikeCT.InvalidClassFileException;
import de.uniks.vs.methodresourceprediction.slicer.Slicer;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

@TestInstance(Lifecycle.PER_CLASS)
public class DependencyTest {
  private Slicer slicer;

  @BeforeEach
  public void setUp() throws Exception {
    // Get path to java class
    String classFilePath =
        DependencyTest.class.getProtectionDomain().getCodeSource().getLocation().getPath();
    String path = URLDecoder.decode(classFilePath, StandardCharsets.UTF_8);

    String jvmPackageName = SlicerValidation.class.getPackageName();
    String packageName = jvmPackageName.replaceAll("\\.", File.separator) + File.separator;

    String slicerValidationClassPath =
        FilenameUtils.concat(
            path, packageName + DependencyValidation.class.getSimpleName() + ".class");
    String dependencyValidationJarPath =
        FilenameUtils.concat(
            path, packageName + DependencyValidation.class.getSimpleName() + ".jar");

    Process process =
        new ProcessBuilder("jar", "-cfv", dependencyValidationJarPath, slicerValidationClassPath)
            .start();
    process.waitFor();
    if (process.exitValue() != 0) {
      System.err.println("Some error occured during jar compilation\n");
      System.err.println("\nStderr:\n" + readInputStream(process.getErrorStream()));
      System.out.println("\nStdout:\n" + readInputStream(process.getInputStream()));
      throw new IOException("Process exited with exit code " + process.exitValue());
    }

    slicer = new Slicer();
    slicer.setInputJar(dependencyValidationJarPath);
  }

  @Test
  public void testStaticMethodWithoutDependencies() throws IOException, InvalidClassFileException {
    slicer.setMethodSignature(
        "Lde.uniks.vs.methodresourceprediction.slicer.test.DependencyValidation;.staticMethodWithoutDependencies(I)I");
//    assertTrue(slice.isFunctional());
  }

  @Test
  public void testStaticMethodWithStaticFieldDependency()
      throws IOException, InvalidClassFileException {
    slicer.setMethodSignature(
        "Lde.uniks.vs.methodresourceprediction.slicer.test.DependencyValidation;.staticMethodWithStaticFieldDependency(I)I");
//    assertTrue(slicer.isFunctional());
  }

  @Test
  public void testStaticMethodWithStaticVariableDependency()
      throws IOException, InvalidClassFileException {
    slicer.setMethodSignature(
        "Lde.uniks.vs.methodresourceprediction.slicer.test.DependencyValidation;.staticMethodWithStaticVariableDependency(I)I");
//    assertFalse(slicer.isFunctional());
  }

  @Test
  public void testMethodWithoutDependency() throws IOException, InvalidClassFileException {
    slicer.setMethodSignature(
        "Lde.uniks.vs.methodresourceprediction.slicer.test.DependencyValidation;.methodWithoutDependency(I)I");
//    assertFalse(slicer.isFunctional());
  }

  @Test
  public void testStaticMethodWithJavaLangDependency()
      throws IOException, InvalidClassFileException {
    slicer.setMethodSignature(
        "Lde.uniks.vs.methodresourceprediction.slicer.test.DependencyValidation;.staticMethodWithJavaLangDependency(I)I");
//    assertTrue(slicer.isFunctional());
  }

  @Test
  public void testMethodWithVariableDependency() throws IOException, InvalidClassFileException {
    slicer.setMethodSignature(
        "Lde.uniks.vs.methodresourceprediction.slicer.test.DependencyValidation;.methodWithVariableDependency(I)I");
//    assertFalse(slicer.isFunctional());
  }

  @Test
  public void testMethodCallWithoutDependency() throws IOException, InvalidClassFileException {
    slicer.setMethodSignature(
        "Lde.uniks.vs.methodresourceprediction.slicer.test.DependencyValidation;.methodCallWithoutDependency(I)I");
//    assertFalse(slicer.isFunctional());
  }

  @Test
  public void testMethodCallWithDependency() throws IOException, InvalidClassFileException {
    slicer.setMethodSignature(
        "Lde.uniks.vs.methodresourceprediction.slicer.test.DependencyValidation;.methodCallWithDependency(I)I");
//    assertFalse(slicer.isFunctional());
  }

  @Test
  public void testStaticMethodNew() throws IOException, InvalidClassFileException {
    slicer.setMethodSignature(
        "Lde.uniks.vs.methodresourceprediction.slicer.test.DependencyValidation;.staticMethodNew(I)I");
//    assertFalse(slicer.isFunctional());
  }

  @Test
  public void testStaticMethodCallWithoutDependency()
      throws IOException, InvalidClassFileException {
    slicer.setMethodSignature(
        "Lde.uniks.vs.methodresourceprediction.slicer.test.DependencyValidation;.staticMethodCallWithoutDependency(I)I");
//    assertTrue(slicer.isFunctional());
  }

  @Test
  public void testStaticMethodCallWithDependency() throws IOException, InvalidClassFileException {
    slicer.setMethodSignature(
        "Lde.uniks.vs.methodresourceprediction.slicer.test.DependencyValidation;.staticMethodCallWithDependency(I)I");
//    assertTrue(slicer.isFunctional());
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
