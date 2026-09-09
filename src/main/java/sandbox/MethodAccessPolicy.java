package sandbox;

import java.util.Set;

/**
 * {@code allowed}/{@code denied} method-name sets for one call kind (instance or static) of one
 * class - see {@link ClassSecurityPolicy}. Both default to {@code null} (left unset), not an
 * empty {@link Set}: {@code null} means no entry at all for this call kind (nothing allowed,
 * nothing extra denied), while an explicit empty value in the source means every method of that
 * kind is allowed - see {@link RuntimeGuard}.
 */
public class MethodAccessPolicy {

    private Set<String> allowed;
    private Set<String> denied;

    /** No-arg for Spring's JavaBean-style binding - {@code allowed}/{@code denied} are set via
     *  the setters below as the corresponding property is found in the source. */
    public MethodAccessPolicy() {}

    public MethodAccessPolicy(Set<String> allowed, Set<String> denied) {
        this.allowed = allowed;
        this.denied = denied;
    }

    public Set<String> getAllowed() {
        return allowed;
    }

    public void setAllowed(Set<String> allowed) {
        this.allowed = allowed;
    }

    public Set<String> getDenied() {
        return denied;
    }

    public void setDenied(Set<String> denied) {
        this.denied = denied;
    }
}
