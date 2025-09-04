package APF;

import java.util.*;
import java.time.Instant;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * NounDefinition acts as the prototype/schema for game objects.
 * It defines the structure, constraints, and default values for instances.
 * This follows the Prototype design pattern.
 */
public class NounDefinition {
    private final String className;
    private final String description;
    private final Map<String, NounProperty> propertyPrototypes;  // Using NounProperty as the schema
    private final Set<String> requiredProperties;
    private final Set<VerbDefinition> availableVerbs = new HashSet<>();

    private NounDefinition(Builder builder) {
        this.className = builder.className;
        this.description = builder.description;
        this.propertyPrototypes = Collections.unmodifiableMap(new HashMap<>(builder.propertyPrototypes));
        this.requiredProperties = Collections.unmodifiableSet(new HashSet<>(builder.requiredProperties));
    }

    /**
     * Creates a new NounInstance from this definition (Prototype pattern).
     * Each instance gets its own copy of properties with default values.
     */
    public NounInstance createInstance(String objectId) {
        return new NounInstance(this, objectId);
    }

    /**
     * Creates a new NounInstance with initial property overrides
     */
    public NounInstance createInstance(String objectId, Map<String, Object> initialValues) {
        NounInstance instance = new NounInstance(this, objectId);
        if (initialValues != null) {
            instance.updateProperties(initialValues);
        }
        return instance;
    }

    // Getters
    public String getClassName() { return className; }
    public String getDescription() { return description; }
    public NounProperty getPropertyPrototype(String name) {
        return propertyPrototypes.get(name);
    }
    public Map<String, NounProperty> getPropertyPrototypes() {
        return propertyPrototypes;
    }
    public Set<String> getRequiredProperties() { return requiredProperties; }

    /**
     * Validates if a value is valid for a specific property
     */
    public boolean isValidPropertyValue(String propertyName, Object value) {
        NounProperty prototype = propertyPrototypes.get(propertyName);
        if (prototype == null) return false;

        // Use the domain validation from NounProperty
        Domain<?> domain = prototype.getDomain();
        if (domain != null) {
            @SuppressWarnings("unchecked")
            Domain<Object> d = (Domain<Object>) domain;
            return d.isValid(value);
        }

        return true;
    }

    public void addVerb(VerbDefinition v) {
        availableVerbs.add(v);
    }
    public boolean hasVerb(VerbDefinition v) {
        return availableVerbs.contains(v);
    }

    public boolean hasVerbNamed(String name) {
        return availableVerbs.stream().anyMatch(v -> name.equals(v.name()));
    }
    public java.util.Set<VerbDefinition> getAvailableVerbs() {
        return java.util.Collections.unmodifiableSet(availableVerbs);
    }

    /**
     * Builder pattern for NounDefinition
     */
    public static class Builder {
        private String className;
        private String description = "";
        private Map<String, NounProperty> propertyPrototypes = new HashMap<>();
        private Set<String> requiredProperties = new HashSet<>();

