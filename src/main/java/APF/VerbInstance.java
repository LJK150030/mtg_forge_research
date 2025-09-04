package APF;


import java.time.Instant;
import java.util.*;

public class VerbInstance {
    private final VerbDefinition definition;
    private final NounInstance source;
    private final List<NounInstance> targets;
    private final Instant timestamp = Instant.now();
    private final String instanceId = UUID.randomUUID().toString();

    // NEW: bound variables & bookkeeping
    private final Map<String, Object> bindings;                  // resolved variables/modes for this execution
    private final List<PropertyChange> changes = new ArrayList<>(); // undo log
    private boolean executed, countered, replaced, fizzled;

    // simple context map for adapters/AI
    private final Map<String, Object> context = new HashMap<>();

    public VerbInstance(VerbDefinition def, NounInstance source, List<NounInstance> targets, Map<String, Object> bindings) {
        this.definition = def;
        this.source = source;
        this.targets = targets == null ? List.of() : List.copyOf(targets);
        this.bindings = bindings == null ? Map.of() : Map.copyOf(bindings);
    }

    /* ===========================
       1) Execution entrypoints
       =========================== */

    public void apply(KnowledgeBase kb) {
        if (executed) return;
        ExecutionContext ctx = new ExecutionContext(kb, this);
        // Costs first
        for (Cost c : definition.getCosts()) {
            if (!c.canPay(ctx)) { this.fizzled = true; return; }
        }
        for (Cost c : definition.getCosts()) c.apply(ctx);

        // Effects pipeline
        for (Effect e : definition.getEffects()) e.apply(ctx);
        this.executed = true;
    }

    public void undo() {
        for (int i = changes.size() - 1; i >= 0; i--) changes.get(i).revert();
        changes.clear();
        executed = false; countered = replaced = fizzled = false;
    }

    // Optional: lightweight simulation without mutations
    public void preview(KnowledgeBase kb) {
        ExecutionContext ctx = new ExecutionContext(kb, this, /*preview*/ true);
        for (Effect e : definition.getEffects()) e.preview(ctx);
    }

    /* ===========================
       2) Flags & accessors
       =========================== */
    public boolean isCountered(){ return countered; }
    public boolean isReplaced(){ return replaced; }
    public boolean isFizzled(){ return fizzled; }
    public Map<String,Object> bindings(){ return bindings; }
    public NounInstance source(){ return source; }
    public List<NounInstance> targets(){ return targets; }

    /* ===========================
       3) Context & change logging
       =========================== */
    public static final class ExecutionContext {
        private final KnowledgeBase kb;
        private final VerbInstance vi;
        private final boolean preview;

        private ExecutionContext(KnowledgeBase kb, VerbInstance vi) { this(kb, vi, false); }
        private ExecutionContext(KnowledgeBase kb, VerbInstance vi, boolean preview) {
            this.kb = kb; this.vi = vi; this.preview = preview;
        }
        static ExecutionContext forAvailability(KnowledgeBase kb, VerbDefinition def, NounInstance src, List<NounInstance> tgts, Map<String,Object> vars) {
            return new ExecutionContext(kb, new VerbInstance(def, src, tgts, vars), true);
        }

        // property mutation with undo logging
        public void change(NounInstance inst, String path, Object newValue) {
            if (preview) return;
            Object old = inst.getProperty(path);
            vi.changes.add(new PropertyChange(inst, path, old));
            inst.setProperty(path, newValue);
            // If your KB needs to index/emit events per change, call that here.
        }

        public void emit(String type, Map<String, Object> payload) {
            if (preview) return;
            kb.recordEvent(type, payload); // implement in your KnowledgeBase
        }

        // convenient helpers used by Effects
        public NounInstance requireSingleTarget() {
            if (vi.targets.isEmpty()) throw new IllegalStateException("No target bound.");
            return vi.targets.get(0);
        }
        public NounInstance source() { return vi.source; }
        public Map<String,Object> vars(){ return vi.bindings; }
        public Object var(String k){ return vi.bindings.get(k); }
        public KnowledgeBase kb(){ return kb; }
    }

    /* ===========================
       4) Undo record
       =========================== */
    private static final class PropertyChange {
        private final NounInstance instance;
        private final String propertyPath;
        private final Object oldValue;
        private PropertyChange(NounInstance inst, String path, Object old) {
            this.instance = inst; this.propertyPath = path; this.oldValue = old;
        }
        private void revert() { instance.setProperty(propertyPath, oldValue); }
    }
}
