package com.liguyano.mcaimod.client;

import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Named locations now live exclusively in JourneyMap; legacy JSON is never read or modified. */
public final class KurumiLocationMemory {
    private static final long DELETE_CONFIRM_WINDOW_MS = 30_000L;
    private static List<KurumiJourneyMapBridge.Point> pendingDeathDeletion = List.of();
    private static long pendingDeathDeletionUntil;
    private static final Pattern SAVE = Pattern.compile("(?:记住|记下|标记)(?:这里|这个位置|这个地方)?(?:是|叫|为)(.{1,24})");
    private static final Pattern GO = Pattern.compile("去 ([^\\s，,。！？!?；;]{1,24})");
    private static final Pattern WHERE = Pattern.compile("([^\\s，,。！？!?；;]{1,24}) (?:在哪里|在哪儿|在哪|的坐标)");

    /** Only an explicit separating space opts into a local location lookup. */
    static boolean isWhereShortcut(String body) {
        return body != null && WHERE.matcher(body.strip()).matches();
    }

    static boolean isGoShortcut(String body) {
        return body != null && GO.matcher(body.strip()).matches();
    }

    public static boolean handle(String message, String senderName) {
        if (!"SekaiSaionji".equals(senderName)) return false;
        String body = message.strip().replaceFirst("(?i)^.*?kurumi[，,：: ]*", "").strip();
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.getConnection() == null) return false;
        if (body.equals("确认删除所有死亡点") || body.equals("确认删除全部死亡点")) {
            confirmDeathDeletion();
            return true;
        }
        if (body.equals("取消删除死亡点") || body.equals("取消删除所有死亡点")) {
            pendingDeathDeletion = List.of();
            pendingDeathDeletionUntil = 0;
            reply("已取消删除死亡点");
            return true;
        }
        if (body.equals("删除所有死亡点") || body.equals("删除全部死亡点")
                || body.equals("清空死亡点") || body.equals("删除所有死亡坐标点")) {
            requestDeathDeletion();
            return true;
        }
        if (body.equals("删除死亡点") || body.equals("删除死亡坐标点")) {
            deleteOneDeathPoint();
            return true;
        }
        // Compound requests are delegated to the agent; single commands preserve the old shortcut.
        if (body.contains("然后") || body.contains("之后") || body.contains("再去")
                || body.contains("并且") || body.contains("接着")
                || body.contains("，") || body.contains(",") || body.contains("；")
                || body.contains(";") || body.contains("。") || body.contains("\n"))
            return false;
        boolean locationIntent = body.startsWith("去 ") || body.startsWith("记住")
                || body.startsWith("记下") || body.startsWith("标记")
                || body.startsWith("忘记") || body.startsWith("删除地点")
                || body.startsWith("删除记忆点") || body.startsWith("删除路径点")
                || isWhereShortcut(body)
                || body.contains("记住了哪些") || body.contains("地点列表")
                || body.contains("记忆的地点");
        if (!locationIntent) return false;
        if (body.startsWith("去 ") && !message.strip().equals(body)) return false;

        Matcher coordinates = Pattern.compile("去 (-?[0-9]+) +(-?[0-9]+) +(-?[0-9]+)").matcher(body);
        if (coordinates.matches()) {
            try {
                reply(BaritoneBridge.gotoCoordinates(Integer.parseInt(coordinates.group(1)),
                        Integer.parseInt(coordinates.group(2)), Integer.parseInt(coordinates.group(3))).message());
            } catch (NumberFormatException ex) {
                reply("坐标超出允许范围");
            }
            return true;
        }
        if (body.matches(".*-?[0-9]+[ ]+-?[0-9]+[ ]+-?[0-9]+.*")) return false;

        Matcher save = SAVE.matcher(body), go = GO.matcher(body), where = WHERE.matcher(body);
        boolean list = body.contains("记住了哪些") || body.contains("地点列表") || body.contains("记忆的地点");
        boolean delete = body.startsWith("忘记") || body.startsWith("删除地点")
                || body.startsWith("删除记忆点") || body.startsWith("删除路径点");
        if (!save.find() && !go.matches() && !where.matches() && !list && !delete) return false;

