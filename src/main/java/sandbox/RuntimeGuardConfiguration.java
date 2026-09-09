package sandbox;

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
 * with independent {@code allowed}/{@code denied} sets - see {@link RuntimeGuard}. There's no
 * hardcoded fallback here on purpose - a missing/misconfigured {@code scripting.security}
 * property means an empty map, which means {@link RuntimeGuard} denies everything. Fail closed,
 * not fail open with a default whitelist nobody asked for.
 */
@Configuration
@EnableConfigurationProperties(ScriptingSecurityProperties.class)
public class RuntimeGuardConfiguration {

    @Bean(initMethod = "registerAsActiveGuard", destroyMethod = "unregisterAsActiveGuard")
    public RuntimeGuard runtimeGuard(ScriptingSecurityProperties properties) {
        return new RuntimeGuard(properties.getSecurity());
    }
}
