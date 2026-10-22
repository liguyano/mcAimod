package com.liguyano.mcaimod.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.concurrent.Executors;

/** Read-only localhost dashboard. HTTP threads never touch Minecraft game objects. */
public final class KurumiWebMonitor {
    private static final Map<KurumiTaskPlanner.Statement, Integer> LINES = new IdentityHashMap<>();
    private static final Map<KurumiTaskPlanner.Program, String> SOURCES = new IdentityHashMap<>();
    private static final Deque<String> EVENTS = new ArrayDeque<>();
    private static String script = "";
    private static String state = "空闲";
    private static String operation = "";
    private static int line = 0;
    private static int actions = 0;
    private static int iteration = 0;
    private static boolean result = false;
    private static HttpServer server;

    public static synchronized void registerLine(KurumiTaskPlanner.Statement stmt, int number) {
        LINES.put(stmt, number);
    }
    public static synchronized void register(KurumiTaskPlanner.Program program, String source) {
        SOURCES.put(program, source);
        if (SOURCES.size() > 32) SOURCES.clear();
        if (LINES.size() > 512) LINES.clear();
    }
    public static synchronized void begin(KurumiTaskPlanner.Program program) {
        script = SOURCES.getOrDefault(program, "(结构化 EXECUTE_PROGRAM：没有原始 Python 脚本)");
        state = "执行中";
        line = 0; actions = 0; iteration = 0; result = false; operation = "";
        EVENTS.clear();
        log("任务启动");
    }
    public static synchronized void step(KurumiTaskPlanner.Statement stmt, int actionCount, int loop, boolean success) {
        line = LINES.getOrDefault(stmt, 0);
        operation = stmt.op();
        actions = actionCount;
        iteration = loop;
        result = success;
    }
    public static synchronized void state(String value) { state = value; log(value); }
    public static synchronized void log(String value) {
        EVENTS.addLast(java.time.LocalTime.now().withNano(0) + "  " + value);
        while (EVENTS.size() > 100) EVENTS.removeFirst();
    }
    private static synchronized String snapshot() {
        JsonObject o = new JsonObject();
        o.addProperty("script", script);
        o.addProperty("state", state);
        o.addProperty("operation", operation);
        o.addProperty("line", line);
        o.addProperty("actions", actions);
        o.addProperty("iteration", iteration);
        o.addProperty("lastResultSuccess", result);
        JsonArray logs = new JsonArray();
        for (String e : EVENTS) logs.add(e);
        o.add("events", logs);
        return o.toString();
    }
    public static synchronized void install() {
        if (server != null) return;
        try {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 8765), 0);
            server.createContext("/api/status", exchange -> {
                if (!"GET".equals(exchange.getRequestMethod())) { exchange.sendResponseHeaders(405, -1); exchange.close(); return; }
                exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
                exchange.getResponseHeaders().set("Cache-Control", "no-store");
                byte[] bytes = snapshot().getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(200, bytes.length);
                try (var out = exchange.getResponseBody()) { out.write(bytes); }
            });
            server.createContext("/", exchange -> {
                if (!"GET".equals(exchange.getRequestMethod())) { exchange.sendResponseHeaders(405, -1); exchange.close(); return; }
                exchange.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
                byte[] bytes = PAGE.getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(200, bytes.length);
                try (var out = exchange.getResponseBody()) { out.write(bytes); }
            });
            server.setExecutor(Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "Kurumi-Web-Monitor");
                t.setDaemon(true);
                return t;
            }));
            server.start();
        } catch (Exception e) {
            server = null;
            System.err.println("Kurumi web monitor failed: " + e.getMessage());
        }
    }
    private static final String PAGE = """
        <!doctype html><html lang="zh-CN"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
        <title>Kurumi 执行监控</title><style>
        body{background:#10151f;color:#e7edf7;font:15px system-ui;margin:0;padding:30px;max-width:1000px;margin:auto}
        h1{font-size:25px}section{background:#1b2432;border:1px solid #344054;border-radius:12px;padding:18px;margin:16px 0}
        .info{display:flex;gap:25px;flex-wrap:wrap}.info b{color:#7cc7ff}
        .code{font:13px ui-monospace,monospace;white-space:pre;overflow:auto}
        .code div{padding:5px 10px;border-radius:4px}.code .active{background:#224d72;border-left:4px solid #6cc5ff}
        .code .number{color:#7e90a8;display:inline-block;width:40px}
        #logs{max-height:240px;overflow:auto;white-space:pre-wrap;color:#aabbd0;font:13px ui-monospace,monospace}
        </style></head><body><h1>Kurumi · 伪代码实时监控</h1>
        <section class="info"><div>状态：<b id="state">连接中</b></div><div>当前行：<b id="line">-</b></div>
        <div>指令：<b id="operation">-</b></div><div>动作数：<b id="actions">0</b></div>
        <div>循环计数：<b id="iteration">0</b></div><div>最近结果：<b id="result">-</b></div></section>
        <section><h3>AI 伪代码</h3><div class="code" id="code">等待任务</div></section>
        <section><h3>执行事件</h3><div id="logs">暂无事件</div></section>
        <script>
        async function refresh(){
          try {
            const r=await fetch('/api/status',{cache:'no-store'});if(!r.ok)throw Error(r.status);
            const d=await r.json();
            for(const k of ['state','line','operation','actions','iteration'])document.getElementById(k).textContent=d[k]??'-';
            document.getElementById('result').textContent=d.lastResultSuccess?'成功':'未成功/尚未验证';
            const code=document.getElementById('code');code.replaceChildren();
            (d.script||'等待任务').split('\\n').forEach((s,i)=>{
              const row=document.createElement('div');if(i+1===d.line)row.className='active';
              const n=document.createElement('span');n.className='number';n.textContent=i+1;
              row.append(n,document.createTextNode(s));code.append(row);
            });
            document.getElementById('logs').textContent=(d.events||[]).join('\\n')||'暂无事件';
          }catch(e){document.getElementById('state').textContent='连接断开';}
        }
        refresh();setInterval(refresh,1000);
        </script></body></html>
        """;
    private KurumiWebMonitor() {}
}
