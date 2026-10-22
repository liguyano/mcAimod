package com.liguyano.mcaimod.client;

import com.liguyano.mcaimod.model.ChatMessage;
import com.liguyano.mcaimod.model.ModelProfiles;
import com.liguyano.mcaimod.model.OpenAiCompatibleProvider;
import net.minecraft.client.Minecraft;
import net.neoforged.fml.loading.FMLPaths;
import java.util.List;

/** A failed checkpoint may be replanned by the model, at most three times. */
public final class KurumiTaskRecovery {
    private static final OpenAiCompatibleProvider PROVIDER = new OpenAiCompatibleProvider();
    private static int failures;
    private static boolean waiting;
    private static long generation;
    private static long deadline;

    public static void begin() {
        failures = 0;
        waiting = false;
        generation++;
        deadline = System.currentTimeMillis() + 300_000;
    }
    public static void cancel() {
        generation++;
        waiting = false;
        failures = 0;
    }
    public static String status() { return "重规划次数：" + failures + "/3" + (waiting ? "（等待模型）" : ""); }
    public static void failed(String goal, String reason, String itemId) {
        Minecraft mc = Minecraft.getInstance();
        if (waiting || mc.player == null || mc.getConnection() == null) return;
        if (System.currentTimeMillis() >= deadline || failures >= 3) {
            mc.getConnection().sendChat("长任务已终止：重规划次数达到上限或超过五分钟。最后错误：" + reason);
            return;
        }
        failures++;
        waiting = true;
        long token = ++generation;
        mc.getConnection().sendChat("检查失败（第 " + failures + "/3 次），正在重新规划：" + reason);
        try {
            var profile = ModelProfiles.load(FMLPaths.CONFIGDIR.get().resolve("mcaimod-models.json")).first();
            if (profile.isEmpty()) { waiting = false; return; }
            String observation = KurumiTaskChecks.observe(itemId, mc.player.getBlockX(),
                    mc.player.getBlockY(), mc.player.getBlockZ(), 3);
            PROVIDER.chat(profile.get(), List.of(
                    new ChatMessage("system", "你是 Minecraft Kurumi 的任务恢复规划器。上一步失败，禁止宣称成功。"
                            + "只返回符合 Kurumi 现有动作格式的 JSON。仅可选择 action=COLLECT_AND_STORE "
                            + "且 block_id 为已验证原木ID、quantity 为1至64，或 type=chat 说明无法继续。"
                            + "禁止危险动作和其他玩家攻击。根据检查结果决定是否重试。"),
                    new ChatMessage("user", "目标：" + goal + "；失败：" + reason + "；真实检查：" + observation)))
                    .whenComplete((answer, error) -> mc.execute(() -> {
                        if (token != generation) return;
                        waiting = false;
                        if (error != null || answer == null) {
                            mc.getConnection().sendChat("重规划模型请求失败，任务已停止。");
                            return;
                        }
                        var decision = KurumiDecisionParser.parse(answer);
                        if (decision.isEmpty() || !decision.get().target().startsWith("collect_store:")
                                || !itemId.equals(decision.get().blockId())) {
                            mc.getConnection().sendChat("模型没有给出安全可执行的恢复方案，任务已停止。");
                            return;
                        }
                        if (System.currentTimeMillis() >= deadline) {
                            mc.getConnection().sendChat("任务超过五分钟，已停止。");
                            return;
                        }
                        int quantity = Integer.parseInt(decision.get().target().substring(14));
                        var result = KurumiLongTaskBridge.collectAndStore(itemId, quantity);
                        mc.getConnection().sendChat(result.success()
                                ? "已按新计划重新开始采集：" + itemId
                                : "重新规划执行失败：" + result.message());
                    }));
        } catch (Exception e) {
            waiting = false;
            mc.getConnection().sendChat("无法加载重规划模型配置，任务已停止。");
        }
    }
    private KurumiTaskRecovery() {}
}
