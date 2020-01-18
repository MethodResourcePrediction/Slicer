package de.rherzog.master.thesis.slicer;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.codec.DecoderException;
import org.apache.commons.io.IOUtils;
import org.jgrapht.graph.DefaultEdge;

import com.ibm.wala.ipa.cha.ClassHierarchyException;
import com.ibm.wala.shrikeBT.IInstruction;
import com.ibm.wala.shrikeCT.InvalidClassFileException;
import com.ibm.wala.util.CancelException;

import de.rherzog.master.thesis.slicer.instrumenter.export.SliceWriter.ExportFormat;

public class MySlicer {
	private String inputJar;
	private String outputJar;
	private String methodSignature;
	private String mainClass;
	private String resultFilePath;
	private Set<Integer> instructionIndexes;
	private ExportFormat exportFormat;
	private String additionalJarsPath;

	public MySlicer() {
		this.instructionIndexes = new HashSet<>();
	}

	public static void main(String[] args)
			throws ParseException, IOException, ClassHierarchyException, IllegalArgumentException, CancelException,
			IllegalStateException, DecoderException, InvalidClassFileException, InterruptedException {
		MySlicer mySlicer = new MySlicer();
		mySlicer.parseArgs(args);
		mySlicer.makeSlicedFile();
	}

	public String makeSlicedFile() throws IOException, ClassHierarchyException, IllegalArgumentException,
			CancelException, IllegalStateException, DecoderException, InvalidClassFileException, InterruptedException {
		Set<Integer> instructionIndexesToKeep = getInstructionIndexesToKeep();
//		System.out.println("InstructionIndexesToKeep: " + instructionIndexesToKeep);

		// Instrument a new program with a modified method which we analyze
		Instrumenter instrumenter = new Instrumenter(additionalJarsPath, inputJar, outputJar, methodSignature,
				mainClass, resultFilePath, exportFormat);
		instrumenter.instrument(instructionIndexes, instructionIndexesToKeep, Collections.emptySet());
		instrumenter.finalize();

		return outputJar;
	}

	public Set<Integer> getInstructionIndexesToKeep() throws ClassHierarchyException, IllegalArgumentException,
			IOException, CancelException, InvalidClassFileException, InterruptedException {
		ControlFlow controlFlow = new ControlFlow(inputJar, methodSignature);
		ControlDependency controlDependency = new ControlDependency(controlFlow);
		BlockDependency blocks = new BlockDependency(controlFlow);
		DataDependency dataDependency = new DataDependency(controlFlow);

		final Path dir = Files.createTempDirectory("slicer-");
		dotShow(dir, controlFlow.dotPrint());
		dotShow(dir, controlDependency.dotPrint());
		dotShow(dir, blocks.dotPrint());
		dotShow(dir, dataDependency.dotPrint());

		Set<Integer> indexesToKeep = new HashSet<>();
		for (int instructionIndex : instructionIndexes) {
			slice(controlFlow, controlDependency, blocks, dataDependency, indexesToKeep, instructionIndex);
		}
		// Add last return instruction
		IInstruction[] instructions = controlFlow.getMethodData().getInstructions();
		indexesToKeep.add(instructions.length - 1);

		return indexesToKeep;
	}

	private static void dotShow(Path dir, String dotPrint) throws IOException, InterruptedException {
		final String format = "png";
		final String path = Files.createTempFile(dir, "slicer-", "." + format).toFile().getPath();

		ProcessBuilder builder = new ProcessBuilder("dot", "-T" + format, "-o" + path);
		Process process = builder.start();
		OutputStream outputStream = process.getOutputStream();

		IOUtils.write(dotPrint, outputStream, Charset.defaultCharset());
		outputStream.close();

		process.waitFor();

		builder = new ProcessBuilder("xdg-open", path);
		builder.start().waitFor();
	}

