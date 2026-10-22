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
import java.util.Map;
import java.util.IdentityHashMap;
import java.util.Collections;
import java.util.Set;

/**
 * Client recipe-book assisted crafting. Never invents recipes or blindly places inventory items.
 * Recipe placement is delegated to the vanilla game mode / server.
 */
public final class KurumiAutoCraftBridge {
    private record Job(String itemId, int requested, int initial, long started, int stage, Object recipeId) {}
    private static Job job;

    public static void install() { NeoForge.EVENT_BUS.addListener(KurumiAutoCraftBridge::tick); }
    public static boolean busy() { return job != null; }
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
        BaritoneBridge.Result opened = KurumiCraftingBridge.open();
        if (!opened.success()) return opened;
        job = new Job(itemId, quantity, initial, System.currentTimeMillis(), 0, null);
        return new BaritoneBridge.Result(true, "正在等待工作台打开，随后查询配方并尝试合成");
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
            Object recipeId = findRecipe(current.itemId());
            if (recipeId == null) {
                job = null;
                report("工作台已打开，但客户端未找到 " + current.itemId() + " 的可用配方；请检查配方是否解锁");
                return;
            }
            if (!placeRecipe(menu, recipeId)) {
                job = null;
                report("无法调用服务器配方放置接口，已停止，不会乱动背包");
                return;
            }
            job = new Job(current.itemId(), current.requested(), current.initial(),
                    current.started(), 1, recipeId);
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

    /**
     * Inspect both the client recipe book and the synced recipe manager.
     * Modern Minecraft versions use RecipeHolder/RecipeDisplay wrappers, not
     * necessarily the old getCollections/getResultItem zero-arg API.
     * The server still validates ingredients when a recipe is placed.
     */
    private static Object findRecipe(String itemId) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return null;
        Set<Object> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        Object found = inspectRecipeTree(mc.player.getRecipeBook(), itemId, visited, 0);
        if (found != null) return found;
        // ClientLevel in Minecraft 26.2 does not expose getRecipeManager().
        // Query the client connection's synced recipe state reflectively.
        Object connection = mc.getConnection();
        if (connection == null) return null;
        for (Method method : connection.getClass().getMethods()) {
            if (method.getParameterCount() != 0) continue;
            if (!Set.of("getRecipeManager", "getRecipeBook", "getRecipeAccess").contains(method.getName()))
                continue;
            try {
                Object found = inspectRecipeTree(method.invoke(connection), itemId, visited, 0);
                if (found != null) return found;
            } catch (ReflectiveOperationException | RuntimeException ignored) { }
        }
        return null;
    }

    private static Object inspectRecipeTree(Object node, String itemId,
                                            Set<Object> visited, int depth) {
        if (node == null || depth > 5 || visited.size() > 4096) return null;
        if (node instanceof Iterable<?> iterable) {
            for (Object child : iterable) {
                Object found = inspectRecipeTree(child, itemId, visited, depth + 1);
                if (found != null) return found;
            }
            return null;
        }
        if (node instanceof Map<?, ?> map)
            return inspectRecipeTree(map.values(), itemId, visited, depth + 1);
        if (!visited.add(node)) return null;
        Object id = null;
        Object recipe = null;
        for (Method method : node.getClass().getMethods()) {
            if (method.getParameterCount() != 0) continue;
            String name = method.getName();
            try {
                if (name.equals("id") || name.equals("getId")) id = method.invoke(node);
                else if (name.equals("value") || name.equals("getRecipe")) recipe = method.invoke(node);
            } catch (ReflectiveOperationException | RuntimeException ignored) { }
        }
        if (recipe != null && id != null && resultMatches(recipe, itemId))
            return id;
        if (id != null && resultMatches(node, itemId)) return id;
        if (recipe != null) {
            Object found = inspectRecipeTree(recipe, itemId, visited, depth + 1);
            if (found != null) return found;
        }
        for (Method method : node.getClass().getMethods()) {
            if (method.getParameterCount() != 0) continue;
            String name = method.getName();
            if (!Set.of("getCollections", "getAllRecipes", "getRecipes",
                    "recipes", "getRecipesFor", "getOrderedRecipes", "getRecipeList",
                    "getDisplays", "getEntries", "getRecipeMap").contains(name))
                continue;
            try {
                Object found = inspectRecipeTree(method.invoke(node), itemId, visited, depth + 1);
                if (found != null) return found;
            } catch (ReflectiveOperationException | RuntimeException ignored) { }
        }
        return null;
    }

    private static boolean resultMatches(Object recipe, String itemId) {
        Minecraft mc = Minecraft.getInstance();
        for (Method method : recipe.getClass().getMethods()) {
            if (!Set.of("result", "getResultItem", "getResult").contains(method.getName()))
                continue;
            try {
                Object value;
                if (method.getParameterCount() == 0) value = method.invoke(recipe);
                else if (method.getParameterCount() == 1 && mc.level != null
                        && method.getParameterTypes()[0].isInstance(mc.level.registryAccess()))
                    value = method.invoke(recipe, mc.level.registryAccess());
                else continue;
                if (value instanceof ItemStack stack && !stack.isEmpty()
                        && itemId.equals(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString()))
                    return true;
            } catch (ReflectiveOperationException | RuntimeException ignored) { }
        }
        return false;
    }

    private static void report(String message) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getConnection() != null) mc.getConnection().sendChat(message);
    }

    private KurumiAutoCraftBridge() {}
}
