package com.liguyano.mcaimod.client;

import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * Client recipe-book assisted crafting. Never invents recipes or blindly places inventory items.
 * Recipe placement is delegated to the vanilla game mode / server.
 */
public final class KurumiAutoCraftBridge {
    private record Job(String itemId, int requested, int initial, long started, int stage, Object recipeId) {}
    private static Job job;

    public static void install() { NeoForge.EVENT_BUS.addListener(KurumiAutoCraftBridge::tick); }
    public static void cancel() { job = null; }

    public static BaritoneBridge.Result craft(String itemId, int quantity) {
        if (itemId == null || !itemId.matches("[a-z0-9_.-]+:[a-z0-9_./-]{1,128}")
                || quantity < 1 || quantity > 64)
            return new BaritoneBridge.Result(false, "合成物品 ID 或数量无效");
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.gameMode == null)
            return new BaritoneBridge.Result(false, "尚未进入游戏");
        if (job != null) return new BaritoneBridge.Result(false, "上一项合成任务仍在执行");
        int initial = count(itemId);
        Object recipeId = findRecipe(itemId);
        if (recipeId == null)
            return new BaritoneBridge.Result(false, "客户端配方书没有找到 " + itemId + " 的可用合成配方；不会猜测材料");
        BaritoneBridge.Result opened = KurumiCraftingBridge.open();
        if (!opened.success()) return opened;
        job = new Job(itemId, quantity, initial, System.currentTimeMillis(), 0, recipeId);
        return new BaritoneBridge.Result(true, "已找到配方，正在等待工作台打开并由服务器放入材料");
    }

    public static String lookup(String itemId) {
        if (itemId == null || !itemId.matches("[a-z0-9_.-]+:[a-z0-9_./-]{1,128}"))
            return "{\"error\":\"invalid_item_id\"}";
        return findRecipe(itemId) == null
                ? "{\"item_id\":\"" + itemId + "\",\"available\":false,\"reason\":\"recipe_not_in_client_recipe_book\"}"
                : "{\"item_id\":\"" + itemId + "\",\"available\":true,\"source\":\"client_recipe_book\",\"note\":\"server_will_check_ingredients\"}";
    }

    private static int count(String id) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return 0;
        int sum = 0;
        for (int i = 0; i < mc.player.getInventory().getContainerSize(); i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (!stack.isEmpty() && id.equals(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString()))
                sum += stack.getCount();
        }
        return sum;
    }

    private static void tick(ClientTickEvent.Post event) {
        Job current = job;
        if (current == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.gameMode == null) { job = null; return; }
        if (System.currentTimeMillis() - current.started() > 15000) {
            job = null;
            report("合成等待超时，已停止；请检查材料或工作台");
            return;
        }
        if (!(mc.player.containerMenu instanceof CraftingMenu menu)) return;
        int done = count(current.itemId()) - current.initial();
        if (done >= current.requested()) {
            job = null;
            mc.player.closeContainer();
            report("合成完成，已获得 " + done + " 个 " + current.itemId());
            return;
        }
        if (current.stage() == 0) {
            if (!placeRecipe(menu, current.recipeId())) {
                job = null;
                report("无法调用服务器配方放置接口，已停止，不会乱动背包");
                return;
            }
            job = new Job(current.itemId(), current.requested(), current.initial(),
                    current.started(), 1, current.recipeId());
            return;
        }
        ItemStack result = menu.getSlot(0).getItem();
        if (current.stage() == 1 && !result.isEmpty() && current.itemId().equals(BuiltInRegistries.ITEM.getKey(result.getItem()).toString())) {
            mc.gameMode.handleContainerInput(menu.containerId, 0, 0, ContainerInput.QUICK_MOVE, mc.player);
            job = new Job(current.itemId(), current.requested(), current.initial(),
                    current.started(), 2, current.recipeId());
        } else if (current.stage() == 2 && done > 0) {
            job = null;
            mc.player.closeContainer();
            report("已获得 " + done + " 个 " + current.itemId()
                    + "；如未达到目标数量，请再次下达合成指令");
        }
    }

    private static boolean placeRecipe(AbstractContainerMenu menu, Object recipeId) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.gameMode == null) return false;
        for (Method method : mc.gameMode.getClass().getMethods()) {
            if (!method.getName().equals("handlePlaceRecipe") || method.getParameterCount() != 3) continue;
            Class<?>[] params = method.getParameterTypes();
            if (params[0] != int.class || params[2] != boolean.class || !params[1].isInstance(recipeId))
                continue;
            try { method.invoke(mc.gameMode, menu.containerId, recipeId, false); return true; }
            catch (ReflectiveOperationException | RuntimeException ignored) { return false; }
        }
        return false;
    }

    private static Object findRecipe(String itemId) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return null;
        try {
            Object book = mc.player.getRecipeBook();
            // Different Minecraft versions expose recipe-book entries through different
            // collection methods. Only entries with a matching real result are accepted.
            for (Method method : book.getClass().getMethods()) {
                if (method.getParameterCount() != 0 ||
                        !(method.getName().equals("getCollections") || method.getName().equals("getAllRecipes")))
                    continue;
                Object result = method.invoke(book);
                if (result instanceof Iterable<?> entries)
                    for (Object entry : entries) {
                        Object id = inspectEntry(entry, itemId);
                        if (id != null) return id;
                    }
            }
        } catch (ReflectiveOperationException | RuntimeException ignored) { }
        return null;
    }

    private static Object inspectEntry(Object entry, String itemId) {
        if (entry == null) return null;
        List<Object> candidates = new ArrayList<>();
        candidates.add(entry);
        for (Method method : entry.getClass().getMethods()) {
            if (method.getParameterCount() != 0 ||
                    !(method.getName().equals("getRecipes") || method.getName().equals("recipes")))
                continue;
            try {
                Object nested = method.invoke(entry);
                if (nested instanceof Iterable<?> iterable)
                    for (Object recipe : iterable) candidates.add(recipe);
            } catch (ReflectiveOperationException | RuntimeException ignored) { }
        }
        for (Object candidate : candidates) {
            Object id = null;
            boolean match = false;
            for (Method method : candidate.getClass().getMethods()) {
                if (method.getParameterCount() != 0) continue;
                try {
                    if (method.getName().equals("id") || method.getName().equals("getId"))
                        id = method.invoke(candidate);
                    if (method.getName().equals("result") || method.getName().equals("getResultItem")) {
                        Object value = method.invoke(candidate);
                        if (value instanceof ItemStack stack && !stack.isEmpty()
                                && itemId.equals(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString()))
                            match = true;
                    }
                } catch (ReflectiveOperationException | RuntimeException ignored) { }
            }
            if (match && id != null) return id;
        }
        return null;
    }

    private static void report(String message) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getConnection() != null) mc.getConnection().sendChat(message);
    }

    private KurumiAutoCraftBridge() {}
}
