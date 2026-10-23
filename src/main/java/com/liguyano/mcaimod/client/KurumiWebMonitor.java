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
    private static final Deque<Task> HISTORY = new ArrayDeque<>();
    private static final int HISTORY_LIMIT = 30;
    private static Task current;
    private static String nextRequirement = "";
    private static long nextId = 1;
    private static HttpServer server;

    private static final class Task {
        final long id = nextId++;
        final String requirement;
        final String script;
        final String startedAt = java.time.LocalDateTime.now().toString();
        final Deque<String> events = new ArrayDeque<>();
        String state = "执行中", operation = "";
        String endedAt = "";
        int line, actions, iteration;
        boolean result;
        Task(String requirement, String script) {
            this.requirement = requirement;
            this.script = script;
        }
        JsonObject json() {
            JsonObject o = new JsonObject();
            o.addProperty("id", id);
            o.addProperty("requirement", requirement);
            o.addProperty("script", script);
            o.addProperty("state", state);
            o.addProperty("operation", operation);
            o.addProperty("startedAt", startedAt);
            o.addProperty("endedAt", endedAt);
            o.addProperty("line", line);
            o.addProperty("actions", actions);
            o.addProperty("iteration", iteration);
            o.addProperty("lastResultSuccess", result);
            JsonArray logs = new JsonArray();
            for (String entry : events) logs.add(entry);
            o.add("events", logs);
            return o;
        }
    }

    public static synchronized void registerLine(KurumiTaskPlanner.Statement stmt, int number) {
        LINES.put(stmt, number);
    }
    public static synchronized void register(KurumiTaskPlanner.Program program, String source) {
        SOURCES.put(program, source);
        if (SOURCES.size() > 32) SOURCES.clear();
        if (LINES.size() > 512) LINES.clear();
    }
    /** Set only after the model returns a valid program, before executor.start(). */
    public static synchronized void requirement(String message) {
        nextRequirement = message == null ? "" : message.strip();
    }
    public static synchronized void begin(KurumiTaskPlanner.Program program) {
        if (current != null) archive("已中断");
        String source = SOURCES.remove(program);
        current = new Task(nextRequirement.isBlank() ? "未记录原始需求" : nextRequirement,
                source == null ? "(结构化任务，无原始伪代码)" : source);
        nextRequirement = "";
        log("任务启动");
    }
    private static void archive(String status) {
        if (current == null) return;
        current.state = status;
        current.endedAt = java.time.LocalDateTime.now().toString();
        HISTORY.addFirst(current);
        while (HISTORY.size() > HISTORY_LIMIT) HISTORY.removeLast();
        current = null;
    }
    public static synchronized void step(KurumiTaskPlanner.Statement stmt, int actionCount, int loop, boolean success) {
        if (current == null) return;
        current.line = LINES.getOrDefault(stmt, 0);
        current.operation = stmt.op();
        current.actions = actionCount;
        current.iteration = loop;
        current.result = success;
    }
    public static synchronized void state(String value) {
        log(value);
        if (current == null) return;
        current.state = value;
        if (!"执行中".equals(value)) archive(value);
    }
    public static synchronized void log(String value) {
        if (current == null) return;
        current.events.addLast(java.time.LocalTime.now().withNano(0) + "  " + value);
        while (current.events.size() > 100) current.events.removeFirst();
    }
    private static synchronized String snapshot() {
        JsonObject o = new JsonObject();
        o.add("current", current == null ? com.google.gson.JsonNull.INSTANCE : current.json());
        JsonArray history = new JsonArray();
        for (Task task : HISTORY) history.add(task.json());
        o.add("history", history);
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
        <!doctype html>
        <html lang="zh-CN"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
        <title>Kurumi · 任务监测</title>
        <style>
        *{box-sizing:border-box}body{background:#0d1320;color:#e5ecf7;font:15px system-ui,sans-serif;margin:0;padding:24px;max-width:1100px;margin:auto}
        header{display:flex;align-items:center;justify-content:space-between;gap:16px;flex-wrap:wrap;margin-bottom:22px}
        h1{font-size:24px;margin:0}h2{font-size:17px;margin:24px 0 12px}h3{font-size:15px;margin:15px 0 9px}
        .muted{color:#92a1b7;font-size:13px}.card{background:#192334;border:1px solid #334258;border-radius:14px;padding:20px;margin:14px 0}
        .current{border-color:#4b9bd5}.head{display:flex;align-items:center;justify-content:space-between;gap:12px;flex-wrap:wrap}
        .status{border-radius:20px;background:#324257;padding:5px 11px;font-size:12px}
        .running{background:#174b68;color:#9bddff}.success{background:#1a4938;color:#a4ecc6}
        .stats{display:flex;flex-wrap:wrap;gap:10px 22px;margin:14px 0;color:#b6c8db}
        .stats strong{color:#e5f4ff}.requirement{white-space:pre-wrap;overflow-wrap:anywhere;margin:0}
        .code{background:#101825;border-radius:9px;padding:8px 0;overflow-x:auto;font:13px ui-monospace,SFMono-Regular,Consolas,monospace;line-height:1.65}
        .code-line{white-space:pre;padding:2px 12px;min-width:max-content}
        .code-line.active{background:#234b6a;border-left:4px solid #7dd0ff;padding-left:8px}
        .number{color:#8090a6;display:inline-block;min-width:3ch;margin-right:12px;user-select:none}
        details{margin-top:12px}summary{cursor:pointer;color:#a9c8e3}
        .logs{white-space:pre-wrap;overflow-wrap:anywhere;font:12px ui-monospace,monospace;color:#a2b8cd;max-height:180px;overflow:auto}
        .empty{border:1px dashed #46556a;border-radius:12px;padding:24px;color:#a1b0c4}
        </style></head><body>
        <header><h1>Kurumi · 任务监测</h1><span class="muted" id="connection">连接中…</span></header>
        <h2>当前任务</h2><div id="current" class="empty">等待新任务…</div>
        <h2>历史任务 <span class="muted">最近30条 · 新结束的排在最上方</span></h2>
        <div id="history" class="empty">暂无已结束任务</div>
        <script>
        function el(tag,cls,content){const n=document.createElement(tag);if(cls)n.className=cls;if(content!==undefined)n.textContent=String(content);return n;}
        function createTask(t,active){
          const card=el('article','card'+(active?' current':''));
          const head=el('div','head'), title=el('strong','', '任务 #'+t.id);
          const status=el('span','status '+(active?'running':t.state==='已完成'?'success':''),t.state);
          head.append(title,status);card.append(head);
          card.append(el('div','muted',(t.startedAt||'').replace('T',' ')+(t.endedAt?' → '+t.endedAt.replace('T',' '):'')));
          card.append(el('h3','','需求'),el('p','requirement',t.requirement||'未记录'));
          const stats=el('div','stats');
          [['当前行',t.line||'-'],['步骤',t.operation||'-'],['动作数',t.actions||0],['循环次数',t.iteration||0]].forEach(([k,v])=>{
            const x=el('span','',k+'：');x.append(el('strong','',v));stats.append(x);
          });
          card.append(stats,el('h3','','伪代码'));
          const code=el('div','code');
          (t.script||'（无伪代码）').split('\\n').forEach((line,i)=>{
            const row=el('div','code-line'+(active&&i+1===t.line?' active':''));
            row.append(el('span','number',i+1),document.createTextNode(line));code.append(row);
          });card.append(code);
          if(t.events&&t.events.length){
            const details=el('details');const summary=el('summary','','执行日志（'+t.events.length+'）');
            details.append(summary,el('div','logs',t.events.join('\\n')));card.append(details);
          }
          return card;
        }
        async function refresh(){
          try{
            const response=await fetch('/api/status',{cache:'no-store'});
            if(!response.ok)throw Error(response.status);
            const data=await response.json();
            const current=document.getElementById('current');current.className=data.current?'':'empty';
            current.replaceChildren(data.current?createTask(data.current,true):document.createTextNode('等待新任务…'));
            const history=document.getElementById('history');history.replaceChildren();
            for(const task of data.history||[])history.append(createTask(task,false));
            history.className=history.children.length?'':'empty';
            if(!history.children.length)history.textContent='暂无已结束任务';
            document.getElementById('connection').textContent='已连接 · 每秒刷新';
          }catch(e){document.getElementById('connection').textContent='连接断开';}
        }
        refresh();setInterval(refresh,1000);
        </script></body></html>
        """;
    private KurumiWebMonitor() {}
}
