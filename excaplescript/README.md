# Kurumi 示例脚本

## `tree_planting.py`

独立种树技能 `plant_square(center, sapling_id, side)`，与砍树脚本互不依赖。以 `center = pos(x,y,z)` 为正方形中心，`side` 为自定义边长（当前每次 1～12 格）；种植点 X/Z 均每次加 2，确保相邻树苗之间空一格。`center.y` 必须是树苗要占据的**空气格 Y 坐标**，下方应有适合种树的泥土等支撑方块。例：`plant_square(pos(-21,64,-8), "minecraft:cherry_sapling", 9)`，最多尝试 25 处种植。

脚本依次移动到目标斜对角邻格、把指定树苗拿到主手、调用 `place_block(id,x,y,z)`，失败时记录信息并尝试下一处；背包树苗用尽则提前返回。暂不支持需要 2×2 树苗的深色橡木。`place_block` 的成功只表示交互请求已被接受，脚本未逐格重新扫描服务器确认，所以首次建议在平整、已加载的草地测试。单次边长上限与解释器动作预算有关，较大区域请分批执行。

要保存为 Kurumi Skill，仅复制从 `def plant_square(...)` 开始的函数定义，不要复制文件末尾的调用示例。此脚本是 Kurumi 伪代码，不能交给 CPython 执行。

## `tree_harvest_16.py`

以 `harvest_trees(center, log_id, box)` 为入口的砍树技能示例。先移动到中心点，选择中心点水平 ±16 格、垂直 ±8 格内的指定原木进行砍伐，再处理对应树叶；之后等待掉落物、两次调用 `pickup_all()`（包含树苗），最后存入箱子并关闭连锁。单次最多尝试 12 个原木目标和 12 个树叶目标，避免脚本动作预算失控。

- `center` 为 `pos(x,y,z)`；`log_id` 如 `"minecraft:cherry_log"`。
- `box` 可指定 `pos(x,y,z)`，或使用 `pos(0,0,0)` 自动选择**到达中心时**附近最近的普通箱子。
- 支持樱花、橡木、云杉、白桦、丛林、金合欢、深色橡木、红树、苍白橡木及对应树苗（红树是胎生苗）。
- **注意**：脚本只按中心范围筛选操作坐标；连锁破坏模组自身可能波及范围外。`search_all_block()` 只返回当前位置周围最多 64 个匹配方块，且上下扫描仅 ±8 格，因此不保证穷尽整个 16 格区域。若目标移不到或无法破坏，会安全停止该阶段。
- `pickup_all()` 只拾取角色附近可到达的掉落物，不保证整个区域的物品都能收到；背包已有同类物品也会被 `chest_put_item_all()` 一起存放。中途强制停止或报错时，可能需要手动执行 `chain_off()`、`chest_target()`。
- 这是 Kurumi 的受限伪代码，**不是系统 Python**。复制顶部 `def` 定义保存为技能，或直接执行整个文件。尚未在真实游戏中验证，建议先在测试世界运行。

## `cherry_log_to_sticks_loop.py`

樱花原木箱 -> 工作台 -> 木棍箱，循环生产。指定两个箱子坐标后，脚本首先读取木棍箱实际可用容量，然后读取樱花原木箱库存，每轮最多取64根，并且按木棍箱可容纳数量缩减取出数量。合成后检查背包木板、木棍数量，存放失败或箱子满就停止，不无限重试。

**开始前必须编辑文件顶部的六个坐标值：** `log_x/log_y/log_z` 与 `stick_x/stick_y/stick_z`。示例 0 坐标仅是占位符。工作台通过 `find_nearest_block("minecraft:crafting_table")` 搜索，需要位于操作路径附近。

将文件内容复制到 Kurumi Web 的“直接执行脚本”区域，不需要 Python。需要最新 GitHub main 分支代码。

