package com.liguyano.mcaimod.client;

import java.net.URI;

/** Restrict voice data delivery to local servers or the explicitly approved HTTPS tunnel. */
final class KurumiVoiceEndpoint {
    static URI resolve(String base, String path, String params) {
        URI uri = URI.create(base);
        String host = uri.getHost();
        boolean local = "http".equals(uri.getScheme())
                && ("127.0.0.1".equalsIgnoreCase(host) || "localhost".equalsIgnoreCase(host));
        boolean tunnel = "https".equals(uri.getScheme())
                && "kurumivoice.cpolar.top".equalsIgnoreCase(host)
                && uri.getPort() == -1;
        if (!(local || tunnel) || uri.getUserInfo() != null || uri.getRawQuery() != null
                || uri.getRawFragment() != null || !(uri.getRawPath().isEmpty() || "/".equals(uri.getRawPath())))
            throw new IllegalArgumentException("语音服务器只能使用本地 HTTP 或 https://kurumivoice.cpolar.top");
        return URI.create(base.replaceAll("/+$", "") + path + params);
    }

    private KurumiVoiceEndpoint() {}
}
