package com.liguyano.mcaimod.client;

import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.block.ChestBlock;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;

/** Opens the nearest reachable vanilla chest, waits for its server menu, then observes slots. */
public final class KurumiChestInspector {
    private static String requested;
    private static long started;
    private static String lastItem;
    private static String lastResult;
    private static long observedAt;
    private static int lastCount = -1;

    public static void install() { NeoForge.EVENT_BUS.addListener(KurumiChestInspector::tick); }
    public static void cancel() { requested = null; }
    public static boolean busy() { return requested != null; }
    public static int observedCount(String itemId) {
        return requested == null && itemId != null && itemId.equals(lastItem)
                && System.currentTimeMillis() - observedAt < 10_000 ? lastCount : -1;
    }
    public static String check(String itemId) {
        if (itemId == null || !itemId.matches("[a-z0-9_.-]+:[a-z0-9_./-]{1,128}"))
            return "{\"error\":\"invalid_item_id\"}";
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.gameMode == null)
            return "{\"error\":\"not_in_world\"}";
        if (mc.player.containerMenu instanceof ChestMenu menu)
            return snapshot(menu, itemId, false);
        if (requested != null) return status(itemId);
        if (KurumiChestBridge.busy() || mc.player.containerMenu != mc.player.inventoryMenu)
            return "{\"error\":\"container_busy\"}";
        BlockPos nearest = null;
        double best = 4.5 * 4.5;
        BlockPos origin = mc.player.blockPosition();
        for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-4, -2, -4), origin.offset(4, 2, 4))) {
            if (!mc.level.hasChunkAt(pos) || !(mc.level.getBlockState(pos).getBlock() instanceof ChestBlock))
                continue;
            double d = mc.player.getEyePosition().distanceToSqr(Vec3.atCenterOf(pos));
            if (d < best) { best = d; nearest = pos.immutable(); }
        }
        if (KurumiChestBridge.selectedChest() != null) {
            BlockPos selected = KurumiChestBridge.selectedChest();
            if (!mc.level.hasChunkAt(selected)
                    || !(mc.level.getBlockState(selected).getBlock() instanceof ChestBlock))
                return "{\"error\":\"selected_chest_missing\"}";
            if (mc.player.getEyePosition().distanceToSqr(Vec3.atCenterOf(selected)) >= 4.5 * 4.5)
                return "{\"error\":\"selected_chest_out_of_reach\"}";
            nearest = selected;
        }
        if (nearest == null) return "{\"error\":\"no_chest_in_reach\"}";
        var interaction = mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(nearest), Direction.UP, nearest, false));
        if (!interaction.consumesAction()) return "{\"error\":\"chest_open_rejected\"}";
        requested = itemId;
        started = System.currentTimeMillis();
        lastResult = null;
        lastCount = -1;
        return "{\"status\":\"pending\",\"message\":\"waiting_for_server_chest_menu\"}";
    }
    public static String status(String itemId) {
        if (requested != null) return "{\"status\":\"pending\"}";
        if (lastResult != null && itemId.equals(lastItem) && System.currentTimeMillis() - observedAt < 10_000)
            return lastResult;
        return "{\"status\":\"not_checked\"}";
    }
    private static String snapshot(ChestMenu menu, String id, boolean close) {
        int count = 0;
        int chestSlots = menu.getRowCount() * 9;
        for (int i = 0; i < chestSlots; i++) {
            var stack = menu.getSlot(i).getItem();
            if (!stack.isEmpty() && id.equals(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString()))
                count += stack.getCount();
        }
        JsonObject result = new JsonObject();
        result.addProperty("status", "observed");
        result.addProperty("item_id", id);
        result.addProperty("count", count);
        result.addProperty("has_item", count > 0);
        result.addProperty("enough_for_one", count >= 1);
        result.addProperty("source", "server_synced_chest_menu");
        lastItem = id;
        lastCount = count;
        lastResult = result.toString();
        observedAt = System.currentTimeMillis();
        if (close) Minecraft.getInstance().player.closeContainer();
        return lastResult;
    }
    private static void tick(ClientTickEvent.Post event) {
        if (requested == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.getConnection() == null) { cancel(); return; }
        if (mc.player.containerMenu instanceof ChestMenu menu) {
            snapshot(menu, requested, true);
            requested = null;
        } else if (System.currentTimeMillis() - started > 5000) {
            lastItem = requested;
            lastCount = -1;
            lastResult = "{\"status\":\"error\",\"error\":\"chest_open_timeout\"}";
            observedAt = System.currentTimeMillis();
            requested = null;
        }
    }
    private KurumiChestInspector() {}
}
