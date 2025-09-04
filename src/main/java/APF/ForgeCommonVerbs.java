// ForgeCommonVerbs.java
package APF;

import java.util.List;

public final class ForgeCommonVerbs {
    private ForgeCommonVerbs() {}

    /** Generic “Tap target permanent.” */
    public static VerbDefinition tapVerb() {
        TargetSpecification target = new TargetSpecification(
                /* className */ null, // accept any NounInstance class (we'll gate via filter)
                /* filter    */ (NounInstance n) -> {
            // zone is modeled as List<String>, and tapped is a boolean in your schema
            Object zoneObj = n.getProperty("zone");
            boolean onBattlefield = false;
            if (zoneObj instanceof List) {
                @SuppressWarnings("unchecked")
                List<String> z = (List<String>) zoneObj;
                onBattlefield = z.stream().anyMatch("Battlefield"::equals);
            } else if (zoneObj instanceof String) {
                onBattlefield = "Battlefield".equals(zoneObj);
            }
            Object tappedObj = n.getProperty("tapped");
            boolean isUntapped = !(tappedObj instanceof Boolean) || !((Boolean) tappedObj);
            return onBattlefield && isUntapped;
        },
                /* min */ 1,
                /* max */ 1
        );

        // The effect is how the verb “would” change state if we executed it inside APF.
        // We’ll still *record* a VerbInstance when Forge fires the event, even if we don’t re-apply this.
        Effect setTappedTrue = new VerbDefinition.SetProperty("tapped", ValueExpr.of(Boolean.TRUE));

        return new VerbDefinition.Builder()
                .name("Tap")
                .category("action")
                .description("Tap target permanent.")
                .addTarget(target)
                .addEffect(setTappedTrue)
                // Helpful hint for analytics/UI/agents:
                .putMeta("mtg.keyword", "Tap")
                .build();
    }
}
