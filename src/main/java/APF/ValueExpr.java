package APF;

// A small expression interface for computed values (replaces ad-hoc SVars).
public interface ValueExpr {
    Object eval(VerbInstance.ExecutionContext ctx);
    static ValueExpr of(Object constant) { return ctx -> constant; }
}
