# ==========================================
# Kurumi 全自动木棍生产线
#
# 原木箱：-21, 64, -8
# 木棍箱：-25, 63, -17
# ==========================================


# 查询指定箱子
def inspect_box(box, item_id):
    close_container()

    chest_target(box.x, box.y, box.z)
    goto(box.x, box.y, box.z)

    query = check_nearby_chest(item_id)
    sleep(1)

    box_result = get_nearby_chest_result(item_id)
    tries = 0

    while box_result.status == "pending" and tries < 5:
        sleep(1)
        box_result = get_nearby_chest_result(item_id)
        tries = tries + 1

    if box_result.status != "observed":
        talk("主人~ 箱子查询失败！")
        raise Exception("箱子查询未完成")

    return box_result


# 从箱子取原木
def fetch_logs(box, log_id, count):
    close_container()

    chest_target(box.x, box.y, box.z)
    goto(box.x, box.y, box.z)

    result = chest_take(log_id, count)

    if not result.success:
        talk(result.message)
        raise Exception(result.message)

    sleep(1)

    total = check_inventory_item(log_id)

    if total < count:
        talk("主人~ 取出的原木数量不足！")
        raise Exception("取出的原木不足")

    return count


# 工作台制作木板和木棍
def craft_sticks(log_id, plank_id, count):
    close_container()

    table = find_nearest_block("minecraft:crafting_table")

    if not table.found:
        talk("主人~ 附近找不到工作台！")
        raise Exception("附近找不到工作台")

    goto(table.x, table.y, table.z)
    look_at(table.x, table.y, table.z)

    opened = craft_open()

    if not opened.success:
        talk(opened.message)
        raise Exception(opened.message)

    # 原木转换成木板
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
        talk("主人~ 木板数量不足！")
        raise Exception("木板数量不足")

    # 木板转换成木棍
    while planks >= 2:

        # 整数除法
        half = planks // 2

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
            talk("主人~ 木板没有减少，合成失败！")
            raise Exception("木板未减少，合成失败")

        planks = after

    close_container()

    return check_inventory_item("minecraft:stick")


# 将木棍存入目标箱子
def store_sticks(box):
    close_container()

    chest_target(box.x, box.y, box.z)
    goto(box.x, box.y, box.z)

    result = chest_put_item_all("minecraft:stick")

    if not result.success:
        talk(result.message)
        raise Exception(result.message)

    sleep(1)

    return check_inventory_item("minecraft:stick")


# 自动生产主技能
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
        talk("主人~ 不支持这种原木！")
        raise Exception("未配置对应木板ID")

    talk("主人~ 开始自动制作木棍啦！")

    active = True

    while active:

        # 检查木棍箱容量
        room = inspect_box(stick_box, "minecraft:stick")

        if room.free_capacity < 8:
            talk("主人~ 木棍箱已经没有足够空间啦！")
            break

        # 检查原木库存
        source = inspect_box(log_box, log_id)

        if source.count == 0:
            talk("主人~ 樱花原木已经用完啦！")
            break

        count = source.count

        if count > 64:
            count = 64

        # 根据剩余容量调整数量
        capacity_limit = room.free_capacity // 8

        if count > capacity_limit:
            count = capacity_limit

        if count < 1:
            break

        # 取原木
        fetched = fetch_logs(log_box, log_id, count)

        # 制作木棍
        made = craft_sticks(log_id, plank_id, fetched)

        if made < fetched * 8:
            talk("主人~ 木棍产量不足！")
            raise Exception("合成木棍数量未达到预期")

        # 存入箱子
        remaining = store_sticks(stick_box)

        if remaining > 0:
            talk("主人~ 木棍没有全部存入箱子！")
            break

    close_container()
    chest_target()

    talk("主人~ 木棍生产任务结束啦！")

    return True


# ==========================================
# 主程序
# ==========================================

log_id = "minecraft:cherry_log"

log_box = pos(-21, 64, -8)

stick_box = pos(-25, 63, -17)

make_sticks(log_id, log_box, stick_box)
