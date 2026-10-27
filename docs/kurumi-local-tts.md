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
