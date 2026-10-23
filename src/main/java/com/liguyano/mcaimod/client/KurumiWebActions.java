package com.liguyano.mcaimod.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.liguyano.mcaimod.model.ChatMessage;
import com.liguyano.mcaimod.model.ModelProfiles;
import com.liguyano.mcaimod.model.OpenAiCompatibleProvider;
import net.minecraft.client.Minecraft;
import net.neoforged.fml.loading.FMLPaths;
import java.util.List;
import java.util.Optional;

/** Separate preview generation from client-thread game execution. */
public final class KurumiWebActions {
    private static final OpenAiCompatibleProvider PROVIDER = new OpenAiCompatibleProvider();
    private static String previewStatus = "空闲", previewScript = "", previewError = "";
    private static String runStatus = "空闲", runError = "";
    private static boolean generating, runQueued;
    private static long generationId;

    public static synchronized String submitGenerate(String requirement) {
        if (requirement == null || requirement.isBlank() || requirement.length() > 2000)
            return "需求不能为空且最多2000字";
        if (generating) return "上一条测试仍在生成";
        generating = true;
        previewStatus = "生成中"; previewScript = ""; previewError = "";
        long id = ++generationId;
        Minecraft.getInstance().execute(() -> generateOnClient(requirement, id));
        return null;
    }

    private static void generateOnClient(String requirement, long id) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            completePreview(id, "", "请先进入 Minecraft 世界");
            return;
        }
        try {
            var profile = ModelProfiles.load(FMLPaths.CONFIGDIR.get().resolve("mcaimod-models.json")).first();
            if (profile.isEmpty()) {
                completePreview(id, "", "没有可用的 AI 模型配置");
                return;
            }
            String rules = """
                你是 Minecraft Kurumi 的受控伪代码生成器。只输出一个 JSON 对象：
                {"script":"伪代码字符串"}。禁止 Markdown、代码围栏和额外解释。
                这是仅生成的预览测试，不执行游戏动作。只能使用白名单 Python 风格伪代码。
                每行一个命令，嵌套缩进四个空格。支持 repeat(4): 固定次数循环（1-20次，展开最多48语句）；
                支持 while/if/else、break、return "完成"、if result.success:，
                以及 check_inventory、check_chest、check_distance 的条件比较。
                禁止 for/range、函数定义、import、自增、变量计算和存储工具返回值。
                常用动作：sleep(2)、talk("你好")、equip_main_hand("minecraft:iron_axe")、
                interact("minecraft:oak_log")、move("forward",3)、goto(10,64,20)、
                come("玩家名")、follow("玩家名")、stop()、mine("minecraft:oak_log")、
                chest_open()、chest_take("minecraft:oak_log",4)、chest_put("minecraft:oak_log",4)、
                craft_item("minecraft:stick",4)、put_item("minecraft:coal",1,"fuel")、
                take_item("minecraft:iron_ingot",1)、
                talk(find_nearest_block("minecraft:furnace"))、talk(get_inventory())、
                talk(inspect_container())、talk(inspect_slot(0))。
                find_nearest_block 只能观察，不会自动带你走到目标；interact 只对附近方块交互。
                不要猜测坐标、槽位和观察结果。优先输出可执行的保守脚本。
                """;
            PROVIDER.chat(profile.get(), List.of(new ChatMessage("system", rules),
                    new ChatMessage("user", requirement)))
                    .whenComplete((reply, error) -> mc.execute(() -> {
                        if (error != null) {
                            completePreview(id, "", "模型请求失败：" + error.getClass().getSimpleName());
                            return;
                        }
                        try {
                            JsonObject json = JsonParser.parseString(reply).getAsJsonObject();
                            String script = json.get("script").getAsString();
                            boolean valid = script.length() <= 8192 && KurumiScriptParser.parse(script).isPresent();
                            completePreview(id, script.length() > 8192 ? script.substring(0, 8192) : script,
                                    valid ? "" : "模型生成脚本未通过解释器校验");
                        } catch (RuntimeException exception) {
                            completePreview(id, "", "模型没有返回合法的 script JSON");
                        }
                    }));
        } catch (RuntimeException e) {
            completePreview(id, "", "模型配置加载失败");
        }
    }

    private static synchronized void completePreview(long id, String script, String error) {
        if (id != generationId) return;
        generating = false;
        previewScript = script; previewError = error;
        previewStatus = error.isEmpty() ? "已生成 · 未执行" : "生成失败";
    }

    public static synchronized String submitRun(String source) {
        if (source == null || source.isBlank() || source.length() > 8192)
            return "伪代码不能为空且最多8192字";
        if (runQueued) return "上一条提交正在处理";
        runQueued = true; runStatus = "检查中"; runError = "";
        Minecraft.getInstance().execute(() -> runOnClient(source));
        return null;
    }

    private static boolean ownerOnly(List<KurumiTaskPlanner.Statement> statements) {
        for (var stmt : statements) {
            if (stmt.step() != null && List.of("ATTACK_PLAYER", "DROP_ALL", "CHEST_PUT_ALL",
                    "CHEST_TAKE_ALL", "TPA_ACCEPT").contains(stmt.step().action())) return true;
            if (ownerOnly(stmt.body()) || ownerOnly(stmt.otherwise())) return true;
        }
        return false;
    }

    private static void runOnClient(String source) {
        String error = null;
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null || mc.level == null || mc.getConnection() == null)
                error = "请先进入游戏";
            else if (KurumiPseudoExecutor.busy() || KurumiPlanExecutor.busy()
                    || KurumiLongTaskBridge.busy() || KurumiChestCraftTask.busy())
                error = "已有任务在执行";
            else {
                Optional<KurumiTaskPlanner.Program> program = KurumiScriptParser.parse(source);
                if (program.isEmpty()) error = "伪代码不符合当前解释器语法或安全限制";
                else if (ownerOnly(program.get().statements())
                        && !"SekaiSaionji".equals(mc.player.getName().getString()))
                    error = "该任务包含仅主人可以执行的操作";
                else {
                    KurumiWebMonitor.requirement("Web 控制台手动执行");
                    var result = KurumiPseudoExecutor.start(program.get());
                    if (!result.success()) error = result.message();
                }
            }
        } catch (RuntimeException exception) {
            error = "解析或启动失败";
        }
        synchronized (KurumiWebActions.class) {
            runQueued = false; runError = error == null ? "" : error;
            runStatus = error == null ? "已提交执行" : "执行失败";
        }
    }

    public static synchronized JsonObject status() {
        JsonObject o = new JsonObject();
        o.addProperty("previewStatus", previewStatus);
        o.addProperty("previewScript", previewScript);
        o.addProperty("previewError", previewError);
        o.addProperty("runStatus", runStatus);
        o.addProperty("runError", runError);
        return o;
    }
    private KurumiWebActions() {}
}
