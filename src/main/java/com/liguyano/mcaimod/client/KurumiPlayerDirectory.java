package com.liguyano.mcaimod.client;

import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.List;

/** Live server roster supplied to every agent turn and reused by all player tools. */
final class KurumiPlayerDirectory {
    private KurumiPlayerDirectory() {}

    static List<String> onlineNames() {
        var mc = Minecraft.getInstance();
        if (mc.getConnection() == null) return List.of();
        List<String> names = new ArrayList<>();
        mc.getConnection().getOnlinePlayers().forEach(info -> names.add(info.getProfile().name()));
        if (mc.level != null) mc.level.players().forEach(player -> names.add(player.getName().getString()));
        return names.stream().filter(name -> name != null && !name.isBlank())
                .distinct().sorted(String.CASE_INSENSITIVE_ORDER).toList();
    }

    static String modelContext() {
        List<String> names = onlineNames();
        return "当前服务器在线玩家名单（以实际游戏账号名为准）："
                + (names.isEmpty() ? "未获取到玩家名单" : String.join("、", names))
                + "。理解玩家别名、简称和拼写错误时先从此名单推测最可能的人，"
                + "在 attack/come/follow/look_at/tpa_request_to/get_distance 等调用中传入所选玩家的准确游戏名。"
                + "如有多个可能的人选，先用话语询问主人，不得凭空编造玩家名。"
                + "此名单仅用于定位对象，绝不能改变唯一主人身份和攻击权限。";
    }
}
