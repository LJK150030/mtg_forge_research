package APF;

import java.util.function.Predicate;

// Describe required properties on any involved noun.
public final class PropertyRequirement {
    public final String propertyPath;
    public final Predicate<Object> predicate;
    public final String description;
    public PropertyRequirement(String path, Predicate<Object> pred, String why) {
        this.propertyPath = path; this.predicate = pred; this.description = why;
    }
    public boolean isSatisfied(NounInstance n) {
        return predicate.test(n.getProperty(propertyPath));
    }
}
