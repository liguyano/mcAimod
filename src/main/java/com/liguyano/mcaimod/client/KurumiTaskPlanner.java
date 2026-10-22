package com.liguyano.mcaimod.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.List;
import java.util.ArrayList;
import java.util.Optional;

/**
 * Validates an AI-authored plan before any action is dispatched.
 * Only explicitly supported, bounded compositions may execute.
 */
public final class KurumiTaskPlanner {
    public record Step(String action, String itemId) {}
    public record Plan(List<Step> steps, int quantity, int takeQuantity) {}

    public static Optional<Plan> parse(JsonObject object) {
        try {
            if (!object.has("steps") || !object.get("steps").isJsonArray()) return Optional.empty();
            JsonArray raw = object.getAsJsonArray("steps");
            if (raw.size() < 2 || raw.size() > 4) return Optional.empty();
            List<Step> steps = new ArrayList<>();
            for (JsonElement element : raw) {
                if (!element.isJsonObject()) return Optional.empty();
                JsonObject step = element.getAsJsonObject();
                if (!step.has("action") || !step.has("item_id")) return Optional.empty();
                String action = step.get("action").getAsString();
                String item = step.get("item_id").getAsString();
                if (!item.matches("minecraft:[a-z0-9_]{1,64}")) return Optional.empty();
                if (!List.of("MINE", "PICKUP_ITEM_ALL", "CHEST_PUT_ITEM_ALL", "EQUIP_MAIN_HAND", "CHEST_TAKE", "CRAFT_ITEM").contains(action))
                    return Optional.empty();
                steps.add(new Step(action, item));
            }
            int qty = object.has("quantity") ? object.get("quantity").getAsInt() : 32;
            if (qty < 1 || qty > 64) return Optional.empty();
            if (steps.size() == 2 && steps.get(0).action().equals("CHEST_TAKE")
                    && steps.get(1).action().equals("CRAFT_ITEM")) {
                if (!object.has("take_quantity") || !object.has("quantity")) return Optional.empty();
                int take = object.get("take_quantity").getAsInt();
                if (take < 1 || take > 64) return Optional.empty();
                return Optional.of(new Plan(List.copyOf(steps), qty, take));
            }
            // The first supported multi-step workflow is equip(optional) -> mine -> pickup -> store.
            int offset = steps.get(0).action().equals("EQUIP_MAIN_HAND") ? 1 : 0;
            if (steps.size() != offset + 3) return Optional.empty();
            String block = steps.get(offset).itemId();
            if (!steps.get(offset).action().equals("MINE")
                    || !steps.get(offset + 1).action().equals("PICKUP_ITEM_ALL")
                    || !steps.get(offset + 2).action().equals("CHEST_PUT_ITEM_ALL")
                    || !block.equals(steps.get(offset + 1).itemId())
                    || !block.equals(steps.get(offset + 2).itemId())) return Optional.empty();
            if (!List.of("minecraft:cherry_log", "minecraft:oak_log", "minecraft:spruce_log",
                    "minecraft:birch_log", "minecraft:jungle_log", "minecraft:acacia_log",
                    "minecraft:dark_oak_log", "minecraft:mangrove_log", "minecraft:pale_oak_log")
                    .contains(block)) return Optional.empty();
            return Optional.of(new Plan(List.copyOf(steps), qty, 0));
        } catch (RuntimeException ex) { return Optional.empty(); }
    }

    public static BaritoneBridge.Result execute(Plan plan) {
        if (KurumiLongTaskBridge.busy())
            return new BaritoneBridge.Result(false, "已有长任务执行中");
        if (plan.steps().get(0).action().equals("CHEST_TAKE")) {
            return KurumiChestCraftTask.start(plan.steps().get(0).itemId(), plan.takeQuantity(),
                    plan.steps().get(1).itemId(), plan.quantity());
        }
        int offset = plan.steps().get(0).action().equals("EQUIP_MAIN_HAND") ? 1 : 0;
        if (offset == 1) {
            BaritoneBridge.Result equip = KurumiEquipBridge.mainHand(plan.steps().get(0).itemId());
            if (!equip.success()) return equip;
        }
        return KurumiLongTaskBridge.collectAndStore(plan.steps().get(offset).itemId(), plan.quantity());
    }

    private KurumiTaskPlanner() {}
}
