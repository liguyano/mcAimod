# Kurumi def 技能版：樱花原木箱 -> 工作台 -> 木棍箱
# 这是 Kurumi 的 Python 风格脚本，不是标准 Python。
# 请在脚本底部填写两个箱子坐标；先用少量材料测试。

def inspect_box(x, y, z, item_id):
    close_container()
    chosen = chest_target(x, y, z)
    if not chosen.success:
        raise Exception(chosen.message)
    goto(x, y, z)
    pending = check_nearby_chest(item_id)
    sleep(1)
    snapshot = get_nearby_chest_result(item_id)
    retry = 0
    while snapshot.status == "pending" and retry < 5:
        sleep(1)
        snapshot = get_nearby_chest_result(item_id)
        retry = retry + 1
    if snapshot.status != "observed":
        raise Exception("无法读取目标箱子")
    return snapshot

def fetch_logs(x, y, z, amount):
    chest_target(x, y, z)
    goto(x, y, z)
    moved = chest_take("minecraft:cherry_log", amount)
    if not moved.success:
        raise Exception(moved.message)
    sleep(1)
    total = check_inventory_item("minecraft:cherry_log")
    if total < amount:
        raise Exception("背包原木不足，可能是箱子取物失败")
    return total

def craft_batch(amount):
    close_container()
    table = find_nearest_block("minecraft:crafting_table")
    if not table.found:
        raise Exception("附近未找到工作台")
    goto(table.x, table.y, table.z)
    look_at(table.x, table.y, table.z)
    opened = craft_open()
    if not opened.success:
        raise Exception(opened.message)
    before = check_inventory_item("minecraft:cherry_planks")
    placed = put_item("minecraft:cherry_log", amount, "grid_1")
    if not placed.success:
        raise Exception(placed.message)
    sleep(1)
    collected = take_item("minecraft:cherry_planks")
    if not collected.success:
        raise Exception(collected.message)
    sleep(1)
    planks = check_inventory_item("minecraft:cherry_planks")
    if planks < before + amount * 4:
        raise Exception("未取得预期数量的木板")
    while planks >= 2:
        half = planks / 2
        if half > 64:
            half = 64
        top = put_item("minecraft:cherry_planks", half, "grid_1")
        if not top.success:
            raise Exception(top.message)
        bottom = put_item("minecraft:cherry_planks", half, "grid_4")
        if not bottom.success:
            raise Exception(bottom.message)
        sleep(1)
        sticks = take_item("minecraft:stick")
        if not sticks.success:
            raise Exception(sticks.message)
        sleep(1)
        remaining = check_inventory_item("minecraft:cherry_planks")
        if remaining >= planks:
            raise Exception("木板未减少，合成可能失败")
        planks = remaining
    close_container()
    return check_inventory_item("minecraft:stick")

def store_sticks(x, y, z):
    chest_target(x, y, z)
    goto(x, y, z)
    moved = chest_put_item_all("minecraft:stick")
    if not moved.success:
        raise Exception(moved.message)
    sleep(1)
    return check_inventory_item("minecraft:stick")

# 主程序：改成你的两个箱子坐标
log_x = 0
log_y = 0
log_z = 0
stick_x = 0
stick_y = 0
stick_z = 0

if log_y == 0 or stick_y == 0:
    raise Exception("请先填写原木箱和木棍箱坐标")

talk("主人~ Kurumi 开始自动制作木棍啦！")
working = True
while working:
    capacity = inspect_box(stick_x, stick_y, stick_z, "minecraft:stick")
    if capacity.free_capacity < 8:
        talk("木棍箱没有足够的空间，结束任务！")
        break

    source = inspect_box(log_x, log_y, log_z, "minecraft:cherry_log")
    if source.count == 0:
        talk("樱花原木用完啦，任务完成！")
        break

    amount = source.count
    if amount > 64:
        amount = 64
    limit = capacity.free_capacity / 8
    if amount > limit:
        amount = limit
    if amount < 1:
        break

    taken = fetch_logs(log_x, log_y, log_z, amount)
    produced = craft_batch(amount)
    if produced < amount * 8:
        raise Exception("木棍数量不足，请检查工作台")

    left = store_sticks(stick_x, stick_y, stick_z)
    if left > 0:
        talk("木棍箱已无法完全存入木棍，停止加工！")
        break

close_container()
chest_target()
talk("主人~ Kurumi 的木棍加工任务结束啦！")
