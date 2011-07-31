package edu.uiuc.threadlocalrefactoring;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.FieldAccess;
import org.eclipse.jdt.core.dom.IBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.Name;
import org.eclipse.jdt.core.dom.QualifiedName;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;

public class ThreadLocalVisitor extends ASTVisitor {
	
	private VariableDeclarationFragment declaration;
	private List<Expression> references = new ArrayList<Expression>();
	private IVariableBinding fieldBinding;
	
	public ThreadLocalVisitor(IVariableBinding binding) {
		fieldBinding = binding;
	}
	
	@Override
	public boolean visit(VariableDeclarationFragment node) {
		SimpleName name = node.getName();
		IBinding binding = name.resolveBinding();
		if (binding.isEqualTo(fieldBinding))
			declaration = node;
		return true;
	}
	
//	@Override
//	public boolean visit(FieldAccess node) {		
//		SimpleName name = node.getName();
//		IBinding binding = name.resolveBinding();
//		if (binding.isEqualTo(fieldBinding))
//			references.add(node);
//		
//		return false;
//	}
	
	@Override
	public boolean visit(QualifiedName node) {
		return visit((Name)node);
	}
	
	@Override
	public boolean visit(SimpleName node) {
		return visit((Name)node);
	}
	
	public boolean visit(Name node) {
		IBinding binding = node.resolveBinding();
		if (binding == null)
			return true;
		
		if (binding.isEqualTo(fieldBinding))
			references.add(node);
		
		return true;
	}
	
	@Override
	public boolean visit(MethodInvocation node) {
		Expression ex = node.getExpression();
		if (!(ex instanceof SimpleName))
			return true;
		
		SimpleName name = (SimpleName) ex;
		IBinding binding = name.resolveBinding();
		if (binding.isEqualTo(fieldBinding))
			references.add(node);
			
		return false;
	}
	
	public VariableDeclarationFragment getDeclaratation() {
		return declaration;
	}

	public List<Expression> getReferences() {
		return references;
	}
}
