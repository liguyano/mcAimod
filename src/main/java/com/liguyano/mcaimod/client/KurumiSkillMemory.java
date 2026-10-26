package com.liguyano.mcaimod.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.GsonBuilder;
import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Owner-approved library of safe, reusable, user-defined script functions. */
final class KurumiSkillMemory {
    record Skill(String name, String title, String description, List<String> triggers,
                 String code, int version) {}

    private static final Set<String> RESERVED = Set.of("停止", "stop", "过来", "睡觉", "tp", "保存", "不保存", "取消");
    private static final int MAX_SKILLS = 128;
    private static final int MAX_BYTES = 2_000_000;
    private final Path path;

    KurumiSkillMemory(Path path) { this.path = path; }
    static KurumiSkillMemory configured() {
        return new KurumiSkillMemory(FMLPaths.CONFIGDIR.get().resolve("mcaimod-skills.json"));
    }

    synchronized List<Skill> all() {
        try { return parseAll(); }
        catch (Exception ex) {
            KurumiWebMonitor.log("技能库读取失败：" + ex.getMessage());
            return List.of();
        }
    }
    synchronized Optional<Skill> read(String name) {
        return all().stream().filter(x -> x.name().equals(name)).findFirst();
    }
    synchronized Optional<Skill> findExactTrigger(String text) {
        String target = text.strip();
        return all().stream().filter(s -> s.triggers().contains(target)).findFirst();
    }
    synchronized String titles() {
        StringBuilder out = new StringBuilder();
        for (Skill s : all()) out.append(s.name()).append(" — ").append(s.title()).append('\n');
        return out.isEmpty() ? "（还没有保存的技能）" : out.toString();
    }
    synchronized JsonObject describe(String name) {
        JsonObject result = new JsonObject();
        Optional<Skill> skill = read(name);
        result.addProperty("found", skill.isPresent());
        skill.ifPresent(s -> {
            result.addProperty("name", s.name());
            result.addProperty("title", s.title());
            result.addProperty("description", s.description());
            result.addProperty("code", s.code());
            result.addProperty("version", s.version());
        });
        return result;
    }

    static Optional<Skill> parseProposal(String raw) {
        try {
            if (!KurumiDecisionParser.isFinish(raw)) return Optional.empty();
            JsonObject json = JsonParser.parseString(raw).getAsJsonObject();
            if (!json.has("suggest_skill") || !json.get("suggest_skill").isJsonObject()) return Optional.empty();
            JsonObject proposal = json.getAsJsonObject("suggest_skill");
            JsonArray keywords = proposal.has("triggers") ? proposal.getAsJsonArray("triggers") : new JsonArray();
            List<String> triggers = new ArrayList<>();
            for (JsonElement el : keywords) triggers.add(el.getAsString());
            Skill skill = new Skill(proposal.get("name").getAsString(), proposal.get("title").getAsString(),
                    proposal.get("description").getAsString(), List.copyOf(triggers),
                    proposal.get("code").getAsString(), 1);
            return valid(skill) ? Optional.of(skill) : Optional.empty();
        } catch (RuntimeException ex) { return Optional.empty(); }
    }

