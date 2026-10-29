package com.liguyano.mcaimod.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/** Local-only, authenticated Java game-tool endpoint for the CPython agent. */
public final class KurumiAgentToolServer {
    private static final int PORT = 47832;
    private static final Set<String> READ = Set.of("get_player_status", "get_position",
            "get_environment", "get_inventory", "scan_nearby_entities",
            "scan_nearby_blocks", "scan_dropped_items", "inspect_container");
    private static final Set<String> ACTION = Set.of("goto", "mine", "pickup_all",
            "sleep_in_bed", "stop_fishing");
    private static final String TOKEN = newToken();
    private static volatile boolean active;
    private static volatile long epoch;

    private KurumiAgentToolServer() {}

    private static String newToken() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return java.util.HexFormat.of().formatHex(bytes);
    }

    public static String token() { return TOKEN; }
    public static int port() { return PORT; }

    public static synchronized void install() {
        if (active) return;
        active = true;
        Thread listener = new Thread(KurumiAgentToolServer::listen, "Kurumi-Agent-IPC");
        listener.setDaemon(true);
        listener.start();
    }

    public static void invalidatePending() { epoch++; }

    private static void listen() {
        try (ServerSocket server = new ServerSocket(PORT, 8, InetAddress.getByName("127.0.0.1"))) {
            while (active) {
                try (Socket socket = server.accept()) {
                    socket.setSoTimeout(20_000);
                    BufferedReader reader = new BufferedReader(new InputStreamReader(
                            socket.getInputStream(), StandardCharsets.UTF_8));
                    OutputStreamWriter writer = new OutputStreamWriter(
                            socket.getOutputStream(), StandardCharsets.UTF_8);
                    String line = reader.readLine();
                    JsonObject response = handle(line);
                    writer.write(response.toString() + "\n");
                    writer.flush();
                } catch (Exception error) {
                    KurumiWebMonitor.log("Python IPC 请求失败: " + error.getMessage());
                }
            }
        } catch (Exception error) {
            active = false;
            KurumiWebMonitor.log("Python IPC 启动失败: " + error.getMessage());
        }
    }

    private static JsonObject handle(String line) {
        try {
            if (line == null || line.length() > 12_000) throw new IllegalArgumentException("Invalid request size");
            JsonObject request = JsonParser.parseString(line).getAsJsonObject();
            if (!TOKEN.equals(request.get("token").getAsString()))
                throw new SecurityException("Unauthorized Python agent connection");
            String tool = request.get("tool").getAsString();
            if (!READ.contains(tool) && !ACTION.contains(tool))
                throw new SecurityException("Tool unavailable: " + tool);
            JsonArray arguments = request.has("args") ? request.getAsJsonArray("args") : new JsonArray();
            if (arguments.size() > 4) throw new IllegalArgumentException("Too many tool arguments");
            long requestEpoch = epoch;
            CompletableFuture<String> future = new CompletableFuture<>();
            Minecraft.getInstance().execute(() -> {
                try {
                    if (requestEpoch != epoch) throw new IllegalStateException("Stopped");
                    if (Minecraft.getInstance().player == null) throw new IllegalStateException("Not in world");
                    // Mutating calls require explicit owner enable; no remote user is implicitly authorized.
                    if (ACTION.contains(tool) && !KurumiClientController.pythonAgentActionsAllowed())
                        throw new SecurityException("Kurumi auto actions are disabled");
                    String result;
                    switch (tool) {
                        case "goto" -> {
                            if (arguments.size() != 3) throw new IllegalArgumentException("goto(x,y,z)");
                            result = asResult(BaritoneBridge.gotoCoordinates(
                                    arguments.get(0).getAsInt(), arguments.get(1).getAsInt(),
                                    arguments.get(2).getAsInt()));
                        }
                        case "mine" -> {
                            if (arguments.size() != 1) throw new IllegalArgumentException("mine(block_id)");
                            result = asResult(BaritoneBridge.mineBlock(arguments.get(0).getAsString()));
                        }
                        case "pickup_all" -> result = asResult(KurumiPickupBridge.pickupAll(null));
                        case "sleep_in_bed" -> result = asResult(KurumiSleepBridge.sleep());
                        case "stop_fishing" -> result = asResult(KurumiFishingBridge.stop());
                        default -> {
                            if (arguments.size() != 0) throw new IllegalArgumentException("Tool takes no arguments");
                            result = KurumiToolbox.observe(tool);
                        }
                    }
                    future.complete(result);
                } catch (Exception e) { future.completeExceptionally(e); }
            });
            JsonObject response = JsonParser.parseString(future.get(15, TimeUnit.SECONDS)).getAsJsonObject();
            return response;
        } catch (Exception e) {
            JsonObject response = new JsonObject();
            response.addProperty("success", false);
            response.addProperty("error", e.getMessage());
            return response;
        }
    }

    private static String asResult(BaritoneBridge.Result result) {
        JsonObject data = new JsonObject();
        data.addProperty("success", result.success());
        data.addProperty("completed", false);
        data.addProperty("message", result.message());
        return data.toString();
    }
}
