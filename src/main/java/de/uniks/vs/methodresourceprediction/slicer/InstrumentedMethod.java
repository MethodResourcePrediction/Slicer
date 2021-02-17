package de.uniks.vs.slicer;

import com.ibm.wala.shrikeBT.MethodEditor;

public class InstrumentedMethod {
	private MethodEditor methodEditor;
	private Integer featureLoggerVarIndex;

	public InstrumentedMethod(MethodEditor methodEditor, Integer featureLoggerVarIndex) {
		setMethodEditor(methodEditor);
		setFeatureLoggerVarIndex(featureLoggerVarIndex);
	}

	public MethodEditor getMethodEditor() {
		return methodEditor;
	}

	public void setMethodEditor(MethodEditor methodEditor) {
		this.methodEditor = methodEditor;
	}

	public Integer getFeatureLoggerVarIndex() {
		return featureLoggerVarIndex;
	}

	public void setFeatureLoggerVarIndex(Integer featureLoggerVarIndex) {
		this.featureLoggerVarIndex = featureLoggerVarIndex;
	}
}
