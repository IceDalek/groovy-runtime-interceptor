package sandbox;

import java.util.Set;

/** The whitelist {@link RuntimeGuard} gets by default, via {@link RuntimeGuardConfiguration}. */
public final class DefaultAllowlist {

    public static Set<Class<?>> get() {
        return Set.of(
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
        );
    }

    private DefaultAllowlist() {}
}
