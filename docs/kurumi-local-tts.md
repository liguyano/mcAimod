# Kurumi 本地独立语音服务器：AivisSpeech

Kurumi Java Mod 不打包语音模型，也不启动 Python。语音模型由 Windows 上单独
运行的 AivisSpeech Engine 提供；Mod 通过 HTTP 请求获得 WAV，然后在本机
和 8765 Web 面板播放。该实现仅连接回环地址，语音文本不会发送给互联网 TTS 服务。

## 安装和使用

1. 从 [AivisSpeech 官方网站](https://aivis-project.com/AivisSpeech) 安装并启动。
2. 确认本地语音引擎运行。在本机浏览器打开 `http://127.0.0.1:10101/docs`。
3. 打开 `http://127.0.0.1:10101/speakers`，找到喜欢的说话人风格的 `id` 数字。
4. 将 `config/mcaimod-voice.example.json` 复制到正在运行的 Minecraft 实例的
   `config/mcaimod-voice.json`，把 `speaker_id` 改成实际风格 ID。
5. 重新安装 Mod，游戏里执行 `/kurumi voice test`。

设置示例：

```json
{
  "enabled": true,
  "server_url": "http://127.0.0.1:10101",
  "speaker_id": "0",
  "speed": 1.0,
  "local_playback": true
}
```

`speaker_id: 0` 只是占位默认值，并不保证该 ID 存在；务必从 `/speakers` 中选取。
`speed` 限定 0.5–2.0。`enabled` 控制本机播放，关闭时仍向 Web 面板发布音频；
`local_playback` 则设置是否允许本机播放。`/kurumi voice stop` 清除语音队列并
停止本机播放；对已发送的网络合成请求采取结果丢弃策略。

Web 面板 `http://<游戏电脑局域网IP>:8765/voice` 仍可播放最新生成的语音。
现在 `/api/audio/{id}` 返回 WAV (`audio/wav`)，无需额外 MP3 编码组件。
仅在可信局域网使用 Web 面板，勿对公网开放 8765 端口。

若语音失败，在 Mod Web 日志中查看 `本地 AivisSpeech 语音失败`，并核对
服务端口、`speaker_id` 与 Windows 音频设备。AivisSpeech 的模型和许可证由
语音服务端管理，不随 Mod 分发。
