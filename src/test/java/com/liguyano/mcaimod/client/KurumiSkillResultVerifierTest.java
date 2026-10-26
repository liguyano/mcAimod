package com.liguyano.mcaimod.client;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class KurumiSkillResultVerifierTest {
    @Test void successRequiresExplicitConfirmedResult() {
        assertTrue(KurumiSkillResultVerifier.confirmed("{\"result\":{\"success\":true,\"completed\":true}}"));
        assertFalse(KurumiSkillResultVerifier.confirmed("{\"result\":{\"success\":true,\"completed\":false}}"));
        assertFalse(KurumiSkillResultVerifier.confirmed("{\"result\":true}"));
        assertFalse(KurumiSkillResultVerifier.confirmed("{}"));
        assertFalse(KurumiSkillResultVerifier.hasVerifiedCompletion("结果：脚本全部完成; 成功=true"));
    }
}
