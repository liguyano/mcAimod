package com.liguyano.mcaimod.client;

import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.inventory.ContainerInput;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;
import java.util.UUID;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Short-range, bounded combat against an explicitly selected player or mob. */
public final class KurumiCombatBridge {
    private static UUID targetId;
    private static long started;
    private static long lastHit;
    private static int previousSlot = -1;
    private static int swappedInventorySlot = -1;
    private static int swappedHotbarSlot = -1;
    private static final double RANGE_SQUARED = 3.0 * 3.0;

    public static boolean busy() { return targetId != null; }
    public static void install() { NeoForge.EVENT_BUS.addListener(KurumiCombatBridge::tick); }
    public static void cancel() {
        targetId = null;
        Minecraft mc = Minecraft.getInstance();
        if (previousSlot >= 0 && mc.player != null)
            mc.player.getInventory().setSelectedSlot(previousSlot);
        previousSlot = -1;
        // Do not automatically swap items back: preserve the player's server-synced inventory.
        swappedInventorySlot = -1;
        swappedHotbarSlot = -1;
    }

    /** Choose a sword or axe from the whole 36-slot inventory and equip it in the hotbar. */
    private static void selectWeapon() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.gameMode == null) return;
        var inventory = mc.player.getInventory();
        int bestSlot = -1;
        double bestScore = -1;
        for (int slot = 0; slot < 36; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.isEmpty()) continue;
            double score;
            if (BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath().endsWith("_sword")) score = 100;
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
        if (bestSlot < 0) return;
        previousSlot = inventory.getSelectedSlot();
        if (bestSlot >= 9) {
            if (mc.player.containerMenu != mc.player.inventoryMenu) return;
            // Player inventory slots 9..35 map directly to menu slots 9..35.
            // SWAP's button is the destination hotbar index (0..8).
            int destination = inventory.getSelectedSlot();
            mc.gameMode.handleContainerInput(mc.player.inventoryMenu.containerId,
                    bestSlot, destination, ContainerInput.SWAP, mc.player);
            swappedInventorySlot = bestSlot;
            swappedHotbarSlot = destination;
        } else {
            inventory.setSelectedSlot(bestSlot);
        }
    }

    public static BaritoneBridge.Result attackPlayer(String name) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null)
            return new BaritoneBridge.Result(false, "尚未进入游戏");
        var match = KurumiPlayerResolver.resolve(name, KurumiPlayerDirectory.onlineNames());
        if (!match.found()) return new BaritoneBridge.Result(false, match.failure(name));
        String canonical = match.name();
        if ("SekaiSaionji".equalsIgnoreCase(canonical))
            return new BaritoneBridge.Result(false, "不能攻击主人");
        UUID targetUuid = null;
        UUID ownerUuid = null;
        if (mc.getConnection() != null) {
            for (var info : mc.getConnection().getOnlinePlayers()) {
                String listedName = info.getProfile().name();
                if (listedName.equalsIgnoreCase(canonical)) targetUuid = info.getProfile().id();
                if (listedName.equalsIgnoreCase("SekaiSaionji")) ownerUuid = info.getProfile().id();
            }
        }
        // The roster lists online players, but only spawned/synced entities can be attacked.
        Map<UUID, Player> loaded = new LinkedHashMap<>();
        for (Player player : mc.level.players())
            if (player != mc.player && player.isAlive()) loaded.put(player.getUUID(), player);
        for (var entity : mc.level.getEntities(mc.player, mc.player.getBoundingBox().inflate(32),
                entity -> entity instanceof Player)) {
            if (entity instanceof Player player && player != mc.player && player.isAlive())
                loaded.putIfAbsent(player.getUUID(), player);
        }
        List<KurumiPlayerEntityMatcher.Candidate> candidates = new ArrayList<>();
        for (Player player : loaded.values()) candidates.add(new KurumiPlayerEntityMatcher.Candidate(
                player.getUUID(), player.getGameProfile().name(), player.getName().getString()));
        var selected = KurumiPlayerEntityMatcher.select(canonical, targetUuid, candidates);
        if (selected != null) {
            Player player = loaded.get(selected.uuid());
            // Never attack the owner even when a nickname or mismatched roster is supplied.
            if ("SekaiSaionji".equalsIgnoreCase(selected.profileName())
                    || "SekaiSaionji".equalsIgnoreCase(selected.displayedName())
                    || (ownerUuid != null && ownerUuid.equals(selected.uuid())))
                return new BaritoneBridge.Result(false, "不能攻击主人");
            return begin(player);
        }
        KurumiWebMonitor.log("玩家攻击目标识别失败：请求=" + name + "；解析=" + canonical
                + "；在线UUID=" + targetUuid + "；已加载玩家=" + candidates);
        return new BaritoneBridge.Result(false, "玩家 " + canonical
                + " 在线，但未唯一匹配到客户端玩家实体（已加载 " + candidates.size()
                + " 名玩家，详情见 Kurumi 日志）");
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
