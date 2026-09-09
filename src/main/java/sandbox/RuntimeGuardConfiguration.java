package sandbox;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Builds the {@link RuntimeGuard} bean from externalized configuration, e.g.
 * {@code application.properties}:
 *
 * <pre>
 * scripting.security[java.lang.String].instanceMethods.allowed=
 * scripting.security[java.lang.String].instanceMethods.denied[0]=execute
 * scripting.security[java.util.List].instanceMethods.allowed[0]=add
 * scripting.security[java.lang.Class].staticMethods.allowed[0]=forName
 * </pre>
 *
 * Each class entry has independent {@code instanceMethods}/{@code staticMethods}, each in turn
 * with independent {@code allowed}/{@code denied} sets, flattened here into the four maps
 * {@link RuntimeGuard} takes. Leaving a set unset for a class means no entry at all there
 * (nothing allowed, nothing extra denied); an explicit empty {@code allowed} value means every
 * method of that kind is allowed - see {@link RuntimeGuard}. There's no hardcoded fallback here
 * on purpose - a missing/misconfigured {@code scripting.security} property means all four maps
 * stay empty, which means {@link RuntimeGuard} denies everything. Fail closed, not fail open with
 * a default whitelist nobody asked for.
 */
@Configuration
@EnableConfigurationProperties(ScriptingSecurityProperties.class)
public class RuntimeGuardConfiguration {

    @Bean(initMethod = "registerAsActiveGuard", destroyMethod = "unregisterAsActiveGuard")
    public RuntimeGuard runtimeGuard(ScriptingSecurityProperties properties) {
        Map<String, Set<String>> allowedInstance = new LinkedHashMap<>();
        Map<String, Set<String>> deniedInstance = new LinkedHashMap<>();
        Map<String, Set<String>> allowedStatic = new LinkedHashMap<>();
        Map<String, Set<String>> deniedStatic = new LinkedHashMap<>();

        properties.getSecurity().forEach((className, policy) -> {
            putIfPresent(allowedInstance, className, policy.getInstanceMethods().getAllowed());
            putIfPresent(deniedInstance, className, policy.getInstanceMethods().getDenied());
            putIfPresent(allowedStatic, className, policy.getStaticMethods().getAllowed());
            putIfPresent(deniedStatic, className, policy.getStaticMethods().getDenied());
        });

        return new RuntimeGuard(allowedInstance, deniedInstance, allowedStatic, deniedStatic);
    }

    private static void putIfPresent(Map<String, Set<String>> map, String className, Set<String> methods) {
        if (Objects.nonNull(methods)) {
            map.put(className, methods);
        }
    }
}
