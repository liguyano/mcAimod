# mcAimod — Minecraft 26.2 NeoForge AI Companion

AI 伙伴 Mod（当前开发版本 **v0.4.0**）：在游戏中与大模型聊天，让模型根据**当前玩家的真实游戏状态**回答问题。支持通过配置切换 **Ollama、阿里云百炼（Qwen）、DeepSeek、OpenAI** 等使用 Chat Completions 接口的服务。除基础 `/ai` 问答外，还包含客户端 Kurumi 聊天助手、有限的游戏动作与自由组合任务执行器。当前是开发中的实验功能，实际操作依赖游戏环境和服务端响应。

本项目持续迭代中，最新功能调整与修复记录见 [CHANGELOG.md](CHANGELOG.md)。

### Kurumi 多阶段 Agent（开发中）

**生存中断（实验性）：**开启 `/kurumi on` 与 `/kurumi auto on` 后，每10个客户端 tick 检查饥饿。饥饿值不高于14且正在执行任务时，向模型发送最简短的 EAT/WAIT 决策请求（最多等待15秒）；模型选择 EAT 才临时暂停伪代码执行并进食，选择 WAIT 则30秒后再评估。没有任务时直接尝试选择背包中营养值最高的食物进食，随后恢复原快捷栏物品。玩家受到附近生物攻击且生命值下降时，**不等待模型**，立即停止当前脚本/钓鱼/寻路，并通过 Baritone 尝试沿远离攻击者的方向无破坏移动约20格；会在聊天里告知主人。若中断了正在等待的 Agent 阶段，短暂逃离后才将中断原因发送给模型，让其检查环境并决定是否恢复。逃跑路径不能保证安全，也不会自动攻击敌人。首次运行请在安全环境实测，特别是网络服务器上的换手、进食和寻路行为。

**按需视觉观察（实验性）：**客户端 Kurumi 脚本可以运行 `image = screenshot()`、`observe(image)`，从当前游戏画面生成临时的缩小 JPEG，并在下一阶段将图片送入支持视觉的模型（例如 Qwen3.8-Max）。图像只在当前内存中暂存，不写入普通日志和历史变量；不会自动持续截屏。截图可能包含聊天栏、玩家名及游戏界面，请只使用可信的模型提供商。`observe(image)` 结束当前脚本并让 AI 重新决策，不会继续执行截图后的语句。需要客户端游戏内验证；不支持视觉的模型可能拒绝请求。

```python
image = screenshot()
observe(image)
```

**v0.4.0 自动钓鱼：**主人说「去钓鱼」时模型可以调用 `fish()`。Kurumi 检查背包已有鱼竿，选择附近加载区块中至少5×5开放水面的安全岸边，使用 Baritone 禁破坏寻路，自动抛竿、等待客户端鱼漂下沉信号、收竿并重复。`fish()` 启动后台任务后 Agent 仍能聊天；可以用 `observe(get_fishing_status())` 查询状态、`stop_fishing()` 停止。`/kurumi stop` 或主人直接说「停止」仍立即停止全部动作。鱼竿缺失或即将损坏、找不到安全水域、背包满、寻路/抛竿异常、连续多次无咬钩会中断并记录原因。鱼咬钩检测依靠客户端同步的鱼漂运动，仍需实服验证；「已抛竿」不等于「已获得鱼」。

Kurumi 的模型响应支持 `EXECUTE_SCRIPT` 与明确的 `finish`。一个脚本运行完毕只算完成当前阶段，客户端将真实执行反馈交还模型，由模型继续规划或确认完成。执行中同一玩家的后续消息会排队，在下一次决策时一起考虑；`/kurumi stop` 和主人的“停止”仍立即取消。

```python
items = get_inventory()
observe(items)  # 不向公屏发送 JSON，直接暂停本段脚本并交给 AI 分析
```

模型下一阶段可以用自己的语言分多次 `talk("...")` 回复玩家，再明确返回 `{"type":"finish","reason":"已回答背包物品"}`。`talk()` 每条仍不超过120字符且间隔至少2秒。变量在单次 Agent 任务的各段脚本之间保留，不设固定数量上限，但受单值/总内存和总运行时间保护。大量旧工具反馈会按近期记录和较旧索引整理；需要重读时使用 `observe(get_agent_observation(编号))`。断线、停止、超时和阶段预算用尽属于中断，不等于模型确认完成。

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

