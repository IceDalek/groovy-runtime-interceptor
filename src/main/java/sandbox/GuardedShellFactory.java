package sandbox;

import groovy.lang.Binding;
import groovy.lang.GroovyClassLoader;
import groovy.lang.GroovyShell;
import org.codehaus.groovy.control.CompilerConfiguration;

/** Builds a {@link GroovyShell} wired with {@link InterceptCustomizer}. */
public final class GuardedShellFactory {

    private GuardedShellFactory() {}

    public static GroovyShell create() {
        return create(new Binding());
    }

    public static GroovyShell create(Binding binding) {
        CompilerConfiguration cc = new CompilerConfiguration();
        cc.addCompilationCustomizers(new InterceptCustomizer());
        GroovyClassLoader loader = new GroovyClassLoader(GuardedShellFactory.class.getClassLoader(), cc);
        return new GroovyShell(loader, binding, cc);
    }
}
