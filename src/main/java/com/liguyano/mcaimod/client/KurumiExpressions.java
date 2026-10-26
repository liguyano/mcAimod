package com.liguyano.mcaimod.client;

import com.google.gson.*;
import java.util.Map;

/** Bounded expression evaluator for the pseudocode DSL, not a Python/Java interpreter. */
public final class KurumiExpressions {
    private final String input;
    private final Map<String, JsonElement> variables;
    private int at;
    private int operations;
    private KurumiExpressions(String input, Map<String, JsonElement> variables) {
        this.input = input; this.variables = variables;
    }
    public static JsonElement evaluate(String input, Map<String, JsonElement> variables) {
        if (input == null || input.length() > 500) throw new IllegalArgumentException("表达式太长");
        var parser = new KurumiExpressions(input, variables);
        JsonElement value = parser.or();
        parser.spaces();
        if (parser.at != input.length()) throw new IllegalArgumentException("表达式包含不支持的内容");
        return value;
    }
    /** Split expression arguments without treating commas inside strings or nested calls as separators. */
    public static java.util.List<JsonElement> arguments(String source, Map<String, JsonElement> vars) {
        java.util.List<JsonElement> values = new java.util.ArrayList<>();
        if (source.isBlank()) return values;
        int depth = 0, start = 0;
        boolean quoted = false;
        for (int i = 0; i <= source.length(); i++) {
            if (i == source.length() || (source.charAt(i) == ',' && depth == 0 && !quoted)) {
                if (values.size() >= 6 || start == i) throw new IllegalArgumentException("参数数量或格式错误");
                values.add(evaluate(source.substring(start, i), vars));
                start = i + 1;
                continue;
            }
            char c = source.charAt(i);
            if (c == '"') quoted = !quoted;
            else if (!quoted && c == '(') depth++;
            else if (!quoted && c == ')') depth--;
            if (depth < 0 || depth > 6) throw new IllegalArgumentException("表达式括号不匹配");
        }
        if (quoted || depth != 0) throw new IllegalArgumentException("表达式括号或引号不匹配");
        return values;
    }
    public static String text(JsonElement element) {
        if (element == null || element.isJsonNull()) return "null";
        if (element.isJsonPrimitive()) return element.getAsString();
        return element.toString();
    }
    public static boolean truth(JsonElement element) {
        if (element == null || element.isJsonNull()) return false;
        if (!element.isJsonPrimitive()) return true;
        JsonPrimitive p = element.getAsJsonPrimitive();
        if (p.isBoolean()) return p.getAsBoolean();
        if (p.isNumber()) return p.getAsDouble() != 0;
        return !p.getAsString().isEmpty();
    }
    public static int integer(JsonElement element) {
        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber())
            throw new IllegalArgumentException("参数必须为整数");
        double d = element.getAsDouble();
        if (!Double.isFinite(d) || d != Math.rint(d) || Math.abs(d) > 30000000)
            throw new IllegalArgumentException("参数必须为有限整数");
        return (int)d;
    }
    private void spaces() { while (at < input.length() && Character.isWhitespace(input.charAt(at))) at++; }
    private boolean take(String token) {
        spaces();
        if (input.startsWith(token, at)) { at += token.length(); return true; }
        return false;
    }
    private boolean keyword(String word) {
        spaces();
        if (input.startsWith(word,at) && (at + word.length() == input.length()
                || !(Character.isLetterOrDigit(input.charAt(at + word.length()))
                || input.charAt(at + word.length()) == '_'))) {
            at += word.length(); return true;
        }
        return false;
    }
    private void expect(String symbol) {
        if (!take(symbol)) throw new IllegalArgumentException("缺少 " + symbol);
    }
    private String identifier() {
        spaces();
        int start = at;
        if (at >= input.length() || !Character.isLowerCase(input.charAt(at))) return "";
        at++;
        while (at < input.length() && (Character.isLetterOrDigit(input.charAt(at))
                || input.charAt(at) == '_')) at++;
        String name = input.substring(start,at);
        if (name.length() > 24) throw new IllegalArgumentException("标识符过长");
        return name;
    }
    private JsonElement or() {
        JsonElement a = and();
        while (keyword("or")) { JsonElement b = and(); a = new JsonPrimitive(truth(a) || truth(b)); }
        return a;
    }
    private JsonElement and() {
        JsonElement a = compare();
        while (keyword("and")) { JsonElement b = compare(); a = new JsonPrimitive(truth(a) && truth(b)); }
        return a;
    }
    private JsonElement compare() {
        JsonElement a = add();
        spaces();
        String op = null;
        for (String test : new String[]{"==","!=",">=","<=",">","<"}) {
            if (take(test)) { op=test; break; }
        }
        if (op == null) return a;
        JsonElement b = add();
        if ("==".equals(op)) return new JsonPrimitive(a.equals(b));
        if ("!=".equals(op)) return new JsonPrimitive(!a.equals(b));
        double left = number(a), right = number(b);
        return new JsonPrimitive(switch(op) {
            case ">" -> left > right; case "<" -> left < right;
            case ">=" -> left >= right; default -> left <= right;
        });
    }
    private JsonElement add() {
        JsonElement a = multiply();
        while (true) {
            if (take("+")) {
                JsonElement b = multiply();
                if (a.isJsonPrimitive() && b.isJsonPrimitive()
                        && a.getAsJsonPrimitive().isString() && b.getAsJsonPrimitive().isString())
                    a = new JsonPrimitive(a.getAsString() + b.getAsString());
                else a = numeric(number(a)+number(b));
            } else if (take("-")) a = numeric(number(a)-number(multiply()));
            else return a;
        }
    }
    private JsonElement multiply() {
        JsonElement a = unary();
        while (true) {
            if (take("*")) a = numeric(number(a)*number(unary()));
            else if (take("/")) {
                double d=number(unary());
                if (d==0) throw new IllegalArgumentException("不能除以零");
                a = numeric(number(a)/d);
            } else return a;
        }
    }
    private JsonElement unary() {
        if (keyword("not")) return new JsonPrimitive(!truth(unary()));
        if (take("-")) return numeric(-number(unary()));
        return primary();
    }
    private JsonElement primary() {
        if (++operations > 80) throw new IllegalArgumentException("表达式过于复杂");
        spaces();
        if (take("(")) { JsonElement x=or(); expect(")"); return fields(x); }
        if (at < input.length() && input.charAt(at) == '"') {
            int start = at++, end = input.indexOf('"',at);
            if (end<0 || end-start>190) throw new IllegalArgumentException("字符串格式错误");
            String value=input.substring(at,end);
            at=end+1;
            return fields(new JsonPrimitive(value));
        }
        if (at < input.length() && Character.isDigit(input.charAt(at))) {
            int start=at++;
            while(at<input.length() && (Character.isDigit(input.charAt(at)) || input.charAt(at)=='.')) at++;
            return fields(numeric(Double.parseDouble(input.substring(start,at))));
        }
        if (keyword("True")) return new JsonPrimitive(true);
        if (keyword("False")) return new JsonPrimitive(false);
        String name=identifier();
        if (name.isEmpty()) throw new IllegalArgumentException("非法表达式");
        if ("true".equals(name)) return new JsonPrimitive(true);
        if ("false".equals(name)) return new JsonPrimitive(false);
        if (take("(")) {
            java.util.List<JsonElement> args=new java.util.ArrayList<>();
            if (!take(")")) {
                do { args.add(or()); } while(take(","));
                expect(")");
            }
            return fields(call(name,args));
        }
        JsonElement value=variables.get(name);
        if (value==null) throw new IllegalArgumentException("变量未定义: "+name);
        return fields(value);
    }
    private JsonElement fields(JsonElement value) {
        while (true) {
            if (take(".")) {
                String field=identifier();
                if (field.isEmpty() || !value.isJsonObject() || !value.getAsJsonObject().has(field))
                    throw new IllegalArgumentException("字典字段不存在: "+field);
                value=value.getAsJsonObject().get(field);
            } else if (take("[")) {
                JsonElement indexValue=or();
                expect("]");
                int index=integer(indexValue);
                if (!value.isJsonArray() || index<0 || index>=value.getAsJsonArray().size())
                    throw new IllegalArgumentException("数组下标越界或目标不是数组");
                value=value.getAsJsonArray().get(index);
            } else return value;
        }
    }
    private JsonElement call(String name,java.util.List<JsonElement> args) {
        if ("get_fishing_status".equals(name)) {
            if (!args.isEmpty()) throw new IllegalArgumentException("get_fishing_status 不接受参数");
            return new JsonPrimitive(KurumiFishingBridge.status());
        }
        if ("get_agent_observation".equals(name)) {
            if (args.size() != 1) throw new IllegalArgumentException("需要一个观察记录索引");
            return new JsonPrimitive(KurumiClientController.agentObservation(integer(args.get(0))));
        }
        if ("len".equals(name)) {
            if (args.size()!=1 || !args.get(0).isJsonArray())
                throw new IllegalArgumentException("len 需要一个数组变量");
            return new JsonPrimitive(args.get(0).getAsJsonArray().size());
        }
        if ("search_all_block".equals(name)) {
            if (args.size()!=1) throw new IllegalArgumentException("search_all_block 需要一个方块ID");
            String id=string(args.get(0));
            if (!id.matches("[a-z0-9_.-]+:[a-z0-9_./-]{1,128}"))
                throw new IllegalArgumentException("方块ID无效");
            return KurumiBlockScanner.search(id);
        }
        if ("check_inventory".equals(name)) {
            if (args.size()!=1) throw new IllegalArgumentException("背包检查需要一个物品ID");
            String id=string(args.get(0));
            if (!id.matches("[a-z0-9_.-]+:[a-z0-9_./-]{1,128}"))
                throw new IllegalArgumentException("物品ID无效");
            return new JsonPrimitive(KurumiTaskChecks.inventoryCount(id));
        }
        if ("check_distance".equals(name)) {
            if (args.size()!=2) throw new IllegalArgumentException("距离检查需要两个参数");
            String kind=string(args.get(0)), target=string(args.get(1));
            if (!java.util.Set.of("block","entity","item","player").contains(kind)
                    || !target.matches("[A-Za-z0-9_.:-]{1,128}"))
                throw new IllegalArgumentException("距离目标无效");
            var result=JsonParser.parseString(KurumiToolbox.observe("get_distance:"+kind+":"+target))
                    .getAsJsonObject();
            if (!result.has("found") || !result.get("found").getAsBoolean())
                throw new IllegalArgumentException("距离目标不在搜索范围");
            return result.get("distance");
        }
        if ("list_remembered_blocks".equals(name)) {
            if(!args.isEmpty()) throw new IllegalArgumentException("list_remembered_blocks 不接受参数");
            return KurumiChestSearch.listBlocks();
        }
        if ("get_remembered_block_at".equals(name)) {
            if(args.size()!=1) throw new IllegalArgumentException("get_remembered_block_at 需要从0开始的编号");
            return KurumiChestSearch.blockAt(integer(args.get(0)));
        }
        if ("get_skill".equals(name)) {
            if (args.size()!=1) throw new IllegalArgumentException("get_skill 需要技能名称");
            return KurumiSkillMemory.configured().describe(string(args.get(0)));
        }
        if ("get_remembered_block".equals(name)) {
            if (args.size()!=1) throw new IllegalArgumentException("get_remembered_block 需要记忆名称");
            return KurumiChestSearch.get(string(args.get(0)));
        }
        if ("remember_block".equals(name)) {
            if(args.size()!=4) throw new IllegalArgumentException("remember_block 需要名称及三个坐标");
            return KurumiChestSearch.rememberAt(string(args.get(0)),integer(args.get(1)),
                    integer(args.get(2)),integer(args.get(3)));
        }
        if ("get_chest_search_result".equals(name)) {
            if(!args.isEmpty()) throw new IllegalArgumentException("get_chest_search_result 不接受参数");
            return JsonParser.parseString(KurumiChestSearch.status());
        }
        String tool;
        switch(name) {
            case "find_nearest_block", "check_inventory_item", "check_inventory_count", "get_crafting_recipe",
                    "check_nearby_chest", "get_nearby_chest_result" -> {
                if (args.size()!=1) throw new IllegalArgumentException("工具参数数量错误");
                tool=name+":"+string(args.get(0));
            }
            case "get_position", "get_player_status", "get_environment", "get_inventory",
                    "scan_nearby_entities", "scan_nearby_blocks", "scan_dropped_items",
                    "inspect_container" -> {
                if (!args.isEmpty()) throw new IllegalArgumentException("工具不接受参数");
                tool=name;
            }
            case "inspect_slot" -> {
                if (args.size()!=1) throw new IllegalArgumentException("inspect_slot 需要一个槽位编号");
                int slot=integer(args.get(0));
                if (slot < 0 || slot > 255) throw new IllegalArgumentException("槽位编号必须在0到255之间");
                tool="inspect_slot:"+slot;
            }
            case "get_distance" -> {
                if (args.size()!=2) throw new IllegalArgumentException("距离工具参数数量错误");
                tool=name+":"+string(args.get(0))+":"+string(args.get(1));
            }
            default -> throw new IllegalArgumentException("不支持的表达式函数: "+name);
        }
        if (!tool.matches("[a-z_]+(?::[A-Za-z0-9_./-]+){0,3}") || tool.length()>180)
            throw new IllegalArgumentException("工具参数无效");
        try { return JsonParser.parseString(KurumiToolbox.observe(tool)); }
        catch (RuntimeException ex) { throw new IllegalArgumentException("工具返回无效: "+name); }
    }
    private static String string(JsonElement e) {
        if (!e.isJsonPrimitive() || !e.getAsJsonPrimitive().isString())
            throw new IllegalArgumentException("需要字符串参数");
        return e.getAsString();
    }
    private static double number(JsonElement e) {
        // Observation helpers such as check_inventory_count return {"item_id": ..., "count": N}.
        // Retain the object for .count access, but permit its numeric count in expressions.
        if (e != null && e.isJsonObject()) {
            JsonObject object = e.getAsJsonObject();
            if (object.has("count") && object.get("count").isJsonPrimitive()
                    && object.get("count").getAsJsonPrimitive().isNumber())
                e = object.get("count");
        }
        if (e == null || !e.isJsonPrimitive() || !e.getAsJsonPrimitive().isNumber())
            throw new IllegalArgumentException("需要数值（对象请使用 .count 等数字字段）");
        double value = e.getAsDouble();
        if (!Double.isFinite(value)) throw new IllegalArgumentException("数值必须有限");
        return value;
    }
    private static JsonElement numeric(double value) {
        // Expression intermediates include squared distances and large sentinels.
        // Keep action arguments independently bounded by integer() at 30 million.
        if (!Double.isFinite(value) || Math.abs(value) > 9_007_199_254_740_991d)
            throw new IllegalArgumentException("数值越界");
        return new JsonPrimitive(value);
    }
    private KurumiExpressions() {throw new AssertionError();}
}