## Kurumi 游戏助手（v0.4.0 开发中）

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

基础 `/ai` 问答与 Kurumi 客户端助手并存。**v0.4.0** 新增后台自动钓鱼；保留受限游戏动作、自由组合任务、箱子检查、逐步验证及部分任务恢复功能。完整游戏内自动化测试仍待补充。


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

运行时 `for` 循环示例（最多100轮，支持 `break`、`continue`）：

原先的 `repeat(n):` 已移除，请统一使用 `for i in range(n):`。

```python
for i in range(1, 6):
    if i == 3:
        continue
    talk(i)
```

脚本支持 `while`、`if`、`else`、`break`、`return`、`result = action(...)`、`result.success`、`not result.success`、`check_chest(...)`、`check_inventory(...)`。支持 `def` 自定义函数（仅顶层定义，最多6个参数）、局部变量、`return` 表达式及函数间调用；**禁止直接与间接递归**，重复工作请使用 `for` / `while`。`range(count)` 支持传入整数变量，运行时仍限制最多100轮。

```python
def walk_twice(distance):
    for i in range(2):
        result = move("forward", distance)
    return result

outcome = walk_twice(3)
observe(outcome)
```

动作函数白名单：`attack_mob`、`attack_player`、`pickup_item_all`、`chest_put_item_all`、`equip_armor`、`equip_main_hand`、`mine`。不支持任意 Python 导入模块或执行系统代码。动作成功表示原执行器验证通过；攻击目前仅代表攻击尝试结束，不能证明目标死亡。每级缩进必须为 4 个空格；保留循环次数、总时间与动作数上限。


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

### 主人批准的长期技能与快捷指令（试验版）

### 脚本主动异常：`raise Exception(...)`

受限伪代码支持 `raise Exception("失败原因")` 和 `raise Exception(result.message)`，可放在 `if`、`for`、`while` 和 `def` 函数内部。只有一个字符串字面量或变量属性路径可作为参数；不支持其他异常类、任意 Python 执行或多参数异常。执行到此语句时当前脚本**立即以失败状态结束**，日志会包含源码行号和异常原因，后续由 AI 根据实际报告决定是否重新规划，不会自动重复失败操作。此版本的 `observe()` 暂时仍会交还控制权；非阻塞观察将在兼容性处理后单独上线。

```python
for i in range(10):
    result = pickup_item_all("minecraft:wheat")
    if not result.success:
        raise Exception(result.message)
```

Kurumi 可在实际验证操作成功之后，建议将可重复使用的顶层 `def` 函数保存成长期技能，并提出诸如「捡尸体」的完整快捷触发词。**只有主人回复「保存」才会写入** `config/mcaimod-skills.json`；回复「不保存」或「取消」即放弃。修改已有技能时保存新版本，并记录至多五份历史代码。

每轮大模型只会收到已存技能的英文名称与标题，不把整套脚本塞进提示词；需要查看时可在受限脚本里调用 `skill = get_skill("loot_corpse")`、`observe(skill)` 获取指定源码，再规划后续动作。

主人发出已批准的**完全匹配**快捷词时，客户端直接运行保存的无参技能函数，成功路径不调用 AI。快捷脚本末尾必须返回 `{"success":true,"completed":true}` 形式的已确认结果，才会直接认定成功；否则将实际报告交给 AI 分析，由 AI 决定是否修正或放弃，绝不直接自动重试。快捷执行世界动作需 `/kurumi auto on`。所有脚本仍受原执行器的安全白名单与预算约束。此功能尚需 Windows JDK 25 编译及游戏内验证。

Kurumi 客户端加载时尝试启动只读 HTTP 服务，浏览器打开 **http://127.0.0.1:8765/**。每秒刷新 `/api/status`，展示最新 EXECUTE_SCRIPT 的完整源码、正在执行的源码行、操作类型、动作数、循环计数、最近结果及最近 100 条任务事件。源码行高亮由解析器保留的原始行号驱动。EXECUTE_PROGRAM 没有 Python 原始源码，页面会显示提示；任务状态不等同于游戏内成果验证。服务器只监听 127.0.0.1，无身份认证，因此请勿直接将端口公开到互联网。若端口已被占用，游戏日志会记录启动失败；该功能尚需本地 Gradle 编译与游戏内测试。
