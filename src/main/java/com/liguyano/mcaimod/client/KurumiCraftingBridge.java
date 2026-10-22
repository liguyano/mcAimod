package com.liguyano.mcaimod.client;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.level.block.CraftingTableBlock;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/** Open an actual nearby crafting table; crafting recipes are not yet automated. */
public final class KurumiCraftingBridge {
    public static BaritoneBridge.Result open() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.gameMode == null)
            return new BaritoneBridge.Result(false, "当前不在游戏世界");
        if (mc.player.containerMenu instanceof CraftingMenu)
            return new BaritoneBridge.Result(true, "工作台界面已经打开");
        if (mc.player.containerMenu != mc.player.inventoryMenu)
            return new BaritoneBridge.Result(false, "请先关闭其他容器界面");
        BlockPos nearest = null;
        double best = 6.0 * 6.0;
        BlockPos origin = mc.player.blockPosition();
        for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-6, -3, -6), origin.offset(6, 3, 6))) {
            if (!mc.level.hasChunkAt(pos) ||
                    !(mc.level.getBlockState(pos).getBlock() instanceof CraftingTableBlock))
                continue;
            double distance = mc.player.getEyePosition().distanceToSqr(Vec3.atCenterOf(pos));
            if (distance < best) {
                best = distance;
                nearest = pos.immutable();
            }
        }
        if (nearest == null)
            return new BaritoneBridge.Result(false, "附近交互范围内没有工作台，请先走近");
        var result = mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(nearest), Direction.UP, nearest, false));
        if (!result.consumesAction())
            return new BaritoneBridge.Result(false, "无法打开工作台，可能被阻挡或服务器拒绝");
        return new BaritoneBridge.Result(true, "已请求打开工作台，等待服务器显示合成界面");
    }

    private KurumiCraftingBridge() {}
}
