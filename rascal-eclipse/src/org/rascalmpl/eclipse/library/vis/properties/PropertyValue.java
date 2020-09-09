/*******************************************************************************
 * Copyright (c) 2009-2013 CWI
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:

 *   * Paul Klint - Paul.Klint@cwi.nl - CWI
*******************************************************************************/
package org.rascalmpl.eclipse.library.vis.properties;

import org.rascalmpl.eclipse.library.vis.swt.ICallbackEnv;
import org.rascalmpl.eclipse.library.vis.util.NameResolver;

import io.usethesource.vallang.IValue;

public abstract class PropertyValue<PropType> {
	
	public abstract PropType getValue();
	public void registerMeasures(NameResolver resolver){}
	public IValue execute(ICallbackEnv env, IValue... args){return null;}
	
}
