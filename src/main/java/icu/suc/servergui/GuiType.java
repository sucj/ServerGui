package icu.suc.servergui;

import net.minecraft.world.inventory.MenuType;

public enum GuiType {
    GENERIC_9x1(MenuType.GENERIC_9x1, 9),
    GENERIC_9x2(MenuType.GENERIC_9x2, 18),
    GENERIC_9x3(MenuType.GENERIC_9x3, 27),
    GENERIC_9x4(MenuType.GENERIC_9x4, 36),
    GENERIC_9x5(MenuType.GENERIC_9x5, 45),
    GENERIC_9x6(MenuType.GENERIC_9x6, 54);

    private final MenuType<?> menu;
    private final int size;

    GuiType(MenuType<?> menu, int size) {
        this.menu = menu;
        this.size = size;
    }

    public MenuType<?> menu() {
        return menu;
    }

    public int size() {
        return size;
    }
}
