package org.rascalmpl.eclipse.editor.commands;

import org.rascalmpl.eclipse.editor.ParseController;
import org.rascalmpl.eclipse.library.util.ValueUI;
import org.rascalmpl.values.ValueFactoryFactory;

import io.usethesource.impulse.editor.UniversalEditor;
import io.usethesource.vallang.IConstructor;
import io.usethesource.vallang.IValueFactory;

public class BrowseTree extends AbstractEditorAction {

	public BrowseTree(UniversalEditor editor) {
		super(editor, "Browse parse tree");
	}
	
	@Override
	public void run() {
		IConstructor tree = (IConstructor) ((ParseController) editor.getParseController()).getCurrentAst();

		if (tree != null) {
			IValueFactory valueFactory = ValueFactoryFactory.getValueFactory();
			new ValueUI(valueFactory).tree(tree);
		}
	}
}
