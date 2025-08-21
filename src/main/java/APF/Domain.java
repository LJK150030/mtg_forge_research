package APF;

import java.util.*;
import java.util.regex.Pattern;

interface Domain<T> {

    boolean isValid(T value);
    Class<T> getType();
    String describe();

    final class BooleanDomain implements Domain<Boolean> {
        @Override
        public boolean isValid(Boolean value) {
            return value != null;
        }

        @Override
        public Class<Boolean> getType() {
            return Boolean.class;
        }

        @Override
        public String describe() {
            return "Boolean";
        }
    }

    final class IntDomain implements Domain<Integer> {
        private final Integer min;
        private final Integer max;

        public IntDomain() {
            this.min = null;
            this.max = null;
        }

        public IntDomain(Integer min, Integer max) {
            this.min = min;
            this.max = max;
        }

        @Override
        public boolean isValid(Integer value) {
            if (value == null) return false;
            if (min != null && value < min) return false;
            if (max != null && value > max) return false;
            return true;
        }

        @Override
        public Class<Integer> getType() {
            return Integer.class;
        }

        @Override
        public String describe() {
            if (min == null && max == null) return "Integer (unbounded)";
            if (min == null) return "Integer (max: " + max + ")";
            if (max == null) return "Integer (min: " + min + ")";
            return "Integer [" + min + ", " + max + "]";
        }

        public Integer getMin() {
            return min;
        }

        public Integer getMax() {
            return max;
        }

    }

    final class DoubleDomain implements Domain<Double> {
        private final Double min;
        private final Double max;
        private final boolean minInclusive;
        private final boolean maxInclusive;

        public DoubleDomain() {
            this(null, null, true, true);
        }

        public DoubleDomain(Double min, Double max) {
            this(min, max, true, true);
        }

        public DoubleDomain(Double min, Double max, boolean minInclusive, boolean maxInclusive) {
            this.min = min;
            this.max = max;
            this.minInclusive = minInclusive;
            this.maxInclusive = maxInclusive;
        }

        @Override
        public boolean isValid(Double value) {
            if (value == null) return false;
            if (min != null) {
                if (minInclusive ? value < min : value <= min) return false;
            }
            if (max != null) {
                if (maxInclusive ? value > max : value >= max) return false;
            }
            return true;
        }

        @Override
        public Class<Double> getType() {
            return Double.class;
        }

        @Override
        public String describe() {
            if (min == null && max == null) return "Double (unbounded)";
            String left = minInclusive ? "[" : "(";
            String right = maxInclusive ? "]" : ")";
            return "Double " + left + (min != null ? min : "-∞") + ", " +
                    (max != null ? max : "∞") + right;
        }

        public Double getMin() {
            return min;
        }

        public Double getMax() {
            return max;
        }
    }

    final class EnumDomain<T> implements Domain<T> {
        private final Set<T> validValues;
        private final Class<T> type;

        public EnumDomain(Class<T> type, Collection<T> values) {
            this.type = type;
            this.validValues = new HashSet<>(values);
        }

        @Override
        public boolean isValid(T value) {
            return validValues.contains(value);
        }

        @Override
        public Class<T> getType() {
            return type;
        }

        @Override
        public String describe() {
            return "Enum: " + validValues;
        }

        public Set<T> getValidValues() {
            return new HashSet<>(validValues);
        }
    }

    final class StringDomain implements Domain<String> {
        private final Integer minLength;
        private final Integer maxLength;
        private final Pattern pattern; // compiled regex

        public StringDomain() {
            this(null, null, null);
        }

        public StringDomain(Integer minLength, Integer maxLength, Pattern pattern) {
            this.minLength = minLength;
            this.maxLength = maxLength;
            this.pattern = pattern;
        }

        // Convenience ctor if you have a regex string
        public static StringDomain withRegex(Integer minLength, Integer maxLength, String regex) {
            return new StringDomain(minLength, maxLength, regex == null ? null : Pattern.compile(regex));
        }

        @Override
        public boolean isValid(String value) {
            if (value == null) return false;
            if (minLength != null && value.length() < minLength) return false;
            if (maxLength != null && value.length() > maxLength) return false;
            if (pattern != null && !pattern.matcher(value).matches()) return false;
            return true;
        }

        @Override
        public Class<String> getType() {
            return String.class;
        }

        @Override
        public String describe() {
            List<String> constraints = new ArrayList<>();
            if (minLength != null) constraints.add("minLen: " + minLength);
            if (maxLength != null) constraints.add("maxLen: " + maxLength);
            if (pattern != null) constraints.add("pattern: /" + pattern.pattern() + "/");
            return "String" + (constraints.isEmpty() ? "" : " (" + String.join(", ", constraints) + ")");
        }

        public Integer getMinLength() { return minLength; }
        public Integer getMaxLength() { return maxLength; }
        public Pattern getPattern() { return pattern; }
    }

    final class ListDomain<T> implements Domain<List<T>> {
        private final Set<T> allowedValues;
        private final Class<T> elementType;
        private final Integer minSize;
        private final Integer maxSize;
        private final boolean allowDuplicates;

        public ListDomain(Class<T> elementType, Collection<T> allowedValues) {
            this(elementType, allowedValues, null, null, true);
        }

