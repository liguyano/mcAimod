package com.liguyano.mcaimod.client;

/** Guidance for short, observation-driven container tasks. */
final class KurumiObservationGuidance {
    private KurumiObservationGuidance() {}

    static String text() {
        return "观察—决策—执行—验证规则：当主人要求从箱子里拿食物、工具或其他未明确指定的物品时，"
                + "不要预先枚举所有可能的物品ID，也不要猜测箱子内容。先定位主人指定或已记忆的箱子，"
                + "打开后用 observe(inspect_container()) 把实际非空槽位的物品ID、数量交给 AI；"
                + "observe() 会结束当前脚本，后面的操作应在下一阶段生成，不能写在 observe() 后面。"
                + "收到观察结果后，优先选择实际存在且符合需求的物品，生成只包含必要 take_item、"
                + "数量检查和关闭容器的短脚本；执行后重新观察箱子或背包来验证确实取出。"
                + "若目标箱子没有合适物品或有多个可能地点，请说明所见事实并向主人确认，"
                + "不要臆造结果。已明确目标物品及数量时可直接本地执行并核验，不必额外请求模型。";
    }
}
