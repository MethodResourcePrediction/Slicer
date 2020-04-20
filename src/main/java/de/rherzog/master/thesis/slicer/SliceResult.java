package de.rherzog.master.thesis.slicer;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.ibm.wala.shrikeBT.ArrayLengthInstruction;
import com.ibm.wala.shrikeBT.ArrayLoadInstruction;
import com.ibm.wala.shrikeBT.BinaryOpInstruction;
import com.ibm.wala.shrikeBT.ConditionalBranchInstruction;
import com.ibm.wala.shrikeBT.ConstantInstruction;
import com.ibm.wala.shrikeBT.Constants;
import com.ibm.wala.shrikeBT.ConversionInstruction;
import com.ibm.wala.shrikeBT.DupInstruction;
import com.ibm.wala.shrikeBT.GetInstruction;
import com.ibm.wala.shrikeBT.GotoInstruction;
import com.ibm.wala.shrikeBT.IInstruction;
import com.ibm.wala.shrikeBT.InvokeDynamicInstruction;
import com.ibm.wala.shrikeBT.InvokeInstruction;
import com.ibm.wala.shrikeBT.LoadInstruction;
import com.ibm.wala.shrikeBT.NewInstruction;
import com.ibm.wala.shrikeBT.PopInstruction;
import com.ibm.wala.shrikeBT.ReturnInstruction;
import com.ibm.wala.shrikeBT.StoreInstruction;
import com.ibm.wala.shrikeCT.InvalidClassFileException;
import com.ibm.wala.types.TypeName;
import com.ibm.wala.util.strings.StringStuff;

import de.rherzog.master.thesis.utils.Utilities;

public class SliceResult {
	private String methodSignature;
	private Set<Integer> instructionIndex, instructionsToKeep, instructionsToIgnore;
	private Map<Integer, Integer> instructionPopMap;
	private ControlFlow controlFlow;

	public SliceResult(String methodSignature, Set<Integer> instructionIndex, Set<Integer> instructionsToKeep,
			Set<Integer> instructionsToIgnore, Map<Integer, Integer> instructionPopMap, ControlFlow controlFlow) {
		this.methodSignature = methodSignature;
		this.instructionIndex = instructionIndex;
		this.instructionsToKeep = instructionsToKeep;
		this.instructionsToIgnore = instructionsToIgnore;
		this.instructionPopMap = instructionPopMap;
		this.controlFlow = controlFlow;
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
		return new SliceResult(null, Collections.emptySet(), Collections.emptySet(), Collections.emptySet(),
				Collections.emptyMap(), null);
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
				slice.add(instruction);
			}
			if (getInstructionPopMap().containsKey(index) && index != instructions.length - 1) {
				for (int popCount = 0; popCount < getInstructionPopMap().get(index); popCount++) {
					// TODO Assumed last pushed data type is 1 word sized
					slice.add(PopInstruction.make(1));
				}
			}
		}
		return slice;
	}

	public String toJavaSource() throws IOException, InvalidClassFileException {
		StringBuilder javaSource = new StringBuilder();
		javaSource.append("Arrays.asList(\n");
		for (IInstruction instruction : getSlice()) {
			String instructionSource = "";

			if (instruction instanceof LoadInstruction) {
				LoadInstruction instruction2 = (LoadInstruction) instruction;
				instructionSource = String.format("LoadInstruction.make(%s, %s)",
						Utilities.typeToConstantFieldSource(instruction2.getType()), instruction2.getVarIndex());
			} else if (instruction instanceof StoreInstruction) {
				StoreInstruction instruction2 = (StoreInstruction) instruction;
				instructionSource = String.format("StoreInstruction.make(%s, %s)",
						Utilities.typeToConstantFieldSource(instruction2.getType()), instruction2.getVarIndex());
			} else if (instruction instanceof BinaryOpInstruction) {
				BinaryOpInstruction instruction2 = (BinaryOpInstruction) instruction;
			} else if (instruction instanceof ConditionalBranchInstruction) {
				ConditionalBranchInstruction instruction2 = (ConditionalBranchInstruction) instruction;
			} else if (instruction instanceof GetInstruction) {
				GetInstruction instruction2 = (GetInstruction) instruction;
			} else if (instruction instanceof InvokeInstruction) {
				InvokeInstruction instruction2 = (InvokeInstruction) instruction;
				TypeName typeName = StringStuff.parseForReturnTypeName(instruction2.getMethodSignature());
			} else if (instruction instanceof InvokeDynamicInstruction) {
				InvokeDynamicInstruction instruction2 = (InvokeDynamicInstruction) instruction;
				TypeName typeName = StringStuff.parseForReturnTypeName(instruction2.getMethodSignature());
			} else if (instruction instanceof ConstantInstruction) {
				ConstantInstruction instruction2 = (ConstantInstruction) instruction;
				instructionSource = "ConstantInstruction.";
				TypeName typeName = TypeName.findOrCreate(instruction2.getType());
				if (typeName.isPrimitiveType()) {
					if (instruction2.getType().equals(Constants.TYPE_int)) {
						// Default data type is always int
						instructionSource += "make(" + instruction2.getValue();
					} else {
						instructionSource += "make(" + instruction2.getValue() + instruction2.getType();
					}
				} else if (typeName.toString().equals(Constants.TYPE_String)) {
					instructionSource += "makeString(" + instruction2.getValue();
				}
				instructionSource += ")";
			} else if (instruction instanceof ConversionInstruction) {
				ConversionInstruction instruction2 = (ConversionInstruction) instruction;
			} else if (instruction instanceof ArrayLoadInstruction) {
				ArrayLoadInstruction instruction2 = (ArrayLoadInstruction) instruction;
			} else if (instruction instanceof GotoInstruction) {
				GotoInstruction instruction2 = (GotoInstruction) instruction;
			} else if (instruction instanceof ArrayLengthInstruction) {
				ArrayLengthInstruction instruction2 = (ArrayLengthInstruction) instruction;
			} else if (instruction instanceof PopInstruction) {
				PopInstruction instruction2 = (PopInstruction) instruction;
			} else if (instruction instanceof ReturnInstruction) {
				ReturnInstruction instruction2 = (ReturnInstruction) instruction;
			} else if (instruction instanceof NewInstruction) {
				NewInstruction instruction2 = (NewInstruction) instruction;
			} else if (instruction instanceof DupInstruction) {
				DupInstruction instruction2 = (DupInstruction) instruction;
			}

			javaSource.append("  " + instructionSource.toString() + ",\n");
		}
		javaSource.append(");\n");
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
			builder.append("VarIndexesToRenumber: " + getControlFlow().getVarIndexesToRenumber() + "\n");

			IInstruction[] instructions = getControlFlow().getMethodData().getInstructions();
			int padding = instructions.length / 10;

			builder.append("\n");
			builder.append("=== Slice ===" + "\n");
			for (int index = 0; index < instructions.length; index++) {
				IInstruction instruction = instructions[index];

				String str = String.format("%" + padding + "s", index);
				if (getInstructionsToKeep().contains(index)) {
					builder.append(str + ": " + instruction + "\n");
				} else if (getInstructionsToIgnore().contains(index)) {
					builder.append(str + ": " + instruction + " (IGNORED)" + "\n");
				}
				if (instructionPopMap.containsKey(index)) {
					for (int popCount = 0; popCount < instructionPopMap.get(index); popCount++) {
						builder.append(str + ": " + PopInstruction.make(1) + " (ADDITIONAL)" + "\n");
					}
				}
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
