package de.uniks.vs.methodresourceprediction.slicer;

import com.ibm.wala.shrikeBT.*;
import com.ibm.wala.shrikeBT.IInvokeInstruction.Dispatch;
import com.ibm.wala.shrikeCT.InvalidClassFileException;
import com.ibm.wala.types.TypeName;
import com.ibm.wala.util.debug.UnimplementedError;
import com.ibm.wala.util.strings.StringStuff;
import de.uniks.vs.methodresourceprediction.slicer.export.Nothing;
import de.uniks.vs.methodresourceprediction.utils.Utilities;
import java.io.IOException;
import java.rmi.UnexpectedException;
import java.util.*;

public class SliceResult {
  private final String methodSignature;
  private final Set<Integer> instructionIndex;
  private final Set<Integer> instructionsToKeep;
  private final Set<Integer> instructionsToIgnore;
  private final Map<Integer, Integer> instructionPopMap;
  private final ControlFlow controlFlow;
  private final ArgumentDependency argumentDependency;

  public SliceResult(
      String methodSignature,
      Set<Integer> instructionIndex,
      Set<Integer> instructionsToKeep,
      Set<Integer> instructionsToIgnore,
      Map<Integer, Integer> instructionPopMap,
      ControlFlow controlFlow) {
    this.methodSignature = methodSignature;
    this.instructionIndex = instructionIndex;
    this.instructionsToKeep = instructionsToKeep;
    this.instructionsToIgnore = instructionsToIgnore;
    this.instructionPopMap = instructionPopMap;
    this.controlFlow = controlFlow;
    this.argumentDependency = new ArgumentDependency(controlFlow);
  }

  public Set<Integer> getInstructionIndex() {
    return instructionIndex;
  }

  public Set<Integer> getInstructionsToKeep() {
    return instructionsToKeep;
  }

  public Set<Integer> getInstructionsToIgnore() {
    return instructionsToIgnore;
  }

  public Map<Integer, Integer> getInstructionPopMap() {
    return instructionPopMap;
  }

  public String getMethodSignature() {
    return methodSignature;
  }

  public static SliceResult emptyResult() {
    return new SliceResult(
        null,
        Collections.emptySet(),
        Collections.emptySet(),
        Collections.emptySet(),
        Collections.emptyMap(),
        null);
  }

  public List<IInstruction> getSlice() throws IOException, InvalidClassFileException {
    List<IInstruction> slice = new ArrayList<>();
    IInstruction[] instructions = getControlFlow().getMethodData().getInstructions();
    for (int index = 0; index < instructions.length; index++) {
      IInstruction instruction = instructions[index];

      if (getInstructionsToKeep().contains(index)) {
        // Push a last PopInstruction before the last ReturnInstruction
        if (getInstructionPopMap().containsKey(index) && index == instructions.length - 1) {
          // TODO Assumed last pushed data type is 1 word sized
          slice.add(PopInstruction.make(1));
        }
        slice.add(instruction);
      } else if (getInstructionsToIgnore().contains(index)) {
        slice.add(
            InvokeInstruction.make(
                "()V", Util.makeType(Nothing.class), "doNothing", Dispatch.STATIC));
      }
      if (getInstructionPopMap().containsKey(index) && index != instructions.length - 1) {
        slice.add(PopInstruction.make(getInstructionPopMap().get(index)));
      }
    }
    return slice;
  }

