package icu.suc.servergui;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public enum ClickType {
    LEFT,
    SHIFT_LEFT,
    RIGHT,
    SHIFT_RIGHT,
    MIDDLE,
    DOUBLE,
    THROW,
    THROW_ALL,
    SWAP;

    public static @Nullable ClickType of(net.minecraft.world.inventory.@NotNull ClickType type, byte button) {
        switch (type) {
            case PICKUP -> {
                switch (button) {
                    case 0 -> {
                        return LEFT;
                    }
                    case 1 -> {
                        return RIGHT;
                    }
                }
            }
            case QUICK_MOVE -> {
                switch (button) {
                    case 0 -> {
                        return SHIFT_LEFT;
                    }
                    case 1 -> {
                        return SHIFT_RIGHT;
                    }
                }
            }
            case SWAP -> {
                return SWAP;
            }
            case CLONE -> {
                return MIDDLE;
            }
            case THROW -> {
                switch (button) {
                    case 0 -> {
                        return THROW;
                    }
                    case 1 -> {
                        return THROW_ALL;
                    }
                }
            }
            case PICKUP_ALL -> {
                if (button == 0) {
                    return DOUBLE;
                }
            }
        }

        return null;
    }
}
