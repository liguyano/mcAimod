package com.liguyano.mcaimod.client;

import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.AxeItem;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;
import java.util.UUID;

/** Short-range, bounded combat against an explicitly selected player or mob. */
public final class KurumiCombatBridge {
    private static UUID targetId;
    private static long started;
    private static long lastHit;
    private static int previousSlot = -1;
    private static final double RANGE_SQUARED = 3.0 * 3.0;

    public static void install() { NeoForge.EVENT_BUS.addListener(KurumiCombatBridge::tick); }
    public static void cancel() {
        targetId = null;
        Minecraft mc = Minecraft.getInstance();
        if (previousSlot >= 0 && mc.player != null)
            mc.player.getInventory().setSelectedSlot(previousSlot);
        previousSlot = -1;
    }

    /** Prefer the strongest available hotbar sword, then axe; do not rearrange inventory. */
    private static void selectWeapon() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        var inventory = mc.player.getInventory();
        int bestSlot = -1;
        double bestScore = -1;
        for (int slot = 0; slot < 9; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.isEmpty()) continue;
            double score;
            if (stack.getItem() instanceof SwordItem) score = 100;
            else if (stack.getItem() instanceof AxeItem) score = 50;
            else continue;
            String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
            if (id.contains("netherite")) score += 6;
            else if (id.contains("diamond")) score += 5;
            else if (id.contains("iron")) score += 4;
            else if (id.contains("stone")) score += 3;
            else if (id.contains("golden")) score += 2;
            else if (id.contains("wooden")) score += 1;
            if (score > bestScore) { bestScore = score; bestSlot = slot; }
        }
        if (bestSlot >= 0 && bestSlot != inventory.getSelectedSlot()) {
            previousSlot = inventory.getSelectedSlot();
            inventory.setSelectedSlot(bestSlot);
        }
    }

    public static BaritoneBridge.Result attackPlayer(String name) {
        if (name == null || !name.matches("[A-Za-z0-9_]{3,16}"))
            return new BaritoneBridge.Result(false, "玩家名不合法");
        if ("SekaiSaionji".equals(name))
            return new BaritoneBridge.Result(false, "不能攻击主人");
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null)
            return new BaritoneBridge.Result(false, "尚未进入游戏");
        for (Player player : mc.level.players()) {
            if (name.equals(player.getName().getString()) && player != mc.player)
                return begin(player);
        }
        return new BaritoneBridge.Result(false, "当前视野内找不到指定玩家");
    }

    public static BaritoneBridge.Result attackMob(String id) {
        if (id == null || !id.matches("[a-z0-9_.-]+:[a-z0-9_./-]{1,128}"))
            return new BaritoneBridge.Result(false, "生物 ID 不合法");
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null)
            return new BaritoneBridge.Result(false, "尚未进入游戏");
        LivingEntity nearest = null;
        double best = 16 * 16;
        for (Entity entity : mc.level.getEntities(mc.player, mc.player.getBoundingBox().inflate(16),
                e -> e instanceof LivingEntity && !(e instanceof Player))) {
            if (!id.equals(BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString())) continue;
            double d = mc.player.distanceToSqr(entity);
            if (d < best) { best = d; nearest = (LivingEntity) entity; }
        }
        return nearest == null ? new BaritoneBridge.Result(false, "16 格内找不到指定生物") : begin(nearest);
    }

    private static BaritoneBridge.Result begin(LivingEntity target) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.gameMode == null || !target.isAlive())
            return new BaritoneBridge.Result(false, "目标不可攻击");
        if (mc.player.distanceToSqr(target) > RANGE_SQUARED)
            return new BaritoneBridge.Result(false, "目标不在 3 格攻击范围内，请先靠近");
        cancel();
        selectWeapon();
        targetId = target.getUUID();
        started = System.currentTimeMillis();
        lastHit = 0;
        return new BaritoneBridge.Result(true, "已锁定目标，已尝试切换快捷栏武器，最多攻击 10 秒");
    }

    private static void tick(ClientTickEvent.Post event) {
        if (targetId == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.gameMode == null ||
                System.currentTimeMillis() - started > 10000) { cancel(); return; }
        Entity target = null;
        for (Entity e : mc.level.getEntities(mc.player, mc.player.getBoundingBox().inflate(5),
                e -> e.getUUID().equals(targetId))) { target = e; break; }
        if (!(target instanceof LivingEntity living) || !living.isAlive() ||
                mc.player.distanceToSqr(target) > RANGE_SQUARED) { cancel(); return; }
        long now = System.currentTimeMillis();
        if (now - lastHit < 650) return;
        mc.gameMode.attack(mc.player, target);
        mc.player.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
        lastHit = now;
    }

    private KurumiCombatBridge() {}
}
