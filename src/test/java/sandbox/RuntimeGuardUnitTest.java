package sandbox;

import java.io.File;
import java.util.ArrayList;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Exercises a {@link RuntimeGuard} instance's API directly, without going through the
 * compiler. Complements {@link RuntimeGuardScriptTest}: if these fail, the bug is in the
 * guard's own whitelist logic rather than in how {@link InterceptCustomizer} or
 * {@link GuardHolder} wire calls up to it.
 */
class RuntimeGuardUnitTest {

    private final RuntimeGuard guard = TestGuards.fresh();

    @Test
    void allowedConstructorIsInvoked() throws Throwable {
        Object result = guard.checkedConstructor(ArrayList.class, new Object[]{});
        assertEquals(new ArrayList<>(), result);
    }

    @Test
    void deniedConstructorThrows() {
        assertThrows(SecurityException.class,
                () -> guard.checkedConstructor(File.class, new Object[]{"/etc/passwd"}));
    }

    @Test
    void allowedInstanceMethodIsInvoked() throws Throwable {
        Object result = guard.checkedCall("hello", false, false, "toUpperCase", new Object[0]);
        assertEquals("HELLO", result);
    }

    @Test
    void deniedInstanceMethodThrowsBecauseDeclaringClassIsNotWhitelisted() {
        File f = new File(".");
        assertThrows(SecurityException.class,
                () -> guard.checkedCall(f, false, false, "exists", new Object[0]));
    }

    @Test
    void alwaysDeniedMethodNameIsRejectedEvenOnAnAllowedReceiver() {
        // "hello" is a whitelisted String, but getClass() is denied unconditionally
        assertThrows(SecurityException.class,
                () -> guard.checkedCall("hello", false, false, "getClass", new Object[0]));
    }

    @Test
    void safeNavigationOnNullReceiverShortCircuitsWithoutError() throws Throwable {
        Object result = guard.checkedCall(null, true, false, "toUpperCase", new Object[0]);
        assertNull(result);
    }

    @Test
    void eachRuntimeGuardInstanceHasItsOwnIndependentState() throws Throwable {
        // no static state to leak between instances - each carries its own whitelist
        RuntimeGuard other = TestGuards.fresh();
        assertEquals("HELLO", other.checkedCall("hello", false, false, "toUpperCase", new Object[0]));
        assertThrows(SecurityException.class,
                () -> other.checkedConstructor(File.class, new Object[]{"/tmp/x"}));
    }

    @Test
    void allowedClassesComesFromTheConstructorNotAHardcodedList() throws Throwable {
        // a whitelist with only ArrayList on it - proves the set passed to the constructor is
        // what actually governs, not some list still baked into the class
        RuntimeGuard narrow = new RuntimeGuard(Set.of(ArrayList.class));

        Object result = narrow.checkedConstructor(ArrayList.class, new Object[]{});
        assertEquals(new ArrayList<>(), result);

        // String is in DefaultAllowlist but was left out of this guard's whitelist
        assertThrows(SecurityException.class,
                () -> narrow.checkedCall("hello", false, false, "toUpperCase", new Object[0]));
    }
}
