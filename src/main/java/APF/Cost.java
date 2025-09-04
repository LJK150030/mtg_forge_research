package APF;

public interface Cost {
    boolean canPay(VerbInstance.ExecutionContext ctx);
    void apply(VerbInstance.ExecutionContext ctx);
    default void preview(VerbInstance.ExecutionContext ctx) {}
}
