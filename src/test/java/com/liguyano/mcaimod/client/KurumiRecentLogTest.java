package com.liguyano.mcaimod.client;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class KurumiRecentLogTest {
    @Test void keepsLogsEvenWithoutAnActiveTaskAndBoundsHistory() {
        KurumiRecentLog logs = new KurumiRecentLog(2);
        logs.add("装备鱼竿：开始查找");
        logs.add("装备鱼竿：交换请求已发送");
        logs.add("装备鱼竿：服务器未同步");
        assertEquals(2, logs.entries().size());
        assertTrue(logs.entries().get(0).contains("交换请求已发送"));
        assertTrue(logs.entries().get(1).contains("服务器未同步"));
    }
}
