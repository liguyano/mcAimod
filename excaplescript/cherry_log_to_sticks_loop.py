# Kurumi 樱花原木 -> 木棍自动流水线
# 使用前填写下面两个箱子坐标（0,0,0 是占位值，务必修改）
log_x = 0
log_y = 0
log_z = 0
stick_x = 0
stick_y = 0
stick_z = 0

if log_y == 0 or stick_y == 0:
    raise Exception("请先填写两个箱子的真实坐标")

talk("主人~ 樱花木棍生产线启动啦！")
running = True
while running:
    # 先检查木棍箱的剩余容量。1 根原木最多做 8 根木棍
    close_container()
    chest_target(stick_x,stick_y,stick_z)
    goto(stick_x,stick_y,stick_z)
    space = check_nearby_chest("minecraft:stick")
    sleep(1)
    space = get_nearby_chest_result("minecraft:stick")
    retries = 0
    while space.status == "pending" and retries < 5:
        sleep(1)
        space = get_nearby_chest_result("minecraft:stick")
        retries = retries + 1
    if space.status != "observed":
        raise Exception("未能确认木棍箱容量")
    if space.free_capacity < 8:
        talk("主人~ 木棍箱没有足够的位置了，生产结束！")
        break

    # 从指定原木箱取出不超过64根，且不超过下游箱子可容纳的数量
    chest_target(log_x,log_y,log_z)
    goto(log_x,log_y,log_z)
    source = check_nearby_chest("minecraft:cherry_log")
    sleep(1)
    source = get_nearby_chest_result("minecraft:cherry_log")
    retries = 0
    while source.status == "pending" and retries < 5:
        sleep(1)
        source = get_nearby_chest_result("minecraft:cherry_log")
        retries = retries + 1
    if source.status != "observed":
        raise Exception("无法确认樱花原木箱库存")
    if source.count == 0:
        talk("主人~ 樱花原木已经用完啦！")
        break

    amount = source.count
    if amount > 64:
        amount = 64
    limit = space.free_capacity / 8
    if amount > limit:
        amount = limit
    # 每轮最多64根；请确保背包有足够空位容纳产物
    if amount < 1:
        raise Exception("木棍箱已无足够空间")

    result = chest_take("minecraft:cherry_log",amount)
    if not result.success:
        raise Exception(result.message)
    logs = check_inventory_item("minecraft:cherry_log")
    if logs < amount:
        raise Exception("取原木后背包数量不足")

    # 开工作台，取出全部木板
    close_container()
    table = find_nearest_block("minecraft:crafting_table")
    if not table.found:
        raise Exception("找不到工作台，请把工作台放在两个箱子附近")
    goto(table.x,table.y,table.z)
    look_at(table.x,table.y,table.z)
    opened = craft_open()
    if not opened.success:
        raise Exception(opened.message)
    before = check_inventory_item("minecraft:cherry_planks")
    placed = put_item("minecraft:cherry_log",amount,"grid_1")
    if not placed.success:
        raise Exception(placed.message)
    sleep(1)
    taken = take_item("minecraft:cherry_planks")
    if not taken.success:
        raise Exception(taken.message)
    sleep(1)
    planks = check_inventory_item("minecraft:cherry_planks")
    if planks < before + amount * 4:
        raise Exception("木板产出不足，已停止以避免重复消耗材料")

    # 放两列木板制作木棍，最多一组64个一格
    while planks >= 2:
        half = planks / 2
        if half > 64:
            half = 64
        top = put_item("minecraft:cherry_planks",half,"grid_1")
        if not top.success:
            raise Exception(top.message)
        bottom = put_item("minecraft:cherry_planks",half,"grid_4")
        if not bottom.success:
            raise Exception(bottom.message)
        sleep(1)
        made = take_item("minecraft:stick")
        if not made.success:
            raise Exception(made.message)
        sleep(1)
        next_planks = check_inventory_item("minecraft:cherry_planks")
        if next_planks >= planks:
            raise Exception("木板没有减少，可能没有成功制作木棍")
        planks = next_planks

    # 存到指定木棍箱，检查背包残留；残留表示未全部存入，应停止
    close_container()
    chest_target(stick_x,stick_y,stick_z)
    goto(stick_x,stick_y,stick_z)
    sticks = check_inventory_item("minecraft:stick")
    if sticks == 0:
        raise Exception("未检测到制作完成的木棍")
    saved = chest_put_item_all("minecraft:stick")
    if not saved.success:
        raise Exception(saved.message)
    sleep(1)
    remainder = check_inventory_item("minecraft:stick")
    if remainder > 0:
        talk("主人~ 木棍箱已经装不下了，先停止加工！")
        break

close_container()
chest_target()
talk("主人~ 木棍生产线本轮完成啦！")
