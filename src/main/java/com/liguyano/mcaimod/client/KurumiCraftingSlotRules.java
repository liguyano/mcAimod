package com.liguyano.mcaimod.client;

/** Maps the supported slot aliases to crafting-table grid slots (1..9). */
final class KurumiCraftingSlotRules {
    /** -1 selects the first compatible free grid slot. */
    static int index(String slot) {
        if ("input".equals(slot) || "auto".equals(slot)) return -1;
        if (slot != null && slot.matches("grid_[1-9]"))
            return Integer.parseInt(slot.substring(5));
        throw new IllegalArgumentException("工作台槽位无效");
    }

    private KurumiCraftingSlotRules() {}
}
