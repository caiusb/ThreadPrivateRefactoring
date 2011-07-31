package edu.uiuc.threadprivaterefactoring;

import java.util.Map;

import org.eclipse.ltk.core.refactoring.RefactoringContribution;
import org.eclipse.ltk.core.refactoring.RefactoringDescriptor;

public class ThreadPrivateRefactoringContribution extends
		RefactoringContribution {

	@Override
	public RefactoringDescriptor createDescriptor(String id, String project,
			String description, String comment, Map arguments, int flags)
			throws IllegalArgumentException {
	
		return new ThreadPrivateRefactoringDescriptor(project, description, comment, arguments);
	}
	
	@Override
	public Map retrieveArgumentMap(RefactoringDescriptor descriptor) {
		if (descriptor instanceof ThreadPrivateRefactoringDescriptor)
			return ((ThreadPrivateRefactoringDescriptor)descriptor).getArguments();
		
		return super.retrieveArgumentMap(descriptor);
	}

}
