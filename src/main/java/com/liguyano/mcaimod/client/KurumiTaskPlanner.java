package com.liguyano.mcaimod.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Validates a bounded, freely ordered sequence of supported game actions. */
public final class KurumiTaskPlanner {
    public record Step(String action, String itemId, int quantity, Integer x, Integer y, Integer z, String target) {}
    public record BreakCondition(String source, String itemId, int count) {}
    public record Plan(List<Step> steps, int quantity, int takeQuantity, int loopCount,
                       List<BreakCondition> breakConditions) {}
    /** A restricted pseudocode AST; it is data, never evaluated as Java/Python code. */
    public record Condition(String source, String itemId, String operator, int count) {}
    public record Statement(String op, Step step, Condition condition, List<Statement> body,
                            List<Statement> otherwise) {}
    public record Program(List<Statement> statements) {}
    private static final List<String> SUPPORTED = List.of(
            "EQUIP_MAIN_HAND", "EQUIP_ARMOR", "GOTO", "CHEST_TAKE", "CHEST_PUT",
            "CHEST_PUT_ITEM_ALL", "CRAFT_ITEM", "PICKUP_ITEM_ALL", "MINE",
            "ATTACK_MOB", "ATTACK_PLAYER");

    public static Optional<Plan> parse(JsonObject object) {
        try {
            if (!object.has("steps") || !object.get("steps").isJsonArray()) return Optional.empty();
            JsonArray raw = object.getAsJsonArray("steps");
            if (raw.size() < 1 || raw.size() > 8) return Optional.empty();
            int defaultQuantity = object.has("quantity") ? object.get("quantity").getAsInt() : 1;
            int takeQuantity = object.has("take_quantity") ? object.get("take_quantity").getAsInt() : defaultQuantity;
            if (defaultQuantity < 1 || defaultQuantity > 64 || takeQuantity < 1 || takeQuantity > 64)
                return Optional.empty();
            int loopCount = object.has("loop_count") ? object.get("loop_count").getAsInt() : 1;
            if (loopCount < 1 || loopCount > 20) return Optional.empty();
            List<BreakCondition> conditions = new ArrayList<>();
            if (object.has("break_conditions")) {
                if (!object.get("break_conditions").isJsonArray()) return Optional.empty();
                JsonArray checks = object.getAsJsonArray("break_conditions");
                if (checks.size() > 4) return Optional.empty();
                for (JsonElement element : checks) {
                    if (!element.isJsonObject()) return Optional.empty();
                    JsonObject check = element.getAsJsonObject();
                    String source = check.get("source").getAsString();
                    String id = check.get("item_id").getAsString();
                    int count = check.get("count").getAsInt();
                    if (!List.of("inventory", "chest", "result").contains(source)
                            || (!"result".equals(source) && !id.matches("[a-z0-9_.-]+:[a-z0-9_./-]{1,128}"))
                            || count < 1 || count > 2304) return Optional.empty();
                    conditions.add(new BreakCondition(source, id, count));
                }
            }
            List<Step> steps = new ArrayList<>();
            for (JsonElement element : raw) {
                if (!element.isJsonObject()) return Optional.empty();
                JsonObject step = element.getAsJsonObject();
                String action = step.get("action").getAsString();
                if (!SUPPORTED.contains(action)) return Optional.empty();
                String item = step.has("item_id") ? step.get("item_id").getAsString() : "";
                int qty = step.has("quantity") ? step.get("quantity").getAsInt()
                        : action.equals("CHEST_TAKE") ? takeQuantity : defaultQuantity;
                if (qty < 1 || qty > 64) return Optional.empty();
                Integer x = null, y = null, z = null;
                String target = step.has("target") ? step.get("target").getAsString() : "";
                if (action.equals("ATTACK_PLAYER")) {
                    if (!target.matches("[A-Za-z0-9_]{3,16}") || "SekaiSaionji".equals(target)) return Optional.empty();
                } else if (action.equals("ATTACK_MOB")) {
                    if (!step.has("entity_id")) return Optional.empty();
                    target = step.get("entity_id").getAsString();
                    if (!target.matches("[a-z0-9_.-]+:[a-z0-9_./-]{1,128}")) return Optional.empty();
                }
                if (action.equals("GOTO")) {
                    x = step.get("x").getAsInt();
                    y = step.get("y").getAsInt();
                    z = step.get("z").getAsInt();
                    if (y < -64 || y > 320 || Math.abs((long)x) > 30000000
                            || Math.abs((long)z) > 30000000) return Optional.empty();
                } else if (!action.startsWith("ATTACK_")) {
                    if (!item.matches("[a-z0-9_.-]+:[a-z0-9_./-]{1,128}")) return Optional.empty();
                    if (action.equals("MINE") && !List.of("minecraft:oak_log", "minecraft:spruce_log",
                            "minecraft:birch_log", "minecraft:jungle_log", "minecraft:acacia_log",
                            "minecraft:dark_oak_log", "minecraft:mangrove_log", "minecraft:cherry_log",
                            "minecraft:pale_oak_log", "minecraft:crimson_stem", "minecraft:warped_stem")
                            .contains(item)) return Optional.empty();
                }
                steps.add(new Step(action, item, qty, x, y, z, target));
            }
            return Optional.of(new Plan(List.copyOf(steps), defaultQuantity, takeQuantity, loopCount, List.copyOf(conditions)));
        } catch (RuntimeException e) { return Optional.empty(); }
    }

