package com.liguyano.mcaimod.client;

import net.minecraft.client.Minecraft;

/** Keeps Kurumi near the surface using ordinary jump/swim input. */
public final class KurumiWaterSurvivalBridge {
    private static boolean autoSwimEnabled = true;
    private static boolean holdingJump;

    public static void tick(boolean agentEnabled) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || !agentEnabled) {
            release(mc);
            return;
        }
        boolean ascend = KurumiWaterSurvivalPolicy.shouldAscend(autoSwimEnabled,
                mc.player.isInWater(), mc.player.isUnderWater());
        if (ascend) {
            mc.options.keyJump.setDown(true);
            holdingJump = true;
        } else {
            release(mc);
        }
    }

    public static BaritoneBridge.Result setEnabled(boolean value) {
        autoSwimEnabled = value;
        if (!value) release(Minecraft.getInstance());
        return new BaritoneBridge.Result(true, value
                ? "已开启自动浮水：入水后会尝试上浮；可随时关闭以潜水"
                : "已关闭自动浮水：可以主动下潜；请注意水下氧气");
    }

    public static BaritoneBridge.Result status() {
        return new BaritoneBridge.Result(true,
                "自动浮水：" + (autoSwimEnabled ? "开启" : "关闭"));
    }

    private static void release(Minecraft mc) {
        if (!holdingJump) return;
        mc.options.keyJump.setDown(false);
        holdingJump = false;
    }

    private KurumiWaterSurvivalBridge() {}
}