        try {
            String dimension = mc.level.dimension().identifier().toString();
            if (save.matches()) {
                String name = clean(save.group(1));
                if (!validName(name)) { reply("地点名称无效"); return true; }
                Player owner = owner(mc);
                if (owner == null) { reply("主人不在可见范围内，无法确认位置"); return true; }
                KurumiJourneyMapBridge.save(name, dimension,
                        owner.getBlockX(), owner.getBlockY(), owner.getBlockZ());
                reply("已保存到 JourneyMap：" + name + " (" + owner.getBlockX() + ", "
                        + owner.getBlockY() + ", " + owner.getBlockZ() + ")");
                return true;
            }
            List<KurumiJourneyMapBridge.Point> points = KurumiJourneyMapBridge.list();
            if (list) {
                String names = points.stream().map(KurumiJourneyMapBridge.Point::name).distinct()
                        .reduce((a, b) -> a + "、" + b).orElse("暂无");
                reply("JourneyMap 路径点：" + names);
                return true;
            }
            if (delete) {
                String name = clean(body.replaceFirst("^(忘记|删除地点|删除记忆点|删除路径点)", ""));
                if (!validName(name)) { reply("地点名称无效"); return true; }
                List<KurumiJourneyMapBridge.Point> selected = KurumiWaypointDeletion.exactName(points, name, dimension);
                if (selected.isEmpty()) reply("当前维度没有找到路径点：" + name);
                else if (selected.size() != 1) reply("找到多个同名路径点，请先在 JourneyMap 中区分名称或删除多余的点");
                else if (KurumiJourneyMapBridge.deleteExact(selected.get(0))) reply("已从 JourneyMap 删除地点：" + name);
                else reply("路径点已发生变化，未删除任何记录");
                return true;
            }
            String name = clean(go.matches() ? go.group(1) : where.matches() ? where.group(1) : "");
            if (!validName(name)) { reply("地点名称无效"); return true; }
            List<KurumiJourneyMapBridge.Point> matches = KurumiWaypointSelection.find(points, name, dimension);
            if (matches.isEmpty()) { reply("JourneyMap 中没有地点：" + name); return true; }
            if (matches.size() != 1) { reply("有多个同名路径点“" + name + "”，请在 JourneyMap 中区分名称"); return true; }
            var point = matches.get(0);
            if (go.matches()) reply(navigate(point).message());
            else reply(name + " 在 " + point.dimension() + " (" + point.x() + ", "
                    + point.y() + ", " + point.z() + ")");
            return true;
        } catch (IllegalStateException | LinkageError ex) {
            reply("JourneyMap 地点操作失败：" + ex.getMessage());
            return true;
        } catch (RuntimeException ex) {
            reply("JourneyMap 地点操作失败，请检查该 Mod 是否正确加载");
            return true;
        }
    }

    private static void deleteOneDeathPoint() {
        try {
            var points = KurumiWaypointDeletion.deathPoints(KurumiJourneyMapBridge.list());
            if (points.isEmpty()) { reply("没有找到死亡路径点"); return; }
            if (points.size() != 1) {
                reply("找到 " + points.size() + " 个死亡点。请用完整名称删除指定点，或说“删除所有死亡点”后确认");
                return;
            }
            if (KurumiJourneyMapBridge.deleteExact(points.get(0))) reply("已删除死亡点：" + points.get(0).name());
            else reply("死亡点已经变化，未执行删除");
        } catch (RuntimeException | LinkageError ex) {
            reply("删除死亡点失败：" + ex.getMessage());
        }
    }

    private static void requestDeathDeletion() {
        try {
            JsonObject result = requestDeathDeletionForAgent();
            if (result.get("count").getAsInt() == 0) reply("没有找到死亡路径点");
            else reply("找到 " + result.get("count").getAsInt() + " 个死亡点。此操作不可撤销；30 秒内说“确认删除所有死亡点”才会执行，或说“取消删除死亡点”");
        } catch (RuntimeException | LinkageError ex) {
            pendingDeathDeletion = List.of();
            reply("读取死亡点失败：" + ex.getMessage());
        }
    }

    /** Tool access: the model may request but never confirm a destructive batch deletion. */
    public static JsonObject requestDeathDeletionForAgent() {
        pendingDeathDeletion = KurumiWaypointDeletion.deathPoints(KurumiJourneyMapBridge.list());
        pendingDeathDeletionUntil = pendingDeathDeletion.isEmpty() ? 0
                : System.currentTimeMillis() + DELETE_CONFIRM_WINDOW_MS;
        JsonObject result = new JsonObject();
        result.addProperty("count", pendingDeathDeletion.size());
        result.addProperty("requires_confirmation", !pendingDeathDeletion.isEmpty());
        result.addProperty("confirmation_phrase", "确认删除所有死亡点");
        return result;
    }

    /** Single marker deletion is precise: ambiguous names never select an arbitrary waypoint. */
    public static JsonObject deleteWaypointForAgent(String name) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) throw new IllegalArgumentException("未进入世界");
        if (!validName(name)) throw new IllegalArgumentException("路径点名称无效");
        var matches = KurumiWaypointDeletion.exactName(KurumiJourneyMapBridge.list(), name,
                mc.level.dimension().identifier().toString());
        if (matches.size() > 1) throw new IllegalArgumentException("有多个同名路径点，请先重命名以区分");
        JsonObject result = new JsonObject();
        result.addProperty("name", name);
        result.addProperty("deleted", matches.size() == 1 && KurumiJourneyMapBridge.deleteExact(matches.get(0)));
        return result;
    }

    private static void confirmDeathDeletion() {
        if (pendingDeathDeletion.isEmpty() || System.currentTimeMillis() > pendingDeathDeletionUntil) {
            pendingDeathDeletion = List.of();
            reply("没有待确认的删除任务，或确认已超时");
            return;
        }
        List<KurumiJourneyMapBridge.Point> requested = pendingDeathDeletion;
        pendingDeathDeletion = List.of();
        pendingDeathDeletionUntil = 0;
        try {
            var current = KurumiWaypointDeletion.deathPoints(KurumiJourneyMapBridge.list());
            if (!current.equals(requested)) {
                reply("死亡点列表已变化，未删除。请重新发起删除申请");
                return;
            }
            int deleted = 0;
            for (var point : requested) {
                if (KurumiJourneyMapBridge.deleteExact(point)) deleted++;
            }
            reply("死亡点批量删除完成：" + deleted + "/" + requested.size());
        } catch (RuntimeException | LinkageError ex) {
            reply("批量删除中止，部分路径点可能已删除：" + ex.getMessage());
        }
    }

    /** Resolve a name from JourneyMap for the agent's existing gotoNamed action. */
    public static BaritoneBridge.Result gotoNamed(String name) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.getConnection() == null)
            return new BaritoneBridge.Result(false, "游戏世界尚未就绪");
        String cleaned = clean(name);
        if (!validName(cleaned)) return new BaritoneBridge.Result(false, "地点名称无效");
        try {
            String dimension = mc.level.dimension().identifier().toString();
            List<KurumiJourneyMapBridge.Point> matches = KurumiWaypointSelection.find(
                    KurumiJourneyMapBridge.list(), cleaned, dimension);
            if (matches.isEmpty()) return new BaritoneBridge.Result(false, "JourneyMap 中没有地点：" + cleaned);
            if (matches.size() != 1)
                return new BaritoneBridge.Result(false, "同名 JourneyMap 路径点不止一个，请使用不同名称区分");
            return navigate(matches.get(0));
        } catch (IllegalStateException | LinkageError ex) {
            return new BaritoneBridge.Result(false, "JourneyMap 地点不可用：" + ex.getMessage());
        } catch (RuntimeException ex) {
            return new BaritoneBridge.Result(false, "JourneyMap 地点读取失败");
        }
    }

    /** Read-only world and waypoint context for the model. */
    public static String contextForModel() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return "世界尚未加载";
        StringBuilder context = new StringBuilder("Kurumi当前位置：")
                .append(mc.player.getBlockX()).append(", ")
                .append(mc.player.getBlockY()).append(", ")
                .append(mc.player.getBlockZ()).append("；维度：")
                .append(mc.level.dimension()).append("。");
        Player owner = owner(mc);
        if (owner == null) context.append("主人当前坐标不可见；不要猜测。");
        else context.append("主人当前位置：")
                .append(owner.getBlockX()).append(", ")
                .append(owner.getBlockY()).append(", ")
                .append(owner.getBlockZ()).append("。");
        try {
            List<KurumiJourneyMapBridge.Point> points = KurumiJourneyMapBridge.list();
            if (points.isEmpty()) return context.append("JourneyMap 尚无已保存路径点。").toString();
            context.append("JourneyMap 已保存路径点（包括主人手动添加的；不能跨维度直接寻路）：");
            int count = 0;
            for (var point : points) {
                if (count++ >= 40) { context.append("其余路径点省略；"); break; }
                context.append(point.name()).append("=").append(point.dimension()).append(" (")
                        .append(point.x()).append(",").append(point.y()).append(",")
                        .append(point.z()).append("); ");
            }
        } catch (IllegalStateException | LinkageError ex) {
            context.append("JourneyMap 尚未就绪，地点记忆不可用；");
        } catch (RuntimeException ex) {
            context.append("JourneyMap 路径点暂时无法读取；");
        }
        return context.toString();
    }

    private static BaritoneBridge.Result navigate(KurumiJourneyMapBridge.Point point) {
        Minecraft mc = Minecraft.getInstance();
        if (!mc.level.dimension().identifier().toString().equals(point.dimension()))
            return new BaritoneBridge.Result(false, point.name() + " 在 " + point.dimension() + "，不能跨维度直接导航");
        return BaritoneBridge.gotoCoordinates(point.x(), point.y(), point.z());
    }

    private static Player owner(Minecraft mc) {
        for (Player player : mc.level.players())
            if ("SekaiSaionji".equals(player.getName().getString())) return player;
        return null;
    }

    private static boolean validName(String name) {
        return !name.isBlank() && name.length() <= 24;
    }

    private static String clean(String text) {
        return text == null ? "" : text.strip().replaceAll("[。！？!?，,]+$", "").strip();
    }

    private static void reply(String message) {
        Minecraft.getInstance().getConnection().sendChat(message);
    }

    private KurumiLocationMemory() {}
}
