package edu.uiuc.threadlocalrefactoring.ui;

import org.eclipse.ltk.core.refactoring.Refactoring;
import org.eclipse.ltk.ui.refactoring.RefactoringWizard;


public class ConvertToThreadLocalWizard extends RefactoringWizard {

	public ConvertToThreadLocalWizard(Refactoring refactoring, int flags) {
		super(refactoring, flags);
	}
	
	public ConvertToThreadLocalWizard(Refactoring refactoring, String string) {
		super (refactoring, DIALOG_BASED_USER_INTERFACE | PREVIEW_EXPAND_FIRST_NODE);
		setDefaultPageTitle(string);
	}

	@Override
	protected void addUserInputPages() {		
	}
}
