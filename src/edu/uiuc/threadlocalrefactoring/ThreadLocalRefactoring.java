package edu.uiuc.threadlocalrefactoring;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IField;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.AnonymousClassDeclaration;
import org.eclipse.jdt.core.dom.Assignment;
import org.eclipse.jdt.core.dom.Assignment.Operator;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.ChildListPropertyDescriptor;
import org.eclipse.jdt.core.dom.ClassInstanceCreation;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.FieldAccess;
import org.eclipse.jdt.core.dom.FieldDeclaration;
import org.eclipse.jdt.core.dom.IBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.InfixExpression;
import org.eclipse.jdt.core.dom.Initializer;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.Modifier;
import org.eclipse.jdt.core.dom.Name;
import org.eclipse.jdt.core.dom.NodeFinder;
import org.eclipse.jdt.core.dom.ParameterizedType;
import org.eclipse.jdt.core.dom.PrimitiveType;
import org.eclipse.jdt.core.dom.ReturnStatement;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.jdt.core.dom.Type;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.core.dom.Modifier.ModifierKeyword;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.core.dom.rewrite.ImportRewrite;
import org.eclipse.jdt.core.dom.rewrite.ListRewrite;
import org.eclipse.jdt.core.search.IJavaSearchConstants;
import org.eclipse.jdt.core.search.IJavaSearchScope;
import org.eclipse.jdt.core.search.SearchEngine;
import org.eclipse.jdt.core.search.SearchParticipant;
import org.eclipse.jdt.core.search.SearchPattern;
import org.eclipse.jdt.internal.compiler.util.SimpleNameVector;
import org.eclipse.jdt.internal.corext.refactoring.RefactoringScopeFactory;
import org.eclipse.jdt.internal.corext.refactoring.RefactoringSearchEngine;
import org.eclipse.jdt.internal.corext.refactoring.util.TextChangeManager;
import org.eclipse.jface.text.IDocument;
import org.eclipse.ltk.core.refactoring.Change;
import org.eclipse.ltk.core.refactoring.CompositeChange;
import org.eclipse.ltk.core.refactoring.NullChange;
import org.eclipse.ltk.core.refactoring.Refactoring;
import org.eclipse.ltk.core.refactoring.RefactoringStatus;
import org.eclipse.ltk.core.refactoring.TextChange;
import org.eclipse.ltk.core.refactoring.TextFileChange;
import org.eclipse.text.edits.TextEdit;
import org.eclipse.text.edits.UndoEdit;
import org.eclipse.ui.keys.ModifierKey;

@SuppressWarnings("restriction")
public class ThreadLocalRefactoring extends Refactoring {

	private IField field;
	private TextChangeManager changeManager = new TextChangeManager();

	public ThreadLocalRefactoring() {
	}

	public ThreadLocalRefactoring(IField field) {
		this.field = field;
	}

	@SuppressWarnings("restriction")
	@Override
	public RefactoringStatus checkFinalConditions(IProgressMonitor pm)
			throws CoreException, OperationCanceledException {
		RefactoringStatus result = new RefactoringStatus();
		VariableDeclarationFragment declaration;
		List<Expression> references;
		AST tree;
		ASTRewrite rewrite;

		try {

			pm.beginTask("Checking final conditions", 1);

			ICompilationUnit cu = field.getDeclaringType().getCompilationUnit();
			ASTNode root = getNodeFromCU(cu);
			ASTNode node = NodeFinder.perform(root, field.getNameRange());
			if (!(node instanceof Name))
				return RefactoringStatus
						.createFatalErrorStatus("Node not found");

			Name fieldNode = (Name) node;
			IBinding binding = fieldNode.resolveBinding();
			if (!(binding instanceof IVariableBinding))
				return RefactoringStatus
						.createFatalErrorStatus("It's not a field");

			ICompilationUnit[] affectedCUs = RefactoringSearchEngine
					.findAffectedCompilationUnits(SearchPattern.createPattern(
							field, IJavaSearchConstants.ALL_OCCURRENCES),
							RefactoringScopeFactory.create(field, true),
							new NullProgressMonitor(), result, true);

			if (result.hasFatalError())
				return result;

			for (ICompilationUnit unit : affectedCUs) {
				ThreadLocalVisitor visitor = new ThreadLocalVisitor(
						(IVariableBinding) binding);
				ASTNode rootCU = getNodeFromCU(unit);

				rootCU.accept(visitor);
				references = visitor.getReferences();
				declaration = visitor.getDeclaratation();

				rewrite = ASTRewrite.create(rootCU.getAST());
				tree = rootCU.getAST();

				if (declaration != null)
					convertDeclaration(declaration, rewrite);

				for (Expression e : references) {
					if (e instanceof MethodInvocation) {
						replaceMethodInvocationWithGet(tree, rewrite, e);
					} else if (e instanceof Name) {
						if (isLastInLHSAssignment((Name) e)) {
							if (!isInConstructor((Name) e)) {
								replaceAssignmentWithSet(tree, rewrite,
										getAssignment(e));
							} else {
								replaceAssignmentWithNew(tree, rewrite,
										getAssignment(e));
							}
						} else {
							replaceAccessWithGet(tree, rewrite, e);
						}
					}
				}

				regiterChange(unit, rewrite);
			}

		} finally {
			pm.done();
		}
		return result;
	}

