package com.liguyano.mcaimod.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.crafting.RecipeType;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.event.RecipesReceivedEvent;
import net.neoforged.neoforge.common.NeoForge;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.IdentityHashMap;
import java.util.Collections;
import java.util.Set;
import java.util.HashSet;
import java.lang.reflect.Array;
import java.util.stream.Stream;

/**
 * Client recipe-book assisted crafting. Never invents recipes or blindly places inventory items.
 * Recipe placement is delegated to the vanilla game mode / server.
 */
public final class KurumiAutoCraftBridge {
    private record Job(String itemId, int requested, int initial, long started, int stage, Object recipeId, long stageStarted) {}
    private static Job job;
    private static boolean lastSucceeded;
    private static Object syncedRecipeMap;
    public static boolean lastSucceeded() { return lastSucceeded; }

    public static void install() {
        NeoForge.EVENT_BUS.addListener(KurumiAutoCraftBridge::tick);
        NeoForge.EVENT_BUS.addListener(KurumiAutoCraftBridge::recipesReceived);
        NeoForge.EVENT_BUS.addListener(KurumiAutoCraftBridge::clientLogOut);
    }

    private static void recipesReceived(RecipesReceivedEvent event) {
        syncedRecipeMap = event.getRecipeMap();
    }

    private static void clientLogOut(ClientPlayerNetworkEvent.LoggingOut event) {
        syncedRecipeMap = null;
        job = null;
        lastSucceeded = false;
    }
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
        lastSucceeded = false;
        int initial = count(itemId);
        BaritoneBridge.Result opened = KurumiCraftingBridge.open();
        if (!opened.success()) return opened;
        job = new Job(itemId, quantity, initial, System.currentTimeMillis(), 0, null, System.currentTimeMillis());
        return new BaritoneBridge.Result(true, "正在等待工作台打开，随后查询配方并尝试合成");
    }

    public static String lookup(String itemId) {
        if (itemId == null || !itemId.matches("[a-z0-9_.-]+:[a-z0-9_./-]{1,128}")) {
            JsonObject error = new JsonObject();
            error.addProperty("error", "invalid_item_id");
            return error.toString();
        }
        JsonObject out = new JsonObject();
        out.addProperty("item_id", itemId);
        JsonArray matches = new JsonArray();
        for (Object holder : syncedRecipeHolders()) {
            Object recipe = invokeNoArg(holder, "value");
            if (recipe == null || !resultMatches(recipe, itemId)) continue;
            JsonObject info = new JsonObject();
            info.addProperty("recipe_id", recipeId(holder));
            Object type = invokeNoArg(recipe, "getType");
            if (type != null) info.addProperty("recipe_type", type.toString());
            JsonObject output = new JsonObject();
            output.addProperty("item_id", itemId);
            output.addProperty("count", stackCount(recipeResult(recipe)));
            info.add("output", output);
            info.add("ingredients", describeIngredients(recipe));
            matches.add(info);
            if (matches.size() >= 8) break;
        }
        if (!matches.isEmpty()) {
            out.addProperty("available", true);
            out.addProperty("source", "neoforge_synced_recipe_manager");
            out.addProperty("recipe_count", matches.size());
            out.add("recipes", matches);
            out.addProperty("note", "配方来自服务器通过 NeoForge 同步的配方数据；材料选项可能包含标签或组件限制，实际合成仍由服务器验证");
            return out.toString();
        }
        Object fallback = findRecipe(itemId);
        if (fallback != null) {
            out.addProperty("available", true);
            out.addProperty("source", "client_recipe_book");
            out.addProperty("recipe_id", String.valueOf(fallback));
            out.addProperty("note", "仅在客户端可见的配方书/缓存中找到；未取得完整同步配方数据，不能据此断言已覆盖所有配方");
        } else {
            out.addProperty("available", false);
            out.addProperty("reason", syncedRecipeMap == null
                    ? "recipe_sync_unavailable" : "recipe_not_found_in_synced_workstation_types");
            out.addProperty("note", "没有在已同步的工作台、熔炉、切石机和锻造台配方类型中找到；自定义机器或非 RecipeManager 机制可能需要单独适配");
        }
        return out.toString();
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
        if (mc.player == null || mc.gameMode == null) { lastSucceeded = false; job = null; return; }
        if (System.currentTimeMillis() - current.started() > 45000) {
            job = null;
            report("合成等待超时，已停止；请检查材料或工作台");
            return;
        }
        if (!(mc.player.containerMenu instanceof CraftingMenu menu)) return;
        int done = count(current.itemId()) - current.initial();
        if (done >= current.requested()) {
            lastSucceeded = true;
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
                    current.started(), 1, recipeId, System.currentTimeMillis());
            return;
        }
        ItemStack result = menu.getSlot(0).getItem();
        if (current.stage() == 1) {
            if (!result.isEmpty() && current.itemId().equals(
                    BuiltInRegistries.ITEM.getKey(result.getItem()).toString())) {
                mc.gameMode.handleContainerInput(menu.containerId, 0, 0, ContainerInput.QUICK_MOVE, mc.player);
                job = new Job(current.itemId(), current.requested(), current.initial(),
                        current.started(), 2, current.recipeId(), System.currentTimeMillis());
            } else if (System.currentTimeMillis() - current.stageStarted() > 4000) {
                job = null;
                report("服务器未生成目标合成结果；请检查配方解锁状态及材料");
            }
            return;
        }
        if (current.stage() == 2) {
            // Wait for server inventory sync rather than treating a click as success.
            if (done > 0) {
                if (done >= current.requested()) {
                    lastSucceeded = true;
                    job = null;
                    mc.player.closeContainer();
                    report("合成完成，已获得 " + done + " 个 " + current.itemId());
                } else {
                    // Re-query the current recipe and request the next batch.
                    // Never fabricate ingredients or directly edit inventory slots.
                    job = new Job(current.itemId(), current.requested(), current.initial(),
                            current.started(), 3, current.recipeId(), System.currentTimeMillis());
                }
            } else if (System.currentTimeMillis() - current.stageStarted() > 4000) {
                job = null;
                report("领取合成结果后背包数量没有变化，停止操作");
            }
            return;
        }
        if (current.stage() == 3) {
            if (!result.isEmpty() && current.itemId().equals(
                    BuiltInRegistries.ITEM.getKey(result.getItem()).toString())) {
                mc.gameMode.handleContainerInput(menu.containerId, 0, 0, ContainerInput.QUICK_MOVE, mc.player);
                job = new Job(current.itemId(), current.requested(), current.initial(),
                        current.started(), 4, current.recipeId(), System.currentTimeMillis());
            } else if (System.currentTimeMillis() - current.stageStarted() > 400) {
                if (!placeRecipe(menu, current.recipeId())) {
                    job = null;
                    report("无法再次放置配方，已停止");
                } else job = new Job(current.itemId(), current.requested(), current.initial(),
                        current.started(), 1, current.recipeId(), System.currentTimeMillis());
            }
            return;
        }
        if (current.stage() == 4) {
            if (done >= current.requested()) {
                lastSucceeded = true;
                job = null;
                mc.player.closeContainer();
                report("合成完成，已获得 " + done + " 个 " + current.itemId());
            } else if (System.currentTimeMillis() - current.stageStarted() > 400) {
                job = new Job(current.itemId(), current.requested(), current.initial(),
                        current.started(), 3, current.recipeId(), System.currentTimeMillis());
            }
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

    private static List<RecipeType<?>> supportedRecipeTypes() {
        return List.of(RecipeType.CRAFTING, RecipeType.SMELTING, RecipeType.BLASTING,
                RecipeType.SMOKING, RecipeType.CAMPFIRE_COOKING, RecipeType.STONECUTTING,
                RecipeType.SMITHING);
    }

    private static List<Object> syncedRecipeHolders() {
        Object map = syncedRecipeMap;
        if (map == null) return List.of();
        List<Object> holders = new ArrayList<>();
        Method byType = null;
        for (Method method : map.getClass().getMethods()) {
            if (method.getName().equals("byType") && method.getParameterCount() == 1) {
                byType = method;
                break;
            }
        }
        if (byType == null) return holders;
        for (RecipeType<?> type : supportedRecipeTypes()) {
            if (!byType.getParameterTypes()[0].isInstance(type)) continue;
            try {
                Object found = byType.invoke(map, type);
                if (found instanceof Iterable<?> iterable) {
                    for (Object holder : iterable) holders.add(holder);
                } else if (found instanceof Stream<?> stream) {
                    stream.forEach(holders::add);
                }
            } catch (ReflectiveOperationException | RuntimeException ignored) { }
        }
        return holders;
    }

    private static Object invokeNoArg(Object target, String name) {
        if (target == null) return null;
        for (Method method : target.getClass().getMethods()) {
            if (!method.getName().equals(name) || method.getParameterCount() != 0) continue;
            try { return method.invoke(target); }
            catch (ReflectiveOperationException | RuntimeException ignored) { }
        }
        return null;
    }

    private static String recipeId(Object holder) {
        Object id = invokeNoArg(holder, "id");
        if (id == null) return "unknown";
        Object identifier = invokeNoArg(id, "identifier");
        if (identifier == null) identifier = invokeNoArg(id, "location");
        return identifier == null ? id.toString() : identifier.toString();
    }

    private static Object recipeResult(Object recipe) {
        Minecraft mc = Minecraft.getInstance();
        for (Method method : recipe.getClass().getMethods()) {
            if (!Set.of("result", "getResultItem", "getResult", "getOutput").contains(method.getName()))
                continue;
            try {
                Object value;
                if (method.getParameterCount() == 0) value = method.invoke(recipe);
                else if (method.getParameterCount() == 1 && mc.level != null
                        && method.getParameterTypes()[0].isInstance(mc.level.registryAccess()))
                    value = method.invoke(recipe, mc.level.registryAccess());
                else continue;
                if (value instanceof ItemStack || value instanceof ItemStackTemplate) return value;
            } catch (ReflectiveOperationException | RuntimeException ignored) { }
        }
        return null;
    }

    private static int stackCount(Object stack) {
        if (stack instanceof ItemStack itemStack) return itemStack.getCount();
        if (stack instanceof ItemStackTemplate template) return template.count();
        return 1;
    }

    private static String itemIdOf(Object value) {
        if (value instanceof ItemStack stack)
            return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        if (value instanceof ItemStackTemplate template)
            return BuiltInRegistries.ITEM.getKey(template.item().value()).toString();
        if (value instanceof Item item)
            return BuiltInRegistries.ITEM.getKey(item).toString();
        Object unwrapped = invokeNoArg(value, "value");
        if (unwrapped instanceof Item item)
            return BuiltInRegistries.ITEM.getKey(item).toString();
        return null;
    }

    private static JsonArray describeIngredients(Object recipe) {
        JsonArray ingredients = new JsonArray();
        Object raw = invokeNoArg(recipe, "getIngredients");
        if (!(raw instanceof Iterable<?> iterable)) return ingredients;
        int index = 0;
        for (Object ingredient : iterable) {
            JsonObject entry = new JsonObject();
            entry.addProperty("index", index++);
            JsonArray options = new JsonArray();
            Set<String> seen = new HashSet<>();
            Object values = invokeNoArg(ingredient, "items");
            if (values == null) values = invokeNoArg(ingredient, "getItems");
            appendIngredientOptions(values, options, seen, 0);
            if (!options.isEmpty()) entry.add("options", options);
            else entry.addProperty("description", ingredient == null ? "empty ingredient" : ingredient.toString());
            ingredients.add(entry);
            if (ingredients.size() >= 16) break;
        }
        return ingredients;
    }

    private static void appendIngredientOptions(Object value, JsonArray options, Set<String> seen, int depth) {
        if (value == null || depth > 3 || options.size() >= 32) return;
        if (value instanceof Stream<?> stream) {
            stream.limit(32).forEach(child -> appendIngredientOptions(child, options, seen, depth + 1));
            return;
        }
        if (value instanceof Iterable<?> iterable) {
            for (Object child : iterable) {
                appendIngredientOptions(child, options, seen, depth + 1);
                if (options.size() >= 32) break;
            }
            return;
        }
        if (value.getClass().isArray()) {
            for (int i = 0; i < Math.min(Array.getLength(value), 32); i++)
                appendIngredientOptions(Array.get(value, i), options, seen, depth + 1);
            return;
        }
        String id = itemIdOf(value);
        if (id == null || !seen.add(id)) return;
        JsonObject option = new JsonObject();
        option.addProperty("item_id", id);
        options.add(option);
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
        for (Object holder : syncedRecipeHolders()) {
            Object recipe = invokeNoArg(holder, "value");
            if (recipe != null && resultMatches(recipe, itemId)) {
                Object id = invokeNoArg(holder, "id");
                if (id != null) return id;
            }
        }
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
                Object recipeCandidate = inspectRecipeTree(method.invoke(connection), itemId, visited, 0);
                if (recipeCandidate != null) return recipeCandidate;
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
        return itemId.equals(itemIdOf(recipeResult(recipe)));
    }

    private static void report(String message) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getConnection() != null) mc.getConnection().sendChat(message);
    }

    private KurumiAutoCraftBridge() {}
}
