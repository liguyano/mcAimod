package com.liguyano.mcaimod.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.Optional;

/** Parse a model response as data, never as an executable command. */
public final class KurumiDecisionParser {
    public record Decision(String reply, Optional<KurumiActionValidator.Action> action, String target, Integer x, Integer y, Integer z, String blockId) {}

    public static Optional<Decision> parse(String raw) {
        if (raw == null || raw.length() > 8192) return Optional.empty();
        try {
            JsonObject object = JsonParser.parseString(raw.strip()).getAsJsonObject();
            if (!object.has("reply") || !object.get("reply").isJsonPrimitive()) return Optional.empty();
            String reply = object.get("reply").getAsString().strip().replaceAll("[\\r\\n]+", " ");
            if (reply.isEmpty() || reply.length() > 160 || reply.startsWith("/")) return Optional.empty();
            if (!object.has("type") || !object.get("type").isJsonPrimitive()) return Optional.empty();
            String type = object.get("type").getAsString();
            if ("chat".equals(type)) return Optional.of(new Decision(reply, Optional.empty(), "", null, null, null, ""));
            if (!"action".equals(type)) return Optional.empty();
            if (!object.has("action") || !object.get("action").isJsonPrimitive()) return Optional.empty();
            String name = object.get("action").getAsString();
            if ("MINE".equals(name)) {
                String block = object.has("block_id") ? object.get("block_id").getAsString() : "";
                if (!block.matches("minecraft:[a-z0-9_]{1,64}")) return Optional.empty();
                if (!java.util.Set.of("minecraft:oak_log", "minecraft:spruce_log",
                        "minecraft:birch_log", "minecraft:jungle_log", "minecraft:acacia_log",
                        "minecraft:dark_oak_log", "minecraft:mangrove_log",
                        "minecraft:cherry_log", "minecraft:pale_oak_log",
                        "minecraft:crimson_stem", "minecraft:warped_stem").contains(block))
                    return Optional.empty();
                return Optional.of(new Decision(reply, Optional.empty(), "", null, null, null, block));
            }
            if ("ATTACK_PLAYER".equals(name)) {
                String target = object.has("target") ? object.get("target").getAsString() : "";
                if (!target.matches("[A-Za-z0-9_]{3,16}")) return Optional.empty();
                return Optional.of(new Decision(reply, Optional.empty(), "attack_player:" + target, null, null, null, ""));
            }
            if ("ATTACK_MOB".equals(name)) {
                String id = object.has("entity_id") ? object.get("entity_id").getAsString() : "";
                if (!id.matches("[a-z0-9_.-]+:[a-z0-9_./-]{1,128}")) return Optional.empty();
                return Optional.of(new Decision(reply, Optional.empty(), "attack_mob:" + id, null, null, null, ""));
            }
            if ("TPA_REQUEST_TO".equals(name)) {
                String target = object.has("target") ? object.get("target").getAsString() : "";
                if (!target.matches("[A-Za-z0-9_]{3,16}")) return Optional.empty();
                return Optional.of(new Decision(reply, Optional.empty(), "tpa:" + target, null, null, null, ""));
            }
            if ("FOLLOW".equals(name) || "COME".equals(name)) {
                String target = object.has("target") ? object.get("target").getAsString() : "";
                if (!target.matches("[A-Za-z0-9_]{3,16}")) return Optional.empty();
                return Optional.of(new Decision(reply, Optional.empty(), "COME".equals(name) ? "come:" + target : target, null, null, null, ""));
            }
            if ("GOTO".equals(name)) {
                if (!object.has("x") || !object.has("y") || !object.has("z")) return Optional.empty();
                int x = object.get("x").getAsInt(), y = object.get("y").getAsInt(), z = object.get("z").getAsInt();
                if (y < -64 || y > 320 || Math.abs((long)x) > 30000000 || Math.abs((long)z) > 30000000) return Optional.empty();
                return Optional.of(new Decision(reply, Optional.empty(), "", x, y, z, ""));
            }
            if (!"FARM".equals(name) && !"STOP".equals(name) && !"TPA_REQUEST".equals(name) && !"TPA_ACCEPT".equals(name) && !"SLEEP".equals(name) && !"DROP_ITEM_ALL".equals(name) && !"CHEST_PUT_ITEM_ALL".equals(name) && !"DROP_ALL".equals(name) && !"CHEST_PUT_ALL".equals(name) && !"CHEST_TAKE_ALL".equals(name) && !"CRAFT_ITEM".equals(name) && !"CRAFT_OPEN".equals(name) && !"CHEST_OPEN".equals(name) && !"CHEST_PUT".equals(name) && !"PICKUP_ALL".equals(name) && !"PICKUP_ITEM_ALL".equals(name) && !"PICKUP".equals(name) && !"DROP".equals(name) && !"CHAIN_ON".equals(name) && !"CHAIN_OFF".equals(name)) return Optional.empty();
            String item = object.has("item_id") ? object.get("item_id").getAsString() : "";
            int quantity = object.has("quantity") ? object.get("quantity").getAsInt() : 0;
            int radius = object.has("radius") ? object.get("radius").getAsInt() : 0;
            if ("FARM".equals(name) && radius != 16 || "STOP".equals(name) && radius != 0) return Optional.empty();
            Optional<KurumiActionValidator.Action> action = KurumiActionValidator.validate(name, item, quantity, radius);
            return action.map(value -> new Decision(reply, Optional.of(value), "", null, null, null, ""));
        } catch (RuntimeException error) {
            return Optional.empty();
        }
    }

    private KurumiDecisionParser() {}
}
