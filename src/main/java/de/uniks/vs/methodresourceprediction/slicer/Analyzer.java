package de.uniks.vs.methodresourceprediction.slicer;

import com.ibm.wala.shrikeBT.*;
import com.ibm.wala.shrikeBT.shrikeCT.ClassInstrumenter;
import com.ibm.wala.shrikeBT.shrikeCT.OfflineInstrumenter;
import com.ibm.wala.shrikeCT.ClassReader;
import com.ibm.wala.shrikeCT.InvalidClassFileException;
import de.uniks.vs.methodresourceprediction.utils.InstrumenterComparator;
import de.uniks.vs.methodresourceprediction.utils.Utilities;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

public class Analyzer {
  private final String inputJar;

  public Analyzer(String inputJar) {
    this.inputJar = inputJar;
  }

  public boolean isFunctional(String methodSignature)
      throws IOException, InvalidClassFileException {
    ControlFlow controlFlow = new ControlFlow(inputJar, methodSignature);

    final String[] jvmAllowedPackagePrefixes = new String[] {"Ljava/"};

    // TODO Functional if
    // * Does not store class variables
    // * Does not store fields
    // * Calls only functional methods or jvm methods

    MethodData methodData = controlFlow.getMethodData();
    if (!methodData.getIsStatic()) {
      return false;
    }

    IInstruction[] instructions = methodData.getInstructions();
    int maxLocalVarIndex = Utilities.getMaxLocalVarIndex(instructions);

    for (IInstruction instruction : instructions) {
      if (instruction instanceof InvokeInstruction) {
        InvokeInstruction invokeInstruction = (InvokeInstruction) instruction;
        String classType = invokeInstruction.getClassType();
        String methodName = invokeInstruction.getMethodName();
        String calledMethodSignature = invokeInstruction.getMethodSignature();

        boolean allowedPrefix =
            Arrays.stream(jvmAllowedPackagePrefixes).anyMatch(classType::startsWith);
        if (!allowedPrefix) {
          // Iteratively check dependent invocations
          if (!new Analyzer(getInputJar()).isFunctional(calledMethodSignature)) {
            return false;
          }
        }
      }

      if (instruction instanceof IStoreInstruction) {
        IStoreInstruction storeInstruction = (IStoreInstruction) instruction;
        if (storeInstruction.getVarIndex() > maxLocalVarIndex) {
          return false;
        }
      }

      if (instruction instanceof IGetInstruction) {
        IGetInstruction getInstruction = (IGetInstruction) instruction;
        String classType = getInstruction.getClassType();
        boolean allowedPrefix =
            Arrays.stream(jvmAllowedPackagePrefixes).anyMatch(classType::startsWith);
        if (!allowedPrefix) {
          return false;
        }
      }
    }
    return true;
  }

  public static ClassInstrumenter[] getClassInstrumenters(File inputJar) throws IOException {
    OfflineInstrumenter inst = new OfflineInstrumenter();
    inst.addInputJar(inputJar);
    inst.beginTraversal();

    List<ClassInstrumenter> classInstrumenters = new ArrayList<>();

    ClassInstrumenter ci;
    while ((ci = inst.nextClass()) != null) {
      classInstrumenters.add(ci);
    }
    return classInstrumenters.toArray(new ClassInstrumenter[0]);
  }

  public static ClassInstrumenter getClassInstrumenter(File inputJar, String className)
      throws IOException {
    Optional<ClassInstrumenter> optionalClassInstrumenter =
        Arrays.stream(getClassInstrumenters(inputJar))
            .filter(
                ci -> {
                  try {
                    String name = "L" + ci.getReader().getName() + ";";
                    return name.equals(className);
                  } catch (InvalidClassFileException e) {
                    return false;
                  }
                })
            .findFirst();
    return optionalClassInstrumenter.orElse(null);
  }

  public static MethodData[] getMethods(ClassInstrumenter classInstrumenter)
      throws InvalidClassFileException {
    List<MethodData> methodDataList = new ArrayList<>();
    ClassReader classReader = classInstrumenter.getReader();
    for (int methodIndex = 0; methodIndex < classReader.getMethodCount(); methodIndex++) {
      MethodData md = classInstrumenter.visitMethod(methodIndex);
      if (md == null) {
        continue;
      }
      methodDataList.add(md);
    }
    return methodDataList.toArray(new MethodData[0]);
  }

  public static MethodData getMethod(ClassInstrumenter classInstrumenter, String methodName)
      throws InvalidClassFileException {
    Optional<MethodData> optionalMethodData =
        Arrays.stream(getMethods(classInstrumenter))
            .filter(
                md -> {
                  String name = md.getName();
                  return name.equals(methodName);
                })
            .findFirst();
    return optionalMethodData.orElse(null);
  }

  public static IInstruction[] getInstructions(File inputJar, String methodSignature)
      throws IOException, InvalidClassFileException {
    InstrumenterComparator comparator = InstrumenterComparator.of(methodSignature);

    MethodData md = null;
    ClassInstrumenter[] classInstrumenters = getClassInstrumenters(inputJar);
    for (ClassInstrumenter classInstrumenter : classInstrumenters) {
      if (comparator.equals(classInstrumenter)) {
        MethodData[] methods = getMethods(classInstrumenter);
        for (MethodData method : methods) {
          if (comparator.equals(method)) {
            md = method;
            break;
          }
        }
      }
    }

    if (md == null) {
      return null;
    }

    // Iterate each class in the input program and instrument it
    return md.getInstructions();
  }

  public String getInputJar() {
    return inputJar;
  }
}
