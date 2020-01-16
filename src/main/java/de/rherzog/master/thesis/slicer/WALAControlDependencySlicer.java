package de.rherzog.master.thesis.slicer;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import com.ibm.wala.classLoader.Language;
import com.ibm.wala.ipa.callgraph.AnalysisCache;
import com.ibm.wala.ipa.callgraph.AnalysisCacheImpl;
import com.ibm.wala.ipa.callgraph.AnalysisOptions;
import com.ibm.wala.ipa.callgraph.AnalysisScope;
import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.callgraph.CallGraph;
import com.ibm.wala.ipa.callgraph.CallGraphBuilder;
import com.ibm.wala.ipa.callgraph.Entrypoint;
import com.ibm.wala.ipa.callgraph.impl.Util;
import com.ibm.wala.ipa.callgraph.propagation.InstanceKey;
import com.ibm.wala.ipa.callgraph.propagation.PointerAnalysis;
import com.ibm.wala.ipa.cha.ClassHierarchy;
import com.ibm.wala.ipa.cha.ClassHierarchyException;
import com.ibm.wala.ipa.cha.ClassHierarchyFactory;
import com.ibm.wala.ipa.slicer.NormalStatement;
import com.ibm.wala.ipa.slicer.Slicer;
import com.ibm.wala.ipa.slicer.Slicer.ControlDependenceOptions;
import com.ibm.wala.ipa.slicer.Slicer.DataDependenceOptions;
import com.ibm.wala.ipa.slicer.Statement;
import com.ibm.wala.shrikeBT.MethodData;
import com.ibm.wala.shrikeCT.InvalidClassFileException;
import com.ibm.wala.ssa.ISSABasicBlock;
import com.ibm.wala.ssa.SSACFG;
import com.ibm.wala.ssa.SSACFG.BasicBlock;
import com.ibm.wala.ssa.SSAConditionalBranchInstruction;
import com.ibm.wala.ssa.SSAGotoInstruction;
import com.ibm.wala.ssa.SSAInstruction;
import com.ibm.wala.ssa.SSAInvokeInstruction;
import com.ibm.wala.ssa.SSAReturnInstruction;
import com.ibm.wala.types.MethodReference;
import com.ibm.wala.types.TypeName;
import com.ibm.wala.util.CancelException;
import com.ibm.wala.util.collections.Iterator2Iterable;
import com.ibm.wala.util.config.AnalysisScopeReader;
import com.ibm.wala.util.io.FileProvider;
import com.ibm.wala.util.strings.StringStuff;

import de.rherzog.master.thesis.utils.InstrumenterComparator;

public class WALAControlDependencySlicer {
	public final static String REGRESSION_EXCLUSIONS = "Java60RegressionExclusions.txt";

	private Set<Integer> instructionIndexes;
	private String inputPath;
	private String methodSignature;

	private MethodData md;
	private CGNode methodCGNode;

	public WALAControlDependencySlicer(String inputPath, String methodSignature, Set<Integer> instructionIndexes) {
		this.inputPath = inputPath;
		this.methodSignature = methodSignature;
		this.instructionIndexes = instructionIndexes;
	}

	public Set<Integer> slice() throws IOException, InvalidClassFileException, ClassHierarchyException,
			IllegalArgumentException, CancelException {
		CGNode callerNode = getCallerNode();
		List<BasicBlockWithInstructionIndex> blocks = getInstructionIndexBlocks(callerNode);

		return getInstructionIndexesToKeep(callerNode, blocks);
	}

