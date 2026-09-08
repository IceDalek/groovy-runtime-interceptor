package sandbox;

import groovy.lang.Binding;
import groovy.lang.GroovyShell;
import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end: script text -> InterceptCustomizer -> RuntimeGuard, through a real GroovyShell.
 * No Spring context here, so the bean lifecycle that normally installs the guard is stood in
 * for directly - see {@link RuntimeGuardSpringIntegrationTest} for the real wiring.
 */
class RuntimeGuardScriptTest {

    private final GroovyShell shell = GuardedShellFactory.create();

    @BeforeEach
    void installGuard() {
        GuardHolder.set(TestGuards.fresh());
    }

    @AfterEach
    void removeGuard() {
        GuardHolder.set(null);
    }

    @Test
    void compiledScriptExtendsSecureScriptNotPlainGroovyScript() {
        // Groovy still auto-names the generated class (Script1, Script2, ...) - setting
        // scriptBaseClass only changes what it extends, wired in GuardedShellFactory
        Object result = shell.evaluate("this");
        assertTrue(result instanceof SecureScript, "expected a SecureScript, got " + result.getClass());
        assertEquals(SecureScript.class, result.getClass().getSuperclass());
    }

    @Test
    void whitelistedStringAndCollectionOpsWork() {
        assertEquals("HELLO", shell.evaluate("'hello'.toUpperCase()"));

        @SuppressWarnings("unchecked")
        List<Integer> doubled = (List<Integer>) shell.evaluate("[1, 2, 3].collect { it * 2 }");
        assertEquals(List.of(2, 4, 6), doubled);
    }

    @Test
    void whitelistedConstructorWorks() {
        Object result = shell.evaluate("def l = new ArrayList(); l.add('x'); l");
        assertEquals(List.of("x"), result);
    }

    @Test
    void nonWhitelistedConstructorIsRejected() {
        SecurityException ex = assertThrows(SecurityException.class,
                () -> shell.evaluate("new java.io.File('/tmp/should-not-be-reachable')"));
        assertTrue(ex.getMessage().contains("java.io.File"), ex.getMessage());
    }

    @Test
    void staticCallOnNonWhitelistedClassIsRejected() {
        // proves this is checked without ever letting System.exit actually run
        assertThrows(SecurityException.class, () -> shell.evaluate("System.exit(1)"));
    }

    @Test
    void stringExecuteExtensionMethodIsRejectedDespiteStringBeingWhitelisted() {
        // Groovy's ProcessGroovyMethods registers execute() as an extension on String, with
        // declaringClass reported as java.lang.String itself - so without "execute" in
        // RuntimeGuard's deniedMethods, this would launch a real OS process. Must throw before
        // that happens, not after - otherwise this test spawns a process instead of proving
        // the check happens.
        SecurityException ex = assertThrows(SecurityException.class,
                () -> shell.evaluate("'whoami'.execute()"));
        assertTrue(ex.getMessage().contains("execute"), ex.getMessage());
    }

    @Test
    void dynamicallySpelledExecuteIsDeniedJustAsWellAsTheLiteralSpelling() {
        // ExpressionCheckerVsRuntimeGuardTest.patchingTheDenylistForOneMoreNameDoesNotSurviveTheSameDynamicNameTrick
        // shows patching "execute" into an AST-only denylist doesn't survive this. RuntimeGuard
        // isn't checking a string it saw in the source at all - InterceptCustomizer passes
        // whatever expression computed the method name straight through, and RuntimeGuard
        // resolves it with String.valueOf(methodName) before checking. Literal or computed,
        // by the time the check runs it's just the string "execute" either way.
        SecurityException ex = assertThrows(SecurityException.class,
                () -> shell.evaluate("def n = 'execute'; 'whoami'.\"$n\"()"));
        assertTrue(ex.getMessage().contains("execute"), ex.getMessage());
    }

    @Test
    void unresolvableCallFailsClosedInsteadOfDispatchingDynamically() {
        SecurityException ex = assertThrows(SecurityException.class,
                () -> shell.evaluate("'hello'.thisMethodDoesNotExist()"));
        assertTrue(ex.getMessage().startsWith("Unresolvable call:"), ex.getMessage());
    }

