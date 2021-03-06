/*******************************************************************************
 * Copyright (c) 2009-2013 CWI
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
*******************************************************************************/
package org.rascalmpl.eclipse.library.vis.swt;

import org.eclipse.swt.widgets.Control;
import org.rascalmpl.eclipse.library.vis.figure.Figure;
import org.rascalmpl.eclipse.library.vis.figure.combine.Overlap;
import org.rascalmpl.eclipse.library.vis.properties.IRunTimePropertyChanges;
import org.rascalmpl.eclipse.library.vis.swt.applet.FigureSWTApplet;
import org.rascalmpl.interpreter.IEvaluatorContext;


public interface IFigureConstructionEnv {
	
	FigureSWTApplet getSWTParent();
	void addSWTElement(Control c);
	void addAboveSWTElement(Figure fig);
	void registerOverlap(Overlap o);
	void unregisterOverlap(Overlap o);
	ICallbackEnv getCallBackEnv();
	IEvaluatorContext getRascalContext();
	FigureExecutionEnvironment getFigureExecEnv();
	IRunTimePropertyChanges getRunTimePropertyChanges();

}
