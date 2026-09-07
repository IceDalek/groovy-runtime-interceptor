package sandbox;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
    void allowedMethodsComesFromTheConstructorNotAHardcodedList() throws Throwable {
        // a whitelist with only ArrayList on it (unrestricted) - proves the map passed to the
        // constructor is what actually governs, not something still baked into the class
        RuntimeGuard narrow = new RuntimeGuard(Map.of("java.util.ArrayList", List.of()));

        Object result = narrow.checkedConstructor(ArrayList.class, new Object[]{});
        assertEquals(new ArrayList<>(), result);

        // String is in DefaultAllowlist but was left out of this guard's whitelist
        assertThrows(SecurityException.class,
                () -> narrow.checkedCall("hello", false, false, "toUpperCase", new Object[0]));
    }

    @Test
    void emptyMethodListMeansEveryMethodOnThatClassIsAllowed() throws Throwable {
        RuntimeGuard guard = new RuntimeGuard(Map.of("java.lang.String", List.of()));

        assertEquals("HELLO", guard.checkedCall("hello", false, false, "toUpperCase", new Object[0]));
        assertEquals("hello", guard.checkedCall("HELLO", false, false, "toLowerCase", new Object[0]));
    }

    @Test
    void nonEmptyMethodListOnlyAllowsTheNamedMethods() throws Throwable {
        RuntimeGuard guard = new RuntimeGuard(Map.of("java.lang.String", List.of("toUpperCase")));

        assertEquals("HELLO", guard.checkedCall("hello", false, false, "toUpperCase", new Object[0]));
        assertThrows(SecurityException.class,
                () -> guard.checkedCall("HELLO", false, false, "toLowerCase", new Object[0]));
    }

    @Test
    void constructorIsGatedOnClassPresenceNotOnTheMethodList() throws Throwable {
        // the method list narrows which *methods* are callable; a constructor isn't a method
        // name it could name, so construction is allowed as soon as the class key is present.
        //
        // Keyed by "java.util.List", not "java.util.ArrayList": like the Iterable/collect case
        // in DefaultAllowlist, Groovy's MOP reports add()/size() on an ArrayList as declared by
        // the List interface, not by ArrayList itself - verified directly (same as collect()).
        // ArrayList still counts as "allowed" for construction via the one-level interface
        // fallback in allowedMethodNames(), since ArrayList directly implements List.
        RuntimeGuard guard = new RuntimeGuard(Map.of("java.util.List", List.of("add")));

        Object list = guard.checkedConstructor(ArrayList.class, new Object[]{});
        assertEquals(new ArrayList<>(), list);
        assertEquals(true, guard.checkedCall(list, false, false, "add", new Object[]{"x"}));

        // "size" wasn't listed, so it's still denied even though the class itself is allowed
        assertThrows(SecurityException.class,
                () -> guard.checkedCall(list, false, false, "size", new Object[0]));
    }

    @Test
    void classMissingFromTheMapEntirelyIsDeniedEvenWithNoMethodRestriction() {
        RuntimeGuard guard = new RuntimeGuard(Map.of("java.lang.String", List.of()));

        assertThrows(SecurityException.class,
                () -> guard.checkedConstructor(ArrayList.class, new Object[]{}));
    }
}
