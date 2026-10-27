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
                 String code, int version, String group) {
        Skill(String name, String title, String description, List<String> triggers, String code, int version) {
            this(name, title, description, triggers, code, version, "skills");
        }
    }

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
    synchronized Optional<Skill> read(String name) { return read("skills", name); }
    synchronized Optional<Skill> read(String group, String name) {
        return all().stream().filter(x -> x.group().equals(group) && x.name().equals(name)).findFirst();
    }
    synchronized Optional<Skill> findExactTrigger(String text) {
        String target = text.strip();
        return all().stream().filter(s -> s.triggers().contains(target)).findFirst();
    }
    synchronized String titles() {
        StringBuilder out = new StringBuilder();
        for (Skill s : all()) {
            out.append(s.group()).append(".").append(s.name()).append(" — ").append(s.title())
                    .append("；用途：").append(s.description())
                    .append("；参数：").append(signature(s))
                    .append("；触发词：").append(String.join("、", s.triggers())).append('\n');
        }
        return out.isEmpty() ? "（还没有保存的技能）" : out.toString();
    }
    static String signature(Skill skill) {
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile(
                "(?m)^def\\s+" + java.util.regex.Pattern.quote(skill.name()) + "\\(([^()]*)\\):")
                .matcher(skill.code());
        return matcher.find() ? "(" + matcher.group(1).strip() + ")" : "(未知)";
    }

    /**
     * Expand only exact script lines run_skill("approved_name", ...).
     * The model chooses values; the saved, approved code is loaded locally.
     */
    synchronized String expandCalls(String script) {
        if (script == null || script.length() > 12000)
            throw new IllegalArgumentException("脚本过长");
        java.util.regex.Pattern from = java.util.regex.Pattern.compile(
                "^from ([a-z][a-z0-9_]{0,23}) import ([a-z][a-z0-9_]{0,23}(?:\\s*,\\s*[a-z][a-z0-9_]{0,23})*)$");
        java.util.regex.Pattern single = java.util.regex.Pattern.compile(
                "^import ([a-z][a-z0-9_]{0,23})\\.([a-z][a-z0-9_]{0,23})$");
        java.util.regex.Pattern legacy = java.util.regex.Pattern.compile(
                "^(\\s*)run_skill\\(\\s*\"([a-z][a-z0-9_]{0,23})\"\\s*(?:,\\s*(.*))?\\)\\s*$");
        StringBuilder definitions = new StringBuilder();
        StringBuilder rewritten = new StringBuilder();
        Set<String> names = new HashSet<>();
        java.util.Map<String,String> namespaces = new java.util.HashMap<>();
        for (String line : script.split("\n", -1)) {
            String trimmed = line.strip();
            var multi = from.matcher(trimmed);
            var one = single.matcher(trimmed);
            var old = legacy.matcher(line);
            String group = "skills";
            List<String> imports = null;
            if (multi.matches()) {
                group = multi.group(1);
                imports = java.util.Arrays.stream(multi.group(2).split(","))
                        .map(String::strip).toList();
            } else if (one.matches()) {
                group = one.group(1);
                imports = List.of(one.group(2));
                namespaces.put(group + "." + one.group(2), one.group(2));
            } else if (old.matches()) {
                imports = List.of(old.group(2));
                rewritten.append(old.group(1)).append(old.group(2))
                        .append('(').append(old.group(3) == null ? "" : old.group(3))
                        .append(')').append('\n');
            } else {
                if (trimmed.startsWith("import ") || trimmed.startsWith("from ")
                        || trimmed.startsWith("run_skill("))
                    throw new IllegalArgumentException("只允许从已保存的技能分组 import");
                rewritten.append(line).append('\n');
            }
            if (imports != null) {
                if (!old.matches() && !line.equals(trimmed))
                    throw new IllegalArgumentException("import 必须在脚本顶层");
                final String importGroup = group;
                for (String name : imports) {
                    Skill skill = read(importGroup, name).orElseThrow(() ->
                            new IllegalArgumentException("技能不存在：" + importGroup + "." + name));
                    if (!names.add(name))
                        throw new IllegalArgumentException("重复导入技能名：" + name);
                    if (!valid(skill)) throw new IllegalArgumentException("已保存技能无效：" + name);
                    definitions.append(skill.code()).append('\n');
                }
            }
        }
        if (names.isEmpty()) return script;
        String calls = rewritten.toString();
        for (var entry : namespaces.entrySet())
            calls = calls.replaceAll("(?m)\\b" + java.util.regex.Pattern.quote(entry.getKey())
                    + "\\s*\\(", entry.getValue() + "(");
        String expanded = definitions.append(calls).toString();
        if (expanded.length() > 24000)
            throw new IllegalArgumentException("导入后的技能脚本过长");
        return expanded;
    }

    synchronized JsonObject describe(String name) { return describe("skills", name); }
    synchronized JsonObject describe(String group, String name) {
        JsonObject result = new JsonObject();
        Optional<Skill> skill = read(group, name);
        result.addProperty("found", skill.isPresent());
        skill.ifPresent(s -> {
            result.addProperty("name", s.name());
            result.addProperty("group", s.group());
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
                    proposal.get("code").getAsString(), 1,
                    proposal.has("group") ? proposal.get("group").getAsString() : "skills");
            return valid(skill) ? Optional.of(skill) : Optional.empty();
        } catch (RuntimeException ex) { return Optional.empty(); }
    }

    synchronized boolean saveApproved(Skill candidate) {
        if (!valid(candidate)) return false;
        try {
            JsonObject root = readRoot();
            JsonArray current = root.getAsJsonArray("skills");
            if (current.size() >= MAX_SKILLS && read(candidate.group(), candidate.name()).isEmpty()) return false;
            for (JsonElement element : current) {
                JsonObject entry = element.getAsJsonObject();
                if (entry.get("name").getAsString().equals(candidate.name()) && groupOf(entry).equals(candidate.group())) continue;
                JsonArray triggers = entry.getAsJsonArray("triggers");
                for (JsonElement value : triggers) if (candidate.triggers().contains(value.getAsString())) return false;
            }
            JsonArray updated = new JsonArray();
            boolean replaced = false;
            for (JsonElement element : current) {
                JsonObject old = element.getAsJsonObject();
                if (!old.get("name").getAsString().equals(candidate.name()) || !groupOf(old).equals(candidate.group())) {
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
                        candidate.triggers(), candidate.code(), old.get("version").getAsInt() + 1, candidate.group()), trimmed));
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

    synchronized boolean delete(String name) {
        if (name == null || !name.matches("[a-z][a-z0-9_]{0,23}")) return false;
        try {
            JsonObject root = readRoot();
            JsonArray current = root.getAsJsonArray("skills");
            JsonArray updated = new JsonArray();
            boolean removed = false;
            for (JsonElement element : current) {
                JsonObject entry = element.getAsJsonObject();
                if (name.equals(entry.get("name").getAsString())) removed = true;
                else updated.add(entry);
            }
            if (!removed) return false;
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
            KurumiWebMonitor.log("技能库删除失败：" + ex.getMessage());
            return false;
        }
    }

    private static String groupOf(JsonObject skill) {
        return skill.has("group") ? skill.get("group").getAsString() : "skills";
    }
    private static JsonObject toJson(Skill skill, JsonArray history) {
        JsonObject json = new JsonObject();
        json.addProperty("name", skill.name());
        json.addProperty("group", skill.group());
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
        if (skill == null || skill.group() == null || !skill.group().matches("[a-z][a-z0-9_]{0,23}")
                || skill.name() == null || !skill.name().matches("[a-z][a-z0-9_]{0,23}")
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
                    s.get("code").getAsString(), s.get("version").getAsInt(), groupOf(s)));
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
