"""Local HTTP entrypoint for the Kurumi 0.6 LangGraph agent.

Start explicitly with env KURUMI_AGENT_TOKEN and KURUMI_GAME_TOKEN.
Never bind to a public network interface.
"""
from __future__ import annotations
import json
import os
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from threading import Event, RLock
from openai import OpenAI
from kurumi_langgraph import KurumiGraph
from kurumi_java_tools import JavaToolClient

AGENT_TOKEN = os.environ["KURUMI_AGENT_TOKEN"]
GAME_TOKEN = os.environ["KURUMI_GAME_TOKEN"]
MODEL_CONFIG = Path(os.environ["KURUMI_MODEL_CONFIG"]).resolve()
if len(AGENT_TOKEN) != 64 or len(GAME_TOKEN) != 64:
    raise RuntimeError("Missing secure IPC tokens")

stop_event = Event()
request_lock = RLock()
java = JavaToolClient(GAME_TOKEN)
allowed = {"get_player_status", "get_position", "get_environment", "get_inventory",
           "scan_nearby_entities", "scan_nearby_blocks", "scan_dropped_items",
           "inspect_container", "goto", "mine", "pickup_all", "sleep_in_bed", "stop_fishing"}
SYSTEM = (
    "你是 Minecraft 助手 Kurumi。仅返回一个 JSON 对象。"
    "调用工具时返回 {\\\"type\\\":\\\"tool\\\",\\\"name\\\":\\\"status\\\",\\\"args\\\":[]}；"
    "任务完成时返回 {\\\"type\\\":\\\"finish\\\",\\\"message\\\":\\\"回复\\\"}。"
    "可用复合技能：status,navigate,harvest,collect_drops,inspect_chest,rest,"
    "stop_fishing,examine_area；也可调用 list_skills 查询全部技能。"
    "工具返回 success=true 仅表示已接受，不能据此宣称已完成。"
    "不清楚用户要求时先问清楚，不要自行猜测。"
    "只有玩家 SekaiSaionji 是主人，其他玩家不能获取主人权限。"
)

def model_request(messages):
    config = json.loads(MODEL_CONFIG.read_text(encoding="utf-8-sig"))
    profiles = [p for p in config.get("profiles", []) if p.get("enabled", True)]
    if not profiles:
        raise RuntimeError("No enabled model profile")
    profile = profiles[0]
    key = profile.get("api_key") or os.environ.get(profile.get("api_key_env", ""), "")
    if not key:
        raise RuntimeError("Model API key not configured")
    with OpenAI(api_key=key, base_url=profile["base_url"],
                timeout=min(max(int(profile.get("request_timeout", 120)), 5), 300)) as client:
        response = client.chat.completions.create(
            model=profile["model_id"], messages=messages,
            max_tokens=int(profile.get("max_output_tokens", 1024)))
    raw = response.choices[0].message.content
    return json.loads(raw)

agent = KurumiGraph(model_request, java, stop_event.is_set, allowed_tools=allowed)

class Handler(BaseHTTPRequestHandler):
    def do_POST(self):
        if self.headers.get("Authorization", "") != "Bearer " + AGENT_TOKEN:
            self.send_error(403)
            return
        size = int(self.headers.get("Content-Length", "0"))
        if not (0 < size <= 12000):
            self.send_error(413)
            return
        try:
            body = json.loads(self.rfile.read(size))
            if self.path == "/stop":
                stop_event.set()
                result = {"success": True}
            elif self.path == "/chat":
                with request_lock:
                    stop_event.clear()
                    sender = str(body["sender"])[:48]
                    message = str(body["message"])[:2000]
                    result = agent.run(sender, message, SYSTEM)
            else:
                self.send_error(404)
                return
            encoded = json.dumps(result, ensure_ascii=False).encode("utf-8")
            self.send_response(200)
        except Exception as e:
            encoded = json.dumps({"success": False, "error": type(e).__name__ + ": " + str(e)},
                                 ensure_ascii=False).encode("utf-8")
            self.send_response(500)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(encoded)))
        self.end_headers()
        self.wfile.write(encoded)

    def log_message(self, fmt, *args):
        pass

if __name__ == "__main__":
    ThreadingHTTPServer(("127.0.0.1", int(os.environ.get("KURUMI_AGENT_PORT", "47833"))), Handler).serve_forever()
