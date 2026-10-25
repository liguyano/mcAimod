# 更新日志 / Changelog

从本次更新开始，每次功能修改或问题修复都应新增记录（日期、改动、测试）。
未发布改动记录在 Unreleased，发布时再归入对应版本。

## Unreleased — 2026-10-25

### 新增

- 修复带可选槽位参数的赋值调用 `result = put_item("minecraft:wheat",22,"grid_1")`
  被误判为参数错误的问题；`put_item` 允许2或3个参数，`take_item` 仍要求2个。
  复用现有失败测试并增加拒绝多余参数的回归断言；由用户本地运行 Gradle。
- 所有受支持的游戏动作可通过赋值取得 `success`、`completed`、`message`，如
  `accepted = tpa_accept()`；未确认最终结果的动作保持 `completed=false`。
  增加解析器测试与受限操作权限校验，Gradle 构建仍由用户本地执行。
- 允许 `result = put_item("minecraft:bread",1,"auto")` 和
  `result = take_item("minecraft:bread",1)`，返回 `success`、`completed`、`message`。
  客户端槽位操作仅表示点击请求已提交，`completed=false`，不冒充服务器已确认转移；
  `observe(result)` 可回传给模型，必要时再调用 `inspect_container()` 核对槽位。
  添加赋值调用和非法参数测试，Gradle 构建仍由用户本地执行。
- 移除自动合成指令 `craft_item()` / `CRAFT_ITEM`：脚本解析、组合计划和旧动作接口均不再接受，
  同时删除 AI 与 Web 编辑器提示中的该指令；工作台 `interact`、`put_item`、`take_item` 槽位操作保留。
  添加拒绝自动合成及保留手动槽位合成的测试；实际 Gradle 构建由用户本地执行。
- 修复 `hold_use_item()` 无参数时仍被脚本解析器接受的问题：普通调用与赋值调用
  都必须提供非空物品ID参数和持续秒数参数；补充无参数及缺少秒数的拒绝测试。
- 修复 `hold_use_item()` 解析测试失败：脚本语法已经支持，但 `KurumiTaskPlanner`
  的动态调用与赋值动作白名单漏掉了新方法；现在两处均允许调用。
- 修复 Minecraft 26.2 编译错误：`KurumiItemUseBridge` 不再读取当前映射中不存在的 `Minecraft.screen` 字段，
  继续通过容器状态取消使用操作。`install-mod.ps1` 增加 UTF-8 BOM，兼容 Windows PowerShell 5.1
  对脚本中中文提示与中文 mods 路径的解码，避免字符串解析错误。
- 按使用者要求简化睡觉判定：`sleep_in_bed()` 只要床右键交互被接受，就立即认定本次
  动作完成，不等待 `isSleeping()` 校验；保留90秒重复交互保护，并统一普通 `interact()`
  的床交互记录，避免 AI 不断重复操作。日志明确注明“动作已执行”而非“已确认入睡”。
- 此前曾尝试通过等待客户端确认入睡来避免重复睡觉；本次已按上条规则取消等待，
  改为交互接受即完成。保留90秒防重复保护、跨世界清除缓存和相应单元测试。
- 修复 `use_item()` 只发送一次使用请求、没有实际保持使用键的问题：开始使用时按住游戏的
  `keyUse`，观察到使用完成或超时/取消时释放；新增 `hold_use_item("物品ID", 秒数)`
  允许指定1—30秒持续按住右键，适用于食物及其他持续使用的物品。
  关闭容器、停止脚本、退出世界等取消路径释放按键，增加脚本解析测试；本地游戏内验证待进行。
- 新增 `take_all_items()`：已打开任意带物品槽的容器（包括 Corpse 尸体界面、箱子及模组容器）后，
  自动逐槽快速取出当前页面可拿物品，不切换其他页面、不触碰玩家背包的来源槽；
  分多 Tick 进行以避免一次性大量点击，背包无法再容纳物品或容器关闭时停止，日志记录操作次数和剩余组数。
  为脚本解析器添加调用测试，并更新 AI 与 Web 脚本提示；构建及游戏内验证仍由用户本地完成。
- 主人执行中的新消息现在会暂停当前脚本与 Baritone 寻路，并在下一次 Agent 决策中结合
  取消原因和排队消息重新规划，避免继续追逐已经消失的旧目标；普通玩家消息仍按原队列处理。