        public ListDomain(Class<T> elementType, Collection<T> allowedValues,
                          Integer minSize, Integer maxSize, boolean allowDuplicates) {
            this.elementType = elementType;
            this.allowedValues = new HashSet<>(allowedValues);
            this.minSize = minSize;
            this.maxSize = maxSize;
            this.allowDuplicates = allowDuplicates;
        }

        @Override
        public boolean isValid(List<T> value) {
            if (value == null) return false;

            // Check size constraints
            if (minSize != null && value.size() < minSize) return false;
            if (maxSize != null && value.size() > maxSize) return false;

            // Check for duplicates if not allowed
            if (!allowDuplicates && value.size() != new HashSet<>(value).size()) {
                return false;
            }

            // Check that all elements are in the allowed set
            for (T element : value) {
                if (!allowedValues.contains(element)) {
                    return false;
                }
            }

            return true;
        }

        @Override
        @SuppressWarnings("unchecked")
        public Class<List<T>> getType() {
            return (Class<List<T>>) (Class<?>) List.class;
        }

        @Override
        public String describe() {
            List<String> constraints = new ArrayList<>();
            constraints.add("allowed: " + allowedValues);
            if (minSize != null) constraints.add("minSize: " + minSize);
            if (maxSize != null) constraints.add("maxSize: " + maxSize);
            if (!allowDuplicates) constraints.add("no duplicates");
            return "List<" + elementType.getSimpleName() + "> (" + String.join(", ", constraints) + ")";
        }

        public Class<T> getElementType() {
            return elementType;
        }

        public Set<T> getAllowedValues() {
            return new HashSet<>(allowedValues);
        }

        public Integer getMinSize() {
            return minSize;
        }

        public Integer getMaxSize() {
            return maxSize;
        }
    }

    final class MapDomain<K, V> implements Domain<Map<K, V>> {
        private final Class<K> keyType;
        private final Class<V> valueType;
        private final Domain<K> keyDomain;     // validation for keys
        private final Domain<V> valueDomain;   // validation for values
        private final Integer minSize;         // optional
        private final Integer maxSize;         // optional

        MapDomain(Class<K> keyType,
                  Class<V> valueType,
                  Domain<K> keyDomain,
                  Domain<V> valueDomain,
                  Integer minSize,
                  Integer maxSize) {
            this.keyType = Objects.requireNonNull(keyType);
            this.valueType = Objects.requireNonNull(valueType);
            this.keyDomain = keyDomain;
            this.valueDomain = valueDomain;
            this.minSize = minSize;
            this.maxSize = maxSize;
        }

        @Override
        public boolean isValid(Map<K, V> map) {
            if (map == null) return false;
            if (minSize != null && map.size() < minSize) return false;
            if (maxSize != null && map.size() > maxSize) return false;

            for (Map.Entry<K, V> e : map.entrySet()) {
                K k = e.getKey();
                V v = e.getValue();
                if (k == null || v == null) return false;
                if (!keyType.isInstance(k) || !valueType.isInstance(v)) return false;
                if (keyDomain != null && !keyDomain.isValid(k)) return false;
                if (valueDomain != null && !valueDomain.isValid(v)) return false;
            }
            return true;
        }

        @Override
        public Class<Map<K, V>> getType() {
            @SuppressWarnings("unchecked")
            Class<Map<K, V>> c = (Class<Map<K, V>>) (Class<?>) Map.class;
            return c;
        }

        @Override
        public String describe() {
            return "Map<" + keyType.getSimpleName() + "," + valueType.getSimpleName() + ">"
                    + " with keys: " + (keyDomain == null ? "any" : keyDomain.describe())
                    + ", values: " + (valueDomain == null ? "any" : valueDomain.describe())
                    + sizeText();
        }

        private String sizeText() {
            if (minSize == null && maxSize == null) return "";
            return " (size " + (minSize == null ? "≥0" : "≥" + minSize)
                    + (maxSize == null ? "" : ", ≤" + maxSize) + ")";
        }
    }


    final class KBRefDomain implements Domain<String> {
        private final KnowledgeBase kb;
        private final Pattern shape;

        public KBRefDomain(KnowledgeBase kb, String idRegex) {
            this.kb = kb;
            this.shape = Pattern.compile(idRegex);
        }

        @Override
        public boolean isValid(String v) {
            if (v == null || !shape.matcher(v).matches()) return false;
            // No need to add kb.hasInstance(); use existing getInstance(...)
            return kb.getInstance(v) != null;
        }

        @Override
        public Class<String> getType() { return String.class; }

        @Override
        public String describe() { return "KB reference (regex=" + shape.pattern() + ")"; }
    }

    final class KBRefListDomain implements Domain<List<String>> {
        private final Domain<String> elementDomain;
        private final int min;
        private final int max;

        public KBRefListDomain(Domain<String> elementDomain, int min, int max) {
            this.elementDomain = elementDomain;
            this.min = min;
            this.max = max;
        }

        @Override
        public boolean isValid(List<String> list) {
            if (list == null) return min == 0;              // allow empty if min == 0
            int n = list.size();
            if (n < min || n > max) return false;
            for (String id : list) {
                if (!elementDomain.isValid(id)) return false;
            }
            return true;
        }

        @SuppressWarnings("unchecked")
        @Override
        public Class<List<String>> getType() {
            return (Class<List<String>>) (Class<?>) List.class;
        }

        @Override
        public String describe() {
            return "List<" + elementDomain.describe() + ">[" + min + "," + max + "]";
        }
    }

}


