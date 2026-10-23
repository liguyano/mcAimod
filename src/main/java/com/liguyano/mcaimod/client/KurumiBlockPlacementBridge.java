package com.liguyano.mcaimod.client;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.AABB;

/** Safely place one held block on top of a solid support at an explicit or nearby position. */
public final class KurumiBlockPlacementBridge {
    public static BaritoneBridge.Result place(String itemId, Integer x, Integer y, Integer z) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.gameMode == null)
            return new BaritoneBridge.Result(false, "尚未进入世界");
        if (mc.player.containerMenu != mc.player.inventoryMenu)
            return new BaritoneBridge.Result(false, "请先关闭容器界面");
        if (itemId == null || !itemId.matches("[a-z0-9_.-]+:[a-z0-9_./-]{1,128}"))
            return new BaritoneBridge.Result(false, "方块物品ID不合法");
        var held = mc.player.getMainHandItem();
        if (held.isEmpty() || !(held.getItem() instanceof BlockItem)
                || !itemId.equals(BuiltInRegistries.ITEM.getKey(held.getItem()).toString()))
            return new BaritoneBridge.Result(false, "请先用 equip_main_hand() 把指定方块拿到主手");
        BlockPos target = x == null
                ? mc.player.blockPosition().relative(mc.player.getDirection())
                : new BlockPos(x, y, z);
        if (target.getY() < mc.level.getMinY() || target.getY() >= mc.level.getMaxY()
                || !mc.level.getWorldBorder().isWithinBounds(target))
            return new BaritoneBridge.Result(false, "放置坐标不在世界范围内");
        BlockPos support = target.below();
        if (!mc.level.hasChunkAt(target) || !mc.level.hasChunkAt(support))
            return new BaritoneBridge.Result(false, "目标区块尚未加载");
        if (!mc.level.getBlockState(target).canBeReplaced())
            return new BaritoneBridge.Result(false, "目标位置已有不可替换的方块");
        if (!mc.level.getBlockState(support).isFaceSturdy(mc.level, support, Direction.UP))
            return new BaritoneBridge.Result(false, "目标下方没有可支撑放置的完整顶面");
        if (mc.player.getBoundingBox().intersects(new AABB(target)))
            return new BaritoneBridge.Result(false, "目标位置与玩家身体重叠");
        Vec3 hit = Vec3.atCenterOf(support).add(0, 0.5, 0);
        if (mc.player.getEyePosition().distanceToSqr(hit) > 4.5 * 4.5)
            return new BaritoneBridge.Result(false, "放置距离超过4.5格，请先靠近");
        var interaction = mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND,
                new BlockHitResult(hit, Direction.UP, support, false));
        return new BaritoneBridge.Result(interaction.consumesAction(),
                interaction.consumesAction()
                        ? "已尝试放置 " + itemId + " 到 " + target.toShortString() + "，等待服务器确认"
                        : "服务器未接受放置交互");
    }
    private KurumiBlockPlacementBridge() {}
}
