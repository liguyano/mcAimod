# Kurumi 伪代码，不是 CPython。独立于砍树技能。
# center 的 y 是树苗要放置的空气方块高度（泥土上方一格）。
# side 是正方形边长；每隔一格种一棵，因此格点间距为2。
# 单次最多 12x12，以免耗尽 Kurumi 的动作预算；更大区域可分批执行。
def plant_square(center, sapling_id, side):
    if side < 1 or side > 12:
        raise Exception("边长须在1到12格之间，大面积请分批种植")
    if sapling_id == "minecraft:dark_oak_sapling":
        raise Exception("深色橡木要2x2树苗，暂不支持单棵间隔种植")
    if sapling_id == "minecraft:mangrove_propagule":
        talk("红树胎生苗可以种，但请确认水域与地面条件")
    if check_inventory_item(sapling_id) < 1:
        raise Exception("背包没有指定树苗")

    close_container()
    start_x = center.x - side // 2
    start_z = center.z - side // 2
    planted = 0
    failed = 0

    # 行列坐标都每次+2，相邻树苗中间空一格。
    for dz in range(0, side, 2):
        for dx in range(0, side, 2):
            if check_inventory_item(sapling_id) < 1:
                talk("树苗用完，提前结束")
                return planted
            x = start_x + dx
            z = start_z + dz

            # 站在斜对角邻格，避免玩家身体挡住放置坐标。
            moved = goto(x + 1, center.y, z + 1)
            if not moved.success:
                failed = failed + 1
                talk(moved.message)
                continue
            held = equip_main_hand(sapling_id)
            if not held.success:
                raise Exception(held.message)
            sleep(1)
            result = place_block(sapling_id, x, center.y, z)
            if result.success:
                planted = planted + 1
                sleep(1)
            else:
                failed = failed + 1
                talk(result.message)

    talk("树苗种植遍历完成，请检查是否都已成功落地")
    return planted


# 直接执行示例：更改树苗ID、中心点和正方形边长。
# 若边长为9，则最多为5x5=25个种植点。
plant_square(pos(-21, 64, -8), "minecraft:cherry_sapling", 9)
