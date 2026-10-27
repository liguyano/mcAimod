package com.liguyano.mcaimod.client;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.nio.file.Files;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class KurumiSkillMemoryTest {
    @TempDir Path temp;

    @Test void requiresValidFunctionAndPersistsApprovedSkills() {
        var store = new KurumiSkillMemory(temp.resolve("skills.json"));
        var skill = new KurumiSkillMemory.Skill("loot_corpse", "捡尸体", "寻找尸体并取物",
                List.of("捡尸体", "收尸"), "def loot_corpse():\n    return true", 1);
        assertTrue(store.saveApproved(skill));
        assertTrue(Files.exists(temp.resolve("skills.json")));
        assertEquals("loot_corpse", new KurumiSkillMemory(temp.resolve("skills.json"))
                .findExactTrigger("捡尸体").orElseThrow().name());
        assertTrue(store.findExactTrigger("你会捡尸体吗").isEmpty());
        assertTrue(store.titles().contains("捡尸体"));
        assertTrue(store.read("loot_corpse").isPresent());
    }

    @Test void refusesUnsafeBodyAndConflictingShortcut() {
        var store = new KurumiSkillMemory(temp.resolve("skills.json"));
        assertFalse(store.saveApproved(new KurumiSkillMemory.Skill("hack", "非法", "测试",
                List.of("hack"), "def hack():\n    return 1\ntalk(\"extra\")", 1)));
        assertFalse(store.saveApproved(new KurumiSkillMemory.Skill("safe", "冲突", "测试",
                List.of("停止"), "def safe():\n    return 1", 1)));
    }

    @Test void updatesPreservePriorVersionAndRejectTriggerCollisions() throws Exception {
        var store = new KurumiSkillMemory(temp.resolve("skills.json"));
        assertTrue(store.saveApproved(new KurumiSkillMemory.Skill("alpha", "A", "A", List.of("a"),
                "def alpha():\n    return 1", 1)));
        assertFalse(store.saveApproved(new KurumiSkillMemory.Skill("beta", "B", "B", List.of("a"),
                "def beta():\n    return 2", 1)));
        assertTrue(store.saveApproved(new KurumiSkillMemory.Skill("alpha", "A", "A", List.of("a"),
                "def alpha():\n    return 2", 2)));
        assertEquals(2, store.read("alpha").orElseThrow().version());
        assertTrue(Files.readString(temp.resolve("skills.json")).contains("return 1"));
    }

    @Test void deletesExistingSkillOnly() {
        var store = new KurumiSkillMemory(temp.resolve("skills.json"));
        var skill = new KurumiSkillMemory.Skill("loot_corpse", "捡尸体", "寻找尸体并取物",
                List.of("捡尸体"), "def loot_corpse():\n    return true", 1);
        assertTrue(store.saveApproved(skill));
        assertTrue(store.delete("loot_corpse"));
        assertTrue(store.read("loot_corpse").isEmpty());
        assertFalse(store.delete("missing_skill"));
    }

    @Test void onlyAcceptsSkillProposalsInExplicitFinishReplies() {
        String suggestion = "\"suggest_skill\":{\"name\":\"loot_corpse\",\"title\":\"捡尸体\","
                + "\"description\":\"寻找尸体并取物\",\"triggers\":[\"捡尸体\"],"
                + "\"code\":\"def loot_corpse():\\n    return true\"}";
        assertTrue(KurumiSkillMemory.parseProposal("{\"type\":\"finish\",\"reason\":\"已验证\"," + suggestion + "}").isPresent());
        assertTrue(KurumiSkillMemory.parseProposal("{\"type\":\"action\"," + suggestion + "}").isEmpty());
    }

    @Test void supportsCustomGroupsAndImports() {
        var store = new KurumiSkillMemory(temp.resolve("skills.json"));
        var first = new KurumiSkillMemory.Skill("make_sticks", "木棍", "制作木棍", List.of(),
                "def make_sticks(log_id, source, target):\\n    return log_id", 1, "crafting");
        var second = new KurumiSkillMemory.Skill("make_sticks", "其他", "测试默认组", List.of(),
                "def make_sticks():\\n    return 1", 1);
        assertTrue(store.saveApproved(first));
        assertTrue(store.saveApproved(second));
        assertTrue(store.read("crafting", "make_sticks").isPresent());
        assertEquals("skills", store.read("make_sticks").orElseThrow().group());
        assertTrue(store.titles().contains("crafting.make_sticks"));
        String script = store.expandCalls("from crafting import make_sticks\\nmake_sticks(\"minecraft:cherry_log\",pos(1,64,2),pos(3,64,4))");
        assertTrue(script.contains("def make_sticks(log_id, source, target):"));
        assertTrue(KurumiScriptParser.parse(script).isPresent(), KurumiScriptParser.lastError());
        String other = store.expandCalls("import crafting.make_sticks\\ncrafting.make_sticks(\"minecraft:oak_log\",pos(1,64,2),pos(3,64,4))");
        assertTrue(KurumiScriptParser.parse(other).isPresent(), KurumiScriptParser.lastError());
        assertTrue(store.delete("crafting", "make_sticks"));
        assertTrue(store.read("crafting", "make_sticks").isEmpty());
        assertTrue(store.read("skills", "make_sticks").isPresent());
    }
}
