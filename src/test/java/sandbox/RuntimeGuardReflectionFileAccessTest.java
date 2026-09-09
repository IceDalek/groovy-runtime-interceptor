package sandbox;

import groovy.lang.Binding;
import groovy.lang.Closure;
import groovy.lang.GroovyShell;
import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static sandbox.TestGuards.instancePolicy;

/**
 * File access is not itself special-cased anywhere in {@link RuntimeGuard} - it is denied
 * because {@link File} was never added to {@code ALLOWED_CLASSES}. These tests walk through
 * the ways a script could try to reach the filesystem anyway, including via reflection, and
 * confirm each one is still stopped.
 */
class RuntimeGuardReflectionFileAccessTest {

    @BeforeEach
    void installGuard() {
        GuardHolder.set(TestGuards.fresh());
    }

    @AfterEach
    void removeGuard() {
        GuardHolder.set(null);
    }

    @Test
    void directFileConstructionIsRejected() {
        GroovyShell shell = GuardedShellFactory.create();
        SecurityException ex = assertThrows(SecurityException.class,
                () -> shell.evaluate("new java.io.File('/etc/passwd')"));
        assertTrue(ex.getMessage().contains("java.io.File"), ex.getMessage());
    }

    @Test
    void classForNameIsRejectedByDefaultBecauseJavaLangClassIsNotWhitelisted() {
        // forName is not special-cased by name anywhere in RuntimeGuard - it is denied because
        // it resolves to java.lang.Class, and that class is never a key in DefaultAllowlist -
        // loading java.io.File by name and then reflectively instantiating it is the textbook
        // sandbox bypass.
        GroovyShell shell = GuardedShellFactory.create();
        SecurityException ex = assertThrows(SecurityException.class, () -> shell.evaluate(
                "Class.forName('java.io.File').getConstructor(String).newInstance('/etc/passwd')"));
        assertTrue(ex.getMessage().contains("forName"), ex.getMessage());
    }

    @Test
    void whitelistingJavaLangClassGrantsForNameAndGetClassLoaderToo() {
        // the whitelist is authoritative, with no hidden per-name override standing behind it:
        // whitelisting java.lang.Class opens all of it, forName/getClassLoader included, the
        // same as whitelisting any other class does for its own methods. A deployment that wants
        // java.lang.Class trusted has to mean it. Whitelisted for both instance and static
        // calls, since getName() is an instance method of Class while forName() is static.
        GuardHolder.set(new RuntimeGuard(Map.of("java.lang.Class",
                new ClassSecurityPolicy(new MethodAccessPolicy(Set.of(), null), new MethodAccessPolicy(Set.of(), null)))));
        GroovyShell shell = GuardedShellFactory.create();

        assertEquals("java.lang.String", shell.evaluate("String.class.getName()"));
        assertEquals(File.class, shell.evaluate("Class.forName('java.io.File')"));
    }

    @Test
    void restrictingClassToGetNameStillDeniesGetClassLoaderThroughARealCompiledScript() {
        // end-to-end through GuardedShellFactory/InterceptCustomizer, not just a direct
        // guard.checkedCall - String is whitelisted with no restriction, java.lang.Class is
        // whitelisted but restricted to {"getName"} only. String's own unrestricted status has
        // no bearing on what's allowed on java.lang.Class - getClassLoader()'s declaring class
        // is java.lang.Class regardless of what value the receiver holds, so it's checked
        // against Class's own {"getName"} set and loses.
        GuardHolder.set(new RuntimeGuard(Map.of(
                "java.lang.String", instancePolicy(Set.of(), null),
                "java.lang.Class", instancePolicy(Set.of("getName"), null))));
        GroovyShell shell = GuardedShellFactory.create();

        assertEquals("java.lang.String", shell.evaluate("String.class.getName()"));

        SecurityException ex = assertThrows(SecurityException.class,
                () -> shell.evaluate("String.class.getClassLoader()"));
        assertTrue(ex.getMessage().contains("java.lang.Class.getClassLoader"), ex.getMessage());
    }

    @Test
    void getClassIsRejectedByDefaultBecauseJavaLangObjectIsNotWhitelisted() {
        // getClass() is the usual first hop toward getClassLoader()/reflection; it always
        // resolves to java.lang.Object (verified via pickMethod introspection - it can't be
        // overridden away from Object), and Object is never a key in DefaultAllowlist.
        GroovyShell shell = GuardedShellFactory.create();
        SecurityException ex = assertThrows(SecurityException.class,
                () -> shell.evaluate("'hello'.getClass()"));
        assertTrue(ex.getMessage().contains("getClass"), ex.getMessage());
    }

