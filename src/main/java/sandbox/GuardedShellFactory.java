package sandbox;

import groovy.lang.Binding;
import groovy.lang.GroovyClassLoader;
import groovy.lang.GroovyShell;
import org.codehaus.groovy.control.CompilerConfiguration;

/**
 * Builds a {@link GroovyShell} wired with {@link InterceptCustomizer} and {@link SecureScript}
 * as the compiled scripts' base class.
 *
 * <p>Doesn't touch {@link RuntimeGuard} itself: it's a Spring singleton, so exactly one
 * instance exists for the whole JVM and it registers itself with {@link GuardHolder} on
 * construction. Evaluating a script before the Spring context has started (so before any
 * {@link RuntimeGuard} bean exists) fails closed with a {@link SecurityException} - see
 * {@link GuardHolder#get()}.
 */
public final class GuardedShellFactory {

    private GuardedShellFactory() {}

    public static GroovyShell create() {
        return create(new Binding());
    }

    public static GroovyShell create(Binding binding) {
        CompilerConfiguration cc = new CompilerConfiguration();
        cc.setScriptBaseClass(SecureScript.class.getName());
        cc.addCompilationCustomizers(new InterceptCustomizer());
        GroovyClassLoader loader = new GroovyClassLoader(GuardedShellFactory.class.getClassLoader(), cc);
        return new GroovyShell(loader, binding, cc);
    }
}
