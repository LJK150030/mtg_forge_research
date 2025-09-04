package APF;

import java.util.*;


/* ===========================
   2) Your VerbDefinition core
   =========================== */

public class VerbDefinition {
    private final String verbName;
    private final String description;
    private final String category; // "ability" | "triggered" | "replacement" | "playerAction" | "generic"

    private final List<PropertyRequirement> prerequisites;
    private final List<TargetSpecification> targetSpecs;
    private final List<Cost> costs;
    private final List<Effect> effects;
    private final Map<String, ValueExpr> variables;     // like SVar bindings, but engine-agnostic
    private final Map<String, Object> metadata;         // UI/AI hints

    private VerbDefinition(Builder b) {
        this.verbName = b.verbName;
        this.description = b.description;
        this.category = b.category;
        this.prerequisites = List.copyOf(b.prerequisites);
        this.targetSpecs = List.copyOf(b.targetSpecs);
        this.costs = List.copyOf(b.costs);
        this.effects = List.copyOf(b.effects);
        this.variables = Map.copyOf(b.variables);
        this.metadata = Map.copyOf(b.metadata);
    }

    public String name() { return verbName; }
    public String category() { return category; }
    public List<TargetSpecification> targets() { return targetSpecs; }
    public Map<String, Object> metadata() { return metadata; }

    /** Check if this verb is currently performable with the given source/targets. */
    public boolean isAvailable(NounInstance source, List<NounInstance> chosenTargets, KnowledgeBase kb) {
        // 1) prerequisites on source
        for (PropertyRequirement req : prerequisites) if (!req.isSatisfied(source)) return false;

        // 2) target arity + filters vs. candidate targets
        if (chosenTargets == null) chosenTargets = List.of();
        if (!targetSpecs.isEmpty()) {
            int i = 0, j = 0;
            while (i < targetSpecs.size()) {
                TargetSpecification spec = targetSpecs.get(i++);
                int accepted = 0;
                while (j < chosenTargets.size() && accepted < spec.max) {
                    NounInstance t = chosenTargets.get(j++);
                    boolean classOk = spec.className == null || spec.className.equals(t.getClassName());
                    boolean filtOk = spec.filter == null || spec.filter.test(t);
                    if (classOk && filtOk) accepted++;
                }
                if (accepted < spec.min) return false;
            }
        }

        // 3) canPay costs? (don’t mutate yet)
        VerbInstance.ExecutionContext probe = VerbInstance.ExecutionContext.forAvailability(kb, this, source, chosenTargets, resolveVars(null, kb, source, chosenTargets));
        for (Cost c : costs) if (!c.canPay(probe)) return false;

        return true;
    }

    /** Bind source/targets and produce a concrete, executable instance. */
    public VerbInstance bind(NounInstance source, List<NounInstance> targets, KnowledgeBase kb) {
        Map<String, Object> boundVars = resolveVars(null, kb, source, targets);
        return new VerbInstance(this, source, targets, boundVars);
    }

    private Map<String, Object> resolveVars(Map<String, Object> overrides,
                                            KnowledgeBase kb,
                                            NounInstance source,
                                            List<NounInstance> targets) {
        Map<String, Object> out = new HashMap<>();
        VerbInstance.ExecutionContext ctx = VerbInstance.ExecutionContext.forAvailability(kb, this, source, targets, out);
        // Evaluate all declared variables now (you can lazily eval too).
        for (Map.Entry<String, ValueExpr> e : variables.entrySet()) {
            out.put(e.getKey(), e.getValue().eval(ctx));
        }
        if (overrides != null) out.putAll(overrides);
        return out;
    }


    // SetProperty("tapped", true) or SetProperty("counters.poison", 3)
    public static final class SetProperty implements Effect {
        private final String path; private final ValueExpr value;
        public SetProperty(String path, ValueExpr value) { this.path = path; this.value = value; }
        @Override public void apply(VerbInstance.ExecutionContext ctx) {
            ctx.change(ctx.requireSingleTarget(), path, value.eval(ctx));
        }
    }

    // IncProperty("counters.+1/+1", +1)
    public static final class IncProperty implements Effect {
        private final String path; private final ValueExpr delta;
        public IncProperty(String path, ValueExpr delta) { this.path = path; this.delta = delta; }
        @Override public void apply(VerbInstance.ExecutionContext ctx) {
            NounInstance t = ctx.requireSingleTarget();
            Object cur = t.getProperty(path);
            Number d = (Number) delta.eval(ctx);
            Number next = (cur instanceof Number ? ((Number)cur).doubleValue() : 0d) + d.doubleValue();
            ctx.change(t, path, next);
        }
    }

    // Transfer(targetInstance, fromZone, toZone) — zones are just strings from your domains.
    public static final class Transfer implements Effect {
        private final ValueExpr instanceRef; // usually the first target or "self"
        private final ValueExpr fromZone, toZone;
        public Transfer(ValueExpr instanceRef, ValueExpr fromZone, ValueExpr toZone) {
            this.instanceRef = instanceRef; this.fromZone = fromZone; this.toZone = toZone;
        }
        @Override public void apply(VerbInstance.ExecutionContext ctx) {
            NounInstance inst = (NounInstance) instanceRef.eval(ctx);
            String from = String.valueOf(fromZone.eval(ctx));
            String to = String.valueOf(toZone.eval(ctx));
            // Implement as two property writes or via your KB move helper:
            ctx.change(inst, "zone", to);
            // (If you track container membership separately, call a kb.move(inst, from, to) here.)
        }
    }

    // EmitEvent("damage", {source, target, amount}) etc.
    public static final class EmitEvent implements Effect {
        private final String type; private final Map<String, ValueExpr> payload;
        public EmitEvent(String type, Map<String, ValueExpr> payload) { this.type = type; this.payload = payload; }
        @Override public void apply(VerbInstance.ExecutionContext ctx) {
            Map<String, Object> m = new HashMap<>();
            for (var e : payload.entrySet()) m.put(e.getKey(), e.getValue().eval(ctx));
            ctx.emit(type, m);
        }
    }


    public static final class Builder {
        private String verbName, description = "", category = "generic";
        private final List<PropertyRequirement> prerequisites = new ArrayList<>();
        private final List<TargetSpecification> targetSpecs = new ArrayList<>();
        private final List<Cost> costs = new ArrayList<>();
        private final List<Effect> effects = new ArrayList<>();
        private final Map<String, ValueExpr> variables = new HashMap<>();
        private final Map<String, Object> metadata = new HashMap<>();

        public Builder name(String n) { this.verbName = n; return this; }
        public Builder description(String d){ this.description = d; return this; }
        public Builder category(String c){ this.category = c; return this; }

        public Builder addPrereq(PropertyRequirement r){ prerequisites.add(r); return this; }
        public Builder addTarget(TargetSpecification t){ targetSpecs.add(t); return this; }
        public Builder addVariable(String key, ValueExpr v){ variables.put(key, v); return this; }
        public Builder addCost(Cost c){ costs.add(c); return this; }
        public Builder addEffect(Effect e){ effects.add(e); return this; }
        public Builder putMeta(String k, Object v){ metadata.put(k, v); return this; }

        public VerbDefinition build(){ return new VerbDefinition(this); }
    }

    /* package-private */ List<Cost> getCosts() { return costs; }
    /* package-private */ List<Effect> getEffects() { return effects; }
}
