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
 * scripting.security[java.lang.String].instanceMethods[0]=toUpperCase
 * scripting.security[java.util.List].instanceMethods[0]=add
 * scripting.security[java.lang.Class].staticMethods[0]=forName
 * </pre>
 *
 * Each class entry has independent {@code instanceMethods}/{@code staticMethods} sets, flattened
 * here into the two maps {@link RuntimeGuard} takes. Leaving one unset for a class means that
 * class has no entry in that particular map (nothing allowed there); an explicit empty value
 * ({@code ...instanceMethods=}) means every method in that category is allowed - see
 * {@link RuntimeGuard}. There's no hardcoded fallback here on purpose - a missing/misconfigured
 * {@code scripting.security} property means both maps stay empty, which means {@link RuntimeGuard}
 * denies everything. Fail closed, not fail open with a default whitelist nobody asked for.
 */
@Configuration
@EnableConfigurationProperties(ScriptingSecurityProperties.class)
public class RuntimeGuardConfiguration {

    @Bean(initMethod = "registerAsActiveGuard", destroyMethod = "unregisterAsActiveGuard")
    public RuntimeGuard runtimeGuard(ScriptingSecurityProperties properties) {
        Map<String, Set<String>> instanceMethods = new LinkedHashMap<>();
        Map<String, Set<String>> staticMethods = new LinkedHashMap<>();
        properties.getSecurity().forEach((className, whitelist) -> {
            if (Objects.nonNull(whitelist.getInstanceMethods())) {
                instanceMethods.put(className, whitelist.getInstanceMethods());
            }
            if (Objects.nonNull(whitelist.getStaticMethods())) {
                staticMethods.put(className, whitelist.getStaticMethods());
            }
        });
        return new RuntimeGuard(instanceMethods, staticMethods);
    }
}
