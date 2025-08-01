package icu.suc.servergui;

import com.google.common.collect.Maps;
import icu.suc.serverevents.ServerEvents;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.function.Function;

public class ServerGui {
    public static final Function<@NotNull ServerPlayer, @NotNull Component> DEFAULT_TITLE = player -> Component.empty();
    public static final Function<@NotNull ServerPlayer, @NotNull ItemStack> DEFAULT_CURSOR = player -> ItemStack.EMPTY;
    public static final Function<@NotNull ServerPlayer, @NotNull ItemStack> DEFAULT_ITEM = player -> ItemStack.EMPTY;
    public static final FunctionClick DEFAULT_CLICK = (player, item, context, slot, type1, button) -> item;

    private static final Map<UUID, GuiContext> CONTEXTS = Maps.newConcurrentMap();

    private Function<@NotNull ServerPlayer, @NotNull GuiType> type;
    private Function<@NotNull ServerPlayer, @NotNull Component> title = DEFAULT_TITLE;
    private Function<@NotNull ServerPlayer, @NotNull ItemStack> cursor = DEFAULT_CURSOR;
    private Function<@NotNull ServerPlayer, @NotNull ItemStack> item = DEFAULT_ITEM;
    private final Map<Integer, Function<@NotNull ServerPlayer, @NotNull ItemStack>> items = Maps.newHashMap();
    private FunctionClick click = DEFAULT_CLICK;
    private final Map<Integer, FunctionClick> clicks = Maps.newHashMap();

    protected ServerGui(@NotNull Function<ServerPlayer, GuiType> type) {
        this.type = type;
    }

    public int open(@NotNull ServerPlayer player) {
        int container = nextContainerCounter(player);

        var type = this.type.apply(player);
        var title = this.title.apply(player);
        var cursor = this.cursor.apply(player);
        var item = this.item.apply(player);
        var items = NonNullList.withSize(type.size(), item);
        this.items.forEach((slot, function) -> items.set(slot, function.apply(player)));

        CONTEXTS.put(player.getUUID(), new GuiContext(container, type, title, cursor, item, items, click, clicks));

        player.connection.send(new ClientboundOpenScreenPacket(container, type.menu(), title));
        player.connection.send(new ClientboundContainerSetContentPacket(container, 0, items, cursor));

        return container;
    }

    @Contract(value = "_ -> new", pure = true)
    public static @NotNull ServerGui create(@NotNull Function<@NotNull ServerPlayer, @NotNull GuiType> type) {
        return new ServerGui(type);
    }

    public ServerGui setType(@NotNull Function<@NotNull ServerPlayer, @NotNull GuiType> type) {
        this.type = type;
        return this;
    }

    public ServerGui setTitle(@NotNull Function<@NotNull ServerPlayer, @NotNull Component> title) {
        this.title = title;
        return this;
    }

    public ServerGui setCursor(@NotNull Function<@NotNull ServerPlayer, @NotNull ItemStack> cursor) {
        this.cursor = cursor;
        return this;
    }

    public ServerGui setItem(@NotNull Function<@NotNull ServerPlayer, @NotNull ItemStack> item) {
        this.item = item;
        return this;
    }

    public ServerGui setItem(int slot, @NotNull Function<@NotNull ServerPlayer, @NotNull ItemStack> item) {
        this.items.put(slot, item);
        return this;
    }

    public ServerGui onClick(@NotNull FunctionClick click) {
        this.click = click;
        return this;
    }

    public ServerGui onClick(int slot, @NotNull FunctionClick click) {
        this.clicks.put(slot, click);
        return this;
    }

    private static int nextContainerCounter(@NotNull ServerPlayer player) {
        player.nextContainerCounter();
        return player.containerCounter;
    }

    static {
        ServerEvents.Connection.Receive.ALLOW.register(ResourceLocation.tryBuild("servergui", "listener"), (listener, packet) -> {
            if (listener instanceof ServerGamePacketListenerImpl serverGamePacketListener) {
                var player = serverGamePacketListener.getPlayer();
                var context = CONTEXTS.get(player.getUUID());
                if (Objects.isNull(context)) {
                    return true;
                }

                int container = context.container();

                if (packet instanceof ServerboundContainerClickPacket containerClickPacket) {
                    if (!Objects.equals(container, containerClickPacket.containerId())) {
                        return true;
                    }

                    player.connection.send(new ClientboundSetPlayerInventoryPacket(36, Items.APPLE.getDefaultInstance()));

                    int size = context.type().size();
                    int slot = containerClickPacket.slotNum();
                    byte button = containerClickPacket.buttonNum();
                    var type = ClickType.of(containerClickPacket.clickType(), button);
                    var items = context.items();

                    if (Objects.equals(type, ClickType.SWAP) && Inventory.EQUIPMENT_SLOT_MAPPING.keySet().contains(button)) {
                        player.connection.send(new ClientboundSetPlayerInventoryPacket(button, player.getInventory().getItem(button)));
                    } else {
                        for (int i : containerClickPacket.changedSlots().keySet()) {
                            if (i >= size) {
                                i -= size;
                                if (i < Inventory.INVENTORY_SIZE - Inventory.SELECTION_SIZE) {
                                    i += Inventory.SELECTION_SIZE;
                                } else {
                                    i -= Inventory.INVENTORY_SIZE - Inventory.SELECTION_SIZE;
                                }
                                player.connection.send(new ClientboundSetPlayerInventoryPacket(i, player.getInventory().getItem(i)));
                            }
                        }
                    }

                    if (Objects.nonNull(type) && slot < size) {
                        var click = context.clicks().getOrDefault(slot, context.click());
                        var result = click.apply(player, items.get(slot), context, slot, type, button);
                        items.set(slot, result);
                    }

                    player.connection.send(new ClientboundContainerSetContentPacket(container, 0, items, context.cursor()));
                }
            }
            return true;
        });
    }
}