    @Test
    void closureDelegateEscapeIsStillChecked() {
        // classic groovy-sandbox regression case: reassigning a closure's delegate to an
        // unlisted receiver must not let calls resolved through it slip past the guard.
        //
        // This specifically exercises calling an *unrecognized* method on the closure
        // *variable itself* (c.exists()) - genuine dynamic dispatch through Closure's own MOP
        // (owner -> delegate, per resolveStrategy), which RuntimeGuard.checkedCall has an
        // explicit branch for (see closureTargets()). A *bare* call written inside the
        // closure's own body is a different case entirely and does NOT go through this branch
        // - see bareCallInsideAClosureBodyResolvesToTheOwnerNotTheDelegate below.
        Binding binding = new Binding();
        binding.setVariable("dir", new File("."));
        GroovyShell shell = GuardedShellFactory.create(binding);

        SecurityException ex = assertThrows(SecurityException.class,
                () -> shell.evaluate("def c = { -> 1 }; c.delegate = dir; c.exists()"));
        assertTrue(ex.getMessage().contains("java.io.File"), ex.getMessage());
    }

    @Test
    void bareCallInsideAClosureBodyResolvesToTheOwnerNotTheDelegate() {
        // found while verifying the test above: reassigning `delegate` has *no effect at all*
        // on a bare, unqualified call written inside the closure's own body - Groovy's
        // compiler resolves "abs(-5)" to the enclosing script (the closure's owner) directly,
        // statically, without ever consulting Closure's own dynamic delegate/resolveStrategy
        // machinery. RuntimeGuard.checkedCall's receiver here is the SecureScript instance,
        // never the closure - the instanceof-Closure branch this class's own delegate-escape
        // test relies on simply never fires for this pattern. Still fails closed (the script
        // has no "abs" method of its own to match), just not for the reason one might assume.
        Object closure = shell.evaluate("{ -> delegate = Math; abs(-5) }");
        groovy.lang.Closure<?> c = (groovy.lang.Closure<?>) closure;

        SecurityException ex = assertThrows(SecurityException.class, c::call);
        assertTrue(ex.getMessage().startsWith("Unresolvable call:"), ex.getMessage());
    }

    @Test
    void scriptCannotReadOrReplaceOrReRunItsOwnBindingRegardlessOfWhitelist() {
        // getBinding/setBinding/run/evaluate are declared on groovy.lang.Script itself and are
        // denied unconditionally (RuntimeGuard.DENIED_SCRIPT_METHODS) - unlike every other class,
        // groovy.lang.Script is not governed by the allowedMethods whitelist at all, so
        // whitelisting it (done here, wide open) does not change this outcome.
        GuardHolder.set(new RuntimeGuard(Map.of("groovy.lang.Script", Set.of())));
        GroovyShell shell = GuardedShellFactory.create();

        SecurityException ex = assertThrows(SecurityException.class, () -> shell.evaluate("getBinding()"));
        assertTrue(ex.getMessage().contains("getBinding"), ex.getMessage());

        Binding outer = new Binding();
        outer.setVariable("b", new Binding()); // pre-built in plain Java, not by the script
        GroovyShell shellWithB = GuardedShellFactory.create(outer);
        SecurityException ex2 = assertThrows(SecurityException.class, () -> shellWithB.evaluate("setBinding(b)"));
        assertTrue(ex2.getMessage().contains("setBinding"), ex2.getMessage());
    }

