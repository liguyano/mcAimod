package com.liguyano.mcaimod.client;

import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;

/** Model-free response to hunger and attacks. Runs on the client tick thread. */
public final class KurumiSurvivalBridge {
    private static int tickCount, previousFood = 20, previousSlot, swappedSlot = -1;
    private static float previousHealth = -1;
    private static boolean eating, awaitingEquip, wasUsing;
    private static boolean hungerDecisionPending, hungerApproved;
    private static long nextHungerReview;
    private static long hungerDecisionStarted;
    private static int equipTicks;
    private static String foodId;
    private static long eatingStarted;

    private KurumiSurvivalBridge() {}

    public static boolean eating() { return eating || hungerDecisionPending; }

    static void hungerDecision(boolean eatNow) {
        if (!hungerDecisionPending) return;
        hungerDecisionPending = false;
        hungerApproved = eatNow;
        nextHungerReview = System.currentTimeMillis() + (eatNow ? 0 : 30_000);
        if (!eatNow) talk("主人~ 我有点饿，不过先完成手头的事再吃。");
    }

    public static void reset() {
        eating = false;
        hungerDecisionPending = false;
        hungerApproved = false;
        nextHungerReview = 0;
        hungerDecisionStarted = 0;
        awaitingEquip = false;
        foodId = null;
        swappedSlot = -1;
        previousHealth = -1;
        previousFood = 20;
        tickCount = 0;
    }

    private static void talk(String message) {
        var mc = Minecraft.getInstance();
        KurumiWebMonitor.log("生存中断: " + message);
        if (mc.getConnection() != null) mc.getConnection().sendChat(message);
    }

    public static void tick(boolean enabled) {
        Minecraft mc = Minecraft.getInstance();
        if (!enabled || mc.player == null || mc.gameMode == null || mc.level == null) {
            if (eating) KurumiItemUseBridge.cancel();
            reset();
            return;
        }
        float health = mc.player.getHealth();
        // Compare actual health loss, rather than hurtTime (which lasts multiple ticks).
        if (previousHealth >= 0 && health < previousHealth && mc.player.hurtTime > 0) {
            var attacker = mc.player.getLastHurtByMob();
            if (attacker != null && attacker.isAlive() && mc.player.distanceToSqr(attacker) < 32 * 32) {
                if (eating) finishEating(false);
                previousHealth = health;
                KurumiClientController.onSurvivalAttack(attacker.getX(), attacker.getZ());
                return;
            }
        }
        previousHealth = health;
        if (eating) { tickEating(); return; }
        if (hungerDecisionPending) {
            if (System.currentTimeMillis() - hungerDecisionStarted > 15_000)
                hungerDecision(false);
            return;
        }
        if (++tickCount % 10 != 0 || KurumiItemUseBridge.busy()
                || mc.player.containerMenu != mc.player.inventoryMenu || mc.player.isUsingItem()) return;
        int level = mc.player.getFoodData().getFoodLevel();
        previousFood = level;
        if (!KurumiSurvivalPolicy.needsFood(level, mc.player.getFoodData().needsFood(), false)) return;
        if (!hungerApproved && System.currentTimeMillis() < nextHungerReview) return;
        if (!hungerApproved && KurumiClientController.hasSurvivalTask()) {
            hungerDecisionPending = true;
            hungerDecisionStarted = System.currentTimeMillis();
            KurumiClientController.requestHungerDecision(level);
            return;
        }
        hungerApproved = false;
        int slot = -1;
        int bestNutrition = -1;
        for (int i = 0; i < 36; i++) {
            ItemStack item = mc.player.getInventory().getItem(i);
            var food = item.get(DataComponents.FOOD);
            if (food != null && food.nutrition() > bestNutrition) {
                slot = i;
                bestNutrition = food.nutrition();
            }
        }
        if (slot == -1) {
            if (tickCount % 200 == 0) talk("主人~ 我饿了，但是背包里没有食物！");
            return;
        }
        foodId = BuiltInRegistries.ITEM.getKey(mc.player.getInventory().getItem(slot).getItem()).toString();
        previousSlot = mc.player.getInventory().getSelectedSlot();
        swappedSlot = slot >= 9 ? slot : -1;
        if (slot >= 9) {
            mc.gameMode.handleContainerInput(mc.player.inventoryMenu.containerId,
                    slot, previousSlot, ContainerInput.SWAP, mc.player);
        } else mc.player.getInventory().setSelectedSlot(slot);
        eating = true;
        awaitingEquip = true;
        equipTicks = 2;
        wasUsing = false;
        eatingStarted = System.currentTimeMillis();
        talk("主人~ 我饿了，先吃点东西，马上继续！");
    }

    private static void tickEating() {
        var mc = Minecraft.getInstance();
        if (mc.player == null) { reset(); return; }
        if (System.currentTimeMillis() - eatingStarted > 12_000) { finishEating(false); return; }
        if (awaitingEquip) {
            if (--equipTicks > 0) return;
            awaitingEquip = false;
            var result = KurumiItemUseBridge.use(foodId);
            if (!result.success()) finishEating(false);
            return;
        }
        if (KurumiItemUseBridge.busy()) {
            if (mc.player.isUsingItem()) wasUsing = true;
            return;
        }
        finishEating(KurumiItemUseBridge.lastSucceeded() || mc.player.getFoodData().getFoodLevel() > previousFood);
    }

    private static void finishEating(boolean success) {
        var mc = Minecraft.getInstance();
        if (KurumiItemUseBridge.busy()) KurumiItemUseBridge.cancel();
        if (mc.player != null && mc.gameMode != null) {
            if (swappedSlot >= 9 && mc.player.containerMenu == mc.player.inventoryMenu)
                mc.gameMode.handleContainerInput(mc.player.inventoryMenu.containerId,
                        swappedSlot, previousSlot, ContainerInput.SWAP, mc.player);
            mc.player.getInventory().setSelectedSlot(previousSlot);
        }
        eating = false;
        awaitingEquip = false;
        swappedSlot = -1;
        foodId = null;
        tickCount = 0;
        talk(success ? "主人~ 吃饱啦，继续做事！" : "主人~ 吃东西没有成功，稍后再试。");
    }
}
