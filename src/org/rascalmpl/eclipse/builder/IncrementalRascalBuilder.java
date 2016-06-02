package org.rascalmpl.eclipse.builder;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IResourceDelta;
import org.eclipse.core.resources.IResourceDeltaVisitor;
import org.eclipse.core.resources.IResourceVisitor;
import org.eclipse.core.resources.IncrementalProjectBuilder;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.rascalmpl.eclipse.Activator;
import org.rascalmpl.eclipse.IRascalResources;
import org.rascalmpl.eclipse.editor.MessagesToMarkers;
import org.rascalmpl.eclipse.util.RascalEclipseManifest;
import org.rascalmpl.eclipse.util.ResourcesToModules;
import org.rascalmpl.interpreter.load.RascalSearchPath;
import org.rascalmpl.interpreter.load.URIContributor;
import org.rascalmpl.library.experiments.Compiler.RVM.Interpreter.NoSuchRascalFunction;
import org.rascalmpl.library.experiments.Compiler.RVM.Interpreter.RascalExecutionContext;
import org.rascalmpl.library.experiments.Compiler.RVM.Interpreter.RascalExecutionContextBuilder;
import org.rascalmpl.library.lang.rascal.boot.Kernel;
import org.rascalmpl.uri.ProjectURIResolver;
import org.rascalmpl.uri.URIUtil;
import org.rascalmpl.value.IConstructor;
import org.rascalmpl.value.IList;
import org.rascalmpl.value.IListWriter;
import org.rascalmpl.value.IMapWriter;
import org.rascalmpl.value.ISet;
import org.rascalmpl.value.ISourceLocation;
import org.rascalmpl.value.IValueFactory;
import org.rascalmpl.values.ValueFactoryFactory;

import io.usethesource.impulse.builder.MarkerCreator;
import io.usethesource.impulse.runtime.RuntimePlugin;

/** 
 * This builder manages the execution of the Rascal compiler on all Rascal files which have been changed while editing them in Eclipse.
 * It also interacts with Project Clean actions to clear up files and markers on request.  
 */
public class IncrementalRascalBuilder extends IncrementalProjectBuilder {
	private static final String BIN_FOLDER = "bin";
    private final Kernel kernel;
	private final String checkerModuleName = "RascalIDEChecker";
	private final PrintWriter out;
    private final PrintWriter err;
    private final IValueFactory vf;
    
    // compiler config
    private ISourceLocation projectLoc;
    private IList srcPath;
    private ISourceLocation bootDir;
    private IList libPath;
    private ISourceLocation binDir;
    
    private final List<String> binaryExtension = Arrays.asList("imps","rvm.gz", "tc","sig","sigs");
    private RascalExecutionContext rex;

    public IncrementalRascalBuilder() throws IOException, NoSuchRascalFunction, URISyntaxException {
        out = new PrintWriter(new OutputStreamWriter(RuntimePlugin.getInstance().getConsoleStream(), "UTF16"), true);
        err = new PrintWriter(new OutputStreamWriter(RuntimePlugin.getInstance().getConsoleStream(), "UTF16"), true);
        vf = ValueFactoryFactory.getValueFactory();
        
        IMapWriter moduleTags = vf.mapWriter();
        moduleTags.put(vf.string(checkerModuleName), vf.mapWriter().done());
        
	    rex = RascalExecutionContextBuilder.normalContext(vf, out, err)
            .withModuleTags(moduleTags.done())
            .forModule(checkerModuleName)
            .setJVM(true)         
            .build();
	    
        kernel = new Kernel(vf, rex);
	}

	@Override
	protected void clean(IProgressMonitor monitor) throws CoreException {
		cleanBinFiles(monitor);
		cleanProblemMarkers(monitor);
	}

    private void cleanProblemMarkers(IProgressMonitor monitor) throws CoreException {
        RascalEclipseManifest manifest = new RascalEclipseManifest();
		 
        for (String src : manifest.getSourceRoots(getProject())) {
            getProject().findMember(src).accept(new IResourceVisitor() {
                @Override
                public boolean visit(IResource resource) throws CoreException {
                    if (IRascalResources.RASCAL_EXT.equals(resource.getFileExtension())) {
                        resource.deleteMarkers(IRascalResources.ID_RASCAL_MARKER, true, IResource.DEPTH_ONE);
                        return false;
                    }
                    
                    return true;
                }
            });
        }
    }

    private void cleanBinFiles(IProgressMonitor monitor) throws CoreException {
        getProject().findMember(BIN_FOLDER).accept(new IResourceVisitor() {
            @Override
            public boolean visit(IResource resource) throws CoreException {
                if (binaryExtension.contains(resource.getFileExtension())) {
                    resource.delete(true, monitor);
                    return false;
                }
                
                return true;
            }
        });
    }
	
	@Override
	protected IProject[] build(int kind, Map<String, String> args, IProgressMonitor monitor) throws CoreException {
	    switch (kind) {
	    case INCREMENTAL_BUILD:
	    case AUTO_BUILD:
	        buildIncremental(getDelta(getProject()), monitor);
	        break;
	    case FULL_BUILD:
	        buildMain(monitor);
	        break;
	    }
	    
	    // TODO: return project this project depends on?
		return new IProject[0];
	}

