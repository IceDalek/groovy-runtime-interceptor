package sandbox;

import java.io.File;
import java.util.ArrayList;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Exercises {@link RuntimeGuard}'s static API directly, without going through the compiler.
 * Complements {@link RuntimeGuardScriptTest}: if these fail, the bug is in the guard's own
 * whitelist logic rather than in how {@link InterceptCustomizer} wires calls up to it.
 */
class RuntimeGuardUnitTest {

    @Test
    void allowedConstructorIsInvoked() throws Throwable {
        Object result = RuntimeGuard.checkedConstructor(ArrayList.class, new Object[]{});
        assertEquals(new ArrayList<>(), result);
    }

    @Test
    void deniedConstructorThrows() {
        assertThrows(SecurityException.class,
                () -> RuntimeGuard.checkedConstructor(File.class, new Object[]{"/etc/passwd"}));
    }

    @Test
    void allowedInstanceMethodIsInvoked() throws Throwable {
        Object result = RuntimeGuard.checkedCall("hello", false, false, "toUpperCase", new Object[0]);
        assertEquals("HELLO", result);
    }

    @Test
    void deniedInstanceMethodThrowsBecauseDeclaringClassIsNotWhitelisted() {
        File f = new File(".");
        assertThrows(SecurityException.class,
                () -> RuntimeGuard.checkedCall(f, false, false, "exists", new Object[0]));
    }

    @Test
    void alwaysDeniedMethodNameIsRejectedEvenOnAnAllowedReceiver() {
        // "hello" is a whitelisted String, but getClass() is denied unconditionally
        assertThrows(SecurityException.class,
                () -> RuntimeGuard.checkedCall("hello", false, false, "getClass", new Object[0]));
    }

    @Test
    void safeNavigationOnNullReceiverShortCircuitsWithoutError() throws Throwable {
        Object result = RuntimeGuard.checkedCall(null, true, false, "toUpperCase", new Object[0]);
        assertNull(result);
    }
}
