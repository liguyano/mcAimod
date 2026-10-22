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
                    if (!List.of("inventory", "chest").contains(source)
                            || !id.matches("[a-z0-9_.-]+:[a-z0-9_./-]{1,128}")
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

    public static BaritoneBridge.Result execute(Plan plan) {
        return KurumiPlanExecutor.start(plan);
    }

    private KurumiTaskPlanner() {}
}
