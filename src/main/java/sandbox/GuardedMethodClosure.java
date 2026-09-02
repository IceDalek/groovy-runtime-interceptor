package sandbox;

import org.codehaus.groovy.runtime.InvokerHelper;
import org.codehaus.groovy.runtime.InvokerInvocationException;
import org.codehaus.groovy.runtime.MethodClosure;

/** Replacement for {@code a.&m}, so the deferred call also goes through the guard. */
public class GuardedMethodClosure extends MethodClosure {

    public GuardedMethodClosure(Object target, Object method) {
        super(target, String.valueOf(method));
    }

    protected Object doCall(Object[] args) {
        try {
            return RuntimeGuard.checkedCall(getOwner(), false, false, getMethod(), args);
        } catch (Throwable e) {
            throw new InvokerInvocationException(e);
        }
    }

    protected Object doCall() {
        return doCall(new Object[0]);
    }

    @Override
    protected Object doCall(Object arg) {
        return doCall(InvokerHelper.asArray(arg));
    }
}
