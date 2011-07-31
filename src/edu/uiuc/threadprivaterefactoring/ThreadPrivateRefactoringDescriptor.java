package edu.uiuc.threadprivaterefactoring;

import java.util.Map;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.ltk.core.refactoring.Refactoring;
import org.eclipse.ltk.core.refactoring.RefactoringDescriptor;
import org.eclipse.ltk.core.refactoring.RefactoringStatus;

public class ThreadPrivateRefactoringDescriptor extends
		RefactoringDescriptor {

	private final static String refactoringID = "usr.caiusb.initializeattribute";
	private Map arguments;

	protected ThreadPrivateRefactoringDescriptor(String project,
			String description, String comment, Map arguments) {
		super(refactoringID, project, description, comment,
				RefactoringDescriptor.STRUCTURAL_CHANGE
						| RefactoringDescriptor.MULTI_CHANGE);
		
		this.arguments = arguments;
	}

	@Override
	public Refactoring createRefactoring(RefactoringStatus status)
			throws CoreException {
		ThreadPrivateRefactoring refactoring = new ThreadPrivateRefactoring();
		
		return refactoring;
	}

	public Map getArguments() {
		return arguments;
	}

}
