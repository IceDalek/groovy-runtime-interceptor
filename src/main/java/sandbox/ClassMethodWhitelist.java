package sandbox;

import java.util.Set;

/**
 * One class's whitelist entry, bound from {@code scripting.security[<class name>]} - see
 * {@link RuntimeGuardConfiguration}. {@code instanceMethods}/{@code staticMethods} default to
 * {@code null} (left unset), not an empty {@link Set} - {@link RuntimeGuardConfiguration} relies
 * on that distinction: {@code null} means this class has no entry at all in that particular map
 * (nothing allowed there), while an explicit empty value in the source means every method in
 * that category is allowed - see {@link RuntimeGuard}.
 */
public class ClassMethodWhitelist {

    private Set<String> instanceMethods;
    private Set<String> staticMethods;

    public Set<String> getInstanceMethods() {
        return instanceMethods;
    }

    public void setInstanceMethods(Set<String> instanceMethods) {
        this.instanceMethods = instanceMethods;
    }

    public Set<String> getStaticMethods() {
        return staticMethods;
    }

    public void setStaticMethods(Set<String> staticMethods) {
        this.staticMethods = staticMethods;
    }
}