- `scan_nearby_entities()` 增加 UUID，`interact("实体ID", "UUID")` 支持精确交互指定实体；
  若该实体消失或超出交互距离，会明确报错而不会误开另一具同类型尸体。
  AI 提示要求寻路后重新扫描、验证实体 UUID，再决定是否交互。
- 本轮仅完成代码静态检查，尚未本地编译或游戏内验证（由使用者在 Windows 上构建）。
- 修复 Minecraft 26.2 的 JourneyMap 地点编译错误：`ResourceKey<Level>` 使用
  `identifier()` 获取维度 ID（替代已不存在的 `location()`），涵盖保存、读取、导航及 JourneyMap 插件。
- 安装脚本在 JAR 早于任一源码或构建配置时直接拒绝安装，避免 Gradle 编译失败后把旧 JAR
  误当作新版本移动进 `mods`。
- 新增客户端开关配置 `config/mcaimod-settings.json`：`kurumi_enabled` 和
  `auto_enabled` 两项均默认为 `false`。启动及进入世界时读取配置，
  `/kurumi on/off`、`/kurumi auto on/off` 立即同步保存；关闭 Kurumi 时一并关闭 auto。
  保存与加载结果显示在 Kurumi 日志；设置文件无效时安全关闭，不覆盖损坏的内容。
- 地点记忆现统一使用 JourneyMap 6.0 API v2：保留原来的“记住 / 地点列表 / 在哪里 / 去 / 忘记”
  语句和 `gotoNamed` 导航，但读取的都是 JourneyMap 路径点（包括玩家手动创建的）。
  Kurumi 新增的路径点持久保存在 JourneyMap；“忘记”仅能删除当前维度由 mcaimod 创建的路径点。
  同名地点和跨维度导航会报告明确错误。旧版 `config/mcaimod-locations.json` 不迁移、不读取、不删除。
  构建时从 JourneyMap Maven 获取 `journeymap-api-neoforge:26.2-2.0.0`，仅用于编译，运行时由 JourneyMap 提供。
- Windows 部署脚本 install-mod.ps1：按照 gradle.properties 中的版本号，从 build/libs 选择对应的 mcaimod JAR
  移动到指定 NeoForge 26.2.0.93 实例的 mods 文件夹，覆盖同名文件，并清除同一 Mod 的
  其他旧版本 JAR 以避免重复加载；保留其他 Mod。
- 将在线玩家名单动态传给大模型，帮助其根据主人说的别名、简称或拼写误差
  选择真实玩家账号；攻击、看向、跟随、过来、TPA 和玩家距离查询统一解析。
  唯一匹配时自动使用规范名称，多个候选时返回名单让 AI 继续判断。
- 增加可调用视角动作 look(yaw,pitch) 和 look_at(x,y,z / 方块或实体ID)：
  控制玩家视角朝向指定角度、坐标或附近目标，限制无效角度和世界外坐标；
  不会自行点击/破坏目标；新增解析与视角计算测试。
- look_at 进一步支持玩家名，以及“主人”“ご主人様”别名（映射已有主人
  SekaiSaionji）：看向64格内在线玩家的眼睛，并对找不到玩家或看向自身返回明确错误。
- 给 Kurumi 增加探索式交互推理：不熟悉操作时先观察对象与距离，
  再尝试适合的交互，检查结果、根据失败原因修正；对危险操作不盲试。
- 扩展 interact(id)：保留附近方块右键交互，也能与附近实体交互；
  minecraft:oak_boat 等物品 ID 可以匹配相应船实体。
- 增加 use_item(id)：主手持有指定物品时开始持续使用，并等待客户端观察到物品消耗；
  可从脚本调用，支持变量及动作结果。
- 更新模型操作说明，明确方块、实体、持续使用物品和容器菜单的区别。
- 增加实体 ID 映射和 use_item 脚本解析测试。

### 修复

- 修复最近箱子搜索脚本使用 best_d = 999999999 时因表达式数值上限过低而报“数值越界”的问题。
  表达式中间值支持安全整数精度范围内的较大距离平方，坐标/动作整数参数仍保留 ±3000 万限制。

### 安全及限制

- 成功调用交互不等于服务端确认成功上船；需要下一步观察。
- 使用前需先装备物品；持续使用有 12 秒超时，停止指令会取消使用。
- 本次尚未在真实 Minecraft 世界进行联机验证。
