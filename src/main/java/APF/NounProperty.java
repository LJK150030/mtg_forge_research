package APF;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * NounProperty with domain-aware validation, including MapDomain support.
 * - No reliance on MapDomain getters; uses MapDomain#isValid(Map) exclusively.
 * - Mutating helpers (put/putAll/remove/clear) validate by probing a copy first.
 */
class NounProperty {
    private String name;
    private Object value;   // kept generic for compatibility
    private Domain<?> domain;

    // Unconstrained
    public NounProperty(String name, Object value) {
        this.name = Objects.requireNonNull(name, "name");
        this.value = value;
        this.domain = null;
    }

    // Constrained
    public NounProperty(String name, Object value, Domain<?> domain) {
        this.name = Objects.requireNonNull(name, "name");
        this.domain = domain;
        setValue(value); // validate now (handles MapDomain)
    }

    /** Convenience factory for MapDomain-backed properties. */
    public static <K, V> NounProperty ofMap(String name, Map<K, V> value, Domain.MapDomain<K, V> domain) {
        return new NounProperty(name, value, domain);
    }

    public String getName() { return name; }
    public Object getValue() { return value; }
    public Domain<?> getDomain() { return domain; }

    public void setName(String name) { this.name = Objects.requireNonNull(name, "name"); }

    public void setDomain(Domain<?> domain) {
        this.domain = domain;
        if (value != null) validate(value);
    }

    /** Sets the value with domain validation. */
    public void setValue(Object newValue) {
        if (newValue == null) {
            if (domain != null && !domain.isValid(null)) {
                throw new IllegalArgumentException("Null is not valid for domain: " + domain.describe());
            }
            this.value = null;
            return;
        }
        validate(newValue);
        this.value = newValue;
    }

    /** Internal validation dispatcher. */
    @SuppressWarnings("unchecked")
    private void validate(Object candidate) {
        if (domain == null) return;

        if (domain instanceof Domain.MapDomain<?, ?>) {
            if (!(candidate instanceof Map)) {
                throw new IllegalArgumentException("Expected Map value for MapDomain, got: " + candidate.getClass());
            }
            // Make an exact-typed copy to satisfy MapDomain<K,V>#isValid(Map<K,V>)
            Map<?, ?> raw = (Map<?, ?>) candidate;
            Map<Object, Object> exact = new LinkedHashMap<>( (Map<?, ?>) raw );
            Domain.MapDomain<Object, Object> md = (Domain.MapDomain<Object, Object>) domain;
            if (!md.isValid(exact)) {
                throw new IllegalArgumentException("Map value fails MapDomain constraints: " + md.describe());
            }
            return;
        }

        @SuppressWarnings("unchecked")
        Domain<Object> d = (Domain<Object>) domain;
        if (!d.isValid(candidate)) {
            throw new IllegalArgumentException("Value is not valid for domain: " + domain.describe());
        }
    }

    // ---------------- Map helpers (only valid if domain is a MapDomain) ----------------

    /** Returns the current value as a Map (or null if no value). Throws if not a MapDomain. */
    @SuppressWarnings("unchecked")
    public <K, V> Map<K, V> getMapValue() {
        ensureMapDomain();
        if (value == null) return null;
        if (!(value instanceof Map)) {
            throw new IllegalStateException("Underlying value is not a Map: " + value.getClass());
        }
        return (Map<K, V>) value;
    }

    /** Put a single entry, validating by probing a copy via MapDomain#isValid. */
    @SuppressWarnings("unchecked")
    public <K, V> void put(K key, V val) {
        Domain.MapDomain<K, V> md = (Domain.MapDomain<K, V>) ensureMapDomain();
        Map<K, V> current = ensureMutableMap();
        Map<K, V> probe = new LinkedHashMap<>(current);
        probe.put(key, val);
        if (!md.isValid(probe)) {
            throw new IllegalArgumentException("Putting (" + key + " -> " + val + ") violates: " + md.describe());
        }
        current.put(key, val);
    }

    // PutAll — keep probe as Map<K,V>
    @SuppressWarnings("unchecked")
    public <K, V> void putAll(Map<K, V> toMerge) {
        Domain.MapDomain<K, V> md = (Domain.MapDomain<K, V>) ensureMapDomain();
        Map<K, V> current = ensureMutableMap();
        Map<K, V> probe = new LinkedHashMap<>(current);
        probe.putAll(toMerge);
        if (!md.isValid(probe)) {
            throw new IllegalArgumentException("Merged map violates: " + md.describe());
        }
        current.putAll(toMerge);
    }

    // RemoveKey — make method generic in <K,V> and avoid wildcard md
    @SuppressWarnings("unchecked")
    public <K, V> void removeKey(K key) {
        Domain.MapDomain<K, V> md = (Domain.MapDomain<K, V>) ensureMapDomain();
        Map<K, V> current = ensureMutableMap();
        Map<K, V> probe = new LinkedHashMap<>(current);
        probe.remove(key);
        if (!md.isValid(probe)) {
            throw new IllegalArgumentException("Removal of key '" + key + "' violates: " + md.describe());
        }
        current.remove(key);
    }

    // ClearMap — generic in <K,V>; probe is Map<K,V>, not Map<Object,Object>
    @SuppressWarnings("unchecked")
    public <K, V> void clearMap() {
        Domain.MapDomain<K, V> md = (Domain.MapDomain<K, V>) ensureMapDomain();
        Map<K, V> current = ensureMutableMap();
        Map<K, V> probe = new LinkedHashMap<>();  // exact-typed empty map
        if (!md.isValid(probe)) {
            throw new IllegalArgumentException("Clearing map violates: " + md.describe());
        }
        current.clear();
    }

    // ---------------- Utility ----------------

    private Domain<?> ensureMapDomain() {
        if (!(domain instanceof Domain.MapDomain<?, ?>)) {
            throw new IllegalStateException("This property is not backed by a MapDomain.");
        }
        return domain;
    }

    // Helper: keep return type exact to the caller’s <K,V>
    @SuppressWarnings("unchecked")
    private <K, V> Map<K, V> ensureMutableMap() {
        if (value == null) {
            Map<K, V> fresh = new LinkedHashMap<>();
            this.value = fresh;
            return fresh;
        }
        if (!(value instanceof Map)) {
            throw new IllegalStateException("Underlying value is not a Map: " + value.getClass());
        }
        return (Map<K, V>) value;
    }

    @Override
    public String toString() {
        return "NounProperty{" +
                "name='" + name + '\'' +
                ", value=" + value +
                ", domain=" + (domain == null ? "none" : domain.describe()) +
                '}';
    }

    @Override
    public int hashCode() { return Objects.hash(name, String.valueOf(value)); }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof NounProperty)) return false;
        NounProperty other = (NounProperty) obj;
        return Objects.equals(name, other.name) &&
                Objects.equals(String.valueOf(value), String.valueOf(other.value));
    }

    /** Defensive copy (maps are copied to avoid aliasing). */
    @SuppressWarnings("unchecked")
    public NounProperty copy() {
        Object copiedValue = this.value;
        if (domain instanceof Domain.MapDomain<?, ?> && value instanceof Map<?, ?>) {
            copiedValue = new LinkedHashMap<>((Map<?, ?>) value);
        }
        NounProperty copy = new NounProperty(name, copiedValue);
        copy.domain = this.domain; // domains assumed immutable/structural
        return copy;
    }
}
