package APF;

public interface Effect {
    void apply(VerbInstance.ExecutionContext ctx);
    default void preview(VerbInstance.ExecutionContext ctx) {} // optional
}
