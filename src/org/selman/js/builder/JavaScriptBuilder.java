/*******************************************************************************
 * Copyright (c) 2011 Daniel Selman}.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Daniel Selman - initial API and implementation and/or initial documentation
 *******************************************************************************/ 

package org.selman.js.builder;

import java.util.List;
import java.util.Map;

import javax.swing.text.BadLocationException;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IResourceDelta;
import org.eclipse.core.resources.IResourceDeltaVisitor;
import org.eclipse.core.resources.IResourceVisitor;
import org.eclipse.core.resources.IncrementalProjectBuilder;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.jface.text.Document;
import org.eclipse.jface.text.IDocument;
import org.eclipse.text.edits.MalformedTreeException;
import org.eclipse.text.edits.TextEdit;
import org.eclipse.wst.jsdt.core.IFunction;
import org.eclipse.wst.jsdt.core.IJavaScriptModel;
import org.eclipse.wst.jsdt.core.IJavaScriptProject;
import org.eclipse.wst.jsdt.core.IJavaScriptUnit;
import org.eclipse.wst.jsdt.core.ISourceRange;
import org.eclipse.wst.jsdt.core.JavaScriptCore;
import org.eclipse.wst.jsdt.core.dom.AST;
import org.eclipse.wst.jsdt.core.dom.ASTNode;
import org.eclipse.wst.jsdt.core.dom.ASTParser;
import org.eclipse.wst.jsdt.core.dom.FunctionDeclaration;
import org.eclipse.wst.jsdt.core.dom.JSdoc;
import org.eclipse.wst.jsdt.core.dom.JavaScriptUnit;
import org.eclipse.wst.jsdt.core.dom.TagElement;
import org.eclipse.wst.jsdt.core.dom.TextElement;

/**
 * A little IncrementalProjectBuilder for JavaScript projects that
 * adds the following useful features:
 * <ul>
 *   <li>Copy functions between .js files</li>
 *   <li>Upload modified files using scp to a remote host</li>
 * </ul>
 * @author dselman
 */
public class JavaScriptBuilder extends IncrementalProjectBuilder {

	private static final String COPY_TO = "@copyTo";

	/**
	 * JavaDoc tag that indicates that a Function was generated (copied)
	 * from another Function.
	 */
	private static final String GENERATED_FROM = "@generatedFrom";
	
	/**
	 * The Eclipse identifier for this builder.
	 */
	public static final String BUILDER_ID = "org.selman.js.builder.javaScriptBuilder";
	
	/**
	 * The Eclipse identifier for problem markers
	 */
	private static final String MARKER_TYPE = "org.selman.js.builder.jsProblem";

	/**
	 * IResourceDeltaVisitor called when a resource in our project
	 * is added, removed or changed.
	 */
	class DeltaVisitor implements IResourceDeltaVisitor {
		public boolean visit(IResourceDelta delta) throws CoreException {
			IResource resource = delta.getResource();
			switch (delta.getKind()) {
			case IResourceDelta.ADDED:
				try {
					process(resource);
				} catch (Exception e) {
					throw new CoreException( new BuilderStatus( IStatus.ERROR, resource.getFullPath(), "Failed to build.", e ) );
				}
				break;
			case IResourceDelta.REMOVED:
				processRemovedResource( delta.getResource().getFullPath() );
				break;
			case IResourceDelta.CHANGED:
				try {
					process(resource);
				} catch (Exception e) {
					throw new CoreException( new BuilderStatus( IStatus.ERROR, resource.getFullPath(), "Failed to build.", e ) );
				}
				break;
			}
			//return true to continue visiting children.
			return true;
		}
	}

	/**
	 * IResourceVisitor called to process all resources in our
	 * project.
	 */
	class ResourceVisitor implements IResourceVisitor {
		public boolean visit(IResource resource) {
			try {
				process(resource);
			} catch (Exception e) {
				throw new IllegalStateException( e );
			}
			//return true to continue visiting children.
			return true;
		}
	}

