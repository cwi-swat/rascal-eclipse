/*******************************************************************************
 * Copyright (c) 2009-2011 CWI
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *   * Jurgen J. Vinju - Jurgen.Vinju@cwi.nl - CWI
 *   * Bas Basten - Bas.Basten@cwi.nl (CWI)
 *   * Paul Klint - Paul.Klint@cwi.nl - CWI
 *   * Arnold Lankamp - Arnold.Lankamp@cwi.nl
 *   * Davy Landman - Davy.Landman@cwi.nl
*******************************************************************************/
package org.rascalmpl.eclipse.library.util;

import java.net.URI;
import java.net.URISyntaxException;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IResourceVisitor;
import org.eclipse.core.resources.IStorage;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IStorageEditorInput;
import org.eclipse.ui.IURIEditorInput;
import org.eclipse.ui.ide.FileStoreEditorInput;
import org.eclipse.ui.part.FileEditorInput;
import org.rascalmpl.eclipse.Activator;
import org.rascalmpl.exceptions.RuntimeExceptionFactory;
import org.rascalmpl.exceptions.Throw;
import org.rascalmpl.uri.URIEditorInput;
import org.rascalmpl.uri.URIStorage;
import org.rascalmpl.uri.URIUtil;

import io.usethesource.vallang.IConstructor;
import io.usethesource.vallang.ISet;
import io.usethesource.vallang.ISetWriter;
import io.usethesource.vallang.ISourceLocation;
import io.usethesource.vallang.IValue;
import io.usethesource.vallang.IValueFactory;
import io.usethesource.vallang.exceptions.FactTypeUseException;
import io.usethesource.vallang.type.Type;
import io.usethesource.vallang.type.TypeFactory;
import io.usethesource.vallang.type.TypeStore;

public class Resources {
	private final IValueFactory VF;
	private static final TypeFactory TF = TypeFactory.getInstance();
	private static final IWorkspaceRoot ROOT = ResourcesPlugin.getWorkspace().getRoot();
	private static final TypeStore store = new TypeStore();
	public static final Type res = TF.abstractDataType(store, "Resource");
	public static final Type root = TF.constructor(store, res, "root", TF.setType(res), "projects");
	public static final Type project = TF.constructor(store, res, "project", TF.sourceLocationType(), "id", TF.setType(res), "contents");
	public static final Type folder = TF.constructor(store, res, "folder", TF.sourceLocationType(), "id", TF.setType(res), "contents");
	public static final Type file =  TF.constructor(store, res, "file", TF.sourceLocationType(), "id");
	
	public Resources(IValueFactory vf) {
		this.VF = vf;
	}
	
	public  ISet projects() {
		IProject[] projects = ROOT.getProjects();
		ISetWriter w = VF.setWriter();
		
		for (IProject p : projects) {
			w.insert(makeProject(p));
		}
		
		return w.done();
	}
	
	public  ISet references(ISourceLocation loc) {
		IProject project = getIProject(loc.getAuthority());
		ISetWriter w = VF.setWriter();
		
		try {
			for (IProject r : project.getReferencedProjects()) {
				w.insert(makeProject(r));
			}
		} 
		catch (CoreException e) {
			// TODO what to do about this?
		}
		
		return w.done();
	}
	
	public void openProject(ISourceLocation name) {
		IProject project = getIProject(name.getAuthority());
		try {
			project.open(new NullProgressMonitor());
		} catch (CoreException e) {
			throw RuntimeExceptionFactory.io(VF.string(e.getMessage()), null, null);
		}
	}
	
	public void closeProject(ISourceLocation name) {
		IProject project = getIProject(name.getAuthority());
		try {
			project.close(new NullProgressMonitor());
		} catch (CoreException e) {
			throw RuntimeExceptionFactory.io(VF.string(e.getMessage()), null, null);
		}
	}

	public  ISourceLocation makeProject(IProject r) {
		try {
			return VF.sourceLocation("project", r.getName(), "");
		} catch (URISyntaxException e) {
			// won't happen
			throw RuntimeExceptionFactory.malformedURI("project://" + r.getName(), null, null);
		}
	}
	
	public  ISourceLocation location(ISourceLocation name) {
		IProject project = getIProject(name.getAuthority());
		String path = name.getPath();
		
		if (path != null && path.length() != 0 && !"/".equals(path)) {
			IFile file = project.getFile(path);
			return VF.sourceLocation(file.getLocation().toString());
		}
		
		return VF.sourceLocation(project.getLocationURI());
	}
	
	public  ISet files(ISourceLocation name) {
		final String projectName = name.getAuthority();
		IProject project = getIProject(projectName);
		final ISetWriter w = VF.setWriter();
		try {
			project.accept(new IResourceVisitor() {
				public boolean visit(IResource resource)
						throws CoreException {
					if (resource.exists() && !resource.isDerived()) {
						if (resource instanceof IFile) {
							w.insert(makeFile(resource));	
						}
					}
					return true;
				}

				
			});
		}
		catch (CoreException e) {
			// TODO what to do about this?
		}
		
		return w.done();
	}
	
