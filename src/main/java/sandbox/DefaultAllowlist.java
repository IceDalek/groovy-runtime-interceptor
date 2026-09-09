package sandbox;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Test-only default whitelist - see {@link TestGuards#fresh()}. {@link RuntimeGuardConfiguration}
 *  does not use this; it fails closed on a missing/misconfigured property instead. */
public final class DefaultAllowlist {

    /** No method-name restriction on any of these - every instance/static method they declare
     *  is callable, except "execute" (ProcessGroovyMethods, spawns an OS process) on
     *  java.lang.String/java.util.List, which resolves its declaring class to the receiver's own
     *  type and so has to be clawed back explicitly rather than left implied by omission. */
    public static Map<String, ClassSecurityPolicy> get() {
        Map<String, ClassSecurityPolicy> security = new LinkedHashMap<>();
        for (Class<?> c : List.of(
                String.class,
                Integer.class,
                Long.class,
                Double.class,
                Boolean.class,
                Character.class,
                java.math.BigDecimal.class,
                java.math.BigInteger.class,

                java.util.List.class,
                java.util.ArrayList.class,
                java.util.Map.class,
                java.util.LinkedHashMap.class,
                java.util.HashMap.class,
                java.util.Set.class,
                java.util.LinkedHashSet.class,

                // Groovy's collection/string GDK "extension methods" (each, collect, findAll,
                // join, tokenize, ...) are registered per receiver-interface: pickMethod()
                // reports the declaring class as that interface (Iterable/Collection/
                // CharSequence), never as DefaultGroovyMethods/StringGroovyMethods itself -
                // verified directly against MetaClassImpl, since it's easy to assume otherwise.
                java.lang.Iterable.class,
                java.util.Collection.class,
                java.lang.CharSequence.class,

                java.time.LocalDate.class)) {
            security.put(c.getName(), unrestricted());
        }
        security.get("java.lang.String").getInstanceMethods().setDenied(Set.of("execute"));
        security.get("java.util.List").getInstanceMethods().setDenied(Set.of("execute"));
        return security;
    }

    private static ClassSecurityPolicy unrestricted() {
        MethodAccessPolicy instanceMethods = new MethodAccessPolicy();
        instanceMethods.setAllowed(Set.of());
        MethodAccessPolicy staticMethods = new MethodAccessPolicy();
        staticMethods.setAllowed(Set.of());
        return new ClassSecurityPolicy(instanceMethods, staticMethods);
    }

    private DefaultAllowlist() {}
}