	/**
	 * Adds an Eclipse marker to a file.
	 * @param file
	 * @param message
	 * @param lineNumber
	 * @param severity
	 */
	private void addMarker(IFile file, String message, int lineNumber,
			int severity) {
		try {
			IMarker marker = file.createMarker(MARKER_TYPE);
			marker.setAttribute(IMarker.MESSAGE, message);
			marker.setAttribute(IMarker.SEVERITY, severity);
			if (lineNumber == -1) {
				lineNumber = 1;
			}
			marker.setAttribute(IMarker.LINE_NUMBER, lineNumber);
		} catch (CoreException e) {
		}
	}

	/**
	 * Handles removed resources. This method iterates on all JavaScript files in the project
	 * and examines their Functions. If any Function is found that was generated from the removed
	 * resource it is also removed.
	 * 
	 * @param projectRelativePath
	 */
	public void processRemovedResource(final IPath projectRelativePath) {
		
		IJavaScriptModel model = JavaScriptCore.create(ResourcesPlugin.getWorkspace().getRoot());
		try {
			IJavaScriptProject[] projects = model.getJavaScriptProjects();
			
			for( IJavaScriptProject project : projects ) {
				project.getProject().accept( new IResourceVisitor() {
					@Override
					public boolean visit(IResource resource)
							throws CoreException {
						
						if( JavaScriptCore.isJavaScriptLikeFileName( resource.getName() )) {
							IJavaScriptUnit jsUnit = JavaScriptCore.createCompilationUnitFrom((IFile) resource);
							
							IFunction functions[] = jsUnit.getFunctions();
							for( IFunction function : functions ) {
								ISourceRange jsDocRange = function.getJSdocRange();
								if( jsDocRange != null ) {
									function.getJavaScriptUnit().open( null );
									String text = function.getJavaScriptUnit().getBuffer().getText( jsDocRange.getOffset(), jsDocRange.getLength() );
									if( isGenerated(text,projectRelativePath) ) {
										JavaScriptUnit destRoot = createCU(jsUnit, false);
										destRoot.recordModifications();
										
										FunctionDeclaration destFunction = findFunction( destRoot.statements(), function );

										if( destFunction != null ) {
											destRoot.statements().remove( destFunction );									
										}
										
										String newContent;
										try {
											newContent = evaluateRewrite(jsUnit,destRoot);
											jsUnit.getBuffer().setContents(newContent);
										} catch (Exception e) {
											e.printStackTrace();
										}
									}
								}
							}
						}
						return true;
					}} );
			}
		
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	/**
	 * Determines if the given JavaDoc snippet was generated from
	 * the given path.
	 * @param text
	 * @param path
	 * @return
	 */
	protected boolean isGenerated(String text, IPath path) {
		return text.contains( GENERATED_FROM + " " + path.toString() );
	}
	
	/**
	 * Returns the value of the @generatedFrom tag for the given Function.
	 * @param function
	 * @return
	 */
	private String getGeneratedFrom(FunctionDeclaration function) {
		JSdoc jsdoc = function.getJavadoc();
		List tags = jsdoc.tags();

		for( Object thing : tags ) {
			if( thing instanceof TagElement ) {
				TagElement tag = (TagElement) thing;
				if( GENERATED_FROM.equals( tag.getTagName() ) ) {
					if( tag.fragments().size() > 0 ) {
						return tag.fragments().get(0).toString();
					}
				}
			}
		}
		
		return null;
	}

	/**
	 * Called by Eclipse to build a project.
	 */
	protected IProject[] build(int kind, Map args, IProgressMonitor monitor)
			throws CoreException {
		if (kind == FULL_BUILD) {
			fullBuild(monitor);
		} else {
			IResourceDelta delta = getDelta(getProject());
			if (delta == null) {
				fullBuild(monitor);
			} else {
				incrementalBuild(delta, monitor);
			}
		}
		return null;
	}

	/**
	 * Process added or changed resources. We look at all Functions in the resource and
	 * any Function tagged with '@copyTo' are copied to a destination resource.
	 * Any Functions with '@generatedFrom' whose source resource is missing are flagged
	 * as in error.
	 * 
	 * @param resource
	 * @throws org.eclipse.jface.text.BadLocationException 
	 * @throws BadLocationException 
	 * @throws CoreException 
	 * @throws MalformedTreeException 
	 */
	void process(IResource resource) throws MalformedTreeException, CoreException, BadLocationException, org.eclipse.jface.text.BadLocationException {
		if (resource instanceof IFile && JavaScriptCore.isJavaScriptLikeFileName(resource.getName())) {
			IFile file = (IFile) resource;
			deleteMarkers(file);
			
			IJavaScriptUnit srcUnit = JavaScriptCore.createCompilationUnitFrom(file);
			IFunction functions[] = srcUnit.getFunctions();
			for( IFunction function : functions ) {
				ISourceRange jsDocRange = function.getJSdocRange();
				if( jsDocRange != null ) {
					function.getJavaScriptUnit().open( null );
					JavaScriptUnit srcRoot= createCU(srcUnit, false);
					FunctionDeclaration srcFunction = findFunction( srcRoot.statements(), function );

					String text = function.getJavaScriptUnit().getBuffer().getText( jsDocRange.getOffset(), jsDocRange.getLength() );
					String[] destFiles = getCopyTo(text);
					if( destFiles != null ) {
						for( String destFile : destFiles ) {
							IJavaScriptProject proj = srcUnit.getJavaScriptProject();
							IResource destResource = ResourcesPlugin.getWorkspace().getRoot().findMember( proj.getPath().append(destFile.trim()) );
							if( destResource instanceof IFile ) {
								IJavaScriptUnit destUnit = JavaScriptCore.createCompilationUnitFrom( (IFile) destResource);
								
								JavaScriptUnit destRoot= createCU(destUnit, false);
								destRoot.recordModifications();
								
								List statements = destRoot.statements();
								FunctionDeclaration destFunction = findFunction( destRoot.statements(), function );

								if( destFunction != null ) {
									destRoot.statements().remove( destFunction );									
								}
								
								FunctionDeclaration newFunction = (FunctionDeclaration)ASTNode.copySubtree(destRoot.getAST(), srcFunction);
								replaceCopyTo(newFunction,function);
								destRoot.statements().add(newFunction);
								
								String newContent = evaluateRewrite(destUnit,destRoot);
								destUnit.getBuffer().setContents(newContent);
								destUnit.getBuffer().getOwner().save(null, true );
							}
						}
					}
					
					// create error markers on Functions that no longer have a source
					String generatedFrom = getGeneratedFrom(srcFunction);
					if( generatedFrom != null ) {
						IResource res = ResourcesPlugin.getWorkspace().getRoot().findMember(generatedFrom.trim());
						if( res == null ) {
							addMarker( (IFile) resource, "Cannot find resource " + generatedFrom, function.getJSdocRange().getOffset()+function.getJSdocRange().getLength(), IMarker.SEVERITY_ERROR );
						}
						else {
							if( res instanceof IFile ) {
								IJavaScriptUnit refUnit = JavaScriptCore.createCompilationUnitFrom( (IFile) res);
								if( refUnit.findFunctions( function ) == null ) {
									addMarker( (IFile) resource, "Cannot find source function " + generatedFrom, function.getJSdocRange().getOffset()+function.getJSdocRange().getLength(), IMarker.SEVERITY_ERROR );									
								}
							}
						}
					}
				}
			}
		}
	}
	
	/**
	 * After a Function is copied from source to destination
	 * we replace the '@copyTo' tag to '@generatedFrom' and include
	 * the path to the source resource.
	 * 
	 * @param newFunction
	 * @param src
	 */
	private void replaceCopyTo(FunctionDeclaration newFunction, IFunction src) {
		JSdoc jsdoc = newFunction.getJavadoc();
		List tags = jsdoc.tags();

		for( Object thing : tags ) {
			if( thing instanceof TagElement ) {
				TagElement tag = (TagElement) thing;
				if( COPY_TO.equals( tag.getTagName() ) ) {
					tag.setTagName( GENERATED_FROM );
					tag.fragments().clear();
					TextElement text = newFunction.getAST().newTextElement();
					text.setText( src.getJavaScriptUnit().getPath().toString() );
					tag.fragments().add(text); 
				}
			}
		}
	}

	/**
	 * Finds a FunctionDeclaration in a list with a given name
	 * @param statements
	 * @param function
	 * @return
	 */
	private FunctionDeclaration findFunction(List statements, IFunction function) {
		for( Object thing : statements ) {
			if( thing instanceof FunctionDeclaration ) {
				FunctionDeclaration that = (FunctionDeclaration) thing;
				if( function.getDisplayName().equals( that.getName().toString())) {
					return that;
				}
			}
		}
		
		return null;
	}

	/**
	 * Creates a JavaScriptUnit so we can access the AST for a 
	 * IJavaScriptUnit.
	 * @param unit
	 * @param resolveBindings
	 * @return
	 */
	private JavaScriptUnit createCU(IJavaScriptUnit unit, boolean resolveBindings) {

		try {
			ASTParser c = ASTParser.newParser(AST.JLS2);
			c.setSource(unit);
			c.setResolveBindings(resolveBindings);
			ASTNode result = c.createAST(null);
			return (JavaScriptUnit) result;
		} catch (IllegalStateException e) {
			// convert ASTParser's complaints into old form
			throw new IllegalArgumentException();
		}
	}

	/**
	 * Applies AST changes
	 * @param cu
	 * @param astRoot
	 * @return
	 * @throws CoreException
	 * @throws MalformedTreeException
	 * @throws BadLocationException
	 * @throws org.eclipse.jface.text.BadLocationException
	 */
	private String evaluateRewrite(IJavaScriptUnit cu, JavaScriptUnit astRoot)
			throws CoreException, MalformedTreeException, BadLocationException,
			org.eclipse.jface.text.BadLocationException {
		return evaluateRewrite(cu.getSource(), astRoot, cu
				.getJavaScriptProject().getOptions(true));
	}

	/**
	 * Applies AST changes
	 * @param source
	 * @param astRoot
	 * @param options
	 * @return
	 * @throws MalformedTreeException
	 * @throws BadLocationException
	 * @throws org.eclipse.jface.text.BadLocationException
	 */
	private String evaluateRewrite(String source, JavaScriptUnit astRoot,
			Map options) throws MalformedTreeException, BadLocationException,
			org.eclipse.jface.text.BadLocationException {
		IDocument doc = new Document(source);

		TextEdit changes = astRoot.rewrite(doc, options);
		changes.apply(doc);
		String newSource = doc.get();
		return newSource;
	}

	/**
	 * Returns the names of resources that the JavaDoc fragement that tags
	 * a given fragment should be copied to.
	 * @param text
	 * @return
	 */
	private String[] getCopyTo(String text) {

		final String copyTo = COPY_TO;
		int index = text.indexOf(copyTo);
		if (index >= 0) {
			index += copyTo.length();
			int end = text.indexOf('\n', index);
			if (end == -1) {
				end = text.length();
			}

			String files = text.substring(index, end);
			return files.split(",");
		}

		return null;
	}

	/**
	 * Deletes an error marker on the given file
	 * @param file
	 */
	private void deleteMarkers(IFile file) {
		try {
			file.deleteMarkers(MARKER_TYPE, false, IResource.DEPTH_ZERO);
		} catch (CoreException ce) {
		}
	}

	/**
	 * Called by Eclipse to perform a full build.
	 * @param monitor
	 * @throws CoreException
	 */
	protected void fullBuild(final IProgressMonitor monitor)
			throws CoreException {
		try {
			getProject().accept(new ResourceVisitor());
		} catch (CoreException e) {
		}
	}

	/**
	 * Called by Eclipse to perform an incremental build
	 * @param delta
	 * @param monitor
	 * @throws CoreException
	 */
	protected void incrementalBuild(IResourceDelta delta,
			IProgressMonitor monitor) throws CoreException {
		// the visitor does the work.
		delta.accept(new DeltaVisitor());
	}
}
