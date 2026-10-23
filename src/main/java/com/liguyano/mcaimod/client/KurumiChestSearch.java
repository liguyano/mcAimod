package com.liguyano.mcaimod.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.core.registries.BuiltInRegistries;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.fml.loading.FMLPaths;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Stateful chest search and remembered-block retrieval. All contents come from opened server menus. */
public final class KurumiChestSearch {
    private enum Phase { NAVIGATE, OPEN, WAIT_MENU }
    private static final long LIMIT = 285_000L;
    private static final int MAX_CHESTS = 24;
    private static List<BlockPos> pending = List.of();
    private static int index;
    private static BlockPos current;
    private static BlockPos stand;
    private static Phase phase;
    private static long started, phaseStarted;
    private static String item;
    private static int amount;
    private static boolean taking;
    private static boolean busy;
    private static String lastResult = "{\"status\":\"not_checked\"}";
    private static boolean lastSucceeded;

    public static void install() { NeoForge.EVENT_BUS.addListener(KurumiChestSearch::tick); }
    public static boolean busy() { return busy; }
    public static boolean lastSucceeded() { return lastSucceeded; }
    public static String status() { return lastResult; }
    public static void cancel() {
        if (busy) {
            BaritoneBridge.stop();
            closeMenu();
        }
        busy=false; current=null; pending=List.of(); phase=null;
        lastSucceeded=false;
    }
    private static Minecraft mc() { return Minecraft.getInstance(); }
    private static boolean validId(String value) {
        return value != null && value.matches("[a-z0-9_.-]+:[a-z0-9_./-]{1,128}");
    }
    private static String scope() {
        Minecraft mc=mc();
        String server = mc.getCurrentServer()==null ? "singleplayer" : mc.getCurrentServer().ip;
        String world = mc.level.dimension().toString();
        return (server+"|"+world).replaceAll("[^a-zA-Z0-9_.|:-]","_");
    }
    private static Path file() { return FMLPaths.CONFIGDIR.get().resolve("mcaimod-block-memory.json"); }
    private static JsonObject load() {
        try {
            if (Files.exists(file())) return JsonParser.parseString(Files.readString(file())).getAsJsonObject();
        } catch (Exception ignored) {}
        return new JsonObject();
    }
    private static void save(JsonObject root) {
        try { Files.writeString(file(),root.toString(),StandardCharsets.UTF_8); }
        catch (Exception e) { KurumiWebMonitor.log("方块记忆保存失败："+e.getMessage()); }
    }
    private static JsonObject entries(JsonObject root) {
        String key=scope();
        if (!root.has(key) || !root.get(key).isJsonObject()) root.add(key,new JsonObject());
        return root.getAsJsonObject(key);
    }
    private static String key(String name) {
        if (name==null || !name.matches("[A-Za-z0-9_\\u4e00-\\u9fa5-]{1,32}"))
            throw new IllegalArgumentException("方块记忆名称只能为1到32个字符");
        return name;
    }
    public static JsonObject remember(String name, BlockPos pos, String type) {
        JsonObject root=load(), names=entries(root), e=new JsonObject();
        e.addProperty("x",pos.getX());e.addProperty("y",pos.getY());e.addProperty("z",pos.getZ());
        e.addProperty("block_id",type);
        names.add(key(name),e);save(root);
        JsonObject result=new JsonObject();
        result.addProperty("saved",true);result.addProperty("name",name);
        result.add("position",e.deepCopy());
        return result;
    }
    public static JsonObject get(String name) {
        JsonObject result=new JsonObject();
        JsonObject root=load(), names=entries(root);
        if (!names.has(key(name))) {result.addProperty("found",false);return result;}
        result.addProperty("found",true);result.addProperty("name",name);
        JsonObject e=names.getAsJsonObject(name);
        for (String field : new String[]{"x","y","z","block_id"}) result.add(field,e.get(field).deepCopy());
        return result;
    }
    public static JsonObject rememberAt(String name,int x,int y,int z) {
        Minecraft mc=mc();
        if (mc.player==null || mc.level==null) throw new IllegalArgumentException("未进入世界");
        BlockPos pos=new BlockPos(x,y,z);
        if (!mc.level.hasChunkAt(pos)) throw new IllegalArgumentException("目标区块未加载");
        return remember(name,pos,BuiltInRegistries.BLOCK.getKey(mc.level.getBlockState(pos).getBlock()).toString());
    }
    private static BlockPos accessible(BlockPos box) {
        Minecraft mc=mc();
        BlockPos best=null;
        double distance=Double.POSITIVE_INFINITY;
        for (Direction side:Direction.Plane.HORIZONTAL) {
            BlockPos p=box.relative(side);
            if (!mc.level.hasChunkAt(p) || !mc.level.hasChunkAt(p.above()) || !mc.level.hasChunkAt(p.below())) continue;
            if (!mc.level.getBlockState(p).getCollisionShape(mc.level,p).isEmpty()
                    || !mc.level.getBlockState(p.above()).getCollisionShape(mc.level,p.above()).isEmpty()
                    || mc.level.getBlockState(p.below()).getCollisionShape(mc.level,p.below()).isEmpty()) continue;
            double d=p.distSqr(mc.player.blockPosition());
            if (d<distance) { distance=d;best=p.immutable(); }
        }
        return best;
    }
    public static BaritoneBridge.Result search(String itemId) {
        Minecraft mc=mc();
        if (!validId(itemId)) return new BaritoneBridge.Result(false,"物品ID无效");
        if (busy) return new BaritoneBridge.Result(false,"已有箱子搜索/取物任务");
        if (mc.player==null || mc.level==null || mc.gameMode==null)
            return new BaritoneBridge.Result(false,"尚未进入世界");
        if (mc.player.containerMenu!=mc.player.inventoryMenu)
            return new BaritoneBridge.Result(false,"请先关闭容器");
        BlockPos center=mc.player.blockPosition();
        List<BlockPos> found=new ArrayList<>();
        for(BlockPos p:BlockPos.betweenClosed(center.offset(-24,-6,-24),center.offset(24,6,24))) {
            if (mc.level.hasChunkAt(p) && mc.level.getBlockState(p).getBlock() instanceof ChestBlock)
                found.add(p.immutable());
        }
        found.sort(Comparator.comparingDouble(p->p.distSqr(center)));
        if (found.size()>MAX_CHESTS) found=new ArrayList<>(found.subList(0,MAX_CHESTS));
        if (found.isEmpty()) return new BaritoneBridge.Result(false,"附近已加载区域没有普通箱子");
        pending=found;index=0;checked=0;total=0;item=itemId;amount=0;taking=false;busy=true;
        started=System.currentTimeMillis();lastSucceeded=false;
        resultRoot=new JsonObject();results=new JsonArray();
        resultRoot.addProperty("item_id",itemId);
        resultRoot.addProperty("chests_discovered",found.size());
        resultRoot.addProperty("chests_checked",0);
        resultRoot.addProperty("total_count",0);
        resultRoot.add("chests",results);
        resultRoot.addProperty("status","pending");
        lastResult=resultRoot.toString();
        next();
        return new BaritoneBridge.Result(true,"已开始检查 "+found.size()+" 个附近箱子；不取出物品");
    }
    private static JsonObject resultRoot;
    private static JsonArray results;
    private static int checked,total;
    private static void next() {
        if (!busy) return;
        if (!taking && index>=pending.size()) {
            resultRoot.addProperty("status","completed");
            resultRoot.addProperty("chests_checked",checked);
            resultRoot.addProperty("total_count",total);
            lastResult=resultRoot.toString();
            finish(true);return;
        }
        current=taking?current:pending.get(index);
        if (!mc().level.hasChunkAt(current) || !(mc().level.getBlockState(current).getBlock() instanceof ChestBlock)) {
            failChest("箱子已消失或区块未加载");return;
        }
        stand=accessible(current);
        if (stand==null) { failChest("附近没有可站立位置");return; }
        if (mc().player.getEyePosition().distanceToSqr(Vec3.atCenterOf(current))<=4.5*4.5) {
            phase=Phase.OPEN;
        } else {
            var navigation=BaritoneBridge.gotoCoordinates(stand.getX(),stand.getY(),stand.getZ());
            if (!navigation.success()) {failChest(navigation.message());return;}
            phase=Phase.NAVIGATE;
        }
        phaseStarted=System.currentTimeMillis();
    }
    private static void failChest(String reason) {
        KurumiWebMonitor.log("箱子搜索："+current+"，"+reason);
        if (taking) {lastResult="{\"status\":\"error\",\"message\":\""+reason+"\"}";finish(false);return;}
        addResult(-1,reason);
        index++;
        next();
    }
    private static void addResult(int count,String error) {
        JsonObject e=new JsonObject();
        e.addProperty("x",current.getX());e.addProperty("y",current.getY());e.addProperty("z",current.getZ());
        e.addProperty("count",count);
        e.addProperty("status",error==null?"observed":"error");
        if(error!=null)e.addProperty("error",error);
        if(error==null) {
            checked++;total+=count;
            e.addProperty("memory_name","chest_"+(index+1));
            remember("chest_"+(index+1),current,"minecraft:chest");
        }
        results.add(e);
        resultRoot.addProperty("chests_checked",checked);
        resultRoot.addProperty("total_count",total);
        lastResult=resultRoot.toString();
    }
    private static void finish(boolean ok) {
        BaritoneBridge.stop();
        closeMenu();
        busy=false;lastSucceeded=ok;phase=null;current=null;pending=List.of();
        KurumiWebMonitor.log("箱子任务："+lastResult);
    }
    private static void closeMenu() {
        Minecraft mc=mc();
        if (mc.player!=null && mc.player.containerMenu instanceof ChestMenu)
            mc.player.closeContainer();
    }
    public static BaritoneBridge.Result take(String name,String itemId,int count) {
        Minecraft mc=mc();
        if (busy) return new BaritoneBridge.Result(false,"已有箱子任务");
        if (!validId(itemId) || count<1 || count>64)
            return new BaritoneBridge.Result(false,"物品ID无效或数量不在1到64");
        if(mc.player==null || mc.level==null || mc.gameMode==null || mc.player.containerMenu!=mc.player.inventoryMenu)
            return new BaritoneBridge.Result(false,"请进入游戏并关闭其他容器");
        JsonObject memory;
        try {memory=get(name);}catch(Exception e){return new BaritoneBridge.Result(false,e.getMessage());}
        if (!memory.get("found").getAsBoolean()) return new BaritoneBridge.Result(false,"没有这条方块记忆");
        BlockPos p=new BlockPos(memory.get("x").getAsInt(),memory.get("y").getAsInt(),memory.get("z").getAsInt());
        if(!mc.level.hasChunkAt(p)) return new BaritoneBridge.Result(false,"记忆中的箱子区块尚未加载，无法安全核实");
        if(!(mc.level.getBlockState(p).getBlock() instanceof ChestBlock))
            return new BaritoneBridge.Result(false,"记忆坐标不是箱子，拒绝从其他箱子取物");
        item=itemId;amount=count;taking=true;busy=true;current=p;started=System.currentTimeMillis();
        lastSucceeded=false;lastResult="{\"status\":\"pending\",\"operation\":\"take_from_memory\"}";
        next();
        return new BaritoneBridge.Result(true,"已定位记忆中的箱子 "+name+"，准备取出 "+itemId+" x"+count);
    }
    private static void tick(ClientTickEvent.Post ignored) {
        if(!busy)return;
        Minecraft mc=mc();
        if(mc.player==null || mc.level==null || mc.gameMode==null){cancel();return;}
        if(System.currentTimeMillis()-started>LIMIT){lastResult="{\"status\":\"error\",\"message\":\"任务总超时\"}";finish(false);return;}
        if(System.currentTimeMillis()-phaseStarted>45_000){failChest("导航或打开箱子超时");return;}
        switch(phase) {
            case NAVIGATE -> {
                if(mc.player.getEyePosition().distanceToSqr(Vec3.atCenterOf(current))<=4.5*4.5) {
                    BaritoneBridge.stop();
                    phase=Phase.OPEN;phaseStarted=System.currentTimeMillis();
                }
            }
            case OPEN -> {
                if(!mc.level.hasChunkAt(current) || !(mc.level.getBlockState(current).getBlock() instanceof ChestBlock)){
                    failChest("原位置不再是箱子");return;
                }
                if(mc.player.getEyePosition().distanceToSqr(Vec3.atCenterOf(current))>4.5*4.5){failChest("未走到箱子附近");return;}
                var r=mc.gameMode.useItemOn(mc.player,InteractionHand.MAIN_HAND,
                        new BlockHitResult(Vec3.atCenterOf(current),Direction.UP,current,false));
                if(!r.consumesAction()){failChest("打开箱子被拒绝");return;}
                phase=Phase.WAIT_MENU;phaseStarted=System.currentTimeMillis();
            }
            case WAIT_MENU -> {
                if(!(mc.player.containerMenu instanceof ChestMenu menu)) {
                    if(System.currentTimeMillis()-phaseStarted>5000)failChest("服务器未同步箱子菜单");
                    return;
                }
                int inChest=0;
                for(int i=0;i<menu.getRowCount()*9;i++){
                    var stack=menu.getSlot(i).getItem();
                    if(!stack.isEmpty() && item.equals(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString()))
                        inChest+=stack.getCount();
                }
                if(taking){
                    if(inChest<amount){lastResult="{\"status\":\"error\",\"message\":\"记忆箱子中物品不足\"}";finish(false);return;}
                    // Existing transfer only operates on the currently open menu; no nearest-chest redirect.
                    var result=KurumiChestBridge.takeItem(item,amount);
                    JsonObject output=new JsonObject();output.addProperty("status",result.success()?"take_requested":"error");
                    output.addProperty("item_id",item);output.addProperty("quantity",amount);
                    output.addProperty("message",result.message());lastResult=output.toString();
                    finish(result.success());return;
                }
                addResult(inChest,null);
                mc.player.closeContainer();
                index++;next();
            }
        }
    }
    private KurumiChestSearch() {}
}
