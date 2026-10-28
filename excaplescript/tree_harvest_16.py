# Kurumi 伪代码，不是 CPython。
# 复制 def 部分保存为技能；文件末尾是直接执行的示例。
# 中心点周围水平16格、上下8格；每种方块最多处理12次。
# 注意：连锁破坏本身的波及范围由连锁模组决定，未必受16格约束。
def harvest_trees(center, log_id, box):
    leaf_id = ""
    sapling_id = ""
    if log_id == "minecraft:cherry_log":
        leaf_id = "minecraft:cherry_leaves"
        sapling_id = "minecraft:cherry_sapling"
    if log_id == "minecraft:oak_log":
        leaf_id = "minecraft:oak_leaves"
        sapling_id = "minecraft:oak_sapling"
    if log_id == "minecraft:spruce_log":
        leaf_id = "minecraft:spruce_leaves"
        sapling_id = "minecraft:spruce_sapling"
    if log_id == "minecraft:birch_log":
        leaf_id = "minecraft:birch_leaves"
        sapling_id = "minecraft:birch_sapling"
    if log_id == "minecraft:jungle_log":
        leaf_id = "minecraft:jungle_leaves"
        sapling_id = "minecraft:jungle_sapling"
    if log_id == "minecraft:acacia_log":
        leaf_id = "minecraft:acacia_leaves"
        sapling_id = "minecraft:acacia_sapling"
    if log_id == "minecraft:dark_oak_log":
        leaf_id = "minecraft:dark_oak_leaves"
        sapling_id = "minecraft:dark_oak_sapling"
    if log_id == "minecraft:mangrove_log":
        leaf_id = "minecraft:mangrove_leaves"
        sapling_id = "minecraft:mangrove_propagule"
    if log_id == "minecraft:pale_oak_log":
        leaf_id = "minecraft:pale_oak_leaves"
        sapling_id = "minecraft:pale_oak_sapling"
    if leaf_id == "":
        raise Exception("暂不支持这种原木，请先配置树叶和树苗ID")

    close_container()
    arrived = goto(center.x, center.y, center.z)
    if not arrived.success:
        raise Exception(arrived.message)

    # box = pos(0,0,0) 表示自动找中心点最近的普通箱子。
    if box.x == 0 and box.y == 0 and box.z == 0:
        box = find_nearest_block("minecraft:chest")
        if not box.found:
            raise Exception("中心附近没有找到普通箱子")
    talk("开始砍树：先原木，再树叶，最后拾取树苗")
    enabled = chain_on()
    if not enabled.success:
        raise Exception(enabled.message)

    # 每次重新扫描；仅选择中心点附近的坐标，避免 mine(ID) 无边界寻路。
    for phase in range(2):
        target_id = log_id
        if phase == 1:
            target_id = leaf_id
        for attempt in range(12):
            blocks = search_all_block(target_id)
            found = False
            target = center
            for i in range(len(blocks)):
                p = blocks[i]
                if p.x >= center.x - 16 and p.x <= center.x + 16:
                    if p.z >= center.z - 16 and p.z <= center.z + 16:
                        if p.y >= center.y - 8 and p.y <= center.y + 8:
                            target = p
                            found = True
                            break
            if not found:
                break
            moved = goto(target.x, target.y, target.z)
            if not moved.success:
                talk(moved.message)
                break
            broken = break_block(target_id, target.x, target.y, target.z)
            if not broken.success:
                talk(broken.message)
                break
            sleep(1)

    # 先砍树叶再拾取；给树苗和苹果掉落较长时间。
    sleep(8)
    picked = pickup_all()
    if not picked.success:
        talk(picked.message)
    sleep(8)
    picked = pickup_all()
    if not picked.success:
        talk(picked.message)

    close_container()
    moved = goto(box.x, box.y, box.z)
    if not moved.success:
        chain_off()
        raise Exception(moved.message)
    chest_target(box.x, box.y, box.z)
    # 只存本次树种对应的产物；不清空背包其他物品。
    chest_put_item_all(log_id)
    chest_put_item_all(sapling_id)
    chest_put_item_all("minecraft:stick")
    if log_id == "minecraft:oak_log" or log_id == "minecraft:dark_oak_log":
        chest_put_item_all("minecraft:apple")
    close_container()
    chest_target()
    chain_off()
    talk("主人~ 本轮砍树和树苗收集结束啦")
    return True


# 直接运行：修改中心点；pos(0,0,0) 自动选择中心附近的箱子。
harvest_trees(pos(-21,64,-8), "minecraft:cherry_log", pos(0,0,0))
