package sandbox;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Builds the {@link RuntimeGuard} bean from externalized configuration, e.g.
 * {@code application.yml}:
 *
 * <pre>
 * sandbox:
 *   allowed-methods:
 *     java.lang.String: []
 *     java.util.List: [add, size]
 * </pre>
 *
 * An empty method list means every method on that class is allowed (see {@link RuntimeGuard});
 * a class with no entry at all is not allowed. There's no hardcoded fallback here on purpose -
 * a missing/misconfigured {@code sandbox.allowed-methods} property means an empty map, which
 * means {@link RuntimeGuard} denies everything. Fail closed, not fail open with a default
 * whitelist nobody asked for.
 */
@Configuration
@EnableConfigurationProperties(SandboxWhitelistProperties.class)
public class RuntimeGuardConfiguration {

    @Bean(initMethod = "registerAsActiveGuard", destroyMethod = "unregisterAsActiveGuard")
    public RuntimeGuard runtimeGuard(SandboxWhitelistProperties properties) {
        return new RuntimeGuard(properties.getAllowedMethods());
    }
}