	private void buildMain(IProgressMonitor monitor) throws CoreException {
	    RascalEclipseManifest mf = new RascalEclipseManifest();
        String main = mf.getMainModule(getProject());
        
	    if (main == null) {
	        // no main defined in the RASCAL.MF file is fine
	        return;
	    }
	    
	    initializeParameters(true);
	    ISourceLocation module = rex.getRascalSearchPath().resolveModule(main);
	    
	    if (module == null) {
	        // TODO: this should be a marker on RASCAL.MF
	        Activator.log("Main module does not exist " + main, new IllegalArgumentException());
	        return;
	    }
	    
	    try {
	            
	        IConstructor result = kernel.compileAndLink(vf.string(main), srcPath, libPath, bootDir, binDir, vf.mapWriter().done());
            markErrors(module, result);
	    }
	    catch (Throwable e) {
	        Activator.log("error during compilation of " + main, e);
	    }
	    finally {
	        monitor.done();
	    }
    }

    private void buildIncremental(IResourceDelta delta, IProgressMonitor monitor) {
	    try {
            delta.accept(new IResourceDeltaVisitor() {
                @Override
                public boolean visit(IResourceDelta delta) throws CoreException {
                    IPath path = delta.getProjectRelativePath();
                    
                    if ("/META-INF/RASCAL.MF".equals(path.toPortableString())) {
                        // if the meta information has changed, we need to recompile everything
                        clean(monitor);
                        initializeParameters(true);
                        return false;
                    }
                    else if (IRascalResources.RASCAL_EXT.equals(path.getFileExtension() /* could be null */)) {
                        ISourceLocation loc = ProjectURIResolver.constructProjectURI(delta.getFullPath());
                        monitor.beginTask("Compiling " + loc, 100);
                        try {
                            IFile file = (IFile) delta.getResource();
                            file.deleteMarkers(IMarker.PROBLEM, true, 1);
                            String module = ResourcesToModules.moduleFromFile(file);
                            initializeParameters(true);
                            IConstructor result = kernel.compile(vf.string(module), srcPath, libPath, bootDir, binDir, vf.mapWriter().done());
                            markErrors(loc, result);
                        }
                        catch (Throwable e) {
                            Activator.log("Error during compilation of " + loc, e);
                        }
                        finally {
                            monitor.done();
                        }
                        
                        return false;
                    }
                    
                    return !BIN_FOLDER.equals(path.toPortableString());
                }

               
            });
        } catch (CoreException e) {
            Activator.log("error during Rascal compilation", e);
        }
    }
    
    private void markErrors(ISourceLocation loc, IConstructor result) throws MalformedURLException, IOException {
        if (result.has("main_module")) {
            result = (IConstructor) result.get("main_module");
        }
        
        if (!result.has("messages")) {
            Activator.log("Unexpected Rascal compiler result: " + result, new IllegalArgumentException());
        }
        
        new MessagesToMarkers().process(loc, (ISet) result.get("messages"), new MarkerCreator(new ProjectURIResolver().resolveFile(loc)));
    }

    private void initializeParameters(boolean force) throws CoreException {
        if (projectLoc != null && !force) {
            return;
        }
        
        IProject project = getProject();
        projectLoc = ProjectURIResolver.constructProjectURI(project.getFullPath());
        
        RascalEclipseManifest manifest = new RascalEclipseManifest();
        
        IListWriter libPathWriter = vf.listWriter();
        
        // TODO: this needs to be configured elsewhere
        libPathWriter.append(URIUtil.correctLocation("std", "", ""));
        libPathWriter.append(URIUtil.correctLocation("plugin", "rascal_eclipse", "/src/org/rascalmpl/eclipse/library"));
        
        // These are jar files which make contain compiled Rascal code to link to:
        for (String lib : manifest.getRequiredLibraries(project)) {
            libPathWriter.append(URIUtil.getChildLocation(projectLoc, lib));
        }
        
        // These are other projects referenced by the current project for which we add
        // the bin folder to the lib path
        for (IProject ref : project.getReferencedProjects()) {
            libPathWriter.append(URIUtil.getChildLocation(ProjectURIResolver.constructProjectURI(ref.getFullPath()), BIN_FOLDER));
        }
        
        libPath = libPathWriter.done();
        
        IListWriter srcPathWriter = vf.listWriter();
        RascalSearchPath rascalSearchPath = rex.getRascalSearchPath();
        
        for (String src : manifest.getSourceRoots(project)) {
            ISourceLocation srcLoc = URIUtil.getChildLocation(projectLoc, src);
            srcPathWriter.append(srcLoc);
            
            // TODO: interesting duplication of features, srcPath of compiler and RascalSearchPath?
            rascalSearchPath.addPathContributor(new URIContributor(srcLoc));
        }
        
        // TODO this is necessary while the kernel does not hold a compiled standard library, so remove later:
        srcPathWriter.append(URIUtil.correctLocation("std", "", ""));
        srcPathWriter.append(URIUtil.correctLocation("plugin", "rascal_eclipse", "/src/org/rascalmpl/eclipse/library"));
        
        srcPath = srcPathWriter.done();
        
        binDir = URIUtil.getChildLocation(projectLoc, BIN_FOLDER);
        bootDir = URIUtil.correctLocation("boot", "", "");
        manifest.getSourceRoots(project);
    }
}
