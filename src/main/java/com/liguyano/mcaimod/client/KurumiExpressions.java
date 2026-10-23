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
        String name=identifier();
        if (name.isEmpty()) throw new IllegalArgumentException("非法表达式");
        if ("true".equals(name) || "True".equals(name)) return new JsonPrimitive(true);
        if ("false".equals(name) || "False".equals(name)) return new JsonPrimitive(false);
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
        while(take(".")) {
            String field=identifier();
            if (field.isEmpty() || !value.isJsonObject() || !value.getAsJsonObject().has(field))
                throw new IllegalArgumentException("字典字段不存在: "+field);
            value=value.getAsJsonObject().get(field);
        }
        return value;
    }
    private JsonElement call(String name,java.util.List<JsonElement> args) {
        String tool;
        switch(name) {
            case "find_nearest_block", "check_inventory_count", "get_crafting_recipe" -> {
                if (args.size()!=1) throw new IllegalArgumentException("工具参数数量错误");
                tool=name+":"+string(args.get(0));
            }
            case "get_player_status", "get_environment", "get_inventory",
                    "scan_nearby_entities", "scan_nearby_blocks", "scan_dropped_items",
                    "inspect_container" -> {
                if (!args.isEmpty()) throw new IllegalArgumentException("工具不接受参数");
                tool=name;
            }
            case "get_distance" -> {
                if (args.size()!=2) throw new IllegalArgumentException("距离工具参数数量错误");
                tool=name+":"+string(args.get(0))+":"+string(args.get(1));
            }
            default -> throw new IllegalArgumentException("不支持的表达式函数: "+name);
        }
        if (!tool.matches("[a-z_]+(?::[A-Za-z0-9_.-]+){0,3}") || tool.length()>180)
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
        if (e==null || !e.isJsonPrimitive() || !e.getAsJsonPrimitive().isNumber())
            throw new IllegalArgumentException("需要数值");
        return e.getAsDouble();
    }
    private static JsonElement numeric(double value) {
        if (!Double.isFinite(value) || Math.abs(value)>30000000)
            throw new IllegalArgumentException("数值越界");
        return new JsonPrimitive(value);
    }
    private KurumiExpressions() {throw new AssertionError();}
}
