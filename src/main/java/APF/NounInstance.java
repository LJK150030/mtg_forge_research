package APF;

import java.util.*;
import java.time.Instant;
import java.util.stream.Collectors;

/**
 * NounInstance represents an actual instance of a game object.
 * It contains the mutable state that can change during gameplay.
 * This is created from a NounDefinition (the prototype).
 */

public class NounInstance {
    private final NounDefinition definition;
    private final String objectId;
    private final Map<String, NounProperty> properties;  // Now using NounProperty for runtime state
    private final Instant createdAt;
    private Instant lastModified;
    private Map<String, Object> metadata;

    /**
     * Package-private constructor - instances should be created via NounDefinition.createInstance()
     */
    NounInstance(NounDefinition definition, String objectId) {
        this.definition = definition;
        this.objectId = objectId;
        this.properties = new HashMap<>();
        this.createdAt = Instant.now();
        this.lastModified = createdAt;
        this.metadata = new HashMap<>();
    }

    /**
     * Get a property value
     */
    public Object getProperty(String propertyName) {
        NounProperty prop = properties.get(propertyName);
        return prop != null ? prop.getValue() : null;
    }

    /**
     * Get a typed property value
     */
    @SuppressWarnings("unchecked")
    public <T> T getPropertyAs(String propertyName, Class<T> type) {
        Object value = getProperty(propertyName);
        if (value != null && !type.isAssignableFrom(value.getClass())) {
            throw new ClassCastException("Property " + propertyName + " is not of type " + type.getName());
        }
        return (T) value;
    }

    /**
     * Set a property value with validation
     */
    public void setProperty(String propertyName, Object value) {
        NounProperty prop = properties.get(propertyName);
        if (prop == null) {
            throw new IllegalArgumentException(
                    "Property '" + propertyName + "' does not exist in class '" +
                            definition.getClassName() + "'"
            );
        }

        // The NounProperty.setValue() method handles domain validation
        prop.setValue(value);
        lastModified = Instant.now();
    }

    /**
     * Update multiple properties atomically
     */
    public void updateProperties(Map<String, Object> updates) {
        // First validate all updates
        for (Map.Entry<String, Object> entry : updates.entrySet()) {
            NounProperty prop = properties.get(entry.getKey());
            if (prop == null) {
                throw new IllegalArgumentException(
                        "Property '" + entry.getKey() + "' does not exist"
                );
            }
            // Check if value would be valid (without actually setting it yet)
            if (prop.getDomain() != null) {
                @SuppressWarnings("unchecked")
                Domain<Object> domain = (Domain<Object>) prop.getDomain();
                if (!domain.isValid(entry.getValue())) {
                    throw new IllegalArgumentException(
                            "Value '" + entry.getValue() + "' is not valid for property '" +
                                    entry.getKey() + "'"
                    );
                }
            }
        }

        // Then apply all updates
        for (Map.Entry<String, Object> entry : updates.entrySet()) {
            properties.get(entry.getKey()).setValue(entry.getValue());
        }
        lastModified = Instant.now();
    }

    /**
     * Check if this instance matches a query condition
     */
    public boolean matches(QueryCondition condition) {
        Object value = getProperty(condition.getPropertyName());
        return condition.evaluate(value);
    }

    /**
     * Create a deep copy of this instance (Prototype pattern - clone operation)
     */
    public NounInstance copy() {
        return copy(this.objectId + "_copy");
    }

    /**
     * Create a deep copy with a new ID
     */
    public NounInstance copy(String newObjectId) {
        NounInstance copy = new NounInstance(definition, newObjectId);

        // Copy current property values to the new instance
        for (Map.Entry<String, NounProperty> entry : properties.entrySet()) {
            copy.properties.get(entry.getKey()).setValue(entry.getValue().getValue());
        }

        copy.metadata.putAll(this.metadata);
        return copy;
    }

