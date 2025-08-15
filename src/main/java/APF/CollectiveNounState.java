package APF;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * CollectiveNounState manages collections of NounInstances.
 * Now works directly with NounInstance instead of NounGameEntity.
 */
class CollectiveNounState {
    private final Map<String, NounInstance> instances;

    public CollectiveNounState() {
        this.instances = new HashMap<>();
    }

    public CollectiveNounState(List<NounInstance> instanceList) {
        this.instances = new HashMap<>();
        for (NounInstance instance : instanceList) {
            this.instances.put(instance.getObjectId(), instance);
        }
    }

    public void addInstance(NounInstance instance) {
        instances.put(instance.getObjectId(), instance);
    }

    public void removeInstance(String objectId) {
        instances.remove(objectId);
    }

    public NounInstance getInstance(String objectId) {
        return instances.get(objectId);
    }

    public List<NounInstance> getInstancesOfClass(String className) {
        return instances.values().stream()
                .filter(instance -> instance.getClassName().equals(className))
                .collect(Collectors.toList());
    }

    public List<NounInstance> findInstances(NounInstance.QueryCondition condition) {
        return instances.values().stream()
                .filter(instance -> instance.matches(condition))
                .collect(Collectors.toList());
    }

    public List<List<Object>> toTuple() {
        return instances.values().stream()
                .map(NounInstance::toTuple)
                .sorted((a, b) -> {
                    for (int i = 0; i < Math.min(a.size(), b.size()); i++) {
                        String aStr = String.valueOf(a.get(i));
                        String bStr = String.valueOf(b.get(i));
                        int cmp = aStr.compareTo(bStr);
                        if (cmp != 0) return cmp;
                    }
                    return Integer.compare(a.size(), b.size());
                })
                .collect(Collectors.toList());
    }

    /**
     * Create a deep copy of the entire state
     */
    public CollectiveNounState copy() {
        List<NounInstance> copiedInstances = instances.values().stream()
                .map(NounInstance::copy)
                .collect(Collectors.toList());
        return new CollectiveNounState(copiedInstances);
    }

    @Override
    public int hashCode() {
        return toTuple().hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof CollectiveNounState)) return false;
        CollectiveNounState other = (CollectiveNounState) obj;
        return toTuple().equals(other.toTuple());
    }

    public Map<String, NounInstance> getInstances() {
        return new HashMap<>(instances);
    }
}

