# Kurumi 本地 Edge TTS

语音功能只在运行 Minecraft 的本机播放，不对服务器或其他玩家广播音频。与此前的登录、脚本或模型 API 无关。

## Windows 安装

1. 安装 Python 3，确保 PowerShell 可运行 `py`。
2. 在 PowerShell 运行：`py -m pip install edge-tts pygame`
3. 将仓库 `config/mcaimod-voice.example.json` 复制到当前游戏实例的 `config/mcaimod-voice.json`（不复制也可使用默认值）。
4. 构建并安装 Mod，重启 Minecraft，运行 `/kurumi voice test` 测试声音。
5. `/kurumi voice on` 开启，`/kurumi voice off` 关闭，`/kurumi voice stop` 停止排队与正在播放的音频。

语音只来自 Kurumi 脚本的 talk()，不是监听服务器全部聊天。游戏线程只排队，不等待网络或音频播放。

Edge TTS 需要联网且音频由在线语音服务生成。Java Mod 会从 JAR 提取内置 Python 脚本到系统临时目录，通过 `python_command` 启动，自动合成并播放，游戏端不用手动启动 Python。Python 侧使用 pygame 解码 MP3 并播放。

故障排查：确认 `py -c "import edge_tts, pygame; print('ok')"` 能正常运行；若 PATH 未配置，可将 `python_command` 设置为 Python 解释器的完整路径。日志从 Java 控制台输出。语音每次都新建一个短期 Python 子进程，存在一定启动延迟，未来可改常驻进程。停止后不会强行终止已发送的 HTTP 请求本身，但会终止播放子进程。

## Kurumi 0.6.0 LangGraph 联动

LangGraph 返回给游戏聊天的最终 `reply` 现在也会加入本地语音队列。
原本由 `talk()` 触发的朗读逻辑保持不变；不会朗读所有玩家的公共聊天。
原有 `/kurumi voice on|off|test|stop` 指令继续有效。语音队列限制
200 字符，过长回答只朗读开头部分，避免长时间占用音频。

先运行 `./setup-agent.ps1` 安装 Agent 依赖，其中包括 `edge-tts` 和
`pygame`。若未在 `mcaimod-voice.json` 中设置 `python_command`，
Java 会优先选择 `config/mcaimod-agent/.venv/Scripts/python.exe`，
不存在时回退系统 `py`。已有配置若显式设为 `py`，会继续使用
系统 Python；如需统一虚拟环境，删除 `python_command` 字段即可。

本功能使用现有独立 TTS 播放进程（不是 LangGraph 图中的节点）。
Edge TTS 需要网络连接，声音仅在游戏运行电脑上播放。

## Kurumi 说话专用语音（2026-10）

Kurumi 自己的公开发言统一使用 `KurumiSpeech.say(text)`：
发送游戏聊天与加入 Edge TTS 队列会同时进行。**不会监听或朗读其他玩家消息**。
已接入 LangGraph 最终回答、控制器发言、脚本 `talk()` 与自动生存提醒。
调试日志、其他玩家聊天不会被朗读；来自服务器的回显不再重复入队。

日语默认发音是 `ja-JP-NanamiNeural`，语速 `+12%`，音调
`+35Hz`，以更轻快、可爱的声线呈现；
含日语假名的发言会自动用日语配音，无假名的中文消息仍使用中文声线。
想获得不同声线，可以在 `config/mcaimod-voice.json` 调整
`voice_ja`、`voice_zh`、`pitch` 和 `rate`。声线效果因设备而异，
这只是调整合成声音，并不是专门训练的角色配音。

自动饥饿、生存发言与部分常用控制器提示已经改为活泼可爱的日语。
任务生成文本、报错日志和第三方工具返回消息仍可能保留原始语言；
不应翻译技术报错内容以免损害调试。