  // TODO Extract me to Utilities
  public String toJavaSource() throws IOException, InvalidClassFileException {
    StringBuilder javaSource = new StringBuilder();
    javaSource.append("Arrays.asList(\n");
    List<IInstruction> slice = getSlice();
    for (int index = 0; index < slice.size(); index++) {
      IInstruction instruction = slice.get(index);
      String instructionSource = null;

      if (instruction instanceof LoadInstruction) {
        //				LoadInstruction.make(type, index)
        LoadInstruction instruction2 = (LoadInstruction) instruction;
        instructionSource =
            String.format(
                "LoadInstruction.make(%s, %s)",
                Utilities.typeToConstantFieldSource(instruction2.getType()),
                instruction2.getVarIndex());
      } else if (instruction instanceof StoreInstruction) {
        //				StoreInstruction.make(type, index)
        StoreInstruction instruction2 = (StoreInstruction) instruction;
        instructionSource =
            String.format(
                "StoreInstruction.make(%s, %s)",
                Utilities.typeToConstantFieldSource(instruction2.getType()),
                instruction2.getVarIndex());
      } else if (instruction instanceof BinaryOpInstruction) {
        //				BinaryOpInstruction.make(type, operator)
        BinaryOpInstruction instruction2 = (BinaryOpInstruction) instruction;
        instructionSource =
            String.format(
                "BinaryOpInstruction.make(%s, IBinaryOpInstruction.Operator.%s)",
                Utilities.typeToConstantFieldSource(instruction2.getType()),
                instruction2.getOperator().name());
      } else if (instruction instanceof ConditionalBranchInstruction) {
        //				ConditionalBranchInstruction.make(arg0, arg1, arg2)
        ConditionalBranchInstruction instruction2 = (ConditionalBranchInstruction) instruction;
        instructionSource =
            String.format(
                "ConditionalBranchInstruction.make(%s, ConditionalBranchInstruction.Operator.%s, %s)",
                Utilities.typeToConstantFieldSource(instruction2.getType()),
                instruction2.getOperator().name(),
                instruction2.getTarget());
      } else if (instruction instanceof GetInstruction) {
        //				GetInstruction.make(type, className, fieldName, isStatic)
        GetInstruction instruction2 = (GetInstruction) instruction;
        // TODO Check parameters
        instructionSource =
            String.format(
                "GetInstruction.make(%s, \"%s\", \"%s\", %s)",
                Utilities.typeToConstantFieldSource(instruction2.getFieldType()),
                instruction2.getClassType(),
                instruction2.getFieldName(),
                instruction2.isStatic());
      } else if (instruction instanceof InvokeInstruction) {
        //				InvokeInstruction.make(type, className, methodName, mode)
        InvokeInstruction instruction2 = (InvokeInstruction) instruction;
        TypeName returnTypeName =
            StringStuff.parseForReturnTypeName(instruction2.getMethodSignature());
        // TODO Check parameters
        instructionSource =
            String.format(
                "InvokeInstruction.make(%s, %s, %s, Dispatch.%s)",
                "\"" + instruction2.getMethodSignature() + "\"",
                "\"" + instruction2.getClassType() + "\"",
                "\"" + instruction2.getMethodName() + "\"",
                Dispatch.valueOf(instruction2.getInvocationModeString()));
      } else if (instruction instanceof InvokeDynamicInstruction) {
        InvokeDynamicInstruction instruction2 = (InvokeDynamicInstruction) instruction;
        // TODO How to create an InvokeDynamicInstruction from external? Only
        // package-visible methods are available.
        throw new UnexpectedException(
            "Cannot create instruction source for InvokeDynamicInstruction");
        //				InvokeDynamicInstruction.make(null, index, index);
        //				InvokeDynamicInstruction instruction2 = (InvokeDynamicInstruction) instruction;
        //				TypeName returnTypeName =
        // StringStuff.parseForReturnTypeName(instruction2.getMethodSignature());
        //				instructionSource = String.format("new InvokeDynamicInstruction(%s, %s, %s, %s)",
        //						instruction2.getOpcode(), instruction2.getBootstrap(), null,
        //						Utilities.typeToConstantFieldSource(returnTypeName.toString()));
        //				new InvokeDynamicInstruction(opcode, bootstrap, methodName, methodType)
      } else if (instruction instanceof ConstantInstruction) {
        ConstantInstruction instruction2 = (ConstantInstruction) instruction;
        instructionSource = "ConstantInstruction.";
        TypeName typeName = TypeName.findOrCreate(instruction2.getType());
        if (typeName.isPrimitiveType()) {
          if (instruction2.getType().equals(Constants.TYPE_int)) {
            // Default data type is always int
            instructionSource += "make(" + instruction2.getValue();
          } else if (instruction2.getType().equals(Constants.TYPE_long)) {
            instructionSource += "make(" + instruction2.getValue() + "L";
          } else {
            instructionSource += "make(" + instruction2.getValue() + instruction2.getType();
          }
        } else if (typeName.toString().equals(Constants.TYPE_String)) {
          instructionSource += "makeString(" + instruction2.getValue();
        }
        instructionSource += ")";
      } else if (instruction instanceof ConversionInstruction) {
        ConversionInstruction instruction2 = (ConversionInstruction) instruction;
        //				ConversionInstruction.make(fromType, toType)
        instructionSource =
            String.format(
                "ConversionInstruction.make(%s, %s)",
                Utilities.typeToConstantFieldSource(instruction2.getFromType()),
                Utilities.typeToConstantFieldSource(instruction2.getToType()));
      } else if (instruction instanceof ArrayLoadInstruction) {
        //				ArrayLoadInstruction.make(type)
        ArrayLoadInstruction instruction2 = (ArrayLoadInstruction) instruction;
        instructionSource =
            String.format(
                "ArrayLoadInstruction.make(%s)",
                Utilities.typeToConstantFieldSource(instruction2.getType()));
      } else if (instruction instanceof GotoInstruction) {
        //				GotoInstruction.make(label)
        GotoInstruction instruction2 = (GotoInstruction) instruction;
        instructionSource = String.format("GotoInstruction.make(%s)", instruction2.getLabel());
      } else if (instruction instanceof ArrayLengthInstruction) {
        //				ArrayLengthInstruction.make();
        //				ArrayLengthInstruction instruction2 = (ArrayLengthInstruction) instruction;
        instructionSource = "ArrayLengthInstruction.make()";
      } else if (instruction instanceof PopInstruction) {
        //				PopInstruction.make(size)
        PopInstruction instruction2 = (PopInstruction) instruction;
        instructionSource = String.format("PopInstruction.make(%s)", instruction2.getPoppedCount());
      } else if (instruction instanceof ReturnInstruction) {
        //				ReturnInstruction.make(type)
        ReturnInstruction instruction2 = (ReturnInstruction) instruction;
        instructionSource =
            String.format(
                "ReturnInstruction.make(%s)",
                Utilities.typeToConstantFieldSource(instruction2.getType()));
      } else if (instruction instanceof NewInstruction) {
        //				NewInstruction.make(type, arrayBoundsCount)
        NewInstruction instruction2 = (NewInstruction) instruction;
        instructionSource =
            String.format(
                "NewInstruction.make(%s, %s)",
                Utilities.typeToConstantFieldSource(instruction2.getType()),
                instruction2.getArrayBoundsCount());
      } else if (instruction instanceof DupInstruction) {
        //				DupInstruction.make(delta)
        DupInstruction instruction2 = (DupInstruction) instruction;
        instructionSource = String.format("DupInstruction.make(%s)", instruction2.getDelta());
      } else if (instruction instanceof IComparisonInstruction) {
        //				ComparisonInstruction.make(type, operator)
        ComparisonInstruction instruction2 = (ComparisonInstruction) instruction;
        instructionSource =
            String.format(
                "ComparisonInstruction.make(%s, %s)",
                Utilities.typeToConstantFieldSource(instruction2.getType()),
                "IComparisonInstruction.Operator." + instruction2.getOperator().name());
      } else {
        throw new UnimplementedError("Unhandled instruction type " + instruction);
      }

      javaSource.append("  " + instructionSource.toString());
      if (index != slice.size() - 1) {
        javaSource.append(",");
      }
      javaSource.append("\n");
    }
    javaSource.append(")");
    return javaSource.toString();
  }

