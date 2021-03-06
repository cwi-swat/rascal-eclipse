/*******************************************************************************
 * Copyright (c) 2009-2015 CWI
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *   * Jurgen J. Vinju - Jurgen.Vinju@cwi.nl - CWI
 *   * Paul Klint - Paul.Klint@cwi.nl - CWI
 *   * Arnold Lankamp - Arnold.Lankamp@cwi.nl
*******************************************************************************/
package org.rascalmpl.uri;

import java.io.ByteArrayInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.Charset;

import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.AssertionFailedException;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Path;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.rascalmpl.uri.classloaders.IClassloaderLocationResolver;
import org.rascalmpl.values.ValueFactoryFactory;

import io.usethesource.vallang.ISourceLocation;

public class ProjectURIResolver implements ISourceLocationInputOutput, IURIResourceResolver, IClassloaderLocationResolver {
	
	

    public static ISourceLocation constructProjectURI(IProject project, IPath path){
		return constructProjectURI(project.getName(), path);
	}

	private static ISourceLocation constructProjectURI(String project, IPath path){
		try{
			return ValueFactoryFactory.getValueFactory().sourceLocation("project", project, "/" + path.toString());
		}
		catch(URISyntaxException usex){
			throw new BadURIException(usex);
		}
	}
	
	public static ISourceLocation constructProjectURI(IPath workspaceAbsolutePath){
		String projectName        = workspaceAbsolutePath.segment(0);
		IPath projectAbsolutePath = workspaceAbsolutePath.removeFirstSegments(1);
		return constructProjectURI(projectName, projectAbsolutePath);
	}		
	
	@Override
	public InputStream getInputStream(ISourceLocation uri) throws IOException {
		try {
			return resolveFile(uri).getContents(true);
		} catch (CoreException e) {
			Throwable cause = e.getCause();
			
			if (cause instanceof IOException) {
				throw (IOException) cause;
			}
			
			throw new IOException(e.getMessage());
		}
	}

	public IFile resolveFile(ISourceLocation uri) throws IOException, MalformedURLException {
	    if ("".equals(uri.getAuthority())) {
            throw new IOException("location needs a project name as authority" + uri);
        }
	    
		IProject project = ResourcesPlugin.getWorkspace().getRoot().getProject(uri.getAuthority());
		
		if (project == null || !project.exists()) {
			throw new IOException("project " + uri.getAuthority() + " does not exist");
		}
		else if (!project.isOpen()) {
		    throw new IOException("project " + uri.getAuthority() + " is closed");
		}
		
		return project.getFile(new Path(uri.getPath()));
	}
	
	private IContainer resolveFolder(ISourceLocation uri) throws IOException, MalformedURLException {
	    if ("".equals(uri.getAuthority())) {
	        throw new IOException("location needs a project name as authority" + uri);
	    }
	    
		IProject project = ResourcesPlugin.getWorkspace().getRoot().getProject(uri.getAuthority());
		if (project == null) {
			throw new IOException("project " + uri.getAuthority() + " does not exist");
		}
		
		if (uri.getPath().isEmpty() || uri.getPath().equals("/")) {
		  return project;
		}
		else {
		  return project.getFolder(uri.getPath());
		}
	}

	private IResource resolve(ISourceLocation uri) throws IOException, MalformedURLException {
	    if ("".equals(uri.getAuthority())) {
	        throw new IOException("location needs a project name as authority" + uri);
	    }
	    
		IProject project = ResourcesPlugin.getWorkspace().getRoot().getProject(uri.getAuthority());
		
		if (project == null || !project.exists()) {
			throw new IOException("project " + uri.getAuthority() + " does not exist.");
		}
		
		if (!project.isOpen()) {
			throw new IOException("project " + uri.getAuthority() + " is closed.");
		}
		
		if (uri.getPath().isEmpty() || uri.getPath().equals("/")) {
		    return project;
		}
		if(isDirectory(uri)){
			return project.getFolder(uri.getPath());
		}
		
		if(isFile(uri)){
			return project.getFile(uri.getPath());
		}
		
		throw new IOException(uri+" refers to a resource that does not exist.");
	}
	
    private static final boolean keepHistory = false;
    private static final boolean forceRegardLessOutOfSync = true;

