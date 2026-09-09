package sandbox;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Binds the sandbox whitelist from external configuration, under the {@code scripting} prefix -
 * see {@link RuntimeGuardConfiguration} for the property shape and how this becomes a
 * {@link RuntimeGuard}.
 *
 * <p>A plain properties-binding target, not a {@code @Component} - nothing but
 * {@link RuntimeGuardConfiguration} should depend on it. Kept mutable with a default-constructed
 * map (rather than a constructor-injected immutable one) because that's what Spring Boot's
 * relaxed binder requires: it populates a bean via setters after construction, not through
 * constructor arguments, unless the class is declared {@code @ConstructorBinding}.
 */
@ConfigurationProperties(prefix = "scripting")
public class ScriptingSecurityProperties {

    private Map<String, ClassSecurityPolicy> security = new LinkedHashMap<>();

    public Map<String, ClassSecurityPolicy> getSecurity() {
        return security;
    }

    public void setSecurity(Map<String, ClassSecurityPolicy> security) {
        this.security = security;
    }
}