	private CGNode getCallerNode()
			throws IOException, ClassHierarchyException, IllegalArgumentException, CancelException {
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
		AnalysisScope scope = AnalysisScopeReader.makeJavaBinaryAnalysisScope(inputPath,
				(new FileProvider()).getFile(REGRESSION_EXCLUSIONS));

		// build a class hierarchy, call graph, and system dependence graph
		ClassHierarchy cha = ClassHierarchyFactory.make(scope);
		Iterable<Entrypoint> entrypoints = Util.makeMainEntrypoints(scope, cha, clazz);

		AnalysisOptions options = new AnalysisOptions(scope, entrypoints);
		AnalysisCache cache = new AnalysisCacheImpl();
//		CallGraphBuilder<InstanceKey> builder = Util.makeRTABuilder(options, cache, cha, scope);
		CallGraphBuilder<InstanceKey> builder = Util.makeVanillaZeroOneCFABuilder(Language.JAVA, options, cache, cha,
				scope);
//		CallGraphBuilder<InstanceKey> builder = Util.makeZeroOneContainerCFABuilder(options, cache, cha, scope);

//		CallGraphBuilder builder = Util.makeZeroOneCFABuilder(options, new AnalysisCache(), cha, scope);
		CallGraph cg = builder.makeCallGraph(options, null);
//		SDG<InstanceKey> sdg = new SDG<>(cg, builder.getPointerAnalysis(), dOptions, cOptions);

		// find the call statement of interest
		CGNode callerNode = findMethod(cg, methodSignature);

//		Statement statement = null;
//		for (SSAInstruction instruction : Iterator2Iterable.make(callerNode.getIR().iterateAllInstructions())) {
//			if (instruction.iIndex() == 30) {
//				statement = new NormalStatement(callerNode, 30);
//				break;
//			}
//		}
//		PointerAnalysis<InstanceKey> pa = builder.getPointerAnalysis();
//		Collection<Statement> slice = Slicer.computeBackwardSlice(statement, cg, pa,
//				DataDependenceOptions.NONE, ControlDependenceOptions.FULL);
//
//		for (Statement s : slice) {
//			System.out.println(s);
//		}

		this.methodCGNode = callerNode;
		return methodCGNode;
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
			SSAInstruction lastInstruction = block.getLastInstruction();
			if (lastInstruction instanceof SSAConditionalBranchInstruction) {
				SSAConditionalBranchInstruction instruction = (SSAConditionalBranchInstruction) lastInstruction;
				BasicBlock targetBlock = controlFlowGraph.getBlockForInstruction(instruction.getTarget());
//				BasicBlockWithInstructionIndex myBlock = findBlockWithInstructionIndex(block, blocksWII);

				sliceBackwards(controlFlowGraph, sliceResult, targetBlock, blocksWII, true);
//				if (myBlock == null) {
//					sliceBackwards(controlFlowGraph, sliceResult, targetBlock, blocksWII, true);
//				} else if (myBlock.getInstructionIndex() >= instruction.iindex) {
//					sliceBackwards(controlFlowGraph, sliceResult, targetBlock, blocksWII, true);
//				}
			}
			if (lastInstruction instanceof SSAGotoInstruction) {
				SSAGotoInstruction instruction = (SSAGotoInstruction) lastInstruction;
				BasicBlock targetBlock = controlFlowGraph.getBlockForInstruction(instruction.getTarget());
				BasicBlockWithInstructionIndex myBlock = findBlockWithInstructionIndex(block, blocksWII);

				// TODO Redundant check
				if (myBlock == null) {
					sliceBackwards(controlFlowGraph, sliceResult, targetBlock, blocksWII, true);
				} else if (myBlock.getInstructionIndex() >= instruction.iIndex()) {
					sliceBackwards(controlFlowGraph, sliceResult, targetBlock, blocksWII, true);
				}
			}
			if (lastInstruction instanceof SSAInvokeInstruction) {
				SSAInvokeInstruction instruction = (SSAInvokeInstruction) lastInstruction;
				MethodReference declaredTarget = instruction.getDeclaredTarget();
				TypeName className = declaredTarget.getDeclaringClass().getName();
				String methodName = declaredTarget.getName().toString();
				String signature = declaredTarget.getDescriptor().toString();

				String methodSignature = className.toString() + ";." + methodName + signature;
				if (methodSignature.contentEquals(this.methodSignature)) {
					// Recursive method call
					// TODO Keep all return-instructions which are before the method call
					for (int blockNumber = 0; blockNumber < block.getNumber(); blockNumber++) {
						BasicBlock previousBlock = controlFlowGraph.getBasicBlock(blockNumber);
						if (previousBlock.getLastInstructionIndex() >= 0
								&& previousBlock.getLastInstruction() instanceof SSAReturnInstruction) {
							sliceBackwards(controlFlowGraph, sliceResult, previousBlock, blocksWII, false);
						}
					}
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

	public List<BasicBlockWithInstructionIndex> getInstructionIndexBlocks(CGNode callerNode) {
		// Get all blocks with features we want to slice
		List<BasicBlockWithInstructionIndex> blocks = new ArrayList<>();
		for (Integer instructionIndex : instructionIndexes) {
			BasicBlock block = callerNode.getIR().getControlFlowGraph().getBlockForInstruction(instructionIndex);
			blocks.add(new BasicBlockWithInstructionIndex(block, instructionIndex));
		}
		return blocks;
	}
}
