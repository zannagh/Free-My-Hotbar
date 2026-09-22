package io.github.zannagh.freemyhotbar.fallback;

/**
 * Which equipment branch of vanilla's {@code InventoryMenu.quickMoveStack} a stack would land in,
 * reduced to the two that are reachable before the hotbar-to-main branch.
 *
 * <p>Deliberately not a mirror of the game's whole {@code EquipmentSlot} enum: the only thing the
 * decision needs to know is whether a quick-move would EQUIP the stack instead of moving it.
 */
public enum EquipDestination {

    /**
     * A humanoid armour slot - helmet, chestplate, leggings, boots, and everything vanilla treats
     * as wearable there (elytra, mob heads, carved pumpkins).
     *
     * <p>Strictly the humanoid armour slots. An animal armour piece or a saddle is armour too but
     * is NOT reachable through this branch, which is why the client side must keep testing the
     * precise equipment-slot type rather than a blanket "is this armour".
     */
    HUMANOID_ARMOR,

    /** The offhand slot - shields, and anything else the player can hold in the left hand. */
    OFFHAND,

    /** No equipment branch applies; a quick-move would simply move the stack. */
    NONE
}