	private void slice(ControlFlow controlFlow, ControlDependency controlDependency, BlockDependency blocks,
			DataDependency dataDependency, Set<Integer> dependendInstructions, int index)
			throws IOException, InvalidClassFileException {
		Block block = blocks.getBlockForIndex(index);

		boolean addedAny = false;
		for (int blockInstructionIndex : block.getInstructions().keySet()) {
			// Prevent whole block adding
			if (blockInstructionIndex > index) {
				continue;
			}

			// Add cycle end (goto)
			// TODO Or just if we detect a ConditionalBranchInstruction?
			List<List<Integer>> cycles = controlFlow.getCyclesForInstruction(blockInstructionIndex);
			for (List<Integer> cycle : cycles) {
				if (cycle.get(0) == blockInstructionIndex) {
					// Starting index for loop
					addedAny |= dependendInstructions.add(cycle.get(cycle.size() - 1));
				}
			}
			addedAny |= dependendInstructions.add(blockInstructionIndex);
		}
		if (!addedAny) {
			return;
		}
//		System.out.println(block);

		for (int blockInstructionIndex : block.getInstructions().keySet()) {
			// Consider data dependencies
			for (DefaultEdge edge : dataDependency.getGraph().outgoingEdgesOf(blockInstructionIndex)) {
				int edgeTarget = controlDependency.getGraph().getEdgeTarget(edge);
				slice(controlFlow, controlDependency, blocks, dataDependency, dependendInstructions, edgeTarget);
			}
			// Consider control dependencies
			for (DefaultEdge edge : controlDependency.getGraph().incomingEdgesOf(blockInstructionIndex)) {
				int edgeSource = controlDependency.getGraph().getEdgeSource(edge);
				if (edgeSource == -1) {
					continue;
				}
				slice(controlFlow, controlDependency, blocks, dataDependency, dependendInstructions, edgeSource);
			}
		}
	}

	public void parseArgs(String[] args) throws ParseException {
		Options options = new Options();
		options.addOption("in", "inputJar", true,
				"path to java application (jar) [Default: \"../exampleprogram.jar\"]");
		options.addOption("out", "outputJar", true,
				"path to the sliced java application (jar) [Default: \"sliced.jar\"]");
		options.addOption("ms", "methodSignature", true, "methodSignature");
		options.addOption("mc", "mainClass", true, "");
		options.addOption("ii", "instructionIndexes", true, "instructionIndexes [int,int,...]");
		options.addOption("ef", "exportFormat", true, "export format [CSV,XML] default: XML");
		options.addOption("rf", "resultFilePath", true, "path to saved result file [result.xml]");
		options.addOption("ajp", "additionalJarsPath", true,
				"path where the additional jars are stored [Default: ../]");

		CommandLineParser parser = new DefaultParser();
		CommandLine cmd = parser.parse(options, args);

		inputJar = cmd.getOptionValue("inputJar");
		Objects.requireNonNull(inputJar, "-in/--inputJar must be set");

		outputJar = cmd.getOptionValue("outputJar", "sliced.jar");
		resultFilePath = cmd.getOptionValue("resultFilePath", "result.xml");
		additionalJarsPath = cmd.getOptionValue("additionalJarsPath", "../");

		methodSignature = cmd.getOptionValue("methodSignature");
		Objects.requireNonNull(methodSignature, "-ms/--methodSignature must be set");

		mainClass = cmd.getOptionValue("mainClass");
		Objects.requireNonNull(mainClass, "-mc/--mainClass must be set");

		String exportFormatStr = cmd.getOptionValue("exportFormat", "XML");
		try {
			setExportFormat(ExportFormat.valueOf(exportFormatStr));
		} catch (IllegalArgumentException e) {
			throw new IllegalArgumentException("Unknown --exportFormat '" + exportFormatStr + "'");
		}

		// Support multiple instruction indexes (a feature may consist out of many)
		String instructionIndexesStr = cmd.getOptionValue("instructionIndexes");
		Objects.requireNonNull(instructionIndexesStr, "-ii/--instructionIndexes must be set");
		for (String instructionIndexStr : instructionIndexesStr.split(",")) {
			int instructionIndex = Integer.parseInt(instructionIndexStr);
			instructionIndexes.add(instructionIndex);
		}
	}

	public String getInputJar() {
		return inputJar;
	}

	public void setInputJar(String inputJar) {
		this.inputJar = inputJar;
	}

	public String getOutputJar() {
		return outputJar;
	}

	public void setOutputJar(String outputJar) {
		this.outputJar = outputJar;
	}

	public String getMethodSignature() {
		return methodSignature;
	}

	public void setMethodSignature(String methodSignature) {
		this.methodSignature = methodSignature;
	}

	public Set<Integer> getInstructionIndexes() {
		return instructionIndexes;
	}

	public void setInstructionIndexes(Set<Integer> instructionIndexes) {
		this.instructionIndexes = instructionIndexes;
	}

	public String getResultFilePath() {
		return resultFilePath;
	}

	public void setResultFilePath(String resultFilePath) {
		this.resultFilePath = resultFilePath;
	}

	public ExportFormat getExportFormat() {
		return exportFormat;
	}

	public void setExportFormat(ExportFormat exportFormat) {
		this.exportFormat = exportFormat;
	}

	public String getAdditionalJarsPath() {
		return additionalJarsPath;
	}

	public void setAdditionalJarsPath(String additionalJarsPath) {
		this.additionalJarsPath = additionalJarsPath;
	}

	public String getMainClass() {
		return mainClass;
	}

	public void setMainClass(String mainClass) {
		this.mainClass = mainClass;
	}
}
