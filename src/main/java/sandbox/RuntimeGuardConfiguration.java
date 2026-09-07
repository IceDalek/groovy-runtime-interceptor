package sandbox;

import java.util.List;
import java.util.Map;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Default wiring for {@link RuntimeGuard}: supplies the {@code allowedMethods} bean its
 * constructor needs. Picked up automatically by any component scan that reaches this package
 * (it's a {@code @Component} itself), alongside {@link RuntimeGuard}.
 *
 * <p>To use a different whitelist, define your own {@code Map<String, List<String>>} bean
 * named {@code allowedMethods} - {@link RuntimeGuard}'s constructor parameter has that same
 * name, so it's what gets injected. Whether that needs excluding this configuration or just
 * relies on your definition overriding it depends on your context's bean-overriding setting.
 */
@Configuration
public class RuntimeGuardConfiguration {

    @Bean
    public Map<String, List<String>> allowedMethods() {
        return DefaultAllowlist.get();
    }
}
