package com.liguyano.mcaimod.client;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;

/** Integrates with OneKeyMiner's registered hold key without requiring a hard dependency. */
public final class KurumiChainBridge {
    private static KeyMapping held;
    public static BaritoneBridge.Result setEnabled(boolean enabled) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.getConnection() == null)
            return new BaritoneBridge.Result(false, "尚未进入游戏");
        try {
            Class<?> bindings = Class.forName("org.xiyu.onekeyminer.neoforge.NeoForgeKeyBindings");
            Object key = bindings.getField("CHAIN_MINING_KEY").get(null);
            if (!(key instanceof KeyMapping mapping))
                return new BaritoneBridge.Result(false, "OneKeyMiner 的连锁按键尚未注册");
            mapping.setDown(enabled);
            held = enabled ? mapping : null;
            return new BaritoneBridge.Result(true, enabled
                    ? "已请求开启 OneKeyMiner 连锁破坏；需要实际挖掘方块才会触发"
                    : "已关闭 OneKeyMiner 连锁破坏按键");
        } catch (ReflectiveOperationException | LinkageError e) {
            return new BaritoneBridge.Result(false, "未找到兼容的 OneKeyMiner 1.6.9 客户端");
        }
    }
    public static void release() {
        if (held != null) {
            held.setDown(false);
            held = null;
        }
    }
    private KurumiChainBridge() {}
}
