package icu.suc.servergui;

import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

public record GuiContext(int container, @NotNull GuiType type, @NotNull Component title, @NotNull ItemStack cursor, @NotNull ItemStack item, @NotNull NonNullList<ItemStack> items, @NotNull FunctionSlot slot, @NotNull Map<Integer, FunctionSlot> slots) {
}
