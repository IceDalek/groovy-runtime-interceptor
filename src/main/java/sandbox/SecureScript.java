package sandbox;

import groovy.lang.Script;

/**
 * Base class {@link GuardedShellFactory}-built shells compile scripts as, instead of the
 * default {@link Script}. Groovy still auto-names the generated class ({@code Script1},
 * {@code Script2}, ...) - this only changes what it <i>extends</i>, wired via
 * {@code CompilerConfiguration.setScriptBaseClass(SecureScript.class.getName())}. A place to
 * hang script-visible helpers/API (logging, whitelisted convenience methods, whatever a
 * script should be able to call bare) as this class grows.
 *
 * <p><b>Not a security boundary by itself.</b> Overriding {@link #invokeMethod} or
 * {@link #getProperty} here would only catch <i>unqualified</i> calls/reads inside a script -
 * a bare {@code foo()} or {@code x}, dispatched through this class because there's no explicit
 * receiver. It does nothing for {@code a.foo()} or {@code a.x}, which is almost everything a
 * real script does. {@link RuntimeGuard} plus {@link InterceptCustomizer}'s AST rewriting is
 * what actually enforces the whitelist, on every call regardless of receiver; this class does
 * not duplicate or replace that.
 */
public abstract class SecureScript extends Script {
}
