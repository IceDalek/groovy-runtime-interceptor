package sandbox;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.codehaus.groovy.ast.ClassCodeExpressionTransformer;
import org.codehaus.groovy.ast.ClassHelper;
import org.codehaus.groovy.ast.ClassNode;
import org.codehaus.groovy.ast.ConstructorNode;
import org.codehaus.groovy.ast.FieldNode;
import org.codehaus.groovy.ast.MethodNode;
import org.codehaus.groovy.ast.expr.ArgumentListExpression;
import org.codehaus.groovy.ast.expr.ClassExpression;
import org.codehaus.groovy.ast.expr.ClosureExpression;
import org.codehaus.groovy.ast.expr.ConstantExpression;
import org.codehaus.groovy.ast.expr.ConstructorCallExpression;
import org.codehaus.groovy.ast.expr.Expression;
import org.codehaus.groovy.ast.expr.ListExpression;
import org.codehaus.groovy.ast.expr.MethodCallExpression;
import org.codehaus.groovy.ast.expr.MethodPointerExpression;
import org.codehaus.groovy.ast.expr.StaticMethodCallExpression;
import org.codehaus.groovy.ast.expr.TupleExpression;
import org.codehaus.groovy.ast.stmt.Statement;
import org.codehaus.groovy.classgen.GeneratorContext;
import org.codehaus.groovy.control.CompilePhase;
import org.codehaus.groovy.control.SourceUnit;
import org.codehaus.groovy.control.customizers.CompilationCustomizer;

/**
 * Rewrites method/constructor calls into calls on the active {@link RuntimeGuard}, resolved
 * through {@link GuardHolder}, on the {@code CANONICALIZATION} compile phase (after parsing,
 * before real bytecode generation). Runs against Groovy 3.x/4.x/5.x - the AST package
 * ({@code org.codehaus.groovy.ast}) used here is the one Groovy 3+ still ships and keeps
 * binary-stable across those major versions.
 */
public class InterceptCustomizer extends CompilationCustomizer {

    private static final ClassNode HOLDER = ClassHelper.make(GuardHolder.class);
    private static final ClassNode METHOD_CLOSURE = ClassHelper.make(GuardedMethodClosure.class);

    public InterceptCustomizer() {
        super(CompilePhase.CANONICALIZATION);
    }

    @Override
    public void call(final SourceUnit source, GeneratorContext ctx, ClassNode classNode) {
        if (Objects.isNull(classNode)) return;

        ClassCodeExpressionTransformer tr = new ClassCodeExpressionTransformer() {

            @Override
            protected SourceUnit getSourceUnit() { return source; }

            @Override
            public Expression transform(Expression exp) {
                if (Objects.isNull(exp)) return null;
                Expression out = doTransform(exp);
                if (out != exp) out.setSourcePosition(exp);
                return out;
            }

            private Expression doTransform(Expression exp) {

                // ClosureExpression.transformExpression does not walk into the closure body.
                if (exp instanceof ClosureExpression) {
                    ((ClosureExpression) exp).getCode().visit(this);
                    return exp;
                }

                // a.m(x)  ->  RuntimeGuard.checkedCall(a, safe, spread, "m", [x].toArray())
                if (exp instanceof MethodCallExpression) {
                    MethodCallExpression c = (MethodCallExpression) exp;
                    return guard("checkedCall",
                            transform(c.getObjectExpression()),
                            bool(c.isSafe()),
                            bool(c.isSpreadSafe()),
                            transform(c.getMethod()),
                            packArgs(c.getArguments()));
                }

                // static imports
                if (exp instanceof StaticMethodCallExpression) {
                    StaticMethodCallExpression c = (StaticMethodCallExpression) exp;
                    return guard("checkedStaticCall",
                            new ClassExpression(c.getOwnerType()),
                            new ConstantExpression(c.getMethod()),
                            packArgs(c.getArguments()));
                }

                // new Foo(x)  ->  RuntimeGuard.checkedConstructor(Foo, [x].toArray())
                if (exp instanceof ConstructorCallExpression) {
                    ConstructorCallExpression c = (ConstructorCallExpression) exp;
                    if (c.isSpecialCall()) {
                        // super(...)/this(...) can't be intercepted: the JVM requires it to be
                        // the first statement. Only the arguments get walked.
                        return super.transform(exp);
                    }
                    return guard("checkedConstructor",
                            new ClassExpression(c.getType()),
                            packArgs(c.getArguments()));
                }

                // a.&m  ->  new GuardedMethodClosure(a, "m")
                if (exp instanceof MethodPointerExpression) {
                    MethodPointerExpression mp = (MethodPointerExpression) exp;
                    return new ConstructorCallExpression(METHOD_CLOSURE,
                            new ArgumentListExpression(
                                    transform(mp.getExpression()),
                                    transform(mp.getMethodName())));
                }

                return super.transform(exp);
            }

            /** {@code GuardHolder.get().<name>(args)} */
            private Expression guard(String name, Expression... args) {
                Expression guardInstance = new StaticMethodCallExpression(HOLDER, "get",
                        ArgumentListExpression.EMPTY_ARGUMENTS);
                return new MethodCallExpression(guardInstance, name, new ArgumentListExpression(args));
            }

            private ConstantExpression bool(boolean v) {
                return v ? ConstantExpression.PRIM_TRUE : ConstantExpression.PRIM_FALSE;
            }

            /** call arguments -> Object[] */
            private Expression packArgs(Expression args) {
                List<Expression> l = new ArrayList<>();
                if (args instanceof TupleExpression) {
                    for (Expression e : (TupleExpression) args) l.add(transform(e));
                } else {
                    l.add(transform(args));
                }
                return new MethodCallExpression(new ListExpression(l),
                        "toArray", ArgumentListExpression.EMPTY_ARGUMENTS);
            }
        };

        classNode.getMethods().forEach(tr::visitMethod);
        classNode.getDeclaredConstructors().forEach(tr::visitConstructor);
        classNode.getFields().forEach(tr::visitField);
        classNode.getObjectInitializerStatements().forEach(s -> s.visit(tr));
    }
}