    @Test
    void scriptCannotUseItsOwnInheritedEvaluateToBypassTheGuardEntirelyRegardlessOfWhitelist() {
        // the finding that actually mattered: Script.evaluate(String)/evaluate(File) compile and
        // run their argument through a brand new, completely unguarded GroovyShell - confirmed
        // empirically (not assumed) by temporarily letting it through and observing
        // evaluate("new java.io.File('/tmp/x').getName()") return "x" with no exception, proving
        // it never reached InterceptCustomizer/RuntimeGuard at all. Using java.io.File here only
        // as a detector - it's never whitelisted, so success proves the escape and failure proves
        // the guard held; the payload itself is inert (never touches disk). One call away from a
        // full sandbox escape using nothing but java.lang.String, which DefaultAllowlist already
        // trusts unrestricted - the most dangerous entry in DENIED_SCRIPT_METHODS by far.
        GuardHolder.set(new RuntimeGuard(Map.of("groovy.lang.Script", Set.of())));
        GroovyShell shell = GuardedShellFactory.create();

        SecurityException ex = assertThrows(SecurityException.class,
                () -> shell.evaluate("evaluate(\"new java.io.File('/tmp/x').getName()\")"));
        assertTrue(ex.getMessage().contains("evaluate"), ex.getMessage());
    }

    @Test
    void scriptCanCallAFunctionItDefinesItselfWithNoWhitelistEntryForItAtAll() {
        // the problem this exists to fix: def test(){...} compiles onto Script1 (a fresh,
        // compiler-generated class name every compilation) - before DENIED_SCRIPT_METHODS
        // existed, this failed with "Rejected: Script1.test()" because no whitelist entry could
        // ever name that class ahead of time. Now allowed by default, the same as every other
        // method groovy.lang.Script's subclasses declare, except the three denied outright.
        Object result = shell.evaluate("def test(){ 42 }; test()");
        assertEquals(42, result);
    }

    @Test
    void scriptCannotReadItsOwnMetaClassByDefault() {
        // a compiled script implements groovy.lang.GroovyObject; getMetaClass()/setMetaClass()/
        // invokeMethod()/getProperty()/setProperty() resolve to that interface for it, not
        // java.lang.Object (see RuntimeGuardUnitTest's plain-JDK-receiver case for the
        // difference) - denied because groovy.lang.GroovyObject is never a key in
        // DefaultAllowlist either.
        SecurityException ex = assertThrows(SecurityException.class, () -> shell.evaluate("getMetaClass()"));
        assertTrue(ex.getMessage().contains("getMetaClass"), ex.getMessage());
    }

    @Test
    void whitelistingGroovyObjectGrantsGetMetaClassOnAScriptToo() {
        GuardHolder.set(new RuntimeGuard(Map.of("groovy.lang.GroovyObject", Set.of())));
        GroovyShell shell = GuardedShellFactory.create();

        assertTrue(shell.evaluate("getMetaClass()") instanceof groovy.lang.MetaClass);
    }

    @Test
    void getPropertySetPropertyAndInvokeMethodAreGovernedByTheGroovyObjectWhitelistThroughARealCompiledScript() {
        // end-to-end through GuardedShellFactory/InterceptCustomizer, not guard.checkedCall
        // directly - a restricted set ({"getProperty"} only, not empty-set-means-everything)
        // proves real per-method granularity, and getBinding staying denied throughout proves
        // this whitelist entry and DENIED_SCRIPT_METHODS are two independent mechanisms.
        Binding binding = new Binding();
        binding.setVariable("foo", 7); // Script.getProperty falls back to the Binding for this
        GuardHolder.set(new RuntimeGuard(Map.of("groovy.lang.GroovyObject", Set.of("getProperty"))));
        GroovyShell shell = GuardedShellFactory.create(binding);

        assertEquals(7, shell.evaluate("getProperty('foo')"));

        SecurityException ex1 = assertThrows(SecurityException.class,
                () -> shell.evaluate("setProperty('foo', 9)"));
        assertTrue(ex1.getMessage().contains("setProperty"), ex1.getMessage());

        SecurityException ex2 = assertThrows(SecurityException.class,
                () -> shell.evaluate("invokeMethod('toString', [])"));
        assertTrue(ex2.getMessage().contains("invokeMethod"), ex2.getMessage());

        SecurityException ex3 = assertThrows(SecurityException.class, () -> shell.evaluate("getBinding()"));
        assertTrue(ex3.getMessage().contains("getBinding"), ex3.getMessage());
    }
}
