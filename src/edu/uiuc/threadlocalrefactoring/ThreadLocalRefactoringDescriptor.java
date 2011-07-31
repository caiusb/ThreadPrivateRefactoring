package edu.uiuc.threadlocalrefactoring;

import java.util.Map;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.ltk.core.refactoring.Refactoring;
import org.eclipse.ltk.core.refactoring.RefactoringDescriptor;
import org.eclipse.ltk.core.refactoring.RefactoringStatus;

public class ThreadLocalRefactoringDescriptor extends
		RefactoringDescriptor {

	private final static String refactoringID = "usr.caiusb.initializeattribute";
	private Map arguments;

	protected ThreadLocalRefactoringDescriptor(String project,
			String description, String comment, Map arguments) {
		super(refactoringID, project, description, comment,
				RefactoringDescriptor.STRUCTURAL_CHANGE
						| RefactoringDescriptor.MULTI_CHANGE);
		
		this.arguments = arguments;
	}

	@Override
	public Refactoring createRefactoring(RefactoringStatus status)
			throws CoreException {
		ThreadLocalRefactoring refactoring = new ThreadLocalRefactoring();
		
		return refactoring;
	}

	public Map getArguments() {
		return arguments;
	}

}
