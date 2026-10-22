package com.liguyano.mcaimod.client;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.neoforged.fml.loading.FMLPaths;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Persistent, server-and-dimension-scoped named locations. Only the owner can edit. */
public final class KurumiLocationMemory {
    private static final Gson GSON = new Gson();
    private static final Pattern SAVE = Pattern.compile("(?:记住|记下|标记)(?:这里|这个位置|这个地方)?(?:是|叫|为)(.{1,24})");
    private static final Pattern GO = Pattern.compile("(?:去|前往|带我去|回到|回)(.{1,24})");
    private static final Pattern WHERE = Pattern.compile("(.{1,24})(?:在哪里|在哪儿|在哪|的坐标)");
    private record Point(String dimension, int x, int y, int z) {}

    public static boolean handle(String message, String senderName) {
        if (!"SekaiSaionji".equals(senderName)) return false;
        String body = message.strip().replaceFirst("(?i)^.*?kurumi[，,：: ]*", "").strip();
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.getConnection() == null) return false;
        // Handle standalone location-memory commands deterministically instead of
        // letting the model claim it saved a place without writing the file.
        // Compound requests still go to the planner.
        if (body.contains("然后") || body.contains("之后") || body.contains("再去")
                || body.contains("并且") || body.contains("接着")
                || body.contains("，") || body.contains(",") || body.contains("；")
                || body.contains(";") || body.contains("。") || body.contains("\\n"))
            return false;
        boolean locationIntent = body.startsWith("去") || body.startsWith("记住")
                || body.startsWith("记下") || body.startsWith("标记")
                || body.startsWith("忘记") || body.startsWith("删除地点")
                || body.contains("在哪里") || body.contains("在哪儿")
                || body.contains("在哪") || body.contains("的坐标")
                || body.contains("记住了哪些") || body.contains("地点列表")
                || body.contains("记忆的地点");
        if (!locationIntent) return false;
        Matcher coordinates = Pattern.compile(
                "去(?:坐标)?[ ]*(-?[0-9]+)[ ]+(-?[0-9]+)[ ]+(-?[0-9]+)").matcher(body);
        if (coordinates.matches()) {
            try {
                int x = Integer.parseInt(coordinates.group(1));
                int y = Integer.parseInt(coordinates.group(2));
                int z = Integer.parseInt(coordinates.group(3));
                reply(BaritoneBridge.gotoCoordinates(x, y, z).message());
            } catch (NumberFormatException ex) {
                reply("坐标超出允许范围");
            }
            return true;
        }
        if (body.matches(".*-?[0-9]+[ ]+-?[0-9]+[ ]+-?[0-9]+.*"))
            return false;
        Matcher save = SAVE.matcher(body), go = GO.matcher(body), where = WHERE.matcher(body);
        boolean list = body.contains("记住了哪些") || body.contains("地点列表") || body.contains("记忆的地点");
        boolean delete = body.startsWith("忘记") || body.startsWith("删除地点");
        if (!save.find() && !go.find() && !where.find() && !list && !delete) return false;
        try {
            String scope = mc.getCurrentServer() == null ? "singleplayer" : mc.getCurrentServer().ip;
            Path path = FMLPaths.CONFIGDIR.get().resolve("mcaimod-locations.json");
            JsonObject root = Files.exists(path)
                    ? JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8)).getAsJsonObject()
                    : new JsonObject();
            String key = scope.replaceAll("[^a-zA-Z0-9_.:-]", "_");
            JsonObject locations = root.has(key) && root.get(key).isJsonObject()
                    ? root.getAsJsonObject(key) : new JsonObject();
            String dimension = mc.level.dimension().toString();
            if (save.matches()) {
                String name = clean(save.group(1));
                Player owner = null;
                for (Player p : mc.level.players()) {
                    if ("SekaiSaionji".equals(p.getName().getString())) { owner = p; break; }
                }
                if (owner == null) { reply("主人不在可见范围内，无法确认位置"); return true; }
                JsonObject point = new JsonObject();
                point.addProperty("dimension", dimension);
                point.addProperty("x", owner.getBlockX());
                point.addProperty("y", owner.getBlockY());
                point.addProperty("z", owner.getBlockZ());
                locations.add(name, point);
                root.add(key, locations);
                Files.writeString(path, GSON.toJson(root), StandardCharsets.UTF_8);
                reply("记住啦！" + name + "：" + dimension + " (" + owner.getBlockX() + ", " + owner.getBlockY() + ", " + owner.getBlockZ() + ")");
                return true;
            }
            if (list) {
                reply("已记住的地点：" + (locations.size() == 0 ? "暂无" : String.join("、", locations.keySet())));
                return true;
            }
            if (delete) {
                String name = clean(body.replaceFirst("^(忘记|删除地点)", ""));
                if (locations.remove(name) == null) reply("没有找到地点：" + name);
                else {
                    root.add(key, locations);
                    Files.writeString(path, GSON.toJson(root), StandardCharsets.UTF_8);
                    reply("已忘记地点：" + name);
                }
                return true;
            }
            String name = clean(go.matches() ? go.group(1) : where.matches() ? where.group(1) : "");
            if (!locations.has(name)) { reply("还没有记住地点：" + name); return true; }
            JsonObject point = locations.getAsJsonObject(name);
            String targetDimension = point.get("dimension").getAsString();
            int x = point.get("x").getAsInt(), y = point.get("y").getAsInt(), z = point.get("z").getAsInt();
            if (go.matches()) {
                if (!dimension.equals(targetDimension)) reply(name + " 位于 " + targetDimension + "，不能直接跨维度导航");
                else reply(BaritoneBridge.gotoCoordinates(x, y, z).message());
            } else reply(name + " 在 " + targetDimension + " (" + x + ", " + y + ", " + z + ")");
            return true;
        } catch (Exception ex) {
            reply("地点记忆读取或保存失败");
            return true;
        }
    }

    /** Resolve a saved name in the current server and dimension and start navigation. */
    public static BaritoneBridge.Result gotoNamed(String name) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.getConnection() == null)
            return new BaritoneBridge.Result(false, "游戏世界尚未就绪");
        String cleaned = clean(name);
        if (cleaned.isEmpty() || cleaned.length() > 24)
            return new BaritoneBridge.Result(false, "地点名称无效");
        try {
            String scope = mc.getCurrentServer() == null ? "singleplayer" : mc.getCurrentServer().ip;
            String key = scope.replaceAll("[^a-zA-Z0-9_.:-]", "_");
            Path path = FMLPaths.CONFIGDIR.get().resolve("mcaimod-locations.json");
            if (!Files.exists(path)) return new BaritoneBridge.Result(false, "还没有记住地点：" + cleaned);
            JsonObject root = JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8)).getAsJsonObject();
            if (!root.has(key) || !root.get(key).isJsonObject()
                    || !root.getAsJsonObject(key).has(cleaned))
                return new BaritoneBridge.Result(false, "还没有记住地点：" + cleaned);
            JsonObject point = root.getAsJsonObject(key).getAsJsonObject(cleaned);
            if (!mc.level.dimension().toString().equals(point.get("dimension").getAsString()))
                return new BaritoneBridge.Result(false, "地点位于其他维度，无法直接寻路：" + cleaned);
            return BaritoneBridge.gotoCoordinates(point.get("x").getAsInt(),
                    point.get("y").getAsInt(), point.get("z").getAsInt());
        } catch (RuntimeException | java.io.IOException ex) {
            return new BaritoneBridge.Result(false, "地点数据读取失败");
        }
    }

    /** Read-only grounded location context for the model; never infer coordinates. */
    public static String contextForModel() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return "世界尚未加载";
        StringBuilder context = new StringBuilder();
        context.append("Kurumi当前位置：")
                .append(mc.player.getBlockX()).append(", ")
                .append(mc.player.getBlockY()).append(", ")
                .append(mc.player.getBlockZ()).append("；维度：")
                .append(mc.level.dimension()).append("。");
        Player owner = null;
        for (Player p : mc.level.players()) {
            if ("SekaiSaionji".equals(p.getName().getString())) { owner = p; break; }
        }
        if (owner == null) context.append("主人当前坐标不可见；不要猜测。");
        else context.append("主人当前位置：")
                .append(owner.getBlockX()).append(", ")
                .append(owner.getBlockY()).append(", ")
                .append(owner.getBlockZ()).append("。");
        try {
            String scope = mc.getCurrentServer() == null ? "singleplayer" : mc.getCurrentServer().ip;
            String key = scope.replaceAll("[^a-zA-Z0-9_.:-]", "_");
            Path path = FMLPaths.CONFIGDIR.get().resolve("mcaimod-locations.json");
            if (!Files.exists(path)) return context.append("尚无已保存地点。").toString();
            JsonObject root = JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8)).getAsJsonObject();
            if (!root.has(key) || !root.get(key).isJsonObject())
                return context.append("尚无当前服务器已保存地点。").toString();
            JsonObject locations = root.getAsJsonObject(key);
            context.append("当前服务器已保存地点（仅供查询或规划，不能跨维度直接寻路）：");
            int count = 0;
            for (Map.Entry<String, com.google.gson.JsonElement> entry : locations.entrySet()) {
                if (count++ >= 20) { context.append("其余地点省略；"); break; }
                if (!entry.getValue().isJsonObject()) continue;
                JsonObject point = entry.getValue().getAsJsonObject();
                if (!point.has("dimension") || !point.has("x") || !point.has("y") || !point.has("z")) continue;
                context.append(entry.getKey()).append("=")
                        .append(point.get("dimension").getAsString()).append(" (")
                        .append(point.get("x").getAsInt()).append(",")
                        .append(point.get("y").getAsInt()).append(",")
                        .append(point.get("z").getAsInt()).append("); ");
            }
        } catch (RuntimeException | java.io.IOException ex) {
            context.append("地点文件暂时无法读取；");
        }
        return context.toString();
    }

    private static String clean(String text) {
        return text.strip().replaceAll("[。！？!?，,]+$", "").strip();
    }

    private static void reply(String text) {
        Minecraft.getInstance().getConnection().sendChat("" + text);
    }

    private KurumiLocationMemory() {}
}
