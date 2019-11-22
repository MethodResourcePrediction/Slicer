package de.rherzog.master.thesis.slicer;

import java.io.IOException;
import java.util.ArrayList;
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

import com.ibm.wala.classLoader.Language;
import com.ibm.wala.ipa.callgraph.AnalysisCacheImpl;
import com.ibm.wala.ipa.callgraph.AnalysisOptions;
import com.ibm.wala.ipa.callgraph.AnalysisScope;
import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.callgraph.CallGraph;
import com.ibm.wala.ipa.callgraph.CallGraphBuilder;
import com.ibm.wala.ipa.callgraph.CallGraphBuilderCancelException;
import com.ibm.wala.ipa.callgraph.Entrypoint;
import com.ibm.wala.ipa.callgraph.impl.Util;
import com.ibm.wala.ipa.callgraph.propagation.InstanceKey;
import com.ibm.wala.ipa.cha.ClassHierarchy;
import com.ibm.wala.ipa.cha.ClassHierarchyException;
import com.ibm.wala.ipa.cha.ClassHierarchyFactory;
import com.ibm.wala.shrikeCT.InvalidClassFileException;
import com.ibm.wala.ssa.ISSABasicBlock;
import com.ibm.wala.ssa.SSACFG;
import com.ibm.wala.ssa.SSACFG.BasicBlock;
import com.ibm.wala.ssa.SSAConditionalBranchInstruction;
import com.ibm.wala.ssa.SSAGotoInstruction;
import com.ibm.wala.types.MethodReference;
import com.ibm.wala.util.CancelException;
import com.ibm.wala.util.config.AnalysisScopeReader;
import com.ibm.wala.util.io.FileProvider;
import com.ibm.wala.util.strings.StringStuff;

import de.rherzog.master.thesis.utils.InstrumenterComparator;

public class MySlicer {
	public final static String REGRESSION_EXCLUSIONS = "Java60RegressionExclusions.txt";

	public enum ExportFormat {
		CSV, XML
	}

	private String inputJar;
	private String outputJar;
	private String methodSignature;
	private String mainClass;
	private String resultFilePath;
	private List<Integer> instructionIndexes;
	private ExportFormat exportFormat;
	private String additionalJarsPath;

	// Internal
	private CGNode methodCGNode;

