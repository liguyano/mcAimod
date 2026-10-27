# Kurumi Python 风格伪代码；不是 CPython。坐标用 pos(x,y,z) 表示。
# 运行前只修改文件末尾的 log_box 和 stick_box。
def inspect_box(box, item_id):
    close_container()
    selected = chest_target(box.x, box.y, box.z)
    if not selected.success:
        talk(selected.message)
        raise Exception(selected.message)
    goto(box.x, box.y, box.z)
    check_nearby_chest(item_id)
    sleep(1)
    result = get_nearby_chest_result(item_id)
    tries = 0
    while result.status == "pending" and tries < 5:
        sleep(1)
        result = get_nearby_chest_result(item_id)
        tries = tries + 1
    if result.status != "observed":
        talk("箱子查询未完成")
        raise Exception("箱子查询未完成")
    return result

def fetch_logs(box, log_id, count):
    close_container()
    chest_target(box.x, box.y, box.z)
    goto(box.x, box.y, box.z)
    result = chest_take(log_id, count)
    if not result.success:
        talk(result.message)
        raise Exception(result.message)
    sleep(1)
    if check_inventory_item(log_id) < count:
        talk("取出的原木不足")
        raise Exception("取出的原木不足")
    return count

def craft_sticks(log_id, plank_id, count):
    close_container()
    table = find_nearest_block("minecraft:crafting_table")
    if not table.found:
        talk("附近找不到工作台")
        raise Exception("附近找不到工作台")
    goto(table.x, table.y, table.z)
    look_at(table.x, table.y, table.z)
    opened = craft_open()
    if not opened.success:
        talk(opened.message)
        raise Exception(opened.message)
    placed = put_item(log_id, count, "grid_1")
    if not placed.success:
        talk(placed.message)
        raise Exception(placed.message)
    sleep(1)
    planks_result = take_item(plank_id)
    if not planks_result.success:
        talk(planks_result.message)
        raise Exception(planks_result.message)
    sleep(1)
    planks = check_inventory_item(plank_id)
    if planks < count * 4:
        talk("木板数量不足")
        raise Exception("木板数量不足")
    while planks >= 2:
        half = planks / 2
        if half > 64:
            half = 64
        top = put_item(plank_id, half, "grid_1")
        if not top.success:
            talk(top.message)
            raise Exception(top.message)
        bottom = put_item(plank_id, half, "grid_4")
        if not bottom.success:
            talk(bottom.message)
            raise Exception(bottom.message)
        sleep(1)
        made = take_item("minecraft:stick")
        if not made.success:
            talk(made.message)
            raise Exception(made.message)
        sleep(1)
        after = check_inventory_item(plank_id)
        if after >= planks:
            talk("木板未减少，合成失败")
            raise Exception("木板未减少，合成失败")
        planks = after
    close_container()
    return check_inventory_item("minecraft:stick")

def store_sticks(box):
    chest_target(box.x, box.y, box.z)
    goto(box.x, box.y, box.z)
    result = chest_put_item_all("minecraft:stick")
    if not result.success:
        talk(result.message)
        raise Exception(result.message)
    sleep(1)
    return check_inventory_item("minecraft:stick")

# 一键调用：原木ID、原木箱坐标对象、木棍箱坐标对象
def make_sticks(log_id, log_box, stick_box):
    plank_id = ""
    if log_id == "minecraft:cherry_log":
        plank_id = "minecraft:cherry_planks"
    if log_id == "minecraft:oak_log":
        plank_id = "minecraft:oak_planks"
    if log_id == "minecraft:spruce_log":
        plank_id = "minecraft:spruce_planks"
    if log_id == "minecraft:birch_log":
        plank_id = "minecraft:birch_planks"
    if log_id == "minecraft:jungle_log":
        plank_id = "minecraft:jungle_planks"
    if log_id == "minecraft:acacia_log":
        plank_id = "minecraft:acacia_planks"
    if log_id == "minecraft:dark_oak_log":
        plank_id = "minecraft:dark_oak_planks"
    if log_id == "minecraft:mangrove_log":
        plank_id = "minecraft:mangrove_planks"
    if log_id == "minecraft:pale_oak_log":
        plank_id = "minecraft:pale_oak_planks"
    if plank_id == "":
        talk("此脚本暂未配置该原木的木板ID")
        raise Exception("此脚本暂未配置该原木的木板ID")
    talk("主人~ 开始自动加工木棍！")
    active = True
    while active:
        room = inspect_box(stick_box, "minecraft:stick")
        if room.free_capacity < 8:
            talk("木棍箱剩余空间不足，结束任务！")
            break
        source = inspect_box(log_box, log_id)
        if source.count == 0:
            talk("原木箱没有原木啦，任务完成！")
            break
        count = source.count
        if count > 64:
            count = 64
        capacity_limit = room.free_capacity / 8
        if count > capacity_limit:
            count = capacity_limit
        if count < 1:
            break
        fetched = fetch_logs(log_box, log_id, count)
        made = craft_sticks(log_id, plank_id, fetched)
        if made < fetched * 8:
            talk("合成木棍数量未达到预期")
            raise Exception("合成木棍数量未达到预期")
        remaining = store_sticks(stick_box)
        if remaining > 0:
            talk("木棍箱已满，结束任务！")
            break
    close_container()
    chest_target()
    return True

# === 只需修改下面三个变量 ===
log_id = "minecraft:cherry_log"
log_box = pos(100, 64, 200)
stick_box = pos(110, 64, 200)

make_sticks(log_id, log_box, stick_box)