    synchronized boolean saveApproved(Skill candidate) {
        if (!valid(candidate)) return false;
        try {
            JsonObject root = readRoot();
            JsonArray current = root.getAsJsonArray("skills");
            if (current.size() >= MAX_SKILLS && read(candidate.name()).isEmpty()) return false;
            for (JsonElement element : current) {
                JsonObject entry = element.getAsJsonObject();
                if (entry.get("name").getAsString().equals(candidate.name())) continue;
                JsonArray triggers = entry.getAsJsonArray("triggers");
                for (JsonElement value : triggers) if (candidate.triggers().contains(value.getAsString())) return false;
            }
            JsonArray updated = new JsonArray();
            boolean replaced = false;
            for (JsonElement element : current) {
                JsonObject old = element.getAsJsonObject();
                if (!old.get("name").getAsString().equals(candidate.name())) {
                    updated.add(old);
                    continue;
                }
                replaced = true;
                JsonArray history = old.has("history") ? old.getAsJsonArray("history") : new JsonArray();
                JsonObject prior = new JsonObject();
                prior.addProperty("version", old.get("version").getAsInt());
                prior.addProperty("code", old.get("code").getAsString());
                history.add(prior);
                JsonArray trimmed = new JsonArray();
                for (int i = Math.max(0, history.size() - 5); i < history.size(); i++) trimmed.add(history.get(i));
                updated.add(toJson(new Skill(candidate.name(), candidate.title(), candidate.description(),
                        candidate.triggers(), candidate.code(), old.get("version").getAsInt() + 1), trimmed));
            }
            if (!replaced) updated.add(toJson(candidate, new JsonArray()));
            root.add("skills", updated);
            Files.createDirectories(path.getParent());
            Path pending = path.resolveSibling(path.getFileName() + ".tmp");
            Files.writeString(pending, new GsonBuilder().setPrettyPrinting().create().toJson(root), StandardCharsets.UTF_8);
            try { Files.move(pending, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (java.nio.file.AtomicMoveNotSupportedException ex) {
                Files.move(pending, path, StandardCopyOption.REPLACE_EXISTING);
            }
            return true;
        } catch (Exception ex) {
            KurumiWebMonitor.log("技能库保存失败：" + ex.getMessage());
            return false;
        }
    }

    private static JsonObject toJson(Skill skill, JsonArray history) {
        JsonObject json = new JsonObject();
        json.addProperty("name", skill.name());
        json.addProperty("title", skill.title());
        json.addProperty("description", skill.description());
        json.addProperty("code", skill.code());
        json.addProperty("version", skill.version());
        JsonArray triggers = new JsonArray();
        for (String trigger : skill.triggers()) triggers.add(trigger);
        json.add("triggers", triggers);
        json.add("history", history);
        return json;
    }
    private static boolean valid(Skill skill) {
        if (skill == null || skill.name() == null || !skill.name().matches("[a-z][a-z0-9_]{0,23}")
                || skill.title() == null || skill.title().isBlank() || skill.title().length() > 48
                || skill.description() == null || skill.description().isBlank() || skill.description().length() > 240
                || skill.code() == null || skill.code().length() > 6000 || skill.triggers() == null
                || skill.triggers().size() > 8 || skill.version() < 1) return false;
        Set<String> unique = new HashSet<>();
        for (String trigger : skill.triggers()) {
            if (trigger == null || trigger.isBlank() || trigger.length() > 28
                    || !trigger.equals(trigger.strip()) || RESERVED.contains(trigger)
                    || !unique.add(trigger)) return false;
        }
        var parsed = KurumiScriptParser.parse(skill.code());
        return parsed.isPresent() && !parsed.get().statements().isEmpty()
                && parsed.get().statements().stream().allMatch(s -> "DEF".equals(s.op()))
                && parsed.get().statements().stream().anyMatch(s -> skill.name().equals(s.step().target()));
    }
    private List<Skill> parseAll() throws IOException {
        JsonObject root = readRoot();
        List<Skill> skills = new ArrayList<>();
        for (JsonElement el : root.getAsJsonArray("skills")) {
            JsonObject s = el.getAsJsonObject();
            List<String> triggers = new ArrayList<>();
            for (JsonElement trigger : s.getAsJsonArray("triggers")) triggers.add(trigger.getAsString());
            skills.add(new Skill(s.get("name").getAsString(), s.get("title").getAsString(),
                    s.get("description").getAsString(), List.copyOf(triggers),
                    s.get("code").getAsString(), s.get("version").getAsInt()));
        }
        return List.copyOf(skills);
    }
    private JsonObject readRoot() throws IOException {
        if (Files.notExists(path)) {
            JsonObject empty = new JsonObject();
            empty.add("skills", new JsonArray());
            return empty;
        }
        if (Files.size(path) > MAX_BYTES) throw new IOException("技能库超过2MB");
        JsonObject root = JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8)).getAsJsonObject();
        if (!root.has("skills") || !root.get("skills").isJsonArray()
                || root.getAsJsonArray("skills").size() > MAX_SKILLS)
            throw new IOException("技能库结构无效");
        return root;
    }
}
