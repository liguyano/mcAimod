package com.liguyano.mcaimod.client;

import java.util.Locale;
import java.util.Optional;

/**
 * Restricts model-proposed automation to explicit, bounded actions.
 * This parser NEVER executes commands or touches game state.
 */
public final class KurumiActionValidator {
    public enum Type { FARM, STOP, SLEEP, CHAIN_ON, CHAIN_OFF, CHEST_OPEN, CHEST_TAKE_ALL, DROP_ALL, CHEST_PUT_ALL, DROP_ITEM_ALL, CHEST_PUT_ITEM_ALL, OPEN_DOOR, PRESS_BUTTON, PICKUP, DROP, CHEST_TAKE, CHEST_PUT }

    public record Action(Type type, String itemId, int quantity, int radius) {}

    public static Optional<Action> validate(String actionName, String itemId, int quantity, int radius) {
        if (actionName == null) return Optional.empty();
        final Type type;
        try {
            type = Type.valueOf(actionName.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
        if (radius < 0 || radius > 32) return Optional.empty();
        boolean itemAction = switch (type) {
            case PICKUP, DROP, CHEST_TAKE, CHEST_PUT -> true;
            default -> false;
        };
        boolean allOfItem = type == Type.DROP_ITEM_ALL || type == Type.CHEST_PUT_ITEM_ALL;
        if (allOfItem) {
            if (quantity != 0 || itemId == null || !itemId.matches("[a-z0-9_.-]+:[a-z0-9_./-]{1,128}")) return Optional.empty();
        } else if (itemAction) {
            if (quantity < 1 || quantity > 64 || itemId == null ||
                    !itemId.matches("[a-z0-9_.-]+:[a-z0-9_./-]{1,128}")) return Optional.empty();
        } else if (quantity != 0 || itemId != null && !itemId.isBlank()) {
            return Optional.empty();
        }
        if (type == Type.FARM && (radius < 4 || radius > 32)) return Optional.empty();
        if (type != Type.FARM && radius != 0) return Optional.empty();
        return Optional.of(new Action(type, itemAction || allOfItem ? itemId : "", quantity, radius));
    }

    private KurumiActionValidator() {}
}
