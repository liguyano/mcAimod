package com.liguyano.mcaimod.client;

import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;

/** One ephemeral, on-demand game-frame capture; never persists an image or logs its data. */
public final class KurumiScreenshotBridge {
    private static long sequence;
    private static long activeId;
    private static long started;
    private static String imageDataUri;
    private static String failure;
    private static final long MAX_AGE_MS = 15000;

    public static synchronized JsonObject capture() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null)
            throw new IllegalArgumentException("尚未进入游戏，无法截图");
        if (activeId != 0 && imageDataUri == null && failure == null
                && System.currentTimeMillis() - started < MAX_AGE_MS)
            throw new IllegalArgumentException("上一张截图还在处理中");
        long id = ++sequence;
        activeId = id;
        started = System.currentTimeMillis();
        imageDataUri = null;
        failure = null;
        Screenshot.takeScreenshot(mc.gameRenderer.mainRenderTarget(), nativeImage -> {
            try (nativeImage) {
                Path temp = Files.createTempFile("kurumi-frame-", ".png");
                try {
                    nativeImage.writeToFile(temp);
                    BufferedImage original = ImageIO.read(temp.toFile());
                    if (original == null) throw new IllegalStateException("截图无法读取");
                    int width = Math.min(960, original.getWidth());
                    int height = Math.max(1, original.getHeight() * width / original.getWidth());
                    BufferedImage scaled = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
                    Graphics2D graphics = scaled.createGraphics();
                    try {
                        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                                RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                        graphics.drawImage(original, 0, 0, width, height, null);
                    } finally { graphics.dispose(); }
                    ByteArrayOutputStream out = new ByteArrayOutputStream();
                    var writers = ImageIO.getImageWritersByFormatName("jpeg");
                    if (!writers.hasNext()) throw new IllegalStateException("缺少JPEG编码器");
                    var writer = writers.next();
                    try (var output = ImageIO.createImageOutputStream(out)) {
                        writer.setOutput(output);
                        ImageWriteParam params = writer.getDefaultWriteParam();
                        params.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                        params.setCompressionQuality(0.78f);
                        writer.write(null, new IIOImage(scaled, null, null), params);
                    } finally { writer.dispose(); }
                    String encoded = "data:image/jpeg;base64," + Base64.getEncoder().encodeToString(out.toByteArray());
                    if (encoded.length() > 3_000_000) throw new IllegalStateException("截图数据超过上限");
                    synchronized (KurumiScreenshotBridge.class) {
                        if (activeId == id) imageDataUri = encoded;
                    }
                } finally { Files.deleteIfExists(temp); }
            } catch (Exception error) {
                synchronized (KurumiScreenshotBridge.class) {
                    if (activeId == id) failure = "截图编码失败";
                }
            }
        });
        JsonObject reference = new JsonObject();
        reference.addProperty("kind", "screenshot");
        reference.addProperty("id", id);
        return reference;
    }

    public static synchronized int status(long id) {
        if (id == 0 || id != activeId) return -1;
        if (failure != null || System.currentTimeMillis() - started > MAX_AGE_MS) return -1;
        return imageDataUri == null ? 0 : 1;
    }

    public static synchronized String consume(long id) {
        if (status(id) != 1) return null;
        String data = imageDataUri;
        reset();
        return data;
    }

    public static synchronized void reset() {
        activeId = 0;
        imageDataUri = null;
        failure = null;
    }

    private KurumiScreenshotBridge() {}
}
