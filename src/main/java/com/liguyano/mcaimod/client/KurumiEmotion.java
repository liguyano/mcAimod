package com.liguyano.mcaimod.client;

/** Safe, finite list of voice delivery styles; model text never becomes a raw caption. */
final class KurumiEmotion {
    static String caption(String emotion) {
        return switch (emotion) {
            case "normal" -> "自然で親しみやすい、可愛らしい少女の声。";
            case "happy" -> "明るく元気で、楽しそうなアニメ少女の声。";
            case "fear" -> "怯えて緊張した少女の声。少し震えながら、助けを求めるように話す。";
            case "affection" -> "甘えるような、優しく可愛らしい少女の声。親しい相手に話しかける。";
            case "shy" -> "照れくさそうに、小さく柔らかい声で話す少女。";
            case "sad" -> "悲しそうに、弱々しく静かに話す少女の声。";
            default -> throw new IllegalArgumentException("未知情绪：" + emotion);
        };
    }

    private KurumiEmotion() {}
}
