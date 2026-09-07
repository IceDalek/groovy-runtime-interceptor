package sandbox;

import java.util.Set;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Default wiring for {@link RuntimeGuard}: supplies the {@code allowedClasses} bean its
 * constructor needs. Picked up automatically by any component scan that reaches this package
 * (it's a {@code @Component} itself), alongside {@link RuntimeGuard}.
 *
 * <p>To use a different whitelist, define your own {@code Set<Class<?>>} bean named
 * {@code allowedClasses} - {@link RuntimeGuard}'s constructor parameter has that same name,
 * so it's what gets injected. Whether that needs excluding this configuration or just relies
 * on your definition overriding it depends on your context's bean-overriding setting.
 */
@Configuration
public class RuntimeGuardConfiguration {

    @Bean
    public Set<Class<?>> allowedClasses() {
        return DefaultAllowlist.get();
    }
}
