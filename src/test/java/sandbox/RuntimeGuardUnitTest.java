package sandbox;

import groovy.lang.Script;
import java.io.File;
import java.util.ArrayList;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
    void executeIsDeniedEvenThoughItsDeclaringClassIsAWhitelistedType() {
        // found empirically (not documentation, actually verified via pickMethod introspection
        // with nothing invoked): Groovy's ProcessGroovyMethods.execute(...) extension method -
        // which launches a real OS process - reports its declaring class as java.lang.String
        // itself, not some ProcessGroovyMethods-named class. String has an unrestricted
        // (empty-set) entry in DefaultAllowlist, so without this in deniedMethods, "execute"
        // would have ridden along for free on any whitelist that leaves String unrestricted.
        // This must throw before InvokerHelper.invokeMethod ever runs - if it didn't, this
        // test would actually spawn a process instead of just proving the check happens.
        assertThrows(SecurityException.class,
                () -> guard.checkedCall("whoami", false, false, "execute", new Object[0]));
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
        RuntimeGuard narrow = new RuntimeGuard(Map.of("java.util.ArrayList", Set.of()));

        Object result = narrow.checkedConstructor(ArrayList.class, new Object[]{});
        assertEquals(new ArrayList<>(), result);

        // String is in DefaultAllowlist but was left out of this guard's whitelist
        assertThrows(SecurityException.class,
                () -> narrow.checkedCall("hello", false, false, "toUpperCase", new Object[0]));
    }

    @Test
    void emptyMethodListMeansEveryMethodOnThatClassIsAllowed() throws Throwable {
        RuntimeGuard guard = new RuntimeGuard(Map.of("java.lang.String", Set.of()));

        assertEquals("HELLO", guard.checkedCall("hello", false, false, "toUpperCase", new Object[0]));
        assertEquals("hello", guard.checkedCall("HELLO", false, false, "toLowerCase", new Object[0]));
    }

    @Test
    void nonEmptyMethodListOnlyAllowsTheNamedMethods() throws Throwable {
        RuntimeGuard guard = new RuntimeGuard(Map.of("java.lang.String", Set.of("toUpperCase")));

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
        RuntimeGuard guard = new RuntimeGuard(Map.of("java.util.List", Set.of("add")));

        Object list = guard.checkedConstructor(ArrayList.class, new Object[]{});
        assertEquals(new ArrayList<>(), list);
        assertEquals(true, guard.checkedCall(list, false, false, "add", new Object[]{"x"}));

        // "size" wasn't listed, so it's still denied even though the class itself is allowed
        assertThrows(SecurityException.class,
                () -> guard.checkedCall(list, false, false, "size", new Object[0]));
    }

    @Test
    void classMissingFromTheMapEntirelyIsDeniedEvenWithNoMethodRestriction() {
        RuntimeGuard guard = new RuntimeGuard(Map.of("java.lang.String", Set.of()));

        assertThrows(SecurityException.class,
                () -> guard.checkedConstructor(ArrayList.class, new Object[]{}));
    }

    // -------------------------------------------------------------------------------------
    // getClass/getMetaClass/setMetaClass/invokeMethod/getProperty/setProperty/wait/notify/
    // notifyAll/finalize, forName/getClassLoader, getBinding/setBinding are not special-cased
    // by name anywhere in RuntimeGuard - each is gated purely by whether its own declaring
    // class (java.lang.Object, groovy.lang.GroovyObject, java.lang.Class, groovy.lang.Script)
    // is a key in allowedMethods, exactly like any other method. Denied by default because
    // none of those four classes are ever in DefaultAllowlist; ALLOWED the moment a deployment
    // whitelists one of them - there is no hidden override standing between the whitelist and
    // what a script can call. The pairs below prove both halves of that for each class.
    // -------------------------------------------------------------------------------------

    @Test
    void objectLevelMopMethodsAreDeniedByDefaultBecauseJavaLangObjectIsNotWhitelisted() {
        // getClass/wait/notify/notifyAll/finalize always resolve to java.lang.Object on every
        // receiver (verified via pickMethod introspection - none can be overridden away from
        // it). getMetaClass/setMetaClass/invokeMethod resolve there too, but only for plain JDK
        // receivers - Groovy exposes them as GDK extensions declared directly on Object itself,
        // not some DefaultGroovyMethods-named class (the same declaring-class-is-surprising
        // lesson "execute" taught, just landing on Object here instead of the receiver's own
        // type).
        assertThrows(SecurityException.class,
                () -> guard.checkedCall("hello", false, false, "getClass", new Object[0]));
        assertThrows(SecurityException.class,
                () -> guard.checkedCall("hello", false, false, "getMetaClass", new Object[0]));
        assertThrows(SecurityException.class,
                () -> guard.checkedCall(new ArrayList<>(), false, false, "invokeMethod",
                        new Object[]{"toUpperCase", new Object[0]}));
    }

    @Test
    void whitelistingJavaLangObjectGrantsGetClassAndGetMetaClassToo() throws Throwable {
        // the whitelist is authoritative - "empty set = every method allowed" on Object means
        // every method Object declares, full stop, including the MOP-escape ones. No name in
        // RuntimeGuard overrides that once a deployment makes this choice.
        RuntimeGuard guard = new RuntimeGuard(Map.of("java.lang.Object", Set.of()));
        Object o = new Object();

        assertEquals(Object.class, guard.checkedCall(o, false, false, "getClass", new Object[0]));
        assertTrue(guard.checkedCall(o, false, false, "getMetaClass", new Object[0]) instanceof groovy.lang.MetaClass);
    }

    @Test
    void scriptMetaClassMethodsAreDeniedByDefaultBecauseGroovyObjectIsNotWhitelisted() {
        // real Groovy objects (scripts, closures, any Groovy-compiled class) resolve these five
        // to groovy.lang.GroovyObject instead of java.lang.Object - verified directly against a
        // real compiled script, a different declaring class than the same names get on a plain
        // String or ArrayList above.
        Script script = new Script() {
            @Override public Object run() { return null; }
        };

        assertThrows(SecurityException.class, () ->
                guard.checkedCall(script, false, false, "getMetaClass", new Object[0]));
    }

    @Test
    void whitelistingGroovyObjectGrantsGetMetaClassOnAScript() throws Throwable {
        RuntimeGuard guard = new RuntimeGuard(Map.of("groovy.lang.GroovyObject", Set.of()));
        Script script = new Script() {
            @Override public Object run() { return null; }
        };

        // not asserting equality with script.getMetaClass() directly - that goes through
        // GroovyObjectSupport's lazy-init path and wraps the result in a HandleMetaClass,
        // while invokeMethod's dispatch here returns the unwrapped MetaClassImpl; a difference
        // in Groovy's own metaclass bookkeeping, unrelated to what RuntimeGuard is proving
        assertTrue(guard.checkedCall(script, false, false, "getMetaClass", new Object[0]) instanceof groovy.lang.MetaClass);
    }

    @Test
    void getPropertySetPropertyAndInvokeMethodAreGovernedByTheGroovyObjectWhitelistEntryWithFullGranularity() throws Throwable {
        // answers precisely: yes, unlike getBinding/setBinding/run/evaluate (always denied,
        // DENIED_SCRIPT_METHODS), these three are ordinary allowedMethods entries under
        // "groovy.lang.GroovyObject" - a *restricted* set (not empty-set-means-everything) proves
        // real per-method granularity, not just an on/off switch
        RuntimeGuard guard = new RuntimeGuard(Map.of("groovy.lang.GroovyObject", Set.of("getProperty")));
        class ScriptWithFoo extends Script {
            @Override public Object run() { return null; }
            public int getFoo() { return 7; }
        }
        ScriptWithFoo script = new ScriptWithFoo();

        assertEquals(7, guard.checkedCall(script, false, false, "getProperty", new Object[]{"foo"}));

        assertThrows(SecurityException.class, () ->
                guard.checkedCall(script, false, false, "setProperty", new Object[]{"foo", 9}));
        assertThrows(SecurityException.class, () ->
                guard.checkedCall(script, false, false, "invokeMethod", new Object[]{"getFoo", new Object[0]}));

        // getBinding stays denied regardless - proves this whitelist entry and
        // DENIED_SCRIPT_METHODS are two entirely independent mechanisms, not one gate
        assertThrows(SecurityException.class, () ->
                guard.checkedCall(script, false, false, "getBinding", new Object[0]));
    }

    @Test
    void classForNameAndGetClassLoaderAreDeniedByDefaultBecauseJavaLangClassIsNotWhitelisted() {
        // java.lang.Class is never a key in DefaultAllowlist, so every instance/static method it
        // declares - not just these two - is denied by whitelist-absence alone
        assertThrows(SecurityException.class, () -> guard.checkedCall(
                Class.class, false, false, "forName", new Object[]{"java.io.File"}));
        assertThrows(SecurityException.class, () -> guard.checkedCall(
                String.class, false, false, "getClassLoader", new Object[0]));
    }

    @Test
    void whitelistingJavaLangClassGrantsForNameAndGetClassLoaderToo() throws Throwable {
        // proves this isn't a backdoor way of banning forName/getClassLoader specifically -
        // whitelisting java.lang.Class opens all of it, the same as whitelisting any other class
        RuntimeGuard guard = new RuntimeGuard(Map.of("java.lang.Class", Set.of()));

        assertEquals("java.lang.String",
                guard.checkedCall(String.class, false, false, "getName", new Object[0]));
        assertEquals(File.class,
                guard.checkedCall(Class.class, false, false, "forName", new Object[]{"java.io.File"}));
    }

    @Test
    void restrictingJavaLangClassToGetNameStillDeniesGetClassLoaderEvenWithStringFullyWhitelisted() {
        // java.lang.String being unrestricted has no bearing on java.lang.Class's own method
        // list - each declaring class is checked independently. getClassLoader() resolves its
        // declaring class to java.lang.Class (not java.lang.String, even though the receiver
        // *value* here is String.class), so it's checked against Class's own {"getName"} set,
        // not String's empty-set-means-everything one.
        RuntimeGuard guard = new RuntimeGuard(Map.of(
                "java.lang.String", Set.of(),
                "java.lang.Class", Set.of("getName")));

        assertEquals("java.lang.String", guard.checkedCall(String.class, false, false, "getName", new Object[0]));

        SecurityException ex = assertThrows(SecurityException.class, () ->
                guard.checkedCall(String.class, false, false, "getClassLoader", new Object[0]));
        assertTrue(ex.getMessage().contains("java.lang.Class.getClassLoader"), ex.getMessage());
    }

    @Test
    void scriptBindingRunAndEvaluateAreDeniedUnconditionallyRegardlessOfWhitelist() {
        // groovy.lang.Script is deliberately not governed by allowedMethods at all (see
        // RuntimeGuard.DENIED_SCRIPT_METHODS) - so unlike every other class in this test file,
        // whitelisting "groovy.lang.Script" does NOT change this outcome. Proven by using a
        // guard that whitelists it wide open, not the default TestGuards.fresh() instance.
        RuntimeGuard guard = new RuntimeGuard(Map.of("groovy.lang.Script", Set.of()));
        Script script = new Script() {
            @Override public Object run() { return null; }
        };

        assertThrows(SecurityException.class, () ->
                guard.checkedCall(script, false, false, "getBinding", new Object[0]));
        assertThrows(SecurityException.class, () ->
                guard.checkedCall(script, false, false, "setBinding", new Object[]{new groovy.lang.Binding()}));
        assertThrows(SecurityException.class, () ->
                guard.checkedCall(script, false, false, "run", new Object[0]));
        // the one that matters most: evaluate(String) compiles and runs its argument through a
        // brand new, unguarded GroovyShell if it's ever reached - see
        // RuntimeGuardScriptTest.scriptCannotUseItsOwnInheritedEvaluateToBypassTheGuardEntirelyRegardlessOfWhitelist
        // for the end-to-end proof this used to actually succeed before "evaluate" was added here
        assertThrows(SecurityException.class, () ->
                guard.checkedCall(script, false, false, "evaluate", new Object[]{"1+1"}));
    }

    @Test
    void aScriptSubclassMethodOtherThanTheDeniedThreeIsAllowedByDefaultWithNoWhitelistEntryAtAll() throws Throwable {
        // the whole reason DENIED_SCRIPT_METHODS exists instead of governing Script through
        // allowedMethods like every other class: a script's own def foo(){...} compiles onto a
        // fresh, compiler-generated class name every time (Script1, Script2, ...) that no
        // whitelist entry could ever name in advance. Simulated here with a hand-written Script
        // subclass method standing in for a user-defined script function - guard is TestGuards
        // .fresh(), which has no "groovy.lang.Script" entry whatsoever.
        class ScriptWithUserMethod extends Script {
            @Override public Object run() { return null; }
            public int test() { return 42; }
        }
        ScriptWithUserMethod script = new ScriptWithUserMethod();

        assertEquals(42, guard.checkedCall(script, false, false, "test", new Object[0]));
    }

    @Test
    void staticAndInstanceMethodsAreGovernedByIndependentWhitelistMaps() throws Throwable {
        RuntimeGuard guard = new RuntimeGuard(
                Map.of("java.time.LocalDate", Set.of()), // instance methods: all allowed
                Map.of());                                // static methods: nothing whitelisted

        // LocalDate.of(...) is a static factory method - denied, staticMethods has no LocalDate
        // entry, even though instanceMethods leaves the class completely unrestricted
        assertThrows(SecurityException.class, () -> guard.checkedCall(
                java.time.LocalDate.class, false, false, "of", new Object[]{2024, 1, 1}));

        // but a LocalDate instance obtained some other way can still have instance methods called
        java.time.LocalDate date = java.time.LocalDate.of(2024, 1, 1);
        assertEquals(2024, guard.checkedCall(date, false, false, "getYear", new Object[0]));
    }

    @Test
    void whitelistingOnlyStaticMethodsStillDeniesInstanceMethodsOnTheSameClass() throws Throwable {
        RuntimeGuard guard = new RuntimeGuard(
                Map.of(),                                       // instance methods: nothing
                Map.of("java.lang.Class", Set.of("forName")));  // static: only forName

        Object loaded = guard.checkedCall(Class.class, false, false, "forName", new Object[]{"java.lang.String"});
        assertEquals(String.class, loaded);

        // getName() is an *instance* method of java.lang.Class - no instanceMethods entry at all
        assertThrows(SecurityException.class, () -> guard.checkedCall(
                String.class, false, false, "getName", new Object[0]));
    }

    @Test
    void aClassPresentOnlyInStaticMethodsCannotBeConstructed() {
        // checkedConstructor is gated on the instance-methods map alone
        RuntimeGuard guard = new RuntimeGuard(Map.of(), Map.of("java.util.ArrayList", Set.of()));

        assertThrows(SecurityException.class, () -> guard.checkedConstructor(ArrayList.class, new Object[]{}));
    }

    @Test
    void deniedWinsOverAllowedWhenTheSameMethodNameIsListedInBoth() throws Throwable {
        // a genuine collision, not just "denied is unset" - toUpperCase is explicitly present in
        // both the allowed and the denied set for the same class, and denied still wins
        RuntimeGuard guard = new RuntimeGuard(
                Map.of("java.lang.String", Set.of("toUpperCase")),
                Map.of("java.lang.String", Set.of("toUpperCase")),
                Map.of(), Map.of());

        assertThrows(SecurityException.class, () ->
                guard.checkedCall("hello", false, false, "toUpperCase", new Object[0]));

        // an unrestricted (empty-set) "everything allowed" entry doesn't override denied either
        RuntimeGuard guardWithOpenAllow = new RuntimeGuard(
                Map.of("java.lang.String", Set.of()),
                Map.of("java.lang.String", Set.of("toUpperCase")),
                Map.of(), Map.of());

        assertThrows(SecurityException.class, () ->
                guardWithOpenAllow.checkedCall("hello", false, false, "toUpperCase", new Object[0]));
        // other methods on the same unrestricted entry are unaffected
        assertEquals("hello", guardWithOpenAllow.checkedCall("HELLO", false, false, "toLowerCase", new Object[0]));
    }
}
