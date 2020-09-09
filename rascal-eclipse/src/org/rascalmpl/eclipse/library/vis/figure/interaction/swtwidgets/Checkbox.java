/*******************************************************************************
 * Copyright (c) 2009-2013 CWI

 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:

 *   * Paul Klint - Paul.Klint@cwi.nl - CWI
 *   * Arnold Lankamp - Arnold.Lankamp@cwi.nl
 *******************************************************************************/
package org.rascalmpl.eclipse.library.vis.figure.interaction.swtwidgets;

import org.eclipse.swt.SWT;
import org.rascalmpl.eclipse.library.vis.properties.PropertyManager;
import org.rascalmpl.eclipse.library.vis.swt.IFigureConstructionEnv;
import org.rascalmpl.values.ValueFactoryFactory;
import org.rascalmpl.values.functions.IFunction;

public class Checkbox extends Button {

	public Checkbox(IFigureConstructionEnv env, String caption, boolean checked, IFunction fun,PropertyManager properties) {
		super(env, caption, fun, properties);
		widget.setSelection(checked);
	}
	
	int buttonType(){
		return SWT.CHECK;
	}

	@Override
	public void executeCallback() {
		boolean selected = widget.getSelection();
		cbenv.executeRascalCallBack(callback, ValueFactoryFactory.getValueFactory().bool(selected));
	}
}
