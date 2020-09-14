package de.rherzog.master.thesis.slicer;

import java.io.IOException;

import org.jgrapht.Graph;
import org.jgrapht.graph.DefaultEdge;

import com.ibm.wala.shrikeCT.InvalidClassFileException;

// TODO Rewrite this into an abstract class with a method generating the exporter for graph creation
public interface SlicerGraph<T> {
	public Graph<T, DefaultEdge> getGraph() throws IOException, InvalidClassFileException;

	public String dotPrint() throws IOException, InvalidClassFileException;
}
