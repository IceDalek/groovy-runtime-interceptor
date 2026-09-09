package sandbox;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** One class's whitelist entry, bound from {@code scripting.security[<class name>]}. */
@Getter
@RequiredArgsConstructor
public class ClassSecurityPolicy {

    private final MethodAccessPolicy instanceMethods;
    private final MethodAccessPolicy staticMethods;
}