	private void makeUsedArgumentsFinal(ASTRewrite rewrite,
			Assignment assignment) {
		Expression rhs = assignment.getRightHandSide();
		AST ast = rewrite.getAST();

		if (!(rhs instanceof SimpleName))
			return;

		SimpleName name = (SimpleName) rhs;

		MethodDeclaration decl = getMethodDeclaration(assignment);
		List<SingleVariableDeclaration> params = decl.parameters();
		for (SingleVariableDeclaration var : params) {
			if (var.resolveBinding().isEqualTo(name.resolveBinding())) {
				SingleVariableDeclaration newDecl = (SingleVariableDeclaration) ASTNode
						.copySubtree(ast, var);
				if ((newDecl.getModifiers() & Modifier.FINAL) == 0) {
					newDecl.modifiers().add(
							ast.newModifier(ModifierKeyword.FINAL_KEYWORD));
					rewrite.replace(var, newDecl, null);
				}
			}
		}
	}

	private Assignment getAssignment(ASTNode e) {
		if (e == null)
			return null;
		if (e instanceof Assignment)
			return (Assignment) e;
		return getAssignment(e.getParent());
	}

	private void replaceAssignmentWithNew(AST ast, ASTRewrite rewrite,
			Assignment a) {

		ITypeBinding binding = a.getRightHandSide().resolveTypeBinding();
		String typeName = binding.getName();

		typeName = getWrapperClassName(typeName);

		ClassInstanceCreation newInstance = ast.newClassInstanceCreation();
		ParameterizedType type = ast.newParameterizedType(ast.newSimpleType(ast
				.newSimpleName("ThreadLocal")));
		type.typeArguments()
				.add(ast.newSimpleType(ast.newSimpleName(typeName)));

		newInstance.setType(type);
		AnonymousClassDeclaration newInitializer = ast
				.newAnonymousClassDeclaration();

		MethodDeclaration decl = ast.newMethodDeclaration();
		decl.setReturnType2(ast.newSimpleType(ast.newSimpleName(typeName)));
		decl.setName(ast.newSimpleName("initialValue"));
		decl.modifiers()
				.add(ast.newModifier(ModifierKeyword.PROTECTED_KEYWORD));

		Block block = ast.newBlock();
		ReturnStatement rt = ast.newReturnStatement();
		rt.setExpression((Expression) ASTNode.copySubtree(ast,
				a.getRightHandSide()));
		block.statements().add(rt);
		decl.setBody(block);

		newInitializer.bodyDeclarations().add(decl);
		newInstance.setAnonymousClassDeclaration(newInitializer);

		rewrite.replace(a.getRightHandSide(), newInstance, null);

		makeUsedArgumentsFinal(rewrite, a);
	}

	private String getWrapperClassName(String typeName) {
		if (typeName.equals("int"))
			return "Integer";
		if (typeName.equals("boolean"))
			return "Boolean";
		if (typeName.equals("float"))
			return "Float";
		if (typeName.equals("double"))
			return "Double";
		if (typeName.equals("short"))
			return "Short";
		if (typeName.equals("byte"))
			return "Byte";
		if (typeName.equals("char"))
			return "Char";
		if (typeName.equals("long"))
			return "Long";
		return typeName;
	}

