package sandbox;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** Test-only default whitelist - see {@link TestGuards#fresh()}. {@link RuntimeGuardConfiguration}
 *  does not use this; it fails closed on a missing/misconfigured property instead. */
public final class DefaultAllowlist {

    /** No method-name restriction on any of these - every method they declare is callable,
     *  subject to {@link #deniedInstanceMethods()}. */
    public static Map<String, Set<String>> get() {
        return Stream.of(
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

                java.time.LocalDate.class
        ).collect(Collectors.toMap(Class::getName, c -> Set.<String>of()));
    }

    /**
     * "execute" (ProcessGroovyMethods, spawns an OS process) resolves its declaring class to the
     * receiver's own type - java.lang.String or java.util.List, both unrestricted above - so it
     * has to be clawed back explicitly rather than left implied by omission.
     */
    public static Map<String, Set<String>> deniedInstanceMethods() {
        return Map.of(
                "java.lang.String", Set.of("execute"),
                "java.util.List", Set.of("execute")
        );
    }

    private DefaultAllowlist() {}
}