	public  ISourceLocation makeFile(IResource resource) {
		String path = resource.getProjectRelativePath().toString();
		path = path.startsWith("/") ? path : "/" + path;
		try {
			return VF.sourceLocation(URIUtil.create("project", resource.getProject().getName(), path));
		} catch (URISyntaxException e) {
			throw RuntimeExceptionFactory.malformedURI("project: " + resource.getProject().getName() +" path: "+ path, null, null);
		}
	}
	
	public ISourceLocation makeFile(IEditorInput activeEditorInput) {
		if (activeEditorInput instanceof FileEditorInput) {
			IFile actualFile = ((FileEditorInput)activeEditorInput).getFile();
			return makeFile(actualFile);
		}
		else if (activeEditorInput instanceof FileStoreEditorInput) {
		    // a non file editor (not part of any project, such as annotated class files
		    URI urlPath = ((FileStoreEditorInput) activeEditorInput).getURI();

            if (urlPath != null) {
                return VF.sourceLocation(urlPath);
            }
		}
		else if (activeEditorInput instanceof URIEditorInput) {
		    IStorage storage = ((URIEditorInput) activeEditorInput).getStorage();
		    
		    if (storage instanceof URIStorage) {
		        return ((URIStorage) storage).getLocation();
		    }
		}
		else if (activeEditorInput instanceof IURIEditorInput) {
		    return VF.sourceLocation(((IURIEditorInput) activeEditorInput).getURI());
		}
		else if (activeEditorInput instanceof IStorageEditorInput) {
		    try {
                IStorage storage = ((IStorageEditorInput) activeEditorInput).getStorage();
                return VF.sourceLocation(storage.getFullPath().makeAbsolute().toFile().toURI());
            } catch (CoreException e) {
                // if this happens, then we bail to the next thing
            }
		}
	
		// we really don't know so we bail out with some default
	    String fileName = activeEditorInput.getName();
		return VF.sourceLocation(fileName);
	}
	
	public  ISourceLocation makeFolder(IFolder folder) {
		String path = folder.getProjectRelativePath().toString();
		path = path.startsWith("/") ? path : "/" + path;
		try {
			return VF.sourceLocation(URIUtil.create("project", folder.getProject().getName(), path));
		} catch (URISyntaxException e) {
			throw RuntimeExceptionFactory.malformedURI("project: " + folder.getProject().getName() + " path: " + path, null, null);
		}
	}
	
	public  IConstructor root() {
		ISetWriter projects = VF.setWriter();
		
		for (IProject p : ROOT.getProjects()) {
			if (p.exists() && p.isOpen()) {
				ISet contents = getProjectContents(p);
				projects.insert(VF.constructor(project, makeProject(p), contents));
			}
		}
		
		return VF.constructor(root, projects.done());
	}
	
	public  IConstructor getProject(ISourceLocation projectName) {
		IProject p = getIProject(projectName.getAuthority());
		ISet contents = getProjectContents(p);
		return VF.constructor(project, projectName, contents);
	}
	
	private  IProject getIProject(String projectName) {
		IProject p = ROOT.getProject(projectName);
		
		if (p != null && p.exists()) {
			return p;
		}
		else if (!p.isOpen()) {
			throw new Throw(VF.string("Project is not open: " + projectName));
		}
		else {
			throw new Throw(VF.string("Project does not exist: " + projectName));
		}
	}
	
	private  ISet getProjectContents(IProject project) {
		final ISetWriter w = VF.setWriter();

		try {
			project.accept(new IResourceVisitor() {

				public boolean visit(IResource resource)
						throws CoreException {
					if (resource instanceof IFile) {
						w.insert(getFile((IFile) resource));
						return false;
					}
					else if (resource instanceof IFolder) {
						w.insert(getFolder((IFolder) resource));
						return false;
					}
					return true;
				}
			}, IResource.DEPTH_ONE, false);
		} catch (FactTypeUseException e) {
			Activator.getInstance().logException("root", e);
		} catch (CoreException e) {
			Activator.getInstance().logException("root", e);
		}
		
		return w.done();
	}
	
	private  IValue getFolder(IFolder resource) {
		return VF.constructor(folder, makeFolder(resource), getFolderContents(resource));
	}

	private  IValue getFile(IFile resource) {
		return VF.constructor(file, makeFile(resource));
	}

	private  ISet getFolderContents(final IFolder folder) {
	final ISetWriter w = VF.setWriter();
		
		try {
			folder.accept(new IResourceVisitor() {

				public boolean visit(IResource resource)
						throws CoreException {
					if (resource == folder) {
						return true;
					}
					if (resource instanceof IFile) {
						w.insert(getFile((IFile) resource));
						return false;
					}
					else if (resource instanceof IFolder) {
						w.insert(getFolder((IFolder) resource));
						return false;
					}
					return true;
				}
			}, IResource.DEPTH_ONE, false);
		} catch (FactTypeUseException e) {
			// does not happen
		} catch (CoreException e) {
			// TODO what to do about this?
		}
		
		return w.done();
	}

}