  @Override
  public String toString() {
    try {
      StringBuilder builder = new StringBuilder();

      builder.append(getMethodSignature());
      builder.append("\n");
      builder.append("InstructionIndexes: " + getInstructionIndex() + "\n");
      builder.append("InstructionIndexesToKeep: " + getInstructionsToKeep() + "\n");
      builder.append("instructionIndexesToIgnore: " + getInstructionsToIgnore() + "\n");
      builder.append("instructionPopMap: " + getInstructionPopMap() + "\n");
      builder.append(
          "VarIndexesToRenumber: " + argumentDependency.getVarIndexesToRenumber() + "\n");

      IInstruction[] instructions = getControlFlow().getMethodData().getInstructions();
      int padding = (instructions.length / 10) + 1;

      builder.append("\n");
      builder.append("=== Slice ===" + "\n");
      List<IInstruction> slice = getSlice();
      for (int index = 0; index < slice.size(); index++) {
        IInstruction instruction = slice.get(index);

        String str = String.format("%" + padding + "s", index);
        builder.append(str + ": " + instruction + "\n");
      }
      return builder.toString();
    } catch (IOException | InvalidClassFileException e) {
      e.printStackTrace();
    }
    return "SliceResult [empty]";
  }

  public ControlFlow getControlFlow() {
    return controlFlow;
  }
}
