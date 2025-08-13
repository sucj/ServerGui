package icu.suc.servergui

import net.minecraft.world.inventory.ClickType.*

enum class ClickType {
    LEFT,
    SHIFT_LEFT,
    RIGHT,
    SHIFT_RIGHT,
    MIDDLE,
    DOUBLE,
    THROW,
    THROW_ALL,
    SWAP;

    companion object {
        fun of(type: net.minecraft.world.inventory.ClickType, button: Int): ClickType? = when (type) {
            PICKUP -> when (button) {
                0 -> LEFT
                1 -> RIGHT
                else -> null
            }
            QUICK_MOVE -> when (button) {
                0 -> SHIFT_LEFT
                1 -> SHIFT_RIGHT
                else -> null
            }
            net.minecraft.world.inventory.ClickType.SWAP -> SWAP
            CLONE -> MIDDLE
            net.minecraft.world.inventory.ClickType.THROW -> when (button) {
                0 -> THROW
                1 -> THROW_ALL
                else -> null
            }
            PICKUP_ALL ->
                if (button == 0) DOUBLE else null

            QUICK_CRAFT -> null
        }
    }
}