    /**
     * Convert to a tuple representation for serialization/comparison
     */
    public List<Object> toTuple() {
        List<Object> tuple = new ArrayList<>();
        tuple.add(definition.getClassName());
        tuple.add(objectId);

        List<Map.Entry<String, Object>> sortedProps = properties.entrySet().stream()
                .map(e -> Map.entry(e.getKey(), e.getValue().getValue()))
                .sorted(Map.Entry.comparingByKey())
                .collect(Collectors.toList());

        tuple.add(sortedProps);
        return tuple;
    }

    @Override
    public int hashCode() {
        // Hash based on className, objectId, and property values (not the NounProperty objects)
        Map<String, Object> values = properties.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().getValue()));
        return Objects.hash(definition.getClassName(), objectId, values);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof NounInstance)) return false;
        NounInstance other = (NounInstance) obj;

        // Compare based on values, not NounProperty objects
        if (!Objects.equals(definition.getClassName(), other.definition.getClassName()) ||
                !Objects.equals(objectId, other.objectId)) {
            return false;
        }

        // Compare property values
        for (String key : properties.keySet()) {
            Object thisValue = this.getProperty(key);
            Object otherValue = other.getProperty(key);
            if (!Objects.equals(thisValue, otherValue)) {
                return false;
            }
        }

        return true;
    }

    // Getters
    public NounDefinition getDefinition() { return definition; }
    public String getClassName() { return definition.getClassName(); }
    public String getObjectId() { return objectId; }

    public Map<String, Object> getPropertyValues() {
        return properties.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().getValue()));
    }

    public Map<String, NounProperty> getProperties() {
        return new HashMap<>(properties);
    }

    public Instant getCreatedAt() { return createdAt; }
    public Instant getLastModified() { return lastModified; }
    public Map<String, Object> getMetadata() { return metadata; }

    /**
     * Query condition for matching instances
     */
    public static class QueryCondition {
        public enum Operator {
            EQUALS, NOT_EQUALS, GREATER_THAN, LESS_THAN,
            GREATER_OR_EQUAL, LESS_OR_EQUAL, CONTAINS, IN
        }

        private final String propertyName;
        private final Operator operator;
        private final Object value;

        public QueryCondition(String propertyName, Operator operator, Object value) {
            this.propertyName = propertyName;
            this.operator = operator;
            this.value = value;
        }

        public String getPropertyName() { return propertyName; }

        @SuppressWarnings("unchecked")
        public boolean evaluate(Object propertyValue) {
            if (propertyValue == null) {
                return operator == Operator.EQUALS && value == null;
            }

            switch (operator) {
                case EQUALS:
                    return propertyValue.equals(value);
                case NOT_EQUALS:
                    return !propertyValue.equals(value);
                case GREATER_THAN:
                    if (propertyValue instanceof Comparable && value instanceof Comparable) {
                        return ((Comparable) propertyValue).compareTo(value) > 0;
                    }
                    return false;
                case LESS_THAN:
                    if (propertyValue instanceof Comparable && value instanceof Comparable) {
                        return ((Comparable) propertyValue).compareTo(value) < 0;
                    }
                    return false;
                case GREATER_OR_EQUAL:
                    if (propertyValue instanceof Comparable && value instanceof Comparable) {
                        return ((Comparable) propertyValue).compareTo(value) >= 0;
                    }
                    return false;
                case LESS_OR_EQUAL:
                    if (propertyValue instanceof Comparable && value instanceof Comparable) {
                        return ((Comparable) propertyValue).compareTo(value) <= 0;
                    }
                    return false;
                case CONTAINS:
                    if (propertyValue instanceof String && value instanceof String) {
                        return ((String) propertyValue).contains((String) value);
                    }
                    return false;
                case IN:
                    if (value instanceof Collection) {
                        return ((Collection<?>) value).contains(propertyValue);
                    }
                    return false;
                default:
                    return false;
            }
        }
    }
}