	private void replaceMethodInvocationWithGet(AST tree, ASTRewrite rewrite,
			Expression e) {
		MethodInvocation m = (MethodInvocation) e;
		MethodInvocation newm = tree.newMethodInvocation();
		Expression expr = (Expression) ASTNode.copySubtree(tree,
				m.getExpression());
		newm.setExpression(expr);
		newm.setName(tree.newSimpleName("get"));
		rewrite.replace(m.getExpression(), newm, null);
	}

	private void replaceAccessWithGet(AST tree, ASTRewrite rewrite, Expression e) {
		MethodInvocation newm = tree.newMethodInvocation();
		Expression expr = (Expression) ASTNode.copySubtree(tree, e);
		newm.setExpression(expr);
		newm.setName(tree.newSimpleName("get"));
		rewrite.replace(e, newm, null);
	}

	private void replaceAssignmentWithSet(AST tree, ASTRewrite rewrite,
			Assignment a) {
		MethodInvocation newNode = tree.newMethodInvocation();
		newNode.setName(tree.newSimpleName("set"));
		List args = newNode.arguments();
		Expression newLHS = (Expression) ASTNode.copySubtree(tree,
				a.getLeftHandSide());
		newNode.setExpression(newLHS);
		Expression rhs = a.getRightHandSide();
		Expression newRHS = (Expression) ASTNode.copySubtree(tree, rhs);
		if (a.getOperator().equals(Operator.ASSIGN)) {
			args.add(newRHS);
		} else {
			InfixExpression infix = tree.newInfixExpression();
			infix.setRightOperand(newRHS);
			MethodInvocation invoc = tree.newMethodInvocation();
			invoc.setExpression((Expression) ASTNode.copySubtree(tree, a.getLeftHandSide()));
			invoc.setName(tree.newSimpleName("get"));
			infix.setLeftOperand(invoc);
			infix.setOperator(getSimpleOperator(a.getOperator()));
			args.add(infix);
		}
		
		rewrite.replace(a, newNode, null);
	}

	private org.eclipse.jdt.core.dom.InfixExpression.Operator getSimpleOperator(
			Operator operator) {
		if (operator.equals(Operator.PLUS_ASSIGN))
			return org.eclipse.jdt.core.dom.InfixExpression.Operator.PLUS;
		if (operator.equals(Operator.MINUS_ASSIGN))
			return org.eclipse.jdt.core.dom.InfixExpression.Operator.MINUS;
		if (operator.equals(Operator.TIMES_ASSIGN))
			return org.eclipse.jdt.core.dom.InfixExpression.Operator.TIMES;
		if (operator.equals(Operator.DIVIDE_ASSIGN))
			return org.eclipse.jdt.core.dom.InfixExpression.Operator.DIVIDE;
		if (operator.equals(Operator.BIT_AND_ASSIGN))
			return org.eclipse.jdt.core.dom.InfixExpression.Operator.AND;
		if (operator.equals(Operator.BIT_OR_ASSIGN))
			return org.eclipse.jdt.core.dom.InfixExpression.Operator.OR;
		if (operator.equals(Operator.BIT_XOR_ASSIGN))
			return org.eclipse.jdt.core.dom.InfixExpression.Operator.XOR;
		if (operator.equals(Operator.REMAINDER_ASSIGN))
			return org.eclipse.jdt.core.dom.InfixExpression.Operator.REMAINDER;
		if (operator.equals(Operator.LEFT_SHIFT_ASSIGN))
			return org.eclipse.jdt.core.dom.InfixExpression.Operator.LEFT_SHIFT;
		if (operator.equals(Operator.RIGHT_SHIFT_SIGNED_ASSIGN))
			return org.eclipse.jdt.core.dom.InfixExpression.Operator.RIGHT_SHIFT_SIGNED;
		if (operator.equals(Operator.RIGHT_SHIFT_UNSIGNED_ASSIGN))
			return org.eclipse.jdt.core.dom.InfixExpression.Operator.RIGHT_SHIFT_UNSIGNED;
		return null;
	}

	private void regiterChange(ICompilationUnit unit, ASTRewrite rewrite) {
		TextChange change = changeManager.get(unit);

		try {
			if (change.getEdit() == null)
				change.setEdit(rewrite.rewriteAST());
			else
				change.addEdit(rewrite.rewriteAST());
		} catch (JavaModelException e) {
			e.printStackTrace();
		} catch (IllegalArgumentException e) {
			e.printStackTrace();
		}
	}

