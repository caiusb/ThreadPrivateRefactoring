package edu.uiuc.threadlocalrefactoring;

import java.util.Map;

import org.eclipse.ltk.core.refactoring.RefactoringContribution;
import org.eclipse.ltk.core.refactoring.RefactoringDescriptor;

public class ThreadLocalRefactoringContribution extends
		RefactoringContribution {

	@Override
	public RefactoringDescriptor createDescriptor(String id, String project,
			String description, String comment, Map arguments, int flags)
			throws IllegalArgumentException {
	
		return new ThreadLocalRefactoringDescriptor(project, description, comment, arguments);
	}
	
	@Override
	public Map retrieveArgumentMap(RefactoringDescriptor descriptor) {
		if (descriptor instanceof ThreadLocalRefactoringDescriptor)
			return ((ThreadLocalRefactoringDescriptor)descriptor).getArguments();
		
		return super.retrieveArgumentMap(descriptor);
	}

}
