package APF;

import java.util.List;
import java.util.Objects;

// Enhanced NounAttribute with domain support
class NounProperty {
    private String name;
    private Object value;
    private Domain<?> domain;

    // Constructor for unconstrained attribute
    public NounProperty(String name, Object value) {
        this.name = name;
        this.value = value;
        this.domain = null;
    }

    // Constructor with domain
    public <T> NounProperty(String name, T value, Domain<T> domain) {
        this.name = name;
        this.domain = domain;
        setValue(value); // Use setter to validate
    }

    // Legacy constructor for enum lists (backwards compatibility)
    public NounProperty(String name, Object value, List<Object> enumValues) {
        this.name = name;
        this.value = value;
        if (enumValues != null && !enumValues.isEmpty()) {
            this.domain = new Domain.EnumDomain<>(Object.class, enumValues);
        }
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public Object getValue() { return value; }

    @SuppressWarnings("unchecked")
    public void setValue(Object value) {
        if (domain != null) {
            Domain<Object> d = (Domain<Object>) domain;
            if (!d.isValid(value)) {
                throw new IllegalArgumentException(
                        "Value " + value + " is not valid for domain: " + domain.describe()
                );
            }
        }
        this.value = value;
    }

    public Domain<?> getDomain() { return domain; }
    public void setDomain(Domain<?> domain) { this.domain = domain; }

    @Override
    public int hashCode() {
        return Objects.hash(name, String.valueOf(value));
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof NounProperty)) return false;
        NounProperty other = (NounProperty) obj;
        return Objects.equals(name, other.name) &&
                Objects.equals(String.valueOf(value), String.valueOf(other.value));
    }

    public NounProperty copy() {
        NounProperty copy = new NounProperty(name, value);
        copy.domain = this.domain; // Domains are immutable, so sharing is safe
        return copy;
    }
}
