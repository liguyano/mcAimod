package com.liguyano.mcaimod.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Restricted indentation-based Python-like syntax. Never executes arbitrary code. */
public final class KurumiScriptParser {
    private static final Pattern CALL = Pattern.compile("(?:(result)\\s*=\\s*)?([a-z_]+)\\(\"([a-z0-9_.:-]+)\"\\)");
    private static final Pattern COUNT = Pattern.compile("(check_chest|check_inventory)\\(\"([a-z0-9_.:-]+)\"\\)\\s*(<=|>=|==|!=|<|>)\\s*(\\d+)");
    private static final Pattern RESULT = Pattern.compile("result\\.success");
    private static final Pattern RETURN = Pattern.compile("return\\s+\"[^\"\\r\\n]{0,100}\"");
    private record Line(int indent, String code) {}
    private static final class Cursor { int at; }
    public static Optional<KurumiTaskPlanner.Program> parse(String source) {
        if (source == null || source.length() > 8192) return Optional.empty();
        try {
            var lines = new java.util.ArrayList<Line>();
            for (String raw : source.replace("\r", "").split("\n")) {
                if (raw.isBlank() || raw.stripLeading().startsWith("#")) continue;
                int n = 0;
                while (n < raw.length() && raw.charAt(n) == ' ') n++;
                if (n % 4 != 0 || raw.indexOf('\t') >= 0) return Optional.empty();
                lines.add(new Line(n / 4, raw.substring(n).stripTrailing()));
            }
            if (lines.isEmpty() || lines.size() > 48 || lines.get(0).indent() != 0)
                return Optional.empty();
            Cursor cursor = new Cursor();
            JsonArray body = block(lines, cursor, 0, 0);
            if (cursor.at != lines.size()) return Optional.empty();
            JsonObject obj = new JsonObject();
            obj.add("program", body);
            return KurumiTaskPlanner.parseProgram(obj);
        } catch (RuntimeException e) { return Optional.empty(); }
    }
    private static JsonArray block(List<Line> lines, Cursor cursor, int indent, int depth) {
        if (depth > 3) throw new IllegalArgumentException("nesting");
        JsonArray out = new JsonArray();
        while (cursor.at < lines.size() && lines.get(cursor.at).indent() == indent) {
            String code = lines.get(cursor.at++).code();
            JsonObject node = new JsonObject();
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
                    default -> throw new IllegalArgumentException("unsupported action");
                };
                node.addProperty("op", m.group(1) != null ? "ACTION_RESULT" : "ACTION");
                JsonObject step = new JsonObject();
                step.addProperty("action", action);
                step.addProperty("ATTACK_MOB".equals(action) ? "entity_id"
                        : "ATTACK_PLAYER".equals(action) ? "target" : "item_id", m.group(3));
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
            node.addProperty("operator", expression.startsWith("not ") ? "EQ" : "GE");
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