    @Test
    void dynamicallyComputedMethodNameIsCheckedByItsResolvedValueNotItsSourceSpelling() {
        // ExpressionCheckerVsRuntimeGuardTest.dynamicallyComputedMethodNameDefeatsTheEnhancedDenylistCompletely
        // shows a compile-time AST checker - even one with a DENIED_METHODS-style denylist -
        // can't catch this: 'hello'."$n"() compiles to a MethodCallExpression whose method-name
        // expression is a GString, not a literal it could compare against anything. RuntimeGuard
        // doesn't care how the name was spelled in the source: InterceptCustomizer passes
        // whatever expression it is straight through, and RuntimeGuard.checkedCall resolves it
        // with String.valueOf(methodName) before checking - so it sees the real string
        // "getClass" regardless of whether the source wrote that literally or computed it.
        GroovyShell shell = GuardedShellFactory.create();
        SecurityException ex = assertThrows(SecurityException.class,
                () -> shell.evaluate("def n = 'getClass'; 'hello'.\"$n\"()"));
        assertTrue(ex.getMessage().contains("getClass"), ex.getMessage());
    }

    @Test
    void classObjectObtainedWithoutForNameStillCannotReachReflectionApi() {
        // Even if a script gets hold of a java.lang.Class *value* through some other route
        // (bound in here directly, standing in for e.g. a whitelisted API that leaks one),
        // calling reflection methods on it is blocked because java.lang.Class itself is
        // never whitelisted - only calls that resolve to a *static* member of the class it
        // represents are treated specially.
        Binding binding = new Binding();
        binding.setVariable("fileClass", File.class);
        GroovyShell shell = GuardedShellFactory.create(binding);

        SecurityException ex = assertThrows(SecurityException.class,
                () -> shell.evaluate("fileClass.getConstructor(String).newInstance('/etc/passwd')"));
        assertTrue(ex.getMessage().contains("java.lang.Class"), ex.getMessage());
    }

    @Test
    void preBoundReflectiveMethodObjectCannotBeInvoked() throws Exception {
        // Defense in depth: even if a script already holds a live java.lang.reflect.Method
        // (handed to it, not obtained itself - obtaining one is blocked separately by the
        // getClass/getMethod checks above), invoking it is still denied because
        // java.lang.reflect.Method is not in ALLOWED_CLASSES.
        Method exists = File.class.getMethod("exists");
        Binding binding = new Binding();
        binding.setVariable("m", exists);
        binding.setVariable("f", new File("."));
        GroovyShell shell = GuardedShellFactory.create(binding);

        SecurityException ex = assertThrows(SecurityException.class, () -> shell.evaluate("m.invoke(f)"));
        assertTrue(ex.getMessage().contains("java.lang.reflect.Method"), ex.getMessage());
    }

    @Test
    void preBoundReflectiveConstructorCannotBeInvoked() throws Exception {
        Constructor<File> ctor = File.class.getConstructor(String.class);
        Binding binding = new Binding();
        binding.setVariable("ctor", ctor);
        GroovyShell shell = GuardedShellFactory.create(binding);

        SecurityException ex = assertThrows(SecurityException.class,
                () -> shell.evaluate("ctor.newInstance('/etc/passwd')"));
        assertTrue(ex.getMessage().contains("java.lang.reflect.Constructor"), ex.getMessage());
    }

    @Test
    void methodPointerCaptureIsStillCheckedWhenInvoked() {
        // f.&exists captures a deferred call rather than invoking exists() immediately. The
        // script hands the closure back out instead of calling it itself - calling it *inside*
        // the script wouldn't isolate anything, since that call is its own MethodCallExpression
        // and goes through the guard regardless of how the closure was built. Invoking the
        // returned closure from plain Java, with no Groovy compilation involved at all, is what
        // actually exercises whether construction produced a GuardedMethodClosure or - if
        // InterceptCustomizer didn't rewrite MethodPointerExpression - a plain, unguarded
        // groovy.lang.MethodClosure that would just run exists() for real.
        Binding binding = new Binding();
        binding.setVariable("f", new File("."));
        GroovyShell shell = GuardedShellFactory.create(binding);

        Object result = shell.evaluate("f.&exists");
        assertTrue(result instanceof Closure, "expected a Closure, got " + result);
        Closure<?> ref = (Closure<?>) result;

        SecurityException ex = assertThrows(SecurityException.class, ref::call);
        assertTrue(ex.getMessage().contains("java.io.File"), ex.getMessage());
    }

    @Test
    void preBoundFileInstanceIsDeniedByDefaultEvenForHarmlessMethods() {
        // The model is default-deny, not "block dangerous methods": a File that reached the
        // script by some other means still can't have any method called on it, because File
        // was never added to ALLOWED_CLASSES.
        Binding binding = new Binding();
        binding.setVariable("f", new File("."));
        GroovyShell shell = GuardedShellFactory.create(binding);

        SecurityException ex = assertThrows(SecurityException.class, () -> shell.evaluate("f.exists()"));
        assertTrue(ex.getMessage().contains("java.io.File"), ex.getMessage());
    }
}
