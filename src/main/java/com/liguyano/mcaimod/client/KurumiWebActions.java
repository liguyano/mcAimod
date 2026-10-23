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
                允许通用变量赋值、变量重新赋值、加减乘除、布尔条件和 JSON 字典点号访问；禁止 for/range、函数定义、import。\n                例如 target = find_nearest_block("minecraft:crafting_table")，\n                if target.found: 下一行缩进四格 goto(target.x,target.y,target.z)。\n                允许 count = count + 1、talk(target)、sleep(count)；变量最多16个，仅作用于当前脚本。
                get_position() 返回 x、y、z 坐标对象，可赋值 pos = get_position()，使用 goto(pos.x,pos.y,pos.z)。
                也支持 talk(get_position()) 直接输出坐标。
                连锁挖掘：chain_on() 启用 OneKeyMiner 连锁按键，chain_off() 关闭它。
                用户明确要求“连锁”、连锁砍树、连锁挖矿、批量连锁破坏时，生成脚本必须在 mine() 之前调用 chain_on()，
                并在采集结束后调用 chain_off()。注意 chain_on() 只是按下 OneKeyMiner 连锁按键，
                只有客户端安装兼容的 OneKeyMiner 才能生效，不能虚构它已成功挖掘。
                不要把 repeat/while 多次 mine() 当成开启连锁；二者不是同一种功能。
                例子：
                chain_on()
                mine("minecraft:oak_log")
                chain_off()
                战斗：attack("minecraft:skeleton") 攻击指定生物；attack("玩家名") 攻击指定玩家（须符合玩家攻击权限）；也支持 attack_mob("minecraft:skeleton") 和 attack_player("玩家名")。\n                箱子搜索：search_chests("minecraft:diamond") 遍历附近已加载的24格范围内最多24个普通箱子；
                不会自动取物。完成后 result = get_chest_search_result()，talk(result) 查看报告。
                已检查的箱子记为 chest_1、chest_2 等，每次搜索会更新对应记忆。
                通用方块记忆：saved = remember_block("矿场箱子",x,y,z)，
                point = get_remembered_block("矿场箱子")，可用 point.x、point.y、point.z。
                列出所有类型的方块记忆：memory = list_remembered_blocks()，返回 count 和 blocks。
                逐项读取：entry = get_remembered_block_at(0)，从编号0开始；
                可以用 while i < memory.count、get_remembered_block_at(i) 和 i = i + 1 遍历。
                所有方块类型共用此列表，并非只存箱子；保存的坐标需要在使用时重新验证。
                从记忆位置取物：take_from_memory("chest_1","minecraft:diamond",4)，
                只打开记忆中的指定箱子，核对服务端菜单后取物，绝不改选附近其他箱子。
                搜索和取物可分成两次任务完成，不能虚构箱子内容及取物数量。
                常用动作：sleep(2)、talk("你好")、equip_main_hand("minecraft:iron_axe")、
                破坏指定方块：break_block("minecraft:stone") 破坏4.5格内最近的石头；
                break_block(100,64,200) 按坐标破坏，或 break_block(target.x,target.y,target.z)，
                也可 break_block("minecraft:stone",100,64,200) 验证方块类型。
                这是近距离单方块破坏，远处先 goto；mine 用于 Baritone 采集。
                如何选择：用户要求拆除明确坐标、清除附近某一块方块，优先 break_block()；
                用户要求寻找资源、挖矿、砍树、批量收集，优先 mine("minecraft:方块ID")。
                break_block() 只能近距离单块破坏，不自动寻找或寻路；
                mine() 让 Baritone 自主搜索、前往并采集指定方块种类。
                结合用户意图、目标位置和数量自行选择，不要一看到“挖”就固定使用 mine()。
                mine() 不保证获得指定数量，break_block() 不保证拾取掉落物；不能把命令已提交当成任务已完成。
                interact("minecraft:oak_log")、place_block("minecraft:oak_planks")、
                place_block("minecraft:oak_planks",10,64,20)、move("forward",3)、goto(10,64,20)、
                come("玩家名")、follow("玩家名")、stop()、mine("minecraft:oak_log")、
                chest_open()、chest_take("minecraft:oak_log",4)、chest_put("minecraft:oak_log",4)、
                craft_item("minecraft:stick",4)、put_item("minecraft:coal",1,"fuel")、
                take_item("minecraft:iron_ingot",1)、
                talk(find_nearest_block("minecraft:furnace"))、talk(get_inventory())、
                talk(inspect_container())、talk(inspect_slot(0))。\n                观察结果可以直接保存为变量，例如 status = get_player_status()、\n                target = find_nearest_block("minecraft:furnace")、slot = inspect_slot(0)；\n                使用 status.health、target.x 等点号读取字段，需要公屏输出才调用 talk(status)。
                只调用 talk(find_nearest_block(...)) 不会移动；保存查询结果为变量后可调用 goto(target.x,target.y,target.z)。\n                interact 只对附近方块交互。
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
                            boolean wantsChain = requirement.contains("连锁") || requirement.toLowerCase(java.util.Locale.ROOT).contains("vein mine");
                            boolean missingChain = wantsChain && !script.contains("chain_on()");
                            if (missingChain) valid = false;
                            completePreview(id, script.length() > 8192 ? script.substring(0, 8192) : script,
                                    valid ? "" : missingChain ? "用户要求连锁，但 AI 未生成 chain_on()；请重新生成或手动补上" : "模型生成脚本未通过解释器校验");
                        } catch (RuntimeException exception) {
                            completePreview(id, "", "模型没有返回合法的 script JSON");
                        }
                    }));
        } catch (Exception e) {
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
