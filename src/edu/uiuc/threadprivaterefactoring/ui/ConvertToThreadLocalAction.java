package edu.uiuc.threadprivaterefactoring.ui;

import org.eclipse.jdt.core.IField;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.ltk.ui.refactoring.RefactoringWizardOpenOperation;
import org.eclipse.ui.ISelectionService;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.IWorkbenchWindowActionDelegate;
import org.eclipse.ui.PlatformUI;

import edu.uiuc.threadprivaterefactoring.ThreadPrivateRefactoring;

/**
 * Our sample action implements workbench action delegate. The action proxy will
 * be created by the workbench and shown in the UI. When the user tries to use
 * the action, this delegate will be created and execution will be delegated to
 * it.
 * 
 * @see IWorkbenchWindowActionDelegate
 */
public class ConvertToThreadLocalAction implements
		IWorkbenchWindowActionDelegate {

	private IWorkbenchWindow window;
	private IField field = null;
	private String name = "Convert Field to Thread Local";

	/**
	 * The constructor.
	 */
	public ConvertToThreadLocalAction() {
	}

	/**
	 * The action has been activated. The argument of the method represents the
	 * 'real' action sitting in the workbench UI.
	 * 
	 * @see IWorkbenchWindowActionDelegate#run
	 */
	public void run(IAction action) {
		ThreadPrivateRefactoring refactoring = new ThreadPrivateRefactoring(
				field);
		ConvertToThreadLocalWizard wizard = new ConvertToThreadLocalWizard(
				refactoring, name);
		RefactoringWizardOpenOperation operation = new RefactoringWizardOpenOperation(
				wizard);
		try {
			operation.run(window.getShell(), name);
		} catch (InterruptedException e) {
			// nothing
		}

	}

	/**
	 * Selection in the workbench has been changed. We can change the state of
	 * the 'real' action here if we want, but this can only happen after the
	 * delegate has been created.
	 * 
	 * @see IWorkbenchWindowActionDelegate#selectionChanged
	 */
	public void selectionChanged(IAction action, ISelection selection) {
		field = getSelection(selection);
	}

	/**
	 * We can use this method to dispose of any system resources we previously
	 * allocated.
	 * 
	 * @see IWorkbenchWindowActionDelegate#dispose
	 */
	public void dispose() {
	}

	/**
	 * We will cache window object in order to be able to provide parent shell
	 * for the message dialog.
	 * 
	 * @see IWorkbenchWindowActionDelegate#init
	 */
	public void init(IWorkbenchWindow window) {
		this.window = window;
		ISelectionService service = PlatformUI.getWorkbench()
				.getActiveWorkbenchWindow().getSelectionService();
		ISelection selection = service.getSelection();
		field = getSelection(selection);
	}
	
	private IField getSelection(ISelection selection) {
		if (selection instanceof IStructuredSelection) {
			IStructuredSelection struct = (IStructuredSelection) selection;
			Object elem[] = struct.toArray();
			if (elem.length == 1 && elem[0] instanceof IField)
				return (IField) elem[0];
		}
		
		return null;
	}
}