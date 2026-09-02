package sandbox;

import groovy.lang.GroovyShell;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end: script text -> InterceptCustomizer -> RuntimeGuard, through a real GroovyShell.
 */
class RuntimeGuardScriptTest {

    private final GroovyShell shell = GuardedShellFactory.create();

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
    void unresolvableCallFailsClosedInsteadOfDispatchingDynamically() {
        SecurityException ex = assertThrows(SecurityException.class,
                () -> shell.evaluate("'hello'.thisMethodDoesNotExist()"));
        assertTrue(ex.getMessage().startsWith("Unresolvable call:"), ex.getMessage());
    }

    @Test
    void closureDelegateEscapeIsStillChecked() {
        // classic groovy-sandbox regression case: reassigning delegate to an unlisted
        // receiver must not let calls on it slip past the guard just because the call
        // itself is textually unqualified
        assertThrows(SecurityException.class,
                () -> shell.evaluate("{ -> delegate = System; exit(-1) }()"));
    }
}
