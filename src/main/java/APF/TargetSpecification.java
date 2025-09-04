package APF;

import java.util.function.Predicate;

// Target selection contract (class/tag + predicate + cardinality).
public final class TargetSpecification {
    public final String className; // refer to NounDefinition.className
    public final Predicate<NounInstance> filter;
    public final int min, max;
    public TargetSpecification(String className, Predicate<NounInstance> filter, int min, int max) {
        this.className = className; this.filter = filter; this.min = min; this.max = max;
    }
}
