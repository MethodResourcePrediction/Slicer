package de.rherzog.master.thesis.slicer;

import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.codec.DecoderException;
import org.jgrapht.Graph;
import org.jgrapht.graph.DefaultEdge;

import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.cha.ClassHierarchyException;
import com.ibm.wala.shrikeBT.IInstruction;
import com.ibm.wala.shrikeBT.shrikeCT.OfflineInstrumenter;
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

	// Internal
	private CGNode methodCGNode;

	public MySlicer() {
		this.instructionIndexes = new HashSet<>();
	}

	public static void main(String[] args)
			throws ParseException, IOException, ClassHierarchyException, IllegalArgumentException, CancelException,
			IllegalStateException, DecoderException, InvalidClassFileException {
		MySlicer mySlicer = new MySlicer();
		mySlicer.parseArgs(args);
		mySlicer.makeSlicedFile();
	}

	public String makeSlicedFile() throws IOException, ClassHierarchyException, IllegalArgumentException,
			CancelException, IllegalStateException, DecoderException, InvalidClassFileException {
		Set<Integer> instructionIndexesToKeep = getInstructionIndexesToKeep();
//		Set<Integer> instructionIndexesToIgnore = getInstructionIndexesToIgnore();
//		System.out.println("InstructionIndexesToKeep: " + instructionIndexesToKeep);

		// Instrument a new program with a modified method which we analyze
//		Instrumenter instrumenter = new Instrumenter(additionalJarsPath, inputJar, outputJar, methodSignature,
//				mainClass, resultFilePath, exportFormat);
//		instrumenter.instrument(instructionIndexes, instructionIndexesToKeep, Collections.emptySet());
//		instrumenter.finalize();

		// ###### Uncommented due to own backward-slicer #####
//		DataDependenceOptions dOptions = DataDependenceOptions.FULL;
//		ControlDependenceOptions cOptions = ControlDependenceOptions.NO_EXCEPTIONAL_EDGES;
//		
//		// compute the slice as a collection of statements
//		final PointerAnalysis<InstanceKey> pointerAnalysis = builder.getPointerAnalysis();
//		Collection<Statement> slice = Slicer.computeBackwardSlice(s, cg, pointerAnalysis, dOptions, cOptions);
//
//		System.out.println("\nSlice:");
//		slice.stream().filter(a -> a.getNode().equals(callerNode)).forEach(a -> System.out.println(a));
//		System.out.println();
		// ###### Uncommented due to own backward-slicer #####

		return outputJar;
	}

	public Set<Integer> getInstructionIndexesToKeep() throws ClassHierarchyException, IllegalArgumentException,
			IOException, CancelException, InvalidClassFileException {
//		CGNode callerNode = getCallerNode();
//		WALASlicer slicer = new WALASlicer(callerNode, instructionIndexes);
//		Set<Integer> is = slicer.sliceBackwards();

//		WALAControlDependencySlicer slicer = new WALAControlDependencySlicer(inputJar, methodSignature, instructionIndexes);
		ControlFlow controlFlow = new ControlFlow(inputJar, methodSignature);
		Graph<Integer, DefaultEdge> controlFlowGraph = controlFlow.getGraph();

		Blocks blocks = new Blocks(controlFlow);
		Graph<Block, DefaultEdge> blockGraph = blocks.getGraph();

		DataDependency dataDependency = new DataDependency(controlFlow);
		Graph<IInstruction, DefaultEdge> dataDependencyGraph = dataDependency.getGraph();
//		Set<Integer> slice = slicer.slice();

		return Collections.emptySet();
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
