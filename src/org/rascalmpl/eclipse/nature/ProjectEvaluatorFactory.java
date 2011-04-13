/*******************************************************************************
 * Copyright (c) 2009-2011 CWI
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *   * Jurgen J. Vinju - Jurgen.Vinju@cwi.nl - CWI
*******************************************************************************/
package org.rascalmpl.eclipse.nature;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.WeakHashMap;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.FileLocator;
import org.eclipse.core.runtime.Platform;
import org.eclipse.imp.runtime.RuntimePlugin;
import org.rascalmpl.eclipse.Activator;
import org.rascalmpl.eclipse.IRascalResources;
import org.rascalmpl.eclipse.console.RascalScriptInterpreter;
import org.rascalmpl.eclipse.uri.BundleURIResolver;
import org.rascalmpl.eclipse.uri.ProjectURIResolver;
import org.rascalmpl.interpreter.Configuration;
import org.rascalmpl.interpreter.Evaluator;
import org.rascalmpl.interpreter.env.GlobalEnvironment;
import org.rascalmpl.interpreter.env.ModuleEnvironment;
import org.rascalmpl.interpreter.staticErrors.StaticError;
import org.rascalmpl.uri.ClassResourceInputOutput;
import org.rascalmpl.uri.URIResolverRegistry;
import org.rascalmpl.values.ValueFactoryFactory;

public class ProjectEvaluatorFactory {
	private final WeakHashMap<IProject, Evaluator> parserForProject = new WeakHashMap<IProject, Evaluator>();
	private final WeakHashMap<IProject, ModuleReloader> reloaderForProject = new WeakHashMap<IProject, ModuleReloader>();
	private final PrintWriter out = new PrintWriter(RuntimePlugin.getInstance().getConsoleStream());
	
	private ProjectEvaluatorFactory() {
      // TODO: add listeners to remove when projects are deleted or closed!
	}
	
	private static class InstanceHolder {
	      public static final ProjectEvaluatorFactory sInstance = new ProjectEvaluatorFactory();
	}
	
	public static ProjectEvaluatorFactory getInstance() {
		return InstanceHolder.sInstance;
	}
	
	public void clear() {
		reloaderForProject.clear();
		parserForProject.clear();
	}
	
	public void resetParser(IProject project) {
		parserForProject.remove(project);
		reloaderForProject.remove(project);
	}
	
	/**
	 * This method returns and shares a single evaluator for each project
	 */
	public Evaluator getEvaluator(IProject project) {
		Evaluator parser = parserForProject.get(project);
		
		if (parser == null) {
			parser = createProjectEvaluator(project);
			reloaderForProject.put(project, new ModuleReloader(parser));
			parserForProject.put(project, parser);
			return parser;
		}
		
		try {
			reloaderForProject.get(project).updateModules();
		}
		catch (StaticError e) {
			// things may go wrong while reloading modules, simply because the modules still have parse errors in them.
			// these are safely ignored here, the user will have had feedback on those errors elsewhere
			// TODO: remove this debug code
			System.err.println("ignored error: " + e.getMessage());
			e.printStackTrace();
		}
		
		return parser;
	}

	/**
	 * This method creates a fresh evaluator every time you call it.
	 */
	public Evaluator createProjectEvaluator(IProject project) {
		GlobalEnvironment heap = new GlobalEnvironment();
		Evaluator parser = new Evaluator(ValueFactoryFactory.getValueFactory(), out, out, new ModuleEnvironment("***parser***", heap), heap);
		initializeProjectEvaluator(project, parser);
		return parser;
	}

	/**
	 * This method configures an evaluator for use in an eclipse context. 
	 * @param project context to run the evaluator in, may be null
	 * @param evaluator the evaluator to configure, may not be null
	 */
	public void initializeProjectEvaluator(IProject project, Evaluator parser) {
		if (project != null) {
			try {
				parser.addRascalSearchPath(new URI("project://" + project.getName() + "/" + IRascalResources.RASCAL_SRC));
			} catch (URISyntaxException usex) {
				throw new RuntimeException(usex);
			}
		}
		
		ProjectURIResolver resolver = new ProjectURIResolver();
		URIResolverRegistry resolverRegistry = parser.getResolverRegistry();
		resolverRegistry.registerInput(resolver);
		resolverRegistry.registerOutput(resolver);
		
		ClassResourceInputOutput eclipseResolver = new ClassResourceInputOutput(resolverRegistry, "eclipse-std", RascalScriptInterpreter.class, "/org/rascalmpl/eclipse/library");
		resolverRegistry.registerInput(eclipseResolver);
		parser.addRascalSearchPath(URI.create(eclipseResolver.scheme() + ":///"));
		parser.addClassLoader(getClass().getClassLoader());
		
		// add project's bin folder to class loaders
		// TODO: should probably find project's output folder, instead of just
		// using "bin"
		String projectBinFolder = "";
		
		if (project != null) {
			try {
				IResource res = project.findMember("bin");
				if (res != null) {
					projectBinFolder = res.getLocation().toString();
					URLClassLoader loader = new java.net.URLClassLoader(new URL[] {new URL("file", "",  projectBinFolder + "/")}, getClass().getClassLoader());
					parser.addClassLoader(loader);
				}
			} 
			catch (MalformedURLException e1) {
				// 
			}
		}
		
		BundleURIResolver bundleResolver = new BundleURIResolver(resolverRegistry);
		resolverRegistry.registerInput(bundleResolver);
		resolverRegistry.registerOutput(bundleResolver);

		try {
			String rascalPlugin = jarForPlugin("rascal");
			String PDBValuesPlugin = jarForPlugin("org.eclipse.imp.pdb.values");

			Configuration.setRascalJavaClassPathProperty(
					rascalPlugin 
					+ File.pathSeparator 
					+ PDBValuesPlugin 
					+ File.pathSeparator 
					+ rascalPlugin 
					+ File.separator + "src" 
					+ File.pathSeparator 
					+ rascalPlugin + File.separator + "bin" 
					+ File.pathSeparator 
					+ PDBValuesPlugin + File.separator + "bin" 
					+ (projectBinFolder != "" ? File.pathSeparator + projectBinFolder : ""));
		} 
		catch (IOException e) {
			Activator.getInstance().logException("could not create classpath for parser compilation", e);
		}
	}

	private String jarForPlugin(String pluginName) throws IOException {
		URL rascalURI = FileLocator.resolve(Platform.getBundle(pluginName).getEntry("/"));
		
		try {
			if (rascalURI.getProtocol().equals("jar")) {
				String path = rascalURI.toURI().toASCIIString();
				return path.substring(path.indexOf("/"), path.indexOf('!'));
			}
			else {
				// TODO this is a monumental workaround, apparently the Rascal plugin gets unpacked and in 
				// it is a rascal.jar file that we should lookup...
				String path = rascalURI.getPath();
				File folder = new File(path);
				if (folder.isDirectory()) {
					File[] list = folder.listFiles();
					for (File f : list) {
						if (f.getName().startsWith(pluginName) && f.getName().endsWith(".jar")) {
							return f.getAbsolutePath();
						}
					}
				}
				
				return path;
			}
		}
		catch (URISyntaxException e) {
			throw new IOException(e);
		}
	}
}
