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
    private static final Pattern ASSIGN = Pattern.compile("([a-z][a-z0-9_]{0,23})\\s*=\\s*(\\d{1,4})");
    private static final Pattern COUNT_VAR = Pattern.compile("(check_chest|check_inventory)\\(\"([a-z0-9_.:-]+)\"\\)\\s*(<=|>=|==|!=|<|>)\\s*([a-z][a-z0-9_]{0,23})");
    private static final Pattern DISTANCE = Pattern.compile("check_distance\\(\"(block|entity|player|item)\",\\s*\"([A-Za-z0-9_.:-]+)\"\\)\\s*(<=|>=|==|!=|<|>)\\s*(\\d+)");
    private static final Pattern DISTANCE_VAR = Pattern.compile("check_distance\\(\"(block|entity|player|item)\",\\s*\"([A-Za-z0-9_.:-]+)\"\\)\\s*(<=|>=|==|!=|<|>)\\s*([a-z][a-z0-9_]{0,23})");
    private static final Pattern RESULT = Pattern.compile("result\\.success");
    private static final Pattern SLEEP = Pattern.compile("sleep\\((\\d{1,3})\\)");
    private static final Pattern FARM = Pattern.compile("(?:(result)\\s*=\\s*)?farm\\((\\d{1,2})\\)");
    // Read-only observations will be exposed through the script interpreter.
    private static final Pattern OBSERVE = Pattern.compile("talk\\((get_player_status|get_position|get_environment|scan_nearby_entities|scan_nearby_blocks|scan_dropped_items|get_inventory|inspect_container)\\(\\)\\)");
    private static final Pattern OBSERVE_ARG = Pattern.compile("talk\\((get_distance|find_nearest_block|check_inventory_count|get_crafting_recipe|check_nearby_chest|get_nearby_chest_result)\\(\\\"([A-Za-z0-9_.:-]+)\\\"(?:,\\s*\\\"([A-Za-z0-9_.:-]+)\\\")?\\)\\)");
    private static final Pattern INSPECT_SLOT = Pattern.compile("talk\\(inspect_slot\\((\\d{1,3})\\)\\)");
    private static final Pattern OBSERVE_POSITION = Pattern.compile("talk\\(check_position\\(\\s*(-?\\d{1,8})\\s*,\\s*(-?\\d{1,4})\\s*,\\s*(-?\\d{1,8})\\s*,\\s*(\\d{1,2})\\s*\\)\\)");
    private static final Pattern TALK = Pattern.compile("talk\\(\"([^\"\\r\\n]{1,120})\"\\)");

    private static final Pattern ZERO = Pattern.compile("(stop|sleep_in_bed|tpa_request|tpa_accept|craft_open|chest_open|chest_put_all|chest_take_all|take_all_items|drop_all|pickup_all)\\(\\)");
    private static final Pattern ONE = Pattern.compile("(tpa_request_to|drop_item_all|pickup_item_all|chest_put_item_all|equip_main_hand|equip_armor)\\(\"([A-Za-z0-9_.:-]+)\"\\)");
    private static final Pattern TWO = Pattern.compile("(drop|pickup|chest_take|chest_put|craft_item|collect_and_store)\\(\"([a-z0-9_.:-]+)\",\\s*(\\d{1,2})\\)");
    private static final Pattern PLACE_BLOCK = Pattern.compile("place_block\\(\"([a-z0-9_.:-]+)\"(?:,\\s*(-?\\d{1,8}),\\s*(-?\\d{1,4}),\\s*(-?\\d{1,8}))?\\)");
    private static final Pattern INTERACT = Pattern.compile("interact\\(\"([a-z0-9_.:-]+)\"\\)");
    private static final Pattern TAKE_ITEM = Pattern.compile("take_item\\(\"([a-z0-9_.:-]+)\",\\s*(\\d{1,2})\\)");
    private static final Pattern PUT_ITEM = Pattern.compile("put_item\\(\"([a-z0-9_.:-]+)\",\\s*(\\d{1,2})(?:,\\s*\"(input|fuel|auto|grid_[1-9])\")?\\)");
    private static final Pattern MOVE = Pattern.compile("move\\(\\\"(forward|back|left|right)\\\",\\s*(\\d{1,2})\\)");
    private static final Pattern VARIABLE_ASSIGN = Pattern.compile("([a-z][a-z0-9_]{0,23})\\s*=\\s*(.+)");
    private static final Pattern ACTION_ASSIGN = Pattern.compile("([a-z][a-z0-9_]{0,23})\\s*=\\s*(goto|move|look|look_at|interact|use_item|hold_use_item|equip_main_hand|place_block|break_block|search_chests|take_from_memory)\\((.*)\\)");
    private static final Pattern VARIABLE_CALL = Pattern.compile("(goto|talk|observe|fish|stop_fishing|sleep|move|look|look_at|interact|use_item|hold_use_item|equip_main_hand|place_block|break_block|search_chests|take_from_memory)\\((.*)\\)");
    private static final Pattern GOTO_NAMED = Pattern.compile("goto\\(\"([^\"]{1,24})\"\\)");
    private static final Pattern GOTO = Pattern.compile("goto\\((-?\\d{1,8}),\\s*(-?\\d{1,4}),\\s*(-?\\d{1,8})\\)");
    private static final Pattern RETURN = Pattern.compile("return\\s+\"[^\"\\r\\n]{0,100}\"");
    private static final ThreadLocal<String> LAST_ERROR = ThreadLocal.withInitial(() -> "");
    public static String lastError() { return LAST_ERROR.get(); }
    /** Dynamic arguments may be variables, but a hold always needs two nonempty arguments. */
    private static boolean validHoldArgs(String args) {
        return args != null && args.matches("(?s)\\s*\\S[^,]*,\\s*\\S[^,]*\\s*");
    }
    private record Line(int indent, String code, int number) {}
    private static final class Cursor { int at; }
    public static Optional<KurumiTaskPlanner.Program> parse(String source) {
        LAST_ERROR.set("");
        if (source == null || source.length() > 8192) {
            LAST_ERROR.set("脚本为空或超过8192字");
            return Optional.empty();
        }
        try {
            var lines = new java.util.ArrayList<Line>();
            int sourceLine = 0;
            for (String raw : source.replace("\r", "").split("\n")) {
                sourceLine++;
                if (raw.isBlank() || raw.stripLeading().startsWith("#")) continue;
                int n = 0;
                while (n < raw.length() && raw.charAt(n) == ' ') n++;
                if (n % 4 != 0 || raw.indexOf('\t') >= 0) {
                    LAST_ERROR.set("第 " + sourceLine + " 行缩进错误：只能使用4个空格，禁止Tab");
                    return Optional.empty();
                }
                String code = raw.substring(n).stripTrailing();
                lines.add(new Line(n / 4, code, sourceLine));
            }
            if (lines.isEmpty() || lines.size() > 200 || lines.get(0).indent() != 0) {
                LAST_ERROR.set("脚本为空、超过200条或首行缩进不为零");
                return Optional.empty();
            }
            Cursor cursor = new Cursor();
            JsonArray body = block(lines, cursor, 0, 0);
            if (cursor.at != lines.size()) {
                LAST_ERROR.set("第 " + lines.get(cursor.at).number() + " 行缩进或结构错误");
                return Optional.empty();
            }
            JsonObject obj = new JsonObject();
            obj.add("program", body);
            var parsed = KurumiTaskPlanner.parseProgram(obj);
            if (parsed.isEmpty()) LAST_ERROR.set("指令结构校验未通过，请检查动作参数及条件");
            parsed.ifPresent(program -> KurumiWebMonitor.register(program, source));
            return parsed;
        } catch (RuntimeException e) {
            LAST_ERROR.set(e.getMessage() == null ? "未知解析错误" : e.getMessage());
            return Optional.empty();
        }
    }
    private static JsonArray block(List<Line> lines, Cursor cursor, int indent, int depth) {
        if (depth > 3) throw new IllegalArgumentException("nesting");
        JsonArray out = new JsonArray();
        while (cursor.at < lines.size() && lines.get(cursor.at).indent() == indent) {
            String code = lines.get(cursor.at++).code();
            JsonObject node = new JsonObject();
            node.addProperty("line", lines.get(cursor.at - 1).number());
            if (code.matches("repeat\\([1-9][0-9]?\\):")) {
                int repetitions = Integer.parseInt(code.substring(7, code.length() - 2));
                if (repetitions > 20) throw new IllegalArgumentException("repeat count");
                if (cursor.at >= lines.size() || lines.get(cursor.at).indent() != indent + 1)
                    throw new IllegalArgumentException("empty repeat");
                JsonArray body = block(lines, cursor, indent + 1, depth + 1);
                for (int i = 0; i < repetitions; i++) {
                    for (var element : body) {
                        if (out.size() >= 200) throw new IllegalArgumentException("expanded repeat too large");
                        out.add(element.deepCopy());
                    }
                }
                continue;
            }
            if (code.startsWith("while ") && code.endsWith(":")
                    || code.startsWith("if ") && code.endsWith(":")) {
                boolean loop = code.startsWith("while ");
                node.addProperty("op", loop ? "WHILE" : "IF");
                String condition = code.substring(loop ? 6 : 3, code.length() - 1).strip();
                boolean dynamic = !condition.matches("(?s)(check_inventory|check_chest|check_distance)\\(.*")
                        && !condition.equals("result.success") && !condition.equals("not result.success");
                if (dynamic || condition.matches("(?s).*\\)\\s*(<=|>=|==|!=|<|>)\\s*[a-z][a-z0-9_]*")) {
                    node.addProperty("op", loop ? "EXPR_WHILE" : "EXPR_IF");
                    node.addProperty("expression", condition);
                } else node.add("condition", condition(condition));
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
            } else if (ACTION_ASSIGN.matcher(code).matches()) {
                Matcher m = ACTION_ASSIGN.matcher(code); m.matches();
                if ("hold_use_item".equals(m.group(2)) && !validHoldArgs(m.group(3)))
                    throw new IllegalArgumentException("hold_use_item requires item ID and seconds");
                node.addProperty("op", "EXPR_ACTION_ASSIGN");
                node.addProperty("name", m.group(1));
                node.addProperty("method", m.group(2));
                node.addProperty("expression", m.group(3));
            } else if (VARIABLE_ASSIGN.matcher(code).matches() && !code.startsWith("result = ")) {
                Matcher m = VARIABLE_ASSIGN.matcher(code); m.matches();

                node.addProperty("op", "EXPR_ASSIGN");
                node.addProperty("name", m.group(1));
                node.addProperty("expression", m.group(2));
            } else if (SLEEP.matcher(code).matches()) {
                Matcher sleep = SLEEP.matcher(code); sleep.matches();
                int seconds = Integer.parseInt(sleep.group(1));
                if (seconds < 1 || seconds > 120) throw new IllegalArgumentException("sleep seconds");
                node.addProperty("op", "SLEEP");
                node.addProperty("seconds", seconds);
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
            } else if (PLACE_BLOCK.matcher(code).matches()) {
                Matcher m = PLACE_BLOCK.matcher(code); m.matches();
                JsonObject step = new JsonObject();
                step.addProperty("action", "PLACE_BLOCK");
                step.addProperty("item_id", m.group(1));
                if (m.group(2) != null) {
                    step.addProperty("x", Integer.parseInt(m.group(2)));
                    step.addProperty("y", Integer.parseInt(m.group(3)));
                    step.addProperty("z", Integer.parseInt(m.group(4)));
                }
                node.addProperty("op", "ACTION");
                node.add("step", step);
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
            } else if (MOVE.matcher(code).matches()) {
                Matcher move = MOVE.matcher(code); move.matches();
                JsonObject step = new JsonObject();
                step.addProperty("action", "MOVE");
                step.addProperty("target", move.group(1));
                step.addProperty("quantity", Integer.parseInt(move.group(2)));
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
            } else if (OBSERVE_POSITION.matcher(code).matches()) {
                Matcher m = OBSERVE_POSITION.matcher(code);
                m.matches();
                int x = Integer.parseInt(m.group(1)), y = Integer.parseInt(m.group(2));
                int z = Integer.parseInt(m.group(3)), radius = Integer.parseInt(m.group(4));
                if (y < -64 || y > 320 || radius < 1 || radius > 32
                        || Math.abs((long)x) > 30000000 || Math.abs((long)z) > 30000000)
                    throw new IllegalArgumentException("invalid position");
                node.addProperty("op", "OBSERVE_TALK");
                node.addProperty("tool", "check_position:" + x + ":" + y + ":" + z + ":" + radius);
            } else if (INSPECT_SLOT.matcher(code).matches()) {
                Matcher slot = INSPECT_SLOT.matcher(code); slot.matches();
                int index = Integer.parseInt(slot.group(1));
                if (index > 255) throw new IllegalArgumentException("slot index");
                node.addProperty("op", "OBSERVE_TALK");
                node.addProperty("tool", "inspect_slot:" + index);
            } else if (OBSERVE.matcher(code).matches()) {
                Matcher m = OBSERVE.matcher(code);
                m.matches();
                node.addProperty("op", "OBSERVE_TALK");
                node.addProperty("tool", m.group(1));
            } else if (OBSERVE_ARG.matcher(code).matches()) {
                Matcher m = OBSERVE_ARG.matcher(code);
                m.matches();
                String method = m.group(1), first = m.group(2), second = m.group(3);
                String tool;
                if ("get_distance".equals(method)) {
                    if (second == null || !java.util.Set.of("block", "entity", "player", "item").contains(first))
                        throw new IllegalArgumentException("invalid distance arguments");
                    if ("player".equals(first) ? !second.matches("[A-Za-z0-9_]{1,16}")
                            : !second.matches("[a-z0-9_.-]+:[a-z0-9_./-]{1,128}"))
                        throw new IllegalArgumentException("invalid distance target");
                    tool = method + ":" + first + ":" + second;
                } else {
                    if (second != null || !first.matches("[a-z0-9_.-]+:[a-z0-9_./-]{1,128}"))
                        throw new IllegalArgumentException("invalid observation target");
                    tool = method + ":" + first;
                }
                node.addProperty("op", "OBSERVE_TALK");
                node.addProperty("tool", tool);
            } else if (VARIABLE_CALL.matcher(code).matches()) {
                Matcher m = VARIABLE_CALL.matcher(code); m.matches();
                if ("hold_use_item".equals(m.group(1)) && !validHoldArgs(m.group(2)))
                    throw new IllegalArgumentException("hold_use_item requires item ID and seconds");
                node.addProperty("op", "EXPR_CALL");
                node.addProperty("method", m.group(1));
                node.addProperty("expression", m.group(2));
            } else if (TALK.matcher(code).matches()) {
                node.addProperty("op", "TALK");
                node.addProperty("message", TALK.matcher(code).replaceFirst("$1"));
            } else if (code.equals("break")) {
                node.addProperty("op", "BREAK");
            } else if (code.equals("return") || RETURN.matcher(code).matches()) {
                node.addProperty("op", "RETURN");
            } else {
                Matcher m = CALL.matcher(code);
                if (!m.matches()) throw new IllegalArgumentException("第 " + lines.get(cursor.at - 1).number() + " 行不支持的语句：" + code.substring(0, Math.min(code.length(), 100)));
                String action = switch (m.group(2)) {
                    case "attack" -> m.group(3).contains(":") ? "ATTACK_MOB" : "ATTACK_PLAYER";
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
        Matcher distance = DISTANCE.matcher(expression);
        if (distance.matches()) {
            String kind = distance.group(1), target = distance.group(2);
            if ("player".equals(kind) ? !target.matches("[A-Za-z0-9_]{1,16}")
                    : !target.matches("[a-z0-9_.-]+:[a-z0-9_./-]{1,128}"))
                throw new IllegalArgumentException("invalid distance target");
            node.addProperty("source", "distance");
            node.addProperty("item_id", kind + ":" + target);
            node.addProperty("operator", switch (distance.group(3)) {
                case "<" -> "LT"; case "<=" -> "LE"; case "==" -> "EQ";
                case "!=" -> "NE"; case ">=" -> "GE"; case ">" -> "GT";
                default -> throw new IllegalArgumentException();
            });
            node.addProperty("count", Integer.parseInt(distance.group(4)));
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