	public MySlicer() {
		this.instructionIndexes = new ArrayList<>();
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
		Set<Integer> instructionIndexesToIgnore = getInstructionIndexesToIgnore();
//		System.out.println("InstructionIndexesToKeep: " + instructionIndexesToKeep);

		// Instrument a new program with a modified method which we analyze
		Instrumenter instrumenter = new Instrumenter(additionalJarsPath, inputJar, outputJar, methodSignature,
				mainClass, resultFilePath, exportFormat);
		instrumenter.instrument(instructionIndexes, instructionIndexesToKeep, instructionIndexesToIgnore);
		instrumenter.finalize();

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

	public List<BasicBlockWithInstructionIndex> getInstructionIndexBlocks(CGNode callerNode) {
		// Get all blocks with features we want to slice
		List<BasicBlockWithInstructionIndex> blocks = new ArrayList<>();
		for (Integer instructionIndex : instructionIndexes) {
			BasicBlock block = callerNode.getIR().getControlFlowGraph().getBlockForInstruction(instructionIndex);
			blocks.add(new BasicBlockWithInstructionIndex(block, instructionIndex));
		}
		return blocks;
	}

	// TODO This method is very redundant to getInstructionIndexesToKeep()
	public Set<Integer> getInstructionIndexesToIgnore()
			throws ClassHierarchyException, IllegalArgumentException, CallGraphBuilderCancelException, IOException {
		CGNode callerNode = getCallerNode();
		List<BasicBlockWithInstructionIndex> blocks = getInstructionIndexBlocks(callerNode);
		return getInstructionIndexesToIgnore(callerNode, blocks);
	}

	// TODO This method is very redundant to getInstructionIndexesToKeep()
	private Set<Integer> getInstructionIndexesToIgnore(CGNode callerNode, List<BasicBlockWithInstructionIndex> blocks) {
		Set<Integer> instructionIndexesToIgnore = new HashSet<>();

		for (BasicBlockWithInstructionIndex basicBlockWithInstructionIndex : blocks) {
			BasicBlock basicBlock = basicBlockWithInstructionIndex.getBasicBlock();
			int instructionIndex = basicBlockWithInstructionIndex.getInstructionIndex();

			// Get backwards-sliced blocks for our statement
			SliceResult sliceResult = slice(callerNode, basicBlock, blocks);
			for (ISSABasicBlock issaBlock : sliceResult.getIgnoredBlocks()) {
				// We want to keep all instruction indexes which are inside a block
				for (int index = issaBlock.getFirstInstructionIndex(); index <= issaBlock
						.getLastInstructionIndex(); index++) {
					// If the block around our instruction index contains more statements, we want
					// to exclude them

					// TODO This filtering is far to complex... simplify this somehow (it was
					// necessary for filtering instruction indexes inside blocks which are supplied
					// multiple times; like when "2,3" is given and belong to the same block
					BasicBlockWithInstructionIndex searchedBlock = findBlockWithInstructionIndex(issaBlock, blocks);
					if (searchedBlock != null && issaBlock.equals(searchedBlock.getBasicBlock())
							&& index > instructionIndex) {
						break;
					} else {
						if (issaBlock.equals(basicBlock) && index > instructionIndex) {
							break;
						}
					}
					instructionIndexesToIgnore.add(index);
				}
			}
		}
		return instructionIndexesToIgnore;
	}

	public Set<Integer> getInstructionIndexesToKeep()
			throws ClassHierarchyException, IllegalArgumentException, CallGraphBuilderCancelException, IOException {
		CGNode callerNode = getCallerNode();
		List<BasicBlockWithInstructionIndex> blocks = getInstructionIndexBlocks(callerNode);
		return getInstructionIndexesToKeep(callerNode, blocks);
	}

	// TODO This method is very redundant to getInstructionIndexesToIgnore()
	private Set<Integer> getInstructionIndexesToKeep(CGNode callerNode, List<BasicBlockWithInstructionIndex> blocks) {
		Set<Integer> instructionIndexesToKeep = new HashSet<>();

		// All these blocks which we are iterating should be kept (more precise: the
		// extra instruction index) saved in the object
		for (BasicBlockWithInstructionIndex basicBlockWithInstructionIndex : blocks) {
			BasicBlock basicBlock = basicBlockWithInstructionIndex.getBasicBlock();
			int instructionIndex = basicBlockWithInstructionIndex.getInstructionIndex();

			// Get backwards-sliced blocks for our statement
			SliceResult sliceResult = slice(callerNode, basicBlock, blocks);
			for (ISSABasicBlock issaBlock : sliceResult.getNormalBlocks()) {
				// We want to keep all instruction indexes which are inside a block
				for (int index = issaBlock.getFirstInstructionIndex(); index <= issaBlock
						.getLastInstructionIndex(); index++) {
					// If the block around our instruction index contains more statements, we want
					// to exclude them

					// TODO This filtering is far to complex... simplify this somehow (it was
					// necessary for filtering instruction indexes inside blocks which are supplied
					// multiple times; like when "2,3" is given and belong to the same block
					BasicBlockWithInstructionIndex searchedBlock = findBlockWithInstructionIndex(issaBlock, blocks);
					if (searchedBlock != null && issaBlock.equals(searchedBlock.getBasicBlock())
							&& index > instructionIndex) {
						break;
					} else {
						if (issaBlock.equals(basicBlock) && index > instructionIndex) {
							break;
						}
					}
					instructionIndexesToKeep.add(index);
				}
			}
		}
		return instructionIndexesToKeep;
	}

	public CGNode getCallerNode()
			throws IOException, ClassHierarchyException, IllegalArgumentException, CallGraphBuilderCancelException {
		// Return the method CGNode if already processed
		// TODO A bit error-prone since it assumes the inputJar and methodeSignature
		// does not change. Fix later
		if (methodCGNode != null) {
			return methodCGNode;
		}

		String methodSignature = this.methodSignature.startsWith("L") ? this.methodSignature.substring(1)
				: this.methodSignature;
		MethodReference methodReference = StringStuff.makeMethodReference(methodSignature);

		String clazz = methodReference.getDeclaringClass().getName().toString();
		clazz = clazz.endsWith(";") ? clazz.substring(0, clazz.length() - 1) : clazz;

		// create an analysis scope representing the appJar as a J2SE application
		AnalysisScope scope = AnalysisScopeReader.makeJavaBinaryAnalysisScope(inputJar,
				(new FileProvider()).getFile(REGRESSION_EXCLUSIONS));

		// build a class hierarchy, call graph, and system dependence graph
		ClassHierarchy cha = ClassHierarchyFactory.make(scope);
		Iterable<Entrypoint> entrypoints = Util.makeMainEntrypoints(scope, cha, clazz);

		AnalysisOptions options = new AnalysisOptions(scope, entrypoints);
		CallGraphBuilder<InstanceKey> builder = Util.makeVanillaZeroOneCFABuilder(Language.JAVA, options,
				new AnalysisCacheImpl(), cha, scope);

		// CallGraphBuilder builder = Util.makeZeroOneCFABuilder(options, new
		// AnalysisCache(), cha, scope);
		CallGraph cg = builder.makeCallGraph(options, null);
//		SDG<InstanceKey> sdg = new SDG<>(cg, builder.getPointerAnalysis(), dOptions, cOptions);

		// find the call statement of interest
		CGNode callerNode = findMethod(cg, methodSignature);
		this.methodCGNode = callerNode;
		return methodCGNode;
	}

	private SliceResult slice(CGNode callerNode, BasicBlock block, List<BasicBlockWithInstructionIndex> blocksWII) {
		SSACFG controlFlowGraph = callerNode.getIR().getControlFlowGraph();

		SliceResult sliceResult = SliceResult.emptyResult();
		sliceBackwards(controlFlowGraph, sliceResult, block, blocksWII, false);
		return sliceResult;
	}

	private void sliceBackwards(SSACFG controlFlowGraph, SliceResult sliceResult, ISSABasicBlock block,
			List<BasicBlockWithInstructionIndex> blocksWII, boolean ignore) {
		Set<ISSABasicBlock> blocks = sliceResult.getNormalBlocks();
		Set<ISSABasicBlock> ignoredBlocks = sliceResult.getIgnoredBlocks();

		if (blocks.contains(block)) {
			if (!ignore) {
				ignoredBlocks.remove(block);
			}
			return;
		}
		if (ignore) {
			ignoredBlocks.add(block);
		}
		blocks.add(block);

		if (!block.isEntryBlock()) {
			if (block.getLastInstruction() instanceof SSAConditionalBranchInstruction) {
				SSAConditionalBranchInstruction instruction = (SSAConditionalBranchInstruction) block
						.getLastInstruction();
				BasicBlock targetBlock = controlFlowGraph.getBlockForInstruction(instruction.getTarget());
//				BasicBlockWithInstructionIndex myBlock = findBlockWithInstructionIndex(block, blocksWII);

				sliceBackwards(controlFlowGraph, sliceResult, targetBlock, blocksWII, true);
//				if (myBlock == null) {
//					sliceBackwards(controlFlowGraph, sliceResult, targetBlock, blocksWII, true);
//				} else if (myBlock.getInstructionIndex() >= instruction.iindex) {
//					sliceBackwards(controlFlowGraph, sliceResult, targetBlock, blocksWII, true);
//				}
			}
			if (block.getLastInstruction() instanceof SSAGotoInstruction) {
				SSAGotoInstruction instruction = (SSAGotoInstruction) block.getLastInstruction();
				BasicBlock targetBlock = controlFlowGraph.getBlockForInstruction(instruction.getTarget());
				BasicBlockWithInstructionIndex myBlock = findBlockWithInstructionIndex(block, blocksWII);

				// TODO Redundant check
				if (myBlock == null) {
					sliceBackwards(controlFlowGraph, sliceResult, targetBlock, blocksWII, true);
				} else if (myBlock.getInstructionIndex() >= instruction.iIndex()) {
					sliceBackwards(controlFlowGraph, sliceResult, targetBlock, blocksWII, true);
				}
			}
		}

		for (ISSABasicBlock predecessorBlock : controlFlowGraph.getNormalPredecessors(block)) {
			sliceBackwards(controlFlowGraph, sliceResult, predecessorBlock, blocksWII, false);
		}
	}

	/**
	 * Find the block which is equal to the one in the list. The List-Block also
	 * contains the instuctionIndex which we need to limit the instructions inside
	 * the block
	 * 
	 * @param block     block to find in the list of blocks given
	 * @param blocksWII list of blocks to search in
	 * @return block with instructionIndex
	 */
	private static BasicBlockWithInstructionIndex findBlockWithInstructionIndex(ISSABasicBlock block,
			List<BasicBlockWithInstructionIndex> blocksWII) {
		// Find block for instruction if any
		BasicBlockWithInstructionIndex myBlock = null;
		for (BasicBlockWithInstructionIndex blockWII : blocksWII) {
			if (blockWII.getInstructionIndex() >= block.getFirstInstructionIndex()
					&& blockWII.getInstructionIndex() <= block.getLastInstructionIndex()) {
				if (myBlock != null && myBlock.getInstructionIndex() < blockWII.getInstructionIndex()) {
					myBlock = blockWII;
				} else {
					myBlock = blockWII;
				}
//				break;
			}
		}
		return myBlock;
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

	private CGNode findMethod(CallGraph cg, String methodSignature) {
		InstrumenterComparator comparator = InstrumenterComparator.of(methodSignature);
		for (CGNode n : cg) {
			if (comparator.equals(n.getMethod())) {
				return n;
			}
		}
		return Objects.requireNonNull(null, "method not found");
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

	public List<Integer> getInstructionIndexes() {
		return instructionIndexes;
	}

	public void setInstructionIndexes(List<Integer> instructionIndexes) {
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

//	/**
//	 * If s is a call statement, return the statement representing the normal return
//	 * from s
//	 */
//	private Statement getReturnStatementForCall(Statement s) {
//		if (s.getKind() == Kind.NORMAL) {
//			NormalStatement n = (NormalStatement) s;
//			SSAInstruction st = n.getInstruction();
//			if (st instanceof SSAInvokeInstruction) {
//				SSAAbstractInvokeInstruction call = (SSAAbstractInvokeInstruction) st;
//				if (call.getCallSite().getDeclaredTarget().getReturnType().equals(TypeReference.Void)) {
//					throw new IllegalArgumentException(
//							"this driver computes forward slices from the return value of calls.\n" + "" + "Method "
//									+ call.getCallSite().getDeclaredTarget().getSignature() + " returns void.");
//				}
//				return new NormalReturnCaller(s.getNode(), n.getInstructionIndex());
//			} else {
//				return s;
//			}
//		} else {
//			return s;
//		}
//	}

//	private void dumpSlice(Collection<Statement> slice) {
//		dumpSlice(slice, new PrintWriter(System.err));
//	}

//	private void dumpSlice(Collection<Statement> slice, PrintWriter w) {
//		w.println("SLICE:\n");
//		int i = 1;
//		for (Statement s : slice) {
//			String line = (i++) + "   " + s;
//			w.println(line);
//			w.flush();
//		}
//	}
}
