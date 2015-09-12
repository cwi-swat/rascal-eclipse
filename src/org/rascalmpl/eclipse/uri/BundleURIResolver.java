/*******************************************************************************
 * Copyright (c) 2009-2011 CWI
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *   * Jurgen J. Vinju - Jurgen.Vinju@cwi.nl - CWI
 *   * Arnold Lankamp - Arnold.Lankamp@cwi.nl
 *******************************************************************************/
package org.rascalmpl.eclipse.uri;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.Charset;

import org.eclipse.core.runtime.FileLocator;
import org.eclipse.imp.pdb.facts.ISourceLocation;
import org.rascalmpl.interpreter.asserts.NotYetImplemented;
import org.rascalmpl.uri.ISourceLocationInputOutput;
import org.rascalmpl.uri.URIResolverRegistry;
import org.rascalmpl.uri.URIUtil;
import org.rascalmpl.values.ValueFactoryFactory;

public class BundleURIResolver implements  ISourceLocationInputOutput {
	private URIResolverRegistry registry;

	public BundleURIResolver(URIResolverRegistry registry) {
		this.registry = registry;
	}

	@Override
	public OutputStream getOutputStream(ISourceLocation uri, boolean append)
			throws IOException {
		ISourceLocation parent = resolve(URIUtil.getParentLocation(uri));
		parent = resolve(parent);
		return registry.getOutputStream(URIUtil.getChildLocation(parent, URIUtil.getLocationName(uri)), append);
	}

	@Override
	public void mkDirectory(ISourceLocation uri) throws IOException {
		ISourceLocation parent = resolve(URIUtil.getParentLocation(uri));
		parent = resolve(parent);
		registry.mkDirectory(URIUtil.getChildLocation(parent, URIUtil.getLocationName(uri)));
	}

	@Override
	public void remove(ISourceLocation uri) throws IOException {
		registry.remove(resolve(uri));
	}

	@Override
	public String scheme() {
		return "bundleresource";
	}

	@Override
	public boolean exists(ISourceLocation uri) {
		try {
			return registry.exists(resolve(uri));
		} catch (IOException e) {
			return false;
		}
	}

	@Override
	public InputStream getInputStream(ISourceLocation uri) throws IOException {
		return registry.getInputStream(resolve(uri));
	}

	protected ISourceLocation resolve(ISourceLocation uri) throws IOException {
		try {
			URL url = uri.getURI().toURL();
			URL resolved = FileLocator.resolve(url);
			
			if (resolved == url) {
				throw new IOException("could not resolve " + uri);
			}
			
			if (resolved.getProtocol().equals("jar")) {
				String path = resolved.getPath();
				
				if (path.startsWith("file:")) {
					path = path.substring(5);
				}
				
				// TODO: this does not respect offsets
				return ValueFactoryFactory.getValueFactory().sourceLocation(resolved.getProtocol(), resolved.getAuthority(), path);
			}
			else {
				return ValueFactoryFactory.getValueFactory().sourceLocation(resolved.toURI());
			}
		} catch (URISyntaxException e) {
			throw new IOException("unexpected URI syntax exception: " + e.getMessage(), e);
		}
	}

	@Override
	public boolean isDirectory(ISourceLocation uri) {
		try {
			return registry.isDirectory(resolve(uri));
		} catch (IOException e) {
			return false;
		}
	}

	@Override
	public boolean isFile(ISourceLocation uri) {
		try {
			return registry.isFile(resolve(uri));
		} catch (IOException e) {
			return false;
		}
	}

	@Override
	public long lastModified(ISourceLocation uri) throws IOException {
		return registry.lastModified(resolve(uri));
	}

	@Override
	public String[] list(ISourceLocation uri) throws IOException {
		return registry.listEntries(resolve(uri));
	}

	@Override
	public boolean supportsHost() {
		return false;
	}

	@Override
	public Charset getCharset(ISourceLocation uri) {
		return Charset.defaultCharset(); // TODO
	}
	
	@Override
	public boolean supportsToFileURI() {
		return false;
	}

	@Override
	public URI toFileURI(ISourceLocation uri) {
		throw new UnsupportedOperationException("Cannot convert bundle to File URI");
	}
}
