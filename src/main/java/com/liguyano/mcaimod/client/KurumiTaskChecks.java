package com.liguyano.mcaimod.client;

import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.inventory.ChestMenu;

/** Read-only, server-synced client observations for plan checkpoints. */
public final class KurumiTaskChecks {
    public static int inventoryCount(String id) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || id == null) return 0;
        int count = 0;
        for (int i = 0; i < 36; i++) {
            var stack = mc.player.getInventory().getItem(i);
            if (!stack.isEmpty() && id.equals(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString()))
                count += stack.getCount();
        }
        return count;
    }
    public static boolean within(int x, int y, int z, double radius) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return false;
        double dx = mc.player.getX() - x, dy = mc.player.getY() - y, dz = mc.player.getZ() - z;
        return dx * dx + dy * dy + dz * dz <= radius * radius;
    }
    public static String observe(String itemId, int x, int y, int z, double radius) {
        Minecraft mc = Minecraft.getInstance();
        JsonObject out = new JsonObject();
        out.addProperty("item_id", itemId);
        out.addProperty("inventory_count", inventoryCount(itemId));
        out.addProperty("at_target", within(x, y, z, radius));
        if (mc.player != null) {
            out.addProperty("x", mc.player.getBlockX());
            out.addProperty("y", mc.player.getBlockY());
            out.addProperty("z", mc.player.getBlockZ());
            out.addProperty("chest_open", mc.player.containerMenu instanceof ChestMenu);
        }
        return out.toString();
    }
    private KurumiTaskChecks() {}
}