        public Builder(String className) {
            this.className = className;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public <T> Builder addProperty(String name, T defaultValue, Domain<T> domain) {
            NounProperty prototype = new NounProperty(name, defaultValue, domain);
            propertyPrototypes.put(name, prototype);
            return this;
        }

        public Builder addProperty(String name, Object defaultValue) {
            NounProperty prototype = new NounProperty(name, defaultValue);
            propertyPrototypes.put(name, prototype);
            return this;
        }

        public Builder addIntProperty(String name, Integer defaultValue, Integer min, Integer max) {
            return addProperty(name, defaultValue, new Domain.IntDomain(min, max));
        }

        public Builder addDoubleProperty(String name, Double defaultValue, Double min, Double max) {
            return addProperty(name, defaultValue, new Domain.DoubleDomain(min, max));
        }

        public Builder addStringProperty(String name, String defaultValue, Integer minLength, Integer maxLength) {
            return addProperty(name, defaultValue, new Domain.StringDomain(minLength, maxLength, null));
        }

        public Builder addStringProperty(String name, String defaultValue, Integer minLength, Integer maxLength, Pattern regex) {
            return addProperty(name, defaultValue, new Domain.StringDomain(minLength, maxLength, regex));
        }

        public Builder addBooleanProperty(String name, Boolean defaultValue) {
            return addProperty(name, defaultValue, new Domain.BooleanDomain());
        }

        public <T> Builder addEnumProperty(String name, T defaultValue, List<T> validValues) {
            @SuppressWarnings("unchecked")
            Class<T> type = (Class<T>) defaultValue.getClass();
            return addProperty(name, defaultValue, new Domain.EnumDomain<>(type, validValues));
        }

        public Builder addRequiredProperty(String name) {
            requiredProperties.add(name);
            return this;
        }

        /**
         * Adds a list property with simple constraints (just allowed values)
         */
        public <T> Builder addListProperty(String name, List<T> defaultValue, Collection<T> allowedValues) {
            if (defaultValue == null || defaultValue.isEmpty()) {
                throw new IllegalArgumentException("Default value cannot be null or empty for list property");
            }

            @SuppressWarnings("unchecked")
            Class<T> elementType = (Class<T>) defaultValue.get(0).getClass();

            return addProperty(name, defaultValue, new Domain.ListDomain<>(elementType, allowedValues));
        }

        /**
         * Adds a list property with full constraints
         */
        public <T> Builder addListProperty(String name, List<T> defaultValue, Class<T> elementType,
                                           Collection<T> allowedValues, Integer minSize, Integer maxSize,
                                           boolean allowDuplicates) {
            return addProperty(name, defaultValue,
                    new Domain.ListDomain<>(elementType, allowedValues, minSize, maxSize, allowDuplicates));
        }

        /**
         * Adds a list property with size constraints but allows duplicates
         */
        public <T> Builder addListProperty(String name, List<T> defaultValue, Collection<T> allowedValues,
                                           Integer minSize, Integer maxSize) {
            if (defaultValue == null || defaultValue.isEmpty()) {
                throw new IllegalArgumentException("Default value cannot be null or empty for list property");
            }

            @SuppressWarnings("unchecked")
            Class<T> elementType = (Class<T>) defaultValue.get(0).getClass();

            return addProperty(name, defaultValue,
                    new Domain.ListDomain<>(elementType, allowedValues, minSize, maxSize, true));
        }

        /** Add a Map<K,V> property with a MapDomain; allows empty default (recommended). */
        public <K, V> Builder addEmptyMapProperty(String name, Domain.MapDomain<K, V> domain) {
            return addProperty(name, new LinkedHashMap<K, V>(), domain);
        }

        /** Add a Map<K,V> property with an explicit default map (may be empty or pre-populated). */
        public <K, V> Builder addMapProperty(String name, Map<K, V> defaultValue, Domain.MapDomain<K, V> domain) {
            Map<K, V> safeDefault = (defaultValue == null) ? new LinkedHashMap<>() : new LinkedHashMap<>(defaultValue);
            return addProperty(name, safeDefault, domain);
        }

        public <K, V> Builder addMapProperty(
                String name,
                Map<K, V> defaultValue,
                Class<K> keyType,
                Class<V> valueType,
                Domain<K> keyDomain,
                Domain<V> valueDomain,
                Integer minSize,
                Integer maxSize) {

            Map<K, V> copy = defaultValue == null ? new java.util.LinkedHashMap<>() : new java.util.LinkedHashMap<>(defaultValue);
            Domain.MapDomain<K, V> domain = new Domain.MapDomain<>(keyType, valueType, keyDomain, valueDomain, minSize, maxSize);

            // Assuming your NounProperty constructor mirrors other add*Property methods
            propertyPrototypes.put(name, new NounProperty(name, copy, domain));
            return this;
        }



        public NounDefinition build() {
            // Validate that all required properties have definitions
            for (String required : requiredProperties) {
                if (!propertyPrototypes.containsKey(required)) {
                    throw new IllegalStateException("Required property '" + required + "' not defined");
                }
            }
            return new NounDefinition(this);
        }
    }
}