	private boolean isInConstructor(Name e) {
		MethodDeclaration method = getMethodDeclaration(e);
		if (method != null && method.isConstructor())
			return true;
		return false;
	}

	private MethodDeclaration getMethodDeclaration(ASTNode e) {
		if (e == null)
			return null;
		if (e instanceof MethodDeclaration)
			return (MethodDeclaration) e;
		else
			return getMethodDeclaration(e.getParent());
	}

	private boolean isLastInLHSAssignment(Name e) {
		ASTNode parent = e.getParent();
		IVariableBinding varBind = (IVariableBinding) e.resolveBinding();
		while (parent != null) {
			if (parent instanceof Assignment) {
				Expression expr = ((Assignment) parent).getLeftHandSide();
				if (expr instanceof FieldAccess) {
					FieldAccess f = (FieldAccess) expr;
					if (f.resolveFieldBinding().isEqualTo(varBind))
						return true;
				} else if (expr instanceof Name) {
					Name n = (Name) expr;
					if (n.resolveBinding().isEqualTo(varBind))
						return true;
					else
						return false;
				}
			} else
				parent = parent.getParent();
		}
		return false;
	}

	private void convertDeclaration(
			VariableDeclarationFragment declarationFragment, ASTRewrite rewrite) {

		FieldDeclaration declaration = (FieldDeclaration) declarationFragment
				.getParent();
		Type type = declaration.getType();
		AST ast = rewrite.getAST();
		VariableDeclarationFragment newFragment = ast
				.newVariableDeclarationFragment();
		newFragment.setName((SimpleName) ASTNode.copySubtree(ast,
				declarationFragment.getName()));
		FieldDeclaration newDecl = ast.newFieldDeclaration(newFragment);
		ParameterizedType tl = ast.newParameterizedType(ast.newSimpleType(ast
				.newSimpleName("ThreadLocal")));
		List<Type> typeArgs = tl.typeArguments();
		int oldModifiers = declaration.getModifiers();

		Type newType = null;

		if (type.isPrimitiveType()) {
			newType = boxTypes(type, ast);
		} else
			newType = (Type) type.copySubtree(ast, type);

		typeArgs.add(newType);
		newDecl.setType(tl);
		newDecl.modifiers().addAll(ast.newModifiers(oldModifiers));

		if (declarationFragment.getInitializer() != null)
			transferInitializer(declarationFragment, newFragment, rewrite);
		else
			addDefaultInitializer(declarationFragment, newFragment, rewrite);

		if (declaration.fragments().size() == 1)
			rewrite.replace(declaration, newDecl, null);
		else {
			rewrite.remove(declarationFragment, null);
			ASTNode parent = declaration.getParent();
			ListRewrite lr = rewrite.getListRewrite(declaration.getParent(),
					TypeDeclaration.BODY_DECLARATIONS_PROPERTY);
			List bla = lr.getOriginalList();
			lr.insertAfter(newDecl, declaration, null);
		}
	}

	private void addDefaultInitializer(
			VariableDeclarationFragment declarationFragment,
			VariableDeclarationFragment newFragment, ASTRewrite rewrite) {

		AST ast = rewrite.getAST();
		ClassInstanceCreation newInstance = ast.newClassInstanceCreation();
		ParameterizedType type = ast.newParameterizedType(ast.newSimpleType(ast
				.newSimpleName("ThreadLocal")));
		Type declType = ((FieldDeclaration) declarationFragment.getParent())
				.getType();

		Type newType;
		if (declType.isPrimitiveType())
			newType = boxTypes(declType, ast);
		else
			newType = (Type) declType.copySubtree(ast, declType);

		type.typeArguments().add(newType);
		newInstance.setType(type);

		newFragment.setInitializer(newInstance);
	}

