package com.liguyano.mcaimod.client;

/** Non-sensitive size and timing metadata for model-request diagnostics. */
public final class KurumiRequestMetrics {
    public static String summary(long elapsedMs, int inputChars, int outputChars,
                                 int retries, boolean failed) {
        return "模型请求统计：耗时=" + Math.max(0, elapsedMs) + "ms"
                + "，输入字符=" + Math.max(0, inputChars)
                + "，输出字符=" + Math.max(0, outputChars)
                + "，重试次数=" + Math.max(0, retries)
                + "，状态=" + (failed ? "失败" : "成功");
    }

    private KurumiRequestMetrics() {}
}
