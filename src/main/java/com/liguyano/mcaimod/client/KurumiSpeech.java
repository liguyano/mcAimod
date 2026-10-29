package com.liguyano.mcaimod.client;

import net.minecraft.client.Minecraft;

/** Single source of truth for Kurumi-authored public speech and local TTS. */
public final class KurumiSpeech {
    private KurumiSpeech() {}

    /** Never observe or replay incoming server chat: only Kurumi's own messages call this. */
    public static void say(String text) {
        Minecraft mc = Minecraft.getInstance();
        if (text == null || text.isBlank() || mc.getConnection() == null) return;
        String message = text.strip();
        if (message.startsWith("/")) return;
        String sent = message.length() > 120 ? message.substring(0, 120) : message;
        mc.getConnection().sendChat(sent);
        KurumiVoiceManager.speak(sent);
    }
}
