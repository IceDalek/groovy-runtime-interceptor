package sandbox;

import org.codehaus.groovy.ast.ClassNode;
import org.codehaus.groovy.ast.expr.CastExpression;
import org.codehaus.groovy.ast.expr.ClassExpression;
import org.codehaus.groovy.ast.expr.ConstructorCallExpression;
import org.codehaus.groovy.ast.expr.Expression;
import org.codehaus.groovy.control.customizers.SecureASTCustomizer;

import java.util.Set;

public class ClassWhitelistChecker implements SecureASTCustomizer.ExpressionChecker {

    private final Set<String> allowed;

    public ClassWhitelistChecker(Set<String> allowed) {
        this.allowed = allowed;
    }

    private boolean typeOk(ClassNode t) {
        if (t == null) return true;
        if (t.isArray()) return typeOk(t.getComponentType());   // File[] -> проверяем File
        return allowed.contains(t.getName());
    }

    @Override
    public boolean isAuthorized(Expression exp) {
        if (exp instanceof ConstructorCallExpression
                && !((ConstructorCallExpression) exp).isSpecialCall()) {
            return typeOk(exp.getType());                        // new File(...)
        }
        if (exp instanceof ClassExpression) {
            return typeOk(exp.getType());
        }
        if (exp instanceof CastExpression) {
            return typeOk(exp.getType());
        }
        return true;
    }
}