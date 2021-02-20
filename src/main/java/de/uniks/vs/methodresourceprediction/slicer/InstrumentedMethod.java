package de.uniks.vs.methodresourceprediction.slicer;

import com.ibm.wala.shrikeBT.MethodEditor;

public class InstrumentedMethod {
	private MethodEditor methodEditor;

	public InstrumentedMethod(MethodEditor methodEditor) {
		setMethodEditor(methodEditor);
	}

	public MethodEditor getMethodEditor() {
		return methodEditor;
	}

	public void setMethodEditor(MethodEditor methodEditor) {
		this.methodEditor = methodEditor;
	}
}
