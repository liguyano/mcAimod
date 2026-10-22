# mcAimod — Minecraft 26.2 NeoForge AI Companion

AI 伙伴 Mod（当前开发版本 **v0.3.0**）：在游戏中与大模型聊天，让模型根据**当前玩家的真实游戏状态**回答问题。支持通过配置切换 **Ollama、阿里云百炼（Qwen）、DeepSeek、OpenAI** 等使用 Chat Completions 接口的服务。除基础 `/ai` 问答外，还包含客户端 Kurumi 聊天助手、有限的游戏动作与自由组合任务执行器。当前是开发中的实验功能，实际操作依赖游戏环境和服务端响应。

## 要求

- Minecraft **26.2**（Java 版）
- 对应的 **NeoForge 26.2**（本项目开发基线 `26.2.0.93`）
- **JDK 25**（开发和运行所需；不是 Java 17 或 Java 21）
- 一个可访问的模型服务；本地 Ollama 或拥有相应 API Key 的云模型

使用 NeoForge 官方 [26.2 ModDevGradle MDK](https://github.com/NeoForgeMDKs/MDK-26.2-ModDevGradle) 初始化。无需自行安装 Gradle，仓库自带 wrapper。

## 快速开始

1. 安装 JDK 25，确认 `java -version` 显示 25。
2. 在仓库根目录使用 PowerShell：

   ```powershell
   .\gradlew.bat runClient
   ```

   或者在 Linux/macOS：

   ```bash
   ./gradlew runClient
   ```

3. 首次启动会创建开发实例中的 `run/config/mcaimod-models.json`。正式服务器/客户端对应配置在该实例的 `config/mcaimod-models.json`。修改配置后需要**重启游戏/服务器**。
4. 在游戏内输入 `/ai 你好，我现在在哪里？`。

默认 `ollama` 配置连接游戏服务端机器上的 `127.0.0.1:11434`，模型为 `qwen3:8b`。需要提前安装并运行 Ollama，例如：

```bash
ollama pull qwen3:8b
ollama serve
```

如果你已经以后台服务方式运行 Ollama，则只需确保服务在监听，无需再开一个 `ollama serve`。远程专用服务器上的 `127.0.0.1` 指的是**服务器自身**，不是玩家电脑。

## 模型切换

首次启动会自动生成配置；也可以参考仓库里的 [`config/mcaimod-models.example.json`](config/mcaimod-models.example.json)。每条 `profile` 可设置：

| 字段 | 说明 |
| --- | --- |
| `name` | `/ai model use` 使用的名字 |
| `base_url` | 接口前缀，例如 `https://api.deepseek.com/v1` |
| `model_id` | 具体模型名称 |
| `api_key` | 可直接填写 API Key（明文，仅保存在服务器本地配置）；留空时按 `api_key_env` 读取 |
| `api_key_env` | API Key 的**环境变量名称**，本地无认证模型可留空 |
| `request_timeout` | 超时秒数（1–300） |
| `max_output_tokens` | 输出 token 上限（1–8192） |
| `enabled` | 是否允许玩家选择该模型 |

### 阿里云 Qwen3.8-Max（在 JSON 中配置密钥）

打开游戏实例的 `config/mcaimod-models.json`，在 `profiles` 数组中新增下面的配置（已有配置则直接修改）。下面使用公共百炼接口地址；如果你使用工作空间专属 API 地址，请替换为自己的 `base_url`：

```json
{
  "name": "aliyun",
  "base_url": "https://dashscope.aliyuncs.com/compatible-mode/v1",
  "model_id": "qwen3.8-max",
  "api_key": "这里填写你新生成的API Key",
  "api_key_env": "",
  "request_timeout": 60,
  "max_output_tokens": 1024,
  "enabled": true
}
```

注意这是 `profiles` 数组中的**一个对象**，不是整个配置文件；已有其他模型时须按 JSON 语法用逗号隔开。**重启游戏或服务器**后输入 `/ai model use aliyun`，再用 `/ai 你好` 测试。

首次生成的配置示例中 `aliyun` 默认 `enabled: false`、`api_key: ""`，填写真实密钥后再启用。**已生成的 `mcaimod-models.json` 不会被更新覆盖**，升级 Mod 后需要自行增加 `api_key` 字段或 `aliyun` profile。

配置优先级：`api_key` 不为空时使用它；为空时回退读取 `api_key_env` 指向的环境变量；两者都为空时不发送 Authorization 请求头（适合本地 Ollama）。

启用 DeepSeek：

1. 在服务端操作系统设置 `DEEPSEEK_API_KEY` 环境变量，确保启动游戏/服务器的进程能读取。
2. 在 `mcaimod-models.json` 找到 `deepseek`，把 `enabled` 改为 `true`。
3. 重启后游戏内输入 `/ai model use deepseek`。

Windows PowerShell 的**当前窗口临时环境变量**示例（请自行填入自己的密钥，不要分享到聊天/仓库）：

```powershell
$env:DEEPSEEK_API_KEY = "你的密钥"
.\gradlew.bat runClient
```

OpenAI 同理，使用 `OPENAI_API_KEY` 并启用 `openai` 配置。可以增加其他 Chat Completions 兼容模型配置。此首版使用常见的 `max_tokens` 参数；如使用仅接受 `max_completion_tokens` 的模型，请选用兼容模型或后续适配该参数。

安全限制：远程接口必须是 HTTPS；普通 HTTP **仅允许本机回环地址** `127.0.0.1`、`localhost` 或 `::1`。JSON 中的 `api_key` 是**明文存储**，只适用于自己管理的电脑或服务器，不要上传到 GitHub、分享给别人或把它包含在发布的 Mod JAR 中；多人服务器推荐使用 `api_key_env` 环境变量。项目 `.gitignore` 已忽略 `config/mcaimod-models.json` 和开发实例 `run/`，但这并不能阻止密钥被你手动复制到其他文件。每个配置是否启用由服务器管理员决定，玩家无法通过聊天指令任意输入 URL。

## 游戏指令

| 指令 | 功能 |
| --- | --- |
| `/ai <消息>` | 向当前选定的大模型发送消息，自动附带游戏状态 |
| `/ai status` | 当前模型、会话长度、是否请求中、坐标隐私开关 |
| `/ai model list` | 列出管理员启用的模型 |
| `/ai model use <名字>` | 只为自己切换模型，不影响其他玩家 |
| `/ai clear` | 清空自己的聊天记忆并使旧请求失效 |

示例：

- `/ai 我现在在哪个生物群系？`
- `/ai 我血量够不够去打附近的怪？`
- `/ai 我主手拿着什么？接下来建议做什么？`

## 采集哪些游戏信息？

在服务器线程读取一个不可变快照：

- 维度、生物群系、天气、世界时钟、玩家生命值和饥饿值
- 主副手物品、头胸腿脚装备、状态效果
- 身边 **16 格内已经加载** 的实体，按距离只选最近 **12** 个
- 可选整数坐标，默认 `share_exact_coordinates: false`，不会上传精确坐标

向模型发送的历史**只在内存**存放，每位玩家最多最近 10 轮问答、彼此隔离。不会上传玩家 IP、完整背包、其他玩家的聊天记录，也不会运行 AI 建议中的指令。若使用云模型，请注意这些状态信息将发送至所选提供商；若想数据仅留在本机，使用本地 Ollama。

出于服务器性能考虑：HTTP 异步发送，游戏主线程不等待网络；每名玩家同一时间只允许一个请求，两个请求之间至少 4 秒；每次提问不超过 1000 字符。断线或清空会话后，不展示已过期的回答。

## Kurumi 游戏助手（v0.3.0 开发中）

Kurumi 与上面的服务端 `/ai` 问答功能不同，是客户端游戏内助手。使用前需配置可用的大模型，并在游戏内启用聊天监听。

| 指令 | 功能 |
| --- | --- |
| `/kurumi on` / `/kurumi off` | 开启或关闭 Kurumi 聊天监听 |
| `/kurumi auto on` / `/kurumi auto off` | 切换自动响应/执行设置 |
| `/kurumi status` | 查看当前任务、恢复、聊天监听等状态 |
| `/kurumi stop` | 停止当前自动任务 |
| `/kurumi farm` | 尝试启动自动农作 |

### 自由组合任务

模型可以输出 `EXECUTE_PLAN`，按顺序组合 **1–8 个步骤**；执行器逐步执行并检查可观测结果。当前支持：

| 步骤动作 | 作用 |
| --- | --- |
| `GOTO` | 前往指定整数坐标 |
| `EQUIP_MAIN_HAND` | 将指定背包物品装备到主手 |
| `EQUIP_ARMOR` | 将背包中的头盔、胸甲、护腿或靴子穿到对应装备槽并检查结果 |
| `MINE` | 采集受支持的原版树木/菌柄 |
| `PICKUP_ITEM_ALL` | 拾取指定类型掉落物 |
| `CHEST_TAKE` | 从附近普通箱子取指定数量物品 |
| `CHEST_PUT` | 向附近普通箱子放指定数量物品 |
| `CHEST_PUT_ITEM_ALL` | 将背包中指定类型物品全部存箱 |
| `CRAFT_ITEM` | 尝试按可用配方合成指定物品 |
| `ATTACK_MOB` | 攻击 3 格内指定 `entity_id` 生物，最多 10 秒 |
| `ATTACK_PLAYER` | 仅主人可指定 `target` 玩家名，不能攻击主人，最多 10 秒 |

攻击步骤等待本次有界攻击结束后继续，不代表已经击败目标；攻击过程不自动追击。可在计划顶层设置 `loop_count`（**1–20**，默认 1），让整组步骤顺序重复执行指定轮数。例如 `ATTACK_MOB → PICKUP_ITEM_ALL → CHEST_PUT_ITEM_ALL` 可循环尝试。循环仍受全局 **5 分钟** 限制；单步失败会按原规则重试，超过限制即停止，不会自动跳过缺少物品的步骤，也不保证每轮都有可攻击生物或可拾取掉落物。使用 `/kurumi stop` 可随时停止。

示例计划：

```json
{
  "type": "action",
  "action": "EXECUTE_PLAN",
  "reply": "准备循环攻击、拾取并存放战利品",
  "loop_count": 3,
  "break_conditions": [{"source": "chest", "item_id": "minecraft:rotten_flesh", "count": 64}],
  "steps": [
    {"action": "ATTACK_MOB", "entity_id": "minecraft:zombie"},
    {"action": "PICKUP_ITEM_ALL", "item_id": "minecraft:rotten_flesh"},
    {"action": "CHEST_PUT_ITEM_ALL", "item_id": "minecraft:rotten_flesh"}
  ]
}
```

可选 `break_conditions` 数组（最多 4 条），支持 `source: "inventory"`（背包）和 `source: "chest"`（附近普通箱子），指定 `item_id` 和 `count`（1–2304）；**任意一个条件满足即停止**。执行器在开始时和每个步骤完成后检查条件；箱子数量必须来自服务器同步的箱子菜单，检查失败时安全停止。检查到的数量不是本轮新增数量，而是当前总数量。箱子必须在可交互范围内。

计划总时限 **5 分钟**，单步最多 **75 秒**，失败最多重试 **3 次**。目前自由计划失败后会重新检查并重试当前步骤，**并非任意失败都会触发大模型重新规划**。另有固定的 `COLLECT_AND_STORE` 长任务及其独立恢复逻辑。

### 箱子内容检查

Kurumi 提供 `check_nearby_chest` 与 `get_nearby_chest_result` 工具：尝试打开交互范围内（约 **4.5 格**）最近的**普通箱子**，等服务器同步箱子菜单后读取指定物品数量；未完成时返回 `pending`，无法检查时返回错误，不会假装箱子为空。

自由计划执行 `CHEST_TAKE`、`CHEST_PUT` 和 `CHEST_PUT_ITEM_ALL` 时，也会在操作前后读取箱子数量，结合背包增减验证结果。**当前仅比较数量变化方向，并非严格核对每次完整目标数量**；附近多个箱子、服务器延迟、容器打开失败、背包满或物品转移不完整等情况仍需游戏内测试。箱子检查会打开并关闭箱子界面，可能影响玩家正在进行的容器操作。

### 权限和使用限制

部分敏感动作只允许代码中指定的主人玩家触发；不要把聊天中的 AI 输出当作可靠的权限验证。Kurumi 的动作执行仍处于实验阶段，不应在装有重要物品的生存存档中未经备份直接测试。模型可能生成无效计划，执行器也不能保证所有任务成功。

## 本地构建与验证

开发环境需要 **JDK 25**。Windows PowerShell：

```powershell
java -version
.\gradlew.bat test build --no-daemon
```

Linux/macOS：

```bash
java -version
./gradlew test build --no-daemon
```

首次构建需要下载 Gradle/NeoForge 依赖并保持网络畅通。成功后在 `build/libs/` 查找 JAR；**编译成功不等于游戏内功能验证成功**。推荐先本地构建、修复错误，再提交 GitHub；GitHub Actions 可作为额外的 CI 验证。构建命令已列出，但 README 更新本身不代表构建通过。

## 打包与安装

```powershell
.\gradlew.bat test build
```

Linux/macOS：`./gradlew test build`。成功后检查 `build/libs/` 内生成的 Mod JAR，并将其放到 NeoForge 26.2 实例 `mods/` 中。

- **单人游戏**：客户端安装本 Mod，游戏内部集成服务器负责请求模型。
- **专用服务器**：服务器安装本 Mod 并配置服务端密钥；玩家需要支持相应 NeoForge 模组加载的客户端环境。所有 API Key 仅在服务器侧使用。

CI 工作流 `.github/workflows/build.yml` 使用 Temurin 25 运行 Gradle 构建。单元测试采用本地模拟 HTTP 接口，不调用收费的真实模型 API。

## 当前范围

基础 `/ai` 问答与 Kurumi 客户端助手并存。**v0.3.0** 已有受限的游戏动作、自由组合任务、箱子检查、逐步验证及部分任务恢复功能；仍缺少完善的任意计划 LLM 重规划、严格的目标数量校验、长期记忆和完整游戏内自动化测试。独立 NPC、语音和流式回复不属于当前已验证功能。


### 受控伪代码任务（EXECUTE_PROGRAM）

复杂任务可使用结构化伪代码 AST：`ACTION`、`IF`、`WHILE`、`BREAK`。不执行模型生成的任意 Python/Java 代码。条件读取背包或附近箱子里某物品的实际数量，支持 `LT`、`LE`、`EQ`、`GE`、`GT`、`NE` 六种比较运算符。箱子条件通过服务器同步菜单检查，无法检查时停止。

```json
{
  "type": "action",
  "action": "EXECUTE_PROGRAM",
  "reply": "循环攻击、拾取并存放腐肉，直到箱子有64个",
  "program": [
    {
      "op": "WHILE",
      "condition": {"source": "chest", "item_id": "minecraft:rotten_flesh", "operator": "LT", "count": 64},
      "body": [
        {"op": "ACTION", "step": {"action": "ATTACK_MOB", "entity_id": "minecraft:zombie"}},
        {"op": "ACTION", "step": {"action": "PICKUP_ITEM_ALL", "item_id": "minecraft:rotten_flesh"}},
        {"op": "ACTION", "step": {"action": "CHEST_PUT_ITEM_ALL", "item_id": "minecraft:rotten_flesh"}}
      ]
    }
  ]
}
```

每个 WHILE 最多 20 次，最多 3 层嵌套、48 个静态节点、160 次动作，总运行时限 5 分钟。所有 ACTION 均委托现有执行器进行前后验证，失败则停止整个程序；目前尚不支持用 IF 分支处理动作失败返回值，IF 只支持背包和箱子数量判断。战斗动作仍只能确认攻击尝试结束，不代表目标已被击败。旧的 `EXECUTE_PLAN` 仍然可用。


### Python 风格任务脚本（EXECUTE_SCRIPT）

现在可直接编写缩进式伪代码（**不是执行真正的 Python**），由白名单解析器转换成受控指令树。脚本作为模型 JSON 响应中的 `script` 字符串传入，换行需要 JSON 转义。示例脚本：

```python
# 循环刷怪并收集腐肉
while check_chest("minecraft:rotten_flesh") < 64:
    result = attack_mob("minecraft:zombie")
    if result.success:
        result = pickup_item_all("minecraft:rotten_flesh")
        if result.success:
            chest_put_item_all("minecraft:rotten_flesh")
        else:
            break
    else:
        break
return "任务结束"
```

脚本支持 `while`、`if`、`else`、`break`、`return`、`result = action(...)`、`result.success`、`not result.success`、`check_chest(...)`、`check_inventory(...)`。动作函数白名单：`attack_mob`、`attack_player`、`pickup_item_all`、`chest_put_item_all`、`equip_armor`、`equip_main_hand`、`mine`。不支持任意变量、运算、导入模块或执行系统代码。动作成功表示原执行器验证通过；攻击目前仅代表攻击尝试结束，不能证明目标死亡。每级缩进必须为 4 个空格；保留循环次数、总时间与动作数上限。


### 在任务中发送公屏聊天：`talk()`

`EXECUTE_SCRIPT` 支持在任意步骤插入 `talk("文本")`。该指令使用玩家连接的普通聊天发送接口，不是只显示给自己看的本地提示；消息是否对所有玩家可见取决于服务器聊天设置。示例：

```python
talk("我开始收集腐肉啦！")
while check_chest("minecraft:rotten_flesh") < 64:
    result = attack_mob("minecraft:zombie")
    if result.success:
        talk("攻击动作已完成，准备捡取掉落物")
        result = pickup_item_all("minecraft:rotten_flesh")
        if result.success:
            chest_put_item_all("minecraft:rotten_flesh")
        else:
            break
    else:
        break
talk("本次收集任务结束")
return "任务结束"
```

为防止循环刷屏，单次程序最多发送 12 条公屏消息，间隔至少 2 秒，单条最多 120 字符；禁止斜杠命令和换行。达到消息上限时停止任务。不能保证服务器未禁言、聊天频道限制或其他插件过滤时仍能送达。


### 伪代码自动种田 farm(radius)

`EXECUTE_SCRIPT` 现在接受 `farm(16)` 或 `result = farm(16)`（参数范围 4–32）。执行器调用现有 Baritone `#farm` 命令；`result.success` 仅表示命令成功发送，**不表示种田完成或农作物已经收获**。当前参数用于验证，Baritone 的 `#farm` 未按该参数限制实际范围。该命令可能持续运行，需要使用已有停止操作终止；`return` 只结束伪代码，不自动停止 Baritone。

```python
talk("开始种田")
result = farm(16)
if result.success:
    talk("已发出种田命令")
else:
    talk("种田命令发送失败")
return "指令处理结束"
```


### 本机 Web 伪代码监控（试验版）

Kurumi 客户端加载时尝试启动只读 HTTP 服务，浏览器打开 **http://127.0.0.1:8765/**。每秒刷新 `/api/status`，展示最新 EXECUTE_SCRIPT 的完整源码、正在执行的源码行、操作类型、动作数、循环计数、最近结果及最近 100 条任务事件。源码行高亮由解析器保留的原始行号驱动。EXECUTE_PROGRAM 没有 Python 原始源码，页面会显示提示；任务状态不等同于游戏内成果验证。服务器只监听 127.0.0.1，无身份认证，因此请勿直接将端口公开到互联网。若端口已被占用，游戏日志会记录启动失败；该功能尚需本地 Gradle 编译与游戏内测试。
