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

    private static String clean(String text) {
        return text.strip().replaceAll("[。！？!?，,]+$", "").strip();
    }

    private static void reply(String text) {
        Minecraft.getInstance().getConnection().sendChat("" + text);
    }

    private KurumiLocationMemory() {}
}
