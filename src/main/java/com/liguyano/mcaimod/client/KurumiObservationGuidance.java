package com.liguyano.mcaimod.client;

/** Guidance for short, observation-driven container tasks. */
final class KurumiObservationGuidance {
    private KurumiObservationGuidance() {}

    static String text() {
        return "观察—决策—执行—验证规则：当主人要求从箱子里拿食物、工具或其他未明确指定的物品时，"
                + "不要预先枚举所有可能的物品ID，也不要猜测箱子内容。先定位主人指定或已记忆的箱子，"
                + "打开后用 observe(inspect_container()) 把实际非空槽位的物品ID、数量交给 AI；"
                + "observe(a, b, c) 可一次发送多个观察给 AI，减少重复请求；保留各参数名与结果字段，最多7项、最多1张截图。"
                + "observe() 会结束当前脚本，后面的操作应在下一阶段生成，不能写在 observe() 后面。"
                + "收到观察结果后，优先选择实际存在且符合需求的物品，生成只包含必要 take_item、"
                + "数量检查和关闭容器的短脚本；执行后重新观察箱子或背包来验证确实取出。"
                + "若目标箱子没有合适物品或有多个可能地点，请说明所见事实并向主人确认，"
                + "不要臆造结果。已明确目标物品及数量时可直接本地执行并核验，不必额外请求模型。"
                + "所有工具返回结果统一提供 success 字段；success 只表示工具调用有效，"
                + "不代表已完成实际动作。保留各观察工具原有字段，例如 scan_nearby_entities() "
                + "返回 success/tool/entities，直接 for e in scan.entities 遍历，每项含 type、uuid、x、y、z。"
                + "纯数字查询额外提供 count/value，可用 result.count 或直接对结果做数值比较。"
                + "返回 error 时 success=false；找到目标与否仍应检查 found，而不是只看 success。"
                + "尸体可以 interact(\"corpse:corpse\",uuid) 精确交互，之后 inspect_container() 核验。"
                + "若执行器报告字段不存在或参数错误，读取错误报告并修正剩余步骤，不要重复已完成的动作。";
    }
}
