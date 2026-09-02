package sandbox;

import groovy.lang.Binding;
import groovy.lang.GroovyShell;
import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * File access is not itself special-cased anywhere in {@link RuntimeGuard} - it is denied
 * because {@link File} was never added to {@code ALLOWED_CLASSES}. These tests walk through
 * the ways a script could try to reach the filesystem anyway, including via reflection, and
 * confirm each one is still stopped.
 */
class RuntimeGuardReflectionFileAccessTest {

    @Test
    void directFileConstructionIsRejected() {
        GroovyShell shell = GuardedShellFactory.create();
        SecurityException ex = assertThrows(SecurityException.class,
                () -> shell.evaluate("new java.io.File('/etc/passwd')"));
        assertTrue(ex.getMessage().contains("java.io.File"), ex.getMessage());
    }

    @Test
    void classForNameIsRejectedRegardlessOfWhitelist() {
        // forName is in DENIED_METHODS unconditionally - loading java.io.File by name and
        // then reflectively instantiating it is the textbook sandbox bypass.
        GroovyShell shell = GuardedShellFactory.create();
        SecurityException ex = assertThrows(SecurityException.class, () -> shell.evaluate(
                "Class.forName('java.io.File').getConstructor(String).newInstance('/etc/passwd')"));
        assertTrue(ex.getMessage().contains("forName"), ex.getMessage());
    }

    @Test
    void getClassIsRejectedSoScriptsCannotPivotToReflection() {
        // getClass() is the usual first hop toward getClassLoader()/reflection; it is denied
        // on every receiver, whitelisted or not.
        GroovyShell shell = GuardedShellFactory.create();
        SecurityException ex = assertThrows(SecurityException.class,
                () -> shell.evaluate("'hello'.getClass()"));
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