    public static Optional<Program> parseProgram(JsonObject object) {
        try {
            if (!object.has("program") || !object.get("program").isJsonArray()) return Optional.empty();
            int[] budget = {0};
            List<Statement> statements = parseStatements(object.getAsJsonArray("program"), 0, budget);
            return statements.isEmpty() ? Optional.empty() : Optional.of(new Program(statements));
        } catch (RuntimeException ex) { return Optional.empty(); }
    }

    private static List<Statement> parseStatements(JsonArray array, int depth, int[] budget) {
        if (depth > 3 || array.size() > 16) throw new IllegalArgumentException("nesting");
        List<Statement> statements = new ArrayList<>();
        for (JsonElement el : array) {
            if (++budget[0] > 48 || !el.isJsonObject()) throw new IllegalArgumentException("budget");
            JsonObject node = el.getAsJsonObject();
            String op = node.get("op").getAsString();
            if ("IF".equals(op) || "WHILE".equals(op)) {
                Condition condition = parseCondition(node.getAsJsonObject("condition"));
                List<Statement> body = parseStatements(node.getAsJsonArray("body"), depth + 1, budget);
                List<Statement> otherwise = "IF".equals(op) && node.has("else")
                        ? parseStatements(node.getAsJsonArray("else"), depth + 1, budget) : List.of();
                statements.add(new Statement(op, null, condition, body, otherwise));
            } else if ("CHAIN".equals(op)) {
                String action = node.get("enabled").getAsBoolean() ? "CHAIN_ON" : "CHAIN_OFF";
                statements.add(new Statement("ACTION", new Step(action, "", 1, null, null, null, ""), null, List.of(), List.of()));
            } else if ("SLEEP".equals(op) || "SLEEP_RESULT".equals(op)) {
                statements.add(new Statement(op, null, null, List.of(), List.of()));
            } else if ("FARM".equals(op) || "FARM_RESULT".equals(op)) {
                int radius = node.get("radius").getAsInt();
                if (radius < 4 || radius > 32) throw new IllegalArgumentException("farm radius");
                statements.add(new Statement(op, new Step("FARM", "", radius, null, null, null, ""),
                        null, List.of(), List.of()));
            } else if ("TALK".equals(op)) {
                String message = node.get("message").getAsString();
                if (message.isBlank() || message.length() > 120
                        || message.indexOf('\n') >= 0 || message.indexOf('\r') >= 0
                        || message.startsWith("/")) throw new IllegalArgumentException("invalid talk");
                statements.add(new Statement(op, new Step("TALK", "", 1, null, null, null, message),
                        null, List.of(), List.of()));
            } else if ("RETURN".equals(op)) {
                statements.add(new Statement(op, null, null, List.of(), List.of()));
            } else if ("BREAK".equals(op)) {
                statements.add(new Statement(op, null, null, List.of(), List.of()));
            } else if ("ACTION".equals(op) || "ACTION_RESULT".equals(op)) {
                JsonObject stepNode = node.getAsJsonObject("step");
                String special = stepNode.get("action").getAsString();
                // Script-only actions use their own validated parameters; the legacy
                // EXECUTE_PLAN validator intentionally does not accept these actions.
                if (java.util.Set.of("COLLECT_AND_STORE", "STOP", "TPA_REQUEST", "TPA_ACCEPT",
                        "TPA_REQUEST_TO", "CRAFT_OPEN", "CHEST_OPEN", "CHEST_PUT_ALL",
                        "CHEST_TAKE_ALL", "DROP_ALL", "DROP_ITEM_ALL", "DROP",
                        "PICKUP", "PICKUP_ALL").contains(special)) {
                    String item = stepNode.has("item_id") ? stepNode.get("item_id").getAsString() : "";
                    String target = stepNode.has("target") ? stepNode.get("target").getAsString() : "";
                    int quantity = stepNode.has("quantity") ? stepNode.get("quantity").getAsInt() : 1;
                    boolean needsItem = java.util.Set.of("COLLECT_AND_STORE", "DROP_ITEM_ALL",
                            "DROP", "PICKUP").contains(special);
                    if (needsItem && !item.matches("[a-z0-9_.-]+:[a-z0-9_./-]{1,128}"))
                        throw new IllegalArgumentException("invalid item id");
                    if (java.util.Set.of("COLLECT_AND_STORE", "DROP", "PICKUP").contains(special)
                            && (quantity < 1 || quantity > 64))
                        throw new IllegalArgumentException("invalid quantity");
                    if ("TPA_REQUEST_TO".equals(special)
                            && !target.matches("[A-Za-z0-9_]{3,16}"))
                        throw new IllegalArgumentException("invalid player");
                    statements.add(new Statement(op,
                            new Step(special, item, quantity, null, null, null, target),
                            null, List.of(), List.of()));
                    if (node.has("line"))
                        KurumiWebMonitor.registerLine(statements.get(statements.size() - 1),
                                node.get("line").getAsInt());
                    continue;
                }
                if ("INTERACT".equals(special) || "PUT_ITEM".equals(special)) {
                    String item = stepNode.get("item_id").getAsString();
                    int quantity = stepNode.has("quantity") ? stepNode.get("quantity").getAsInt() : 1;
                    String slot = stepNode.has("target") ? stepNode.get("target").getAsString() : "input";
                    if (!item.matches("[a-z0-9_.-]+:[a-z0-9_./-]{1,128}")
                            || quantity < 1 || quantity > 64
                            || "PUT_ITEM".equals(special)
                            && !java.util.Set.of("input", "fuel").contains(slot))
                        throw new IllegalArgumentException("invalid interaction");
                    statements.add(new Statement(op, new Step(special, item, quantity,
                            null, null, null, slot), null, List.of(), List.of()));
                    if (node.has("line"))
                        KurumiWebMonitor.registerLine(statements.get(statements.size() - 1),
                                node.get("line").getAsInt());
                    continue;
                }
                if ("GOTO_NAMED".equals(special)) {
                    String name = stepNode.get("target").getAsString().strip();
                    if (name.isEmpty() || name.length() > 24)
                        throw new IllegalArgumentException("invalid location name");
                    statements.add(new Statement(op, new Step(special, "", 1, null, null, null, name),
                            null, List.of(), List.of()));
                    if (node.has("line"))
                        KurumiWebMonitor.registerLine(statements.get(statements.size() - 1),
                                node.get("line").getAsInt());
                    continue;
                }
                if ("COME".equals(special) || "FOLLOW".equals(special)) {
                    String target = stepNode.get("target").getAsString();
                    if (!target.matches("[A-Za-z0-9_]{3,16}")) throw new IllegalArgumentException("invalid player");
                    statements.add(new Statement(op, new Step(special, "", 1, null, null, null, target), null, List.of(), List.of()));
                    if (node.has("line")) KurumiWebMonitor.registerLine(statements.get(statements.size() - 1), node.get("line").getAsInt());
                    continue;
                }
                JsonObject single = new JsonObject();
                JsonArray steps = new JsonArray();
                steps.add(node.getAsJsonObject("step"));
                single.add("steps", steps);
                Plan parsed = parse(single).orElseThrow();
                statements.add(new Statement(op, parsed.steps().get(0), null, List.of(), List.of()));
            } else throw new IllegalArgumentException("unsupported op");
            if (node.has("line")) KurumiWebMonitor.registerLine(statements.get(statements.size() - 1), node.get("line").getAsInt());
        }
        return List.copyOf(statements);
    }

    private static Condition parseCondition(JsonObject node) {
        String source = node.get("source").getAsString();
        String id = "result".equals(source) ? "" : node.get("item_id").getAsString();
        String operator = node.get("operator").getAsString();
        int count = "result".equals(source) ? 1 : node.get("count").getAsInt();
        if (!List.of("inventory", "chest", "result").contains(source)
                || !List.of("LT", "LE", "EQ", "GE", "GT", "NE").contains(operator)
                || (!"result".equals(source) && !id.matches("[a-z0-9_.-]+:[a-z0-9_./-]{1,128}"))
                || count < 0 || count > 2304) throw new IllegalArgumentException("condition");
        return new Condition(source, id, operator, count);
    }

    public static BaritoneBridge.Result execute(Plan plan) {
        return KurumiPlanExecutor.start(plan);
    }

    private KurumiTaskPlanner() {}
}
