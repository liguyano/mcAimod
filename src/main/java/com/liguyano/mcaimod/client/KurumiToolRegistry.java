package com.liguyano.mcaimod.client;

import com.google.gson.JsonObject;
import java.util.LinkedHashMap;
import java.util.Map;

/** Compact discovery catalog for the Kurumi DSL. Detailed examples stay task-scoped. */
public final class KurumiToolRegistry {
    private record Entry(String signature, String category, String description) {}
    private static final Map<String, Entry> ENTRIES = new LinkedHashMap<>();

    private static void add(String signature, String category, String description) {
        String name = signature.substring(0, signature.indexOf('('));
        ENTRIES.put(name, new Entry(signature, category, description));
    }
    static {
        add("scan_nearby_entities()", "entity", "扫描附近实体，按距离排序，包含 uuid 与坐标");
        add("get_position()", "move", "获取当前位置");
        add("get_player_status()", "observe", "获取血量饥饿等");
        add("get_environment()", "observe", "周围环境");
        add("get_inventory()", "inventory", "读取背包");
        add("scan_nearby_blocks()", "observe", "扫描方块");
        add("scan_dropped_items()", "inventory", "扫描掉落物");
        add("inspect_container()", "container", "查看已打开容器槽位");
        add("inspect_slot(index)", "container", "指定槽位信息");
        add("get_distance(kind,target)", "move", "查询目标距离");
        add("find_nearest_block(block_id)", "observe", "查找最近方块");
        add("search_all_block(block_id)", "harvest", "搜索已加载区域的方块坐标");
        add("check_inventory(item_id)", "inventory", "查询背包数量");
        add("check_inventory_item(item_id)", "inventory", "查询背包数量");
        add("check_inventory_count(item_id)", "inventory", "查询背包数量");
        add("get_crafting_recipe(item_id)", "craft", "查询服务器同步配方");
        add("get_skill(skill_name)", "memory", "读取技能说明");
        add("get_fishing_status()", "fish", "钓鱼状态");
        add("goto(x,y,z)", "move", "目标附近寻路并验证结果");
        add("move(direction,blocks)", "move", "相对位移");
        add("come(player_name)", "move", "来到玩家附近");
        add("follow(player_name)", "move", "持续跟随玩家");
        add("stop()", "move", "停止动作");
        add("look(yaw,pitch)", "move", "调整视角");
        add("look_at(target_id) / look_at(x,y,z)", "move", "看向目标");
        add("interact(target_id[,uuid])", "entity", "近距离实体或方块交互");
        add("use_item(item_id)", "inventory", "持续使用物品");
        add("hold_use_item(item_id,seconds)", "inventory", "长按使用");
        add("equip_main_hand(item_id)", "inventory", "装备主手");
        add("close_container()", "container", "关闭当前容器");
        add("chest_target(x,y,z) / chest_target()", "container", "锁定或清除箱子目标");
        add("chest_open()", "container", "打开箱子");
        add("chest_put_item_all(item_id)", "container", "存入背包指定全部物品");
        add("chest_take_all()", "container", "取出箱子全部物品");
        add("chest_put_all()", "container", "放入全部背包物品");
        add("take_all_items()", "container", "取出当前菜单可取的物品");
        add("put_item(item_id,count[,slot])", "craft", "将物品放到菜单指定槽位");
        add("take_item(item_id,count)", "craft", "取出菜单指定物品");
        add("craft_open()", "craft", "打开工作台");
        add("mine(block_id)", "harvest", "Baritone 搜索挖掘");
        add("break_block(block_id,x,y,z) / break_block(x,y,z)", "harvest", "破坏单个邻近方块");
        add("chain_on()", "harvest", "启动连锁破坏");
        add("chain_off()", "harvest", "关闭连锁破坏");
        add("pickup_all()", "harvest", "拾取掉落物");
        add("pickup_item_all(item_id)", "harvest", "拾取指定掉落物");
        add("farm(radius)", "harvest", "种植收获指令");
        add("fish()", "fish", "开始持续钓鱼");
        add("stop_fishing()", "fish", "停止钓鱼");
        add("sleep_in_bed()", "move", "与床交互睡觉");
        add("sleep(seconds)", "move", "等待一段时间");
        add("attack(target_id_or_player)", "combat", "攻击目标");
        add("tpa_request()", "move", "传送请求");
        add("search_chests(item_id)", "container", "搜索附近箱子物品");
        add("get_chest_search_result()", "container", "箱子搜索报告");
        add("remember_block(name,x,y,z)", "memory", "保存地点");
        add("get_remembered_block(name)", "memory", "读取地点");
        add("list_remembered_blocks()", "memory", "列出地点");
        add("get_remembered_block_at(index)", "memory", "指定记忆索引");
        add("take_from_memory(name,item_id,count)", "container", "从记忆箱子取物");
        add("observe(value)", "observe", "结束本段脚本交回模型");
        add("talk(message)", "observe", "向公屏发言");
    }

    public static String catalog() {
        StringBuilder result = new StringBuilder("Kurumi 可用工具（签名为伪代码格式；[]表示可选参数；细节可用 get_tool_help(\"工具名\") 查询）：\n");
        for (Entry e : ENTRIES.values()) {
            result.append(e.signature()).append(" — ").append(e.description()).append('\n');
        }
        return result.toString();
    }

    public static JsonObject help(String name) {
        JsonObject out = new JsonObject();
        Entry entry = ENTRIES.get(name);
        out.addProperty("found", entry != null);
        out.addProperty("name", name);
        if (entry != null) {
            out.addProperty("signature", entry.signature());
            out.addProperty("category", entry.category());
            out.addProperty("description", entry.description());
            out.addProperty("notes", "成功受理不代表完成；依 success/completed/message 和再次观察验证。详细流程请参照当前任务工具组说明。");
        }
        return out;
    }
    private KurumiToolRegistry() {}
}
