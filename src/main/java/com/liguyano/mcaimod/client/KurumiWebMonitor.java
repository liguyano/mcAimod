package com.liguyano.mcaimod.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpExchange;
import java.io.IOException;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.concurrent.Executors;

/** LAN-accessible dashboard; game actions are dispatched onto the Minecraft client thread. */
public final class KurumiWebMonitor {
    private static final Map<KurumiTaskPlanner.Statement, Integer> LINES = new IdentityHashMap<>();
    private static final Map<KurumiTaskPlanner.Program, String> SOURCES = new IdentityHashMap<>();
    private static final Deque<Task> HISTORY = new ArrayDeque<>();
    private static final int HISTORY_LIMIT = 30;
    private static Task current;
    private static Task pending;
    private static String nextRequirement = "";
    private static long nextId = 1;
    private static HttpServer server;

    private static final class Task {
        final long id = nextId++;
        final String requirement;
        String script;
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
    /** Begin tracking as soon as a chat request is accepted, including model failures. */
    public static synchronized void requestReceived(String message) {
        if (pending != null) finishPending("已中断");
        pending = new Task(message == null || message.isBlank() ? "未记录原始需求" : message.strip(), "");
        pending.state = "生成中";
        addEvent(pending, "收到需求，正在请求 AI 生成伪代码");
    }
    /** Record a successfully dispatched non-script action, including long-task launches. */
    public static synchronized void requestDispatched(String operation) {
        if (pending == null) return;
        pending.operation = operation;
        addEvent(pending, "动作请求已接受：" + operation + "；运行结果仍待游戏确认");
        finishPending("已提交执行");
    }
    public static synchronized void requestFailed(String reason) {
        if (pending == null) return;
        addEvent(pending, "请求失败：" + reason);
        finishPending("生成失败");
    }
    private static void finishPending(String status) {
        if (pending == null) return;
        pending.state = status;
        pending.endedAt = java.time.LocalDateTime.now().toString();
        HISTORY.addFirst(pending);
        while (HISTORY.size() > HISTORY_LIMIT) HISTORY.removeLast();
        pending = null;
    }
    public static synchronized void begin(KurumiTaskPlanner.Program program) {
        if (current != null) archive("已中断");
        String source = SOURCES.remove(program);
        if (pending != null) {
            current = pending;
            pending = null;
            current.script = source == null ? "(结构化任务，无原始伪代码)" : source;
        } else {
            current = new Task(nextRequirement.isBlank() ? "未记录原始需求" : nextRequirement,
                    source == null ? "(结构化任务，无原始伪代码)" : source);
        }
        current.state = "执行中";
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
    private static void addEvent(Task task, String value) {
        if (task == null) return;
        task.events.addLast(java.time.LocalTime.now().withNano(0) + "  " + value);
        while (task.events.size() > 100) task.events.removeFirst();
    }
    public static synchronized void log(String value) {
        addEvent(pending != null ? pending : current, value);
    }
    private static synchronized String snapshot() {
        JsonObject o = new JsonObject();
        o.add("current", current == null ? com.google.gson.JsonNull.INSTANCE : current.json());
        o.add("pending", pending == null ? com.google.gson.JsonNull.INSTANCE : pending.json());
        JsonArray history = new JsonArray();
        for (Task task : HISTORY) history.add(task.json());
        o.add("history", history);
        o.add("controls", KurumiWebActions.status());
        return o.toString();
    }
    private static void reply(HttpExchange exchange, int code, JsonObject payload) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        byte[] bytes = payload.toString().getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(code, bytes.length);
        try (var out = exchange.getResponseBody()) { out.write(bytes); }
    }
    private static void handlePost(HttpExchange exchange, boolean preview) throws IOException {
        if (!"POST".equals(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(405, -1);
            exchange.close();
            return;
        }
        // The page may be opened through any LAN IP or host name. Do not restrict
        // Host/Origin/Sec-Fetch-Site to localhost, but keep the explicit page header
        // so unrelated sites cannot send browser requests to run game actions.
        String contentType = exchange.getRequestHeaders().getFirst("Content-Type");
        String gate = exchange.getRequestHeaders().getFirst("X-Kurumi-Web");
        if (!"1".equals(gate)
                || contentType == null || !contentType.startsWith("application/json")) {
            JsonObject denied = new JsonObject();
            denied.addProperty("error", "请通过 Kurumi Web 面板提交 JSON 请求");
            reply(exchange, 400, denied);
            return;
        }
        try {
            byte[] bytes = exchange.getRequestBody().readNBytes(16385);
            if (bytes.length > 16384) {
                JsonObject tooLarge = new JsonObject();
                tooLarge.addProperty("error", "请求内容过长");
                reply(exchange, 413, tooLarge);
                return;
            }
            JsonObject payload = JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8))
                    .getAsJsonObject();
            String value = payload.get(preview ? "requirement" : "script").getAsString();
            String error = preview ? KurumiWebActions.submitGenerate(value)
                    : KurumiWebActions.submitRun(value);
            JsonObject result = new JsonObject();
            result.addProperty("accepted", error == null);
            if (error != null) result.addProperty("error", error);
            reply(exchange, error == null ? 202 : 400, result);
        } catch (RuntimeException error) {
            JsonObject response = new JsonObject();
            response.addProperty("error", "请求 JSON 格式错误");
            reply(exchange, 400, response);
        }
    }
    public static synchronized void install() {
        if (server != null) return;
        try {
            server = HttpServer.create(new InetSocketAddress("0.0.0.0", 8765), 0);
            server.createContext("/api/generate", exchange -> handlePost(exchange, true));
            server.createContext("/api/run", exchange -> handlePost(exchange, false));
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
        textarea{display:block;width:100%;min-height:155px;resize:vertical;background:#101825;color:#e6f1ff;border:1px solid #415773;border-radius:8px;padding:12px;font:13px ui-monospace,Consolas,monospace}
        textarea:focus{outline:2px solid #4185b9}button{background:#2d6da1;color:#fff;border:0;padding:9px 15px;border-radius:8px;cursor:pointer;font-weight:600}
        button.secondary{background:#394960}button:disabled{opacity:.5;cursor:default}
        .controls{display:flex;gap:9px;align-items:center;flex-wrap:wrap;margin:10px 0}
        .feedback{color:#b8cee4;font-size:13px;white-space:pre-wrap}
        .cols{display:grid;grid-template-columns:1fr 1fr;gap:14px}
        @media(max-width:750px){.cols{grid-template-columns:1fr}body{padding:12px}}

        </style></head><body>
        <header><h1>Kurumi · 任务监测</h1><span class="muted" id="connection">连接中…</span></header>
        <div class="cols">
          <section class="card">
            <h2>生成伪代码 · 仅测试</h2>
            <p class="muted">输入自然语言需求，调用已配置的 AI 生成和校验脚本。不会在游戏里执行。</p>
            <textarea id="requirement" placeholder="例如：用铁斧给附近4个橡木原木去皮"></textarea>
            <div class="controls"><button id="generateBtn">生成伪代码（不执行）</button><span id="previewStatus" class="feedback">空闲</span></div>
            <h3>生成结果</h3>
            <textarea id="previewScript" readonly placeholder="生成结果会显示在这里"></textarea>
            <div class="controls"><button class="secondary" id="copyPreviewBtn">复制到执行区</button></div>
          </section>
          <section class="card">
            <h2>直接执行伪代码</h2>
            <p class="muted">编辑或粘贴受支持的伪代码，然后手动确认执行；会在当前游戏世界中进行真实操作。</p>
            <textarea id="runScript" placeholder="talk(get_player_status())"></textarea>
            <div class="controls"><button id="runBtn">确认并执行</button><span id="runStatus" class="feedback">空闲</span></div>
            <p class="muted">通过原有解释器校验；执行后可在下方当前任务查看进度。</p>
          </section>
        </div>
        <h2>生成请求</h2><div id="pending" class="empty">暂无生成请求</div>
        <h2>当前任务</h2><div id="current" class="empty">等待新任务…</div>
        <h2>历史任务 <span class="muted">最近30条 · 新结束的排在最上方</span></h2>
        <div id="history" class="empty">暂无已结束任务</div>
        <script>
        function el(tag,cls,content){const n=document.createElement(tag);if(cls)n.className=cls;if(content!==undefined)n.textContent=String(content);return n;}
        const logViews=new Map();
        function rememberLogViews(){
          for(const card of document.querySelectorAll('#pending article.card, #current article.card, #history article.card')){
            const details=card.querySelector('details[data-task-id]');
            if(!details)continue;
            const logs=details.querySelector('.logs');
            logViews.set(details.dataset.taskId,{open:details.open,scrollTop:logs?logs.scrollTop:0});
          }
        }
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
            details.dataset.taskId=String(t.id);
            const logs=el('div','logs',t.events.join('\\n'));
            const previous=logViews.get(String(t.id));
            if(previous){details.open=previous.open;logs.scrollTop=previous.scrollTop;}
            details.append(summary,logs);card.append(details);
            if(previous && previous.scrollTop)requestAnimationFrame(()=>{logs.scrollTop=previous.scrollTop;});
          }
          return card;
        }
        async function refresh(){
          try{
            const response=await fetch('/api/status',{cache:'no-store'});
            if(!response.ok)throw Error(response.status);
            const data=await response.json();
            rememberLogViews();
            const controls=data.controls||{};
            document.getElementById('previewStatus').textContent=(controls.previewStatus||'空闲')+(controls.previewError?'：'+controls.previewError:'');
            document.getElementById('runStatus').textContent=(controls.runStatus||'空闲')+(controls.runError?'：'+controls.runError:'');
            document.getElementById('previewScript').value=controls.previewScript||'';
            document.getElementById('generateBtn').disabled=controls.previewStatus==='生成中';
            document.getElementById('runBtn').disabled=controls.runStatus==='检查中';
            const pending=document.getElementById('pending');
            pending.className=data.pending?'':'empty';
            pending.replaceChildren(data.pending?createTask(data.pending,false):document.createTextNode('暂无生成请求'));
            const current=document.getElementById('current');current.className=data.current?'':'empty';
            current.replaceChildren(data.current?createTask(data.current,true):document.createTextNode('等待新任务…'));
            const history=document.getElementById('history');history.replaceChildren();
            for(const task of data.history||[])history.append(createTask(task,false));
            history.className=history.children.length?'':'empty';
            if(!history.children.length)history.textContent='暂无已结束任务';
            document.getElementById('connection').textContent='已连接 · 每秒刷新';
          }catch(e){
            document.getElementById('connection').textContent='监测刷新失败：'+String(e.message||e);
          }
        }

        async function submit(path,body,feedback){
          try{
            const response=await fetch(path,{method:'POST',headers:{'Content-Type':'application/json','X-Kurumi-Web':'1'},
              body:JSON.stringify(body)});
            const data=await response.json();
            if(!response.ok||!data.accepted)throw Error(data.error||'提交失败');
            feedback.textContent='请求已提交';
            refresh();
          }catch(e){feedback.textContent=String(e.message||e);}
        }
        document.getElementById('generateBtn').addEventListener('click',()=>{
          submit('/api/generate',{requirement:document.getElementById('requirement').value},
            document.getElementById('previewStatus'));
        });
        document.getElementById('copyPreviewBtn').addEventListener('click',()=>{
          document.getElementById('runScript').value=document.getElementById('previewScript').value;
          document.getElementById('runScript').focus();
        });
        document.getElementById('runBtn').addEventListener('click',()=>{
          const script=document.getElementById('runScript').value;
          if(!script.trim())return;
          if(!confirm('这段伪代码会在当前 Minecraft 世界中执行真实操作。确定执行吗？'))return;
          submit('/api/run',{script},document.getElementById('runStatus'));
        });
        refresh();setInterval(refresh,1000);
        </script></body></html>
        """;
    private KurumiWebMonitor() {}
}
