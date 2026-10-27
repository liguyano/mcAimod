package com.liguyano.mcaimod.client;

import java.util.List;

/** Pure inspection/speech scripts do not require the world-action toggle. */
public final class KurumiScriptSafety {
    public static boolean isReadOnly(List<KurumiTaskPlanner.Statement> statements) {
        if (statements.isEmpty()) return false;
        for (var statement : statements) {
            boolean safe = switch (statement.op()) {
                case "TALK", "OBSERVE_TALK", "EXPR_ASSIGN", "EXPR_EVAL" -> true;
                case "EXPR_CALL" -> "talk".equals(statement.step().action())
                        || "observe".equals(statement.step().action());
                case "EXPR_IF", "EXPR_WHILE" -> isReadOnly(statement.body())
                        && (statement.otherwise().isEmpty() || isReadOnly(statement.otherwise()));
                default -> false;
            };
            if (!safe) return false;
        }
        return true;
    }
    private KurumiScriptSafety() {}
}
