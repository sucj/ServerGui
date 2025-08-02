package icu.suc.servergui;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

@FunctionalInterface
public interface FunctionClick {
    @NotNull ItemStack apply(@NotNull ServerPlayer player, @NotNull ItemStack item, @NotNull GuiContext context, int slot, @NotNull ClickType type, int button);
}
