package APF;

import forge.game.spellability.SpellAbility;

// Pseudocode: sits in a Forge-facing module, not in APF core.
public final class ForgeAdapter {
    public static VerbDefinition fromSpellAbility(SpellAbility sa) {
        VerbDefinition.Builder b = new VerbDefinition.Builder()
                .name(String.valueOf(sa.getApi()))                       // e.g., "ChangeZone"
                .category("ability")
                .description(sa.getDescription());

        // prerequisites: zone, controller, timing, etc.
        // targets: translate TargetRestrictions -> TargetSpecification
        // costs: translate Cost objects -> Cost (Effect) pipeline
        // variables: translate SVars/Count$ into ValueExpr lambdas
        // effects: map ApiType -> one or more atomic effects
        //    ChangeZone -> new VerbDefinition.Transfer(ValueExpr.of(self), of(from), of(to));
        //    Counters   -> new IncProperty("counters."+type, ValueExpr.of(n));
        //    Damage     -> new EmitEvent("damage", Map.of("source", of(self), "target", of(target), "amount", computeX));
        // (etc.)

        return b.build();
    }
}