**限制：**
- 脚本以背包原本没有其他樱花原木、樱花木板或木棍为前提；否则数量核对和原料上限会混合计算。
- 每轮最多64根原木，会产出最多256木板 / 512木棍；玩家背包需要足够的空格，箱子也必须已加载可交互。
- 产物使用原版工作台 Shift 快速移动，实际游戏及服务器菜单同步必须实测，`success` 不等于服务端已确认。
- `check_nearby_chest` 返回 `free_capacity`（空格按64容量+已有同类堆叠剩余容量；适用于64堆叠的樱花原木和木棍），以及 `empty_slots`、`full_for_item`；结果来自已同步的箱子菜单，不代表任何时候都能成功放入。
- 任务受解释器动作数上限（200）、循环和总执行时限限制；大量原木时可能触发安全预算，不能保证单次脚本处理无限多组。
- `chest_target(x,y,z)` 固定随后箱子操作的目标；最后 `chest_target()` 解除目标。容器间切换通过 `close_container()`。
- 若箱子查询超时、材料不匹配、背包容量不足，主动抛出错误交给 AI；不会绕过错误继续消耗材料。

当前脚本属于尚未在真实游戏运行验证的示例，不应在装有贵重物品的世界中首次试用。建议先用少量原木验证每个步骤。

## `cherry_log_to_sticks_def.py`

与上述生产线同样用途，但将操作拆成四个带参数的 `def` 技能：`inspect_box(x,y,z,item_id)` 查询指定箱子、`fetch_logs(x,y,z,amount)` 取原木、`craft_batch(amount)` 合成木板和木棍、`store_sticks(x,y,z)` 存入目标箱。主循环按目标箱剩余容量和来源数量选择每批最多64根原木。需要填写脚本末尾的六个坐标并在 Kurumi Web 直接执行，不是系统 Python。当前未编译/游戏内实测；保留前述背包空间、材料混入和解释器执行预算限制。

## `cherry_log_to_sticks_struct.py`

坐标使用 `pos(x,y,z)` 构造包含 `.x/.y/.z` 的对象。总技能 `make_sticks(log_id, log_box, stick_box)` 只接收三项参数：原木 ID、原木箱位置、木棍箱位置；示例支持常见九种原木（其余需扩展木板映射）。`pos()` 是 Mod 新增的 Kurumi 表达式，不是标准 Python 的函数。请修改文件最下方两个 `pos(...)` 坐标。首次建议少量测试，原本的解释器动作次数/循环上限和背包净空限制仍适用。未编译实测。

## 技能库 import 调用

保存技能时保留仅顶层 `def make_sticks(log_id, log_box, stick_box):` 等定义代码（不要把示例尾部的调用行一起保存为技能）。经主人批准保存后，Kurumi 的大模型可从技能列表查看名称、描述和参数签名，生成以下脚本：

```python
from skills import make_sticks
make_sticks("minecraft:cherry_log", pos(100,64,200), pos(110,64,200))
```

也支持 `import skills.make_sticks` 后用 `skills.make_sticks(...)`。这是 **Kurumi 限定的技能导入语法**，不会执行任意 Python import、下载模块或从 GitHub 自动加载；来源只限本机 `config/mcaimod-skills.json` 中已批准的技能。缺少参数时 AI 应先询问。现有无参快捷技能仍可运行；需要先开启 `/kurumi auto on`。以上功能尚未编译/游戏内实测。

## 技能分组

Kurumi Web 的「手动新增 / 编辑技能」现在新增 **分组名** 输入框，默认为 `skills`。保存到 `config/mcaimod-skills.json` 时写入 `group`，旧技能没有 group 字段的自动视为 skills，不需要迁移。分组名和函数名使用小写字母、数字、下划线（必须字母开头，最长24字符）。同一组内函数名唯一，不同组允许同名技能；快捷触发词仍必须全局唯一。

例如将 `make_sticks` 保存到 `crafting` 后，AI 可以生成：

```python
from crafting import make_sticks
make_sticks("minecraft:cherry_log", pos(100,64,200), pos(110,64,200))
```

也可以写：

```python
import crafting.make_sticks
crafting.make_sticks("minecraft:cherry_log", pos(100,64,200), pos(110,64,200))
```

注意这是 Kurumi 的受限技能导入语法，**不是标准 Python 的任意模块加载**。不能从两个不同分组同时导入相同的函数名（解释器仅支持一份同名函数定义）。旧的 `from skills import ...` 保持可用。功能尚待编译与实际游戏验证。
