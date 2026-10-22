package com.liguyano.mcaimod.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Restricted indentation-based Python-like syntax. Never executes arbitrary code. */
public final class KurumiScriptParser {
    private static final Pattern CALL = Pattern.compile("(?:(result)\\s*=\\s*)?([a-z_]+)\\(\"([A-Za-z0-9_.:-]+)\"\\)");
    private static final Pattern COUNT = Pattern.compile("(check_chest|check_inventory)\\(\"([a-z0-9_.:-]+)\"\\)\\s*(<=|>=|==|!=|<|>)\\s*(\\d+)");
    private static final Pattern RESULT = Pattern.compile("result\\.success");
    private static final Pattern FARM = Pattern.compile("(?:(result)\\s*=\\s*)?farm\\((\\d{1,2})\\)");
    private static final Pattern SLEEP = Pattern.compile("(?:(result)\\s*=\\s*)?sleep\\(\\)");
    private static final Pattern TALK = Pattern.compile("talk\\(\"([^\"\\r\\n]{1,120})\"\\)");

    private static final Pattern ZERO = Pattern.compile("(stop|tpa_request|tpa_accept|craft_open|chest_open|chest_put_all|chest_take_all|drop_all|pickup_all)\\(\\)");
    private static final Pattern ONE = Pattern.compile("(tpa_request_to|drop_item_all|pickup_item_all|chest_put_item_all|equip_main_hand|equip_armor)\\(\"([A-Za-z0-9_.:-]+)\"\\)");
    private static final Pattern TWO = Pattern.compile("(drop|pickup|chest_take|chest_put|craft_item|collect_and_store)\\(\"([a-z0-9_.:-]+)\",\\s*(\\d{1,2})\\)");
    private static final Pattern INTERACT = Pattern.compile("interact\\(\"([a-z0-9_.:-]+)\"\\)");
    private static final Pattern TAKE_ITEM = Pattern.compile("take_item\\(\"([a-z0-9_.:-]+)\",\\s*(\\d{1,2})\\)");
    private static final Pattern PUT_ITEM = Pattern.compile("put_item\\(\"([a-z0-9_.:-]+)\",\\s*(\\d{1,2})(?:,\\s*\"(input|fuel|auto)\")?\\)");
    private static final Pattern GOTO_NAMED = Pattern.compile("goto\\(\"([^\"]{1,24})\"\\)");
    private static final Pattern GOTO = Pattern.compile("goto\\((-?\\d{1,8}),\\s*(-?\\d{1,4}),\\s*(-?\\d{1,8})\\)");
    private static final Pattern RETURN = Pattern.compile("return\\s+\"[^\"\\r\\n]{0,100}\"");
    private record Line(int indent, String code, int number) {}
    private static final class Cursor { int at; }
    public static Optional<KurumiTaskPlanner.Program> parse(String source) {
        if (source == null || source.length() > 8192) return Optional.empty();
        try {
            var lines = new java.util.ArrayList<Line>();
            int sourceLine = 0;
            for (String raw : source.replace("\r", "").split("\n")) {
                sourceLine++;
                if (raw.isBlank() || raw.stripLeading().startsWith("#")) continue;
                int n = 0;
                while (n < raw.length() && raw.charAt(n) == ' ') n++;
                if (n % 4 != 0 || raw.indexOf('\t') >= 0) return Optional.empty();
                lines.add(new Line(n / 4, raw.substring(n).stripTrailing(), sourceLine));
            }
            if (lines.isEmpty() || lines.size() > 48 || lines.get(0).indent() != 0)
                return Optional.empty();
            Cursor cursor = new Cursor();
            JsonArray body = block(lines, cursor, 0, 0);
            if (cursor.at != lines.size()) return Optional.empty();
            JsonObject obj = new JsonObject();
            obj.add("program", body);
            var parsed = KurumiTaskPlanner.parseProgram(obj);
            parsed.ifPresent(program -> KurumiWebMonitor.register(program, source));
            return parsed;
        } catch (RuntimeException e) { return Optional.empty(); }
    }
    private static JsonArray block(List<Line> lines, Cursor cursor, int indent, int depth) {
        if (depth > 3) throw new IllegalArgumentException("nesting");
        JsonArray out = new JsonArray();
        while (cursor.at < lines.size() && lines.get(cursor.at).indent() == indent) {
            String code = lines.get(cursor.at++).code();
            JsonObject node = new JsonObject();
            node.addProperty("line", lines.get(cursor.at - 1).number());
            if (code.startsWith("while ") && code.endsWith(":")
                    || code.startsWith("if ") && code.endsWith(":")) {
                boolean loop = code.startsWith("while ");
                node.addProperty("op", loop ? "WHILE" : "IF");
                String condition = code.substring(loop ? 6 : 3, code.length() - 1).strip();
                node.add("condition", condition(condition));
                if (cursor.at >= lines.size() || lines.get(cursor.at).indent() != indent + 1)
                    throw new IllegalArgumentException("empty body");
                node.add("body", block(lines, cursor, indent + 1, depth + 1));
                if (!loop && cursor.at < lines.size()
                        && lines.get(cursor.at).indent() == indent
                        && lines.get(cursor.at).code().equals("else:")) {
                    cursor.at++;
                    if (cursor.at >= lines.size() || lines.get(cursor.at).indent() != indent + 1)
                        throw new IllegalArgumentException("empty else");
                    node.add("else", block(lines, cursor, indent + 1, depth + 1));
                }
            } else if (FARM.matcher(code).matches()) {
                Matcher farm = FARM.matcher(code);
                farm.matches();
                int radius = Integer.parseInt(farm.group(2));
                if (radius < 4 || radius > 32) throw new IllegalArgumentException("farm radius");
                node.addProperty("op", farm.group(1) != null ? "FARM_RESULT" : "FARM");
                node.addProperty("radius", radius);
            } else if (code.equals("chain_on()") || code.equals("chain_off()")) {
                node.addProperty("op", "CHAIN");
                node.addProperty("enabled", code.equals("chain_on()"));
            } else if (INTERACT.matcher(code).matches() || PUT_ITEM.matcher(code).matches() || TAKE_ITEM.matcher(code).matches()) {
                JsonObject step = new JsonObject();
                if (INTERACT.matcher(code).matches()) {
                    Matcher m = INTERACT.matcher(code); m.matches();
                    step.addProperty("action", "INTERACT");
                    step.addProperty("item_id", m.group(1));
                } else if (TAKE_ITEM.matcher(code).matches()) {
                    Matcher m = TAKE_ITEM.matcher(code); m.matches();
                    step.addProperty("action", "TAKE_ITEM");
                    step.addProperty("item_id", m.group(1));
                    step.addProperty("quantity", Integer.parseInt(m.group(2)));
                } else {
                    Matcher m = PUT_ITEM.matcher(code); m.matches();
                    step.addProperty("action", "PUT_ITEM");
                    step.addProperty("item_id", m.group(1));
                    step.addProperty("quantity", Integer.parseInt(m.group(2)));
                    step.addProperty("target", m.group(3) == null ? "auto" : m.group(3));
                }
                node.addProperty("op", "ACTION");
                node.add("step", step);
            } else if (GOTO_NAMED.matcher(code).matches()) {
                Matcher named = GOTO_NAMED.matcher(code);
                named.matches();
                JsonObject step = new JsonObject();
                step.addProperty("action", "GOTO_NAMED");
                step.addProperty("target", named.group(1));
                node.addProperty("op", "ACTION");
                node.add("step", step);
            } else if (ZERO.matcher(code).matches() || ONE.matcher(code).matches()
                    || TWO.matcher(code).matches() || GOTO.matcher(code).matches()) {
                JsonObject step = new JsonObject();
                if (ZERO.matcher(code).matches()) {
                    Matcher m = ZERO.matcher(code); m.matches();
                    step.addProperty("action", m.group(1).toUpperCase(java.util.Locale.ROOT));
                } else if (ONE.matcher(code).matches()) {
                    Matcher m = ONE.matcher(code); m.matches();
                    step.addProperty("action", m.group(1).toUpperCase(java.util.Locale.ROOT));
                    step.addProperty(m.group(1).equals("tpa_request_to") ? "target" : "item_id", m.group(2));
                } else if (TWO.matcher(code).matches()) {
                    Matcher m = TWO.matcher(code); m.matches();
                    step.addProperty("action", m.group(1).toUpperCase(java.util.Locale.ROOT));
                    step.addProperty("item_id", m.group(2));
                    step.addProperty("quantity", Integer.parseInt(m.group(3)));
                } else {
                    Matcher m = GOTO.matcher(code); m.matches();
                    step.addProperty("action", "GOTO");
                    step.addProperty("x", Integer.parseInt(m.group(1)));
                    step.addProperty("y", Integer.parseInt(m.group(2)));
                    step.addProperty("z", Integer.parseInt(m.group(3)));
                }
                node.addProperty("op", "ACTION");
                node.add("step", step);
            } else if (SLEEP.matcher(code).matches()) {
                node.addProperty("op", code.startsWith("result") ? "SLEEP_RESULT" : "SLEEP");
            } else if (TALK.matcher(code).matches()) {
                node.addProperty("op", "TALK");
                node.addProperty("message", TALK.matcher(code).replaceFirst("$1"));
            } else if (code.equals("break")) {
                node.addProperty("op", "BREAK");
            } else if (RETURN.matcher(code).matches()) {
                node.addProperty("op", "RETURN");
            } else {
                Matcher m = CALL.matcher(code);
                if (!m.matches()) throw new IllegalArgumentException("unknown command");
                String action = switch (m.group(2)) {
                    case "attack_mob" -> "ATTACK_MOB";
                    case "attack_player" -> "ATTACK_PLAYER";
                    case "pickup_item_all" -> "PICKUP_ITEM_ALL";
                    case "chest_put_item_all" -> "CHEST_PUT_ITEM_ALL";
                    case "equip_armor" -> "EQUIP_ARMOR";
                    case "equip_main_hand" -> "EQUIP_MAIN_HAND";
                    case "mine" -> "MINE";
                    case "come" -> "COME";
                    case "follow" -> "FOLLOW";
                    default -> throw new IllegalArgumentException("unsupported action");
                };
                node.addProperty("op", m.group(1) != null ? "ACTION_RESULT" : "ACTION");
                JsonObject step = new JsonObject();
                step.addProperty("action", action);
                step.addProperty("ATTACK_MOB".equals(action) ? "entity_id"
                        : "ATTACK_PLAYER".equals(action) || "COME".equals(action) || "FOLLOW".equals(action) ? "target" : "item_id", m.group(3));
                node.add("step", step);
            }
            out.add(node);
        }
        return out;
    }
    private static JsonObject condition(String expression) {
        JsonObject node = new JsonObject();
        if (RESULT.matcher(expression).matches() || expression.equals("not result.success")) {
            node.addProperty("source", "result");
            node.addProperty("operator", expression.startsWith("not ") ? "LT" : "GE");
            return node;
        }
        Matcher m = COUNT.matcher(expression);
        if (!m.matches()) throw new IllegalArgumentException("unsupported condition");
        node.addProperty("source", m.group(1).equals("check_chest") ? "chest" : "inventory");
        node.addProperty("item_id", m.group(2));
        node.addProperty("operator", switch (m.group(3)) {
            case "<" -> "LT"; case "<=" -> "LE"; case "==" -> "EQ";
            case "!=" -> "NE"; case ">=" -> "GE"; case ">" -> "GT";
            default -> throw new IllegalArgumentException();
        });
        node.addProperty("count", Integer.parseInt(m.group(4)));
        return node;
    }
    private KurumiScriptParser() {}
}