	private Type boxTypes(Type type, AST ast) {
		PrimitiveType pType = (PrimitiveType) type;
		Type newBoxedType = null;
		if (pType.getPrimitiveTypeCode().equals(PrimitiveType.BOOLEAN))
			newBoxedType = ast.newSimpleType(ast.newSimpleName("Boolean"));
		else if (pType.getPrimitiveTypeCode().equals(PrimitiveType.INT))
			newBoxedType = ast.newSimpleType(ast.newSimpleName("Integer"));
		else if (pType.getPrimitiveTypeCode().equals(PrimitiveType.BYTE))
			newBoxedType = ast.newSimpleType(ast.newSimpleName("Byte"));
		else if (pType.getPrimitiveTypeCode().equals(PrimitiveType.CHAR))
			newBoxedType = ast.newSimpleType(ast.newSimpleName("Char"));
		else if (pType.getPrimitiveTypeCode().equals(PrimitiveType.DOUBLE))
			newBoxedType = ast.newSimpleType(ast.newSimpleName("Double"));
		else if (pType.getPrimitiveTypeCode().equals(PrimitiveType.FLOAT))
			newBoxedType = ast.newSimpleType(ast.newSimpleName("Float"));
		else if (pType.getPrimitiveTypeCode().equals(PrimitiveType.LONG))
			newBoxedType = ast.newSimpleType(ast.newSimpleName("Long"));
		else if (pType.getPrimitiveTypeCode().equals(PrimitiveType.SHORT))
			newBoxedType = ast.newSimpleType(ast.newSimpleName("Short"));
		return newBoxedType;
	}

	private void transferInitializer(
			VariableDeclarationFragment declarationFragment,
			VariableDeclarationFragment newFragment, ASTRewrite rewrite) {

		Expression initializingExpression = declarationFragment
				.getInitializer();
		AST ast = rewrite.getAST();
		ClassInstanceCreation newInstance = ast.newClassInstanceCreation();
		ParameterizedType type = ast.newParameterizedType(ast.newSimpleType(ast
				.newSimpleName("ThreadLocal")));
		Type declType = ((FieldDeclaration) declarationFragment.getParent())
				.getType();

		Type newType;
		if (declType.isPrimitiveType())
			newType = boxTypes(declType, ast);
		else
			newType = (Type) declType.copySubtree(ast, declType);

		type.typeArguments().add(newType);
		newInstance.setType(type);
		AnonymousClassDeclaration newInitializer = ast
				.newAnonymousClassDeclaration();

		MethodDeclaration decl = ast.newMethodDeclaration();
		decl.setReturnType2((Type) newType.copySubtree(ast, newType));
		decl.setName(ast.newSimpleName("initialValue"));
		decl.modifiers()
				.add(ast.newModifier(ModifierKeyword.PROTECTED_KEYWORD));

		Block block = ast.newBlock();
		ReturnStatement rt = ast.newReturnStatement();
		rt.setExpression((Expression) ASTNode.copySubtree(ast,
				initializingExpression));
		block.statements().add(rt);
		decl.setBody(block);

		newInitializer.bodyDeclarations().add(decl);
		newInstance.setAnonymousClassDeclaration(newInitializer);
		newFragment.setInitializer(newInstance);
	}

	@Override
	public RefactoringStatus checkInitialConditions(IProgressMonitor pm)
			throws CoreException, OperationCanceledException {
		try {
			pm.beginTask("Checking initial conditions", 1);
			IType type = field.getDeclaringType();
			if (type == null)
				return RefactoringStatus
						.createFatalErrorStatus("No type found");
			if (!type.isBinary() && !type.isStructureKnown())
				return RefactoringStatus
						.createFatalErrorStatus("Compilation Unit has compilation erros");
			if (!type.isClass())
				return RefactoringStatus
						.createFatalErrorStatus("Type must be a class");
		} finally {
			pm.done();
		}
		return new RefactoringStatus();
	}

	@Override
	public Change createChange(IProgressMonitor arg0) throws CoreException,
			OperationCanceledException {
		CompositeChange change = new CompositeChange("");

		TextChange[] changes = changeManager.getAllChanges();

		change.addAll(changes);

		return change;
	}

	@Override
	public String getName() {
		return "Convert Field to Thread Local";
	}

	private ASTNode getNodeFromCU(ICompilationUnit cu) {
		ASTParser parser = ASTParser.newParser(AST.JLS3);
		parser.setSource(cu);
		parser.setKind(ASTParser.K_COMPILATION_UNIT);
		parser.setResolveBindings(true);
		ASTNode node = parser.createAST(new NullProgressMonitor());
		return node;
	}

}