	public OutputStream getOutputStream(final ISourceLocation uri, boolean append) throws IOException {
	    final IFile file = resolveFile(uri);

	    try {
	        // clear or create the file if needed
    	    if (!file.exists()) {
                file.create(new ByteArrayInputStream(new byte[0]), forceRegardLessOutOfSync, new NullProgressMonitor());
            }
            else if (!append) {
                file.setContents(new ByteArrayInputStream(new byte[0]), keepHistory, forceRegardLessOutOfSync, new NullProgressMonitor());
            }
	    }
	    catch (CoreException e) {
	        throw new IOException(e);
	    }
	    
	    return new OutputStream() {
            
	        @Override
	        public void write(byte[] b, int off, int len) throws IOException {
	            try {
                    file.appendContents(new ByteArrayInputStream(b, off, len), keepHistory, forceRegardLessOutOfSync, new NullProgressMonitor());
	            } catch (CoreException e) {
	                throw new IOException(e);
	            }
	        }
	        
            @Override
            public void write(int b) throws IOException {
                throw new UnsupportedOperationException("always wrap this outputstream with a buffered outputstream");
            }
        };
	}

	public String scheme() {
		return "project";
	}

	@Override
	public boolean exists(ISourceLocation uri) {
		try {
			return resolve(uri).exists();
		} catch (IllegalStateException | IOException | AssertionFailedException e) {
			return false;
		}
	}

	@Override
	public boolean isDirectory(ISourceLocation uri) {
		try {
			return resolveFolder(uri).exists();
		} catch (IllegalStateException | IOException | AssertionFailedException e) {
			return false;
		}
	}

	public boolean isFile(ISourceLocation uri) {
		try {
			return resolveFile(uri).exists();
		} catch (IllegalStateException | IOException | AssertionFailedException e) {
			return false;
		}
	}

	@Override
	public long lastModified(ISourceLocation uri) {
		try {
			return resolve(uri).getLocalTimeStamp();
		} catch (IllegalStateException | IOException | AssertionFailedException e) {
			return 0L;
		}
	}
	
	@Override
	public void setLastModified(ISourceLocation uri, long timestamp) throws IOException {
	    try {
            resolve(uri).setLocalTimeStamp(timestamp);
        } catch (CoreException e) {
            throw new IOException(e.getMessage());
        }
	}

	@Override
	public String[] list(ISourceLocation uri) throws IOException {
		try {
			IContainer folder = resolveFolder(uri);
			
			IResource[] members = folder.members();
			String[] result = new String[members.length];
			
			for (int i = 0; i < members.length; i++) {
				result[i] = members[i].getName();
			}
			
			return result;
		} catch (CoreException e) {
			throw new IOException(e);
		}
	}

	@Override
	public void remove(ISourceLocation uri) throws IOException {
		try {
			resolve(uri).delete(true, new NullProgressMonitor());
		} catch (CoreException e) {
			throw new IOException("could not remove file", e);
		}
	}

	@Override
	public void mkDirectory(ISourceLocation uri) throws IOException {
		IContainer resolved = resolveFolder(uri);
		NullProgressMonitor pm = new NullProgressMonitor();
		
		if (!resolved.exists()) {
			try { 
				if (resolved instanceof IFolder) {
					((IFolder) resolved).create(true, true, pm);
				}
				else if (resolved instanceof IProject) {
					IProject project = (IProject) resolved;
					project.create(pm);
					project.open(pm);
				}
				return;
			} catch (CoreException e) {
				throw new IOException(e.getMessage(), e);
			}
		}

		return;
	}

	@Override
	public boolean supportsHost() {
		return false;
	}

	@Override
	public Charset getCharset(ISourceLocation uri) throws IOException {
		IFile file;
		try {
			file = resolveFile(uri);
		} catch (MalformedURLException e) {
			return null;
		}
		if (file != null) {
			try {
				String charsetName = file.getCharset();
				if (charsetName != null) 
					return Charset.forName(charsetName);
			} catch (CoreException e) {
				return null;
			}
		}
		return null;
	}

	@Override
	public IResource getResource(ISourceLocation uri) throws IOException {
		return resolve(uri);
	}

    @Override
    public ClassLoader getClassLoader(ISourceLocation loc, ClassLoader parent) throws IOException {
        IProject project = (IProject) resolve(loc);

        try {
            if (project.exists() && project.isOpen()) {
                IJavaProject jProject = JavaCore.create(project);

                IPath binFolder = jProject.getOutputLocation();
                String binLoc = project.getLocation() + "/" + binFolder.removeFirstSegments(1).toString();

                URL binURL = new URL("file", "",  binLoc + "/");
                return new URLClassLoader(new URL[] {binURL}, getClass().getClassLoader());
            }
            else {
                throw new FileNotFoundException("project is not open: " + loc);
            }
        } catch (JavaModelException e) {
            throw new IOException("no classloader for project:" + loc, e);
        }
    }
}	
