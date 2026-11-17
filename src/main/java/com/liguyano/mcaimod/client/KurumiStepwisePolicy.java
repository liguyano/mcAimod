package com.liguyano.mcaimod.client;

/** First experimental stepwise-planning mode; no concurrent model requests. */
final class KurumiStepwisePolicy {
    static final boolean ENABLED = true;

    private KurumiStepwisePolicy() {}

    static boolean shouldReplan(boolean succeeded, boolean observed) {
        return ENABLED || !succeeded || observed;
    }

    static String guidance() {
        return "实验性走一步看一步模式：每次只规划一小步或一组必须连续完成的简单动作，"
                + "本阶段完成后会自动回传实际执行结果并再次请求 AI 决策。"
                + "优先生成短脚本，使用动作返回的 success/completed 检查结果，不要提前猜测后续环境。"
                + "若结果不足以决策，可用 observe(a, b, c) 一次提供所需观察。"
                + "明确验证总体目标完成后必须返回 type=finish；不要无限重复成功步骤。"
                + "这不是后台并行推理；每次至多一个脚本和一个模型请求，仍受阶段和时间预算约束。";
    }
}
