/*******************************************************************************
 * Copyright (c) 2009-2013 CWI
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:

 *   * Atze van der Ploeg - ploeg@cwi.nl (CWI)
 *******************************************************************************/

package org.rascalmpl.eclipse.library.vis.figure.interaction.swtwidgets;

import org.eclipse.swt.widgets.Control;
import org.rascalmpl.eclipse.library.vis.properties.PropertyManager;
import org.rascalmpl.eclipse.library.vis.swt.ICallbackEnv;
import org.rascalmpl.eclipse.library.vis.swt.IFigureConstructionEnv;
import org.rascalmpl.values.functions.IFunction;

public abstract class SWTWidgetFigureWithSingleCallBack<WidgetType extends Control> extends SWTWidgetFigure<WidgetType>{

	IFunction callback;
	ICallbackEnv cbenv;
	
	SWTWidgetFigureWithSingleCallBack(IFigureConstructionEnv env, IFunction callback, PropertyManager properties){
		super(env,properties);
		this.cbenv = env.getCallBackEnv();
		this.callback = callback;
	}

	void doCallback(){
		executeCallback();
		cbenv.signalRecompute();
	}
	
	abstract void executeCallback();
	
}
