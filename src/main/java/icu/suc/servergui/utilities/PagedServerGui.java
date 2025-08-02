package icu.suc.servergui.utilities;

import icu.suc.servergui.ClickType;
import icu.suc.servergui.GuiContext;
import icu.suc.servergui.GuiType;
import icu.suc.servergui.ServerGui;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.function.Function;

public class PagedServerGui<E> {
    private static final int[] SLOTS = new int[]{
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34
    };
    private static final int SLOT_COUNT = SLOTS.length;
    private static final int PREV = 45;
    private static final int NEXT = 53;
    private static final Function<ServerPlayer, GuiType> TYPE = player -> GuiType.GENERIC_9x6;

    private final E[] content;
    private final ServerGui[] pages;
    private final Map<UUID, Integer> current = Collections.synchronizedMap(new WeakHashMap<>());

    protected PagedServerGui(E @NotNull [] content) {
        this.content = content;
        pages = new ServerGui[content.length / SLOT_COUNT + (content.length % SLOT_COUNT == 0 ? 0 : 1)];
        for (int i = 0; i < pages.length; i++) {
            var page = ServerGui.create(TYPE);
            for (int slot : SLOTS) {
                page.setItem(slot, player -> ItemStack.EMPTY);
            }
            if (i != 0) {
                page.onClick(PREV, (player, item, context, slot, type, button) -> {
                    prev(player);
                    return item;
                });
            }
            if (i != pages.length - 1) {
                page.onClick(NEXT, (player, item, context, slot, type, button) -> {
                    next(player);
                    return item;
                });
            }
            pages[i] = page;
        }
    }

    public void open(@NotNull ServerPlayer player) {
        open(player, 0);
    }

    public void open(@NotNull ServerPlayer player, int page) {
        if (page < 0 || page >= pages.length) {
            return;
        }
        current.put(player.getUUID(), page);
        pages[page].open(player);
    }

    public void prev(@NotNull ServerPlayer player) {
        open(player, getPrev(player));
    }

    public void next(@NotNull ServerPlayer player) {
        open(player, getNext(player));
    }

    @Contract("_ -> new")
    public static <E> @NotNull PagedServerGui<E> create(@NotNull E[] content) {
        return new PagedServerGui<>(content);
    }

    public PagedServerGui<E> setTitle(@NotNull FunctionTitle title) {
        for (var page : pages) {
            page.setTitle(player -> title.apply(player, getCurrent(player), getMax()));
        }
        return this;
    }

    public PagedServerGui<E> setCursor(@NotNull FunctionItem cursor) {
        for (var page : pages) {
            page.setCursor(player -> cursor.apply(player, getCurrent(player), getMax()));
        }
        return this;
    }

    public PagedServerGui<E> setItem(@NotNull FunctionItem item) {
        for (var page : pages) {
            page.setItem(player -> item.apply(player, getCurrent(player), getMax()));
        }
        return this;
    }

    public PagedServerGui<E> setItem(int slot, @NotNull FunctionItem item) {
        for (var page : pages) {
            page.setItem(slot, player -> item.apply(player, getCurrent(player), getMax()));
        }
        return this;
    }

    public PagedServerGui<E> onClick(@NotNull FunctionClick click) {
        for (var page : pages) {
            page.onClick((player, item, context, slot, type, button) -> click.apply(player, item, context, slot, type, button, getCurrent(player), getMax()));
        }
        return this;
    }

    public PagedServerGui<E> onClick(int slot, @NotNull FunctionClick click) {
        for (var page : pages) {
            page.onClick(slot, (player, item, context, slot1, type, button) -> click.apply(player, item, context, slot1, type, button, getCurrent(player), getMax()));
        }
        return this;
    }

    public PagedServerGui<E> setPrev(@NotNull FunctionItem prev) {
        for (int i = 0; i < pages.length; i++) {
            if (i != 0) {
                pages[i].setItem(PREV, player -> prev.apply(player, getCurrent(player), getMax()));
            }
        }
        return this;
    }

    public PagedServerGui<E> setNext(@NotNull FunctionItem next) {
        for (int i = 0; i < pages.length; i++) {
            if (i != pages.length - 1) {
                pages[i].setItem(NEXT, player -> next.apply(player, getCurrent(player), getMax()));
            }
        }
        return this;
    }

    public PagedServerGui<E> setEntry(@NotNull FunctionEntry<E> entry) {
        for (int i = 0; i < pages.length; i++) {
            for (int j = 0; j < SLOTS.length; j++) {
                int slot = SLOTS[j];
                int index = i * SLOT_COUNT + j;
                if (index >= content.length) {
                    break;
                }
                pages[i].setItem(slot, player -> entry.apply(player, getCurrent(player), getMax(), slot, content[index]));
            }
        }
        return this;
    }

    public PagedServerGui<E> onClickPrev(@NotNull FunctionClick click) {
        for (int i = 0; i < pages.length; i++) {
            if (i != 0) {
                pages[i].onClick(PREV, (player, item, context, slot, type, button) -> click.apply(player, item, context, slot, type, button, getCurrent(player), getMax()));
            }
        }
        return this;
    }

    public PagedServerGui<E> onClickNext(@NotNull FunctionClick click) {
        for (int i = 0; i < pages.length; i++) {
            if (i != pages.length - 1) {
                pages[i].onClick(NEXT, (player, item, context, slot, type, button) -> click.apply(player, item, context, slot, type, button, getCurrent(player), getMax()));
            }
        }
        return this;
    }

    public PagedServerGui<E> onClickEntry(@NotNull FunctionClickEntry<E> click) {
        for (int i = 0; i < pages.length; i++) {
            for (int j = 0; j < SLOTS.length; j++) {
                int slot = SLOTS[j];
                int index = i * SLOT_COUNT + j;
                if (index >= content.length) {
                    break;
                }
                pages[i].onClick(slot, (player, item, context, slot1, type, button) -> click.apply(player, item, context, slot1, type, button, getCurrent(player), getMax(), content[index]));
            }
        }
        return this;
    }

    private int getCurrent0(@NotNull ServerPlayer player) {
        return current.getOrDefault(player.getUUID(), 0);
    }

    public int getCurrent(@NotNull ServerPlayer player) {
        return getCurrent0(player) + 1;
    }

    public int getMax() {
        return pages.length;
    }

    public int getPrev(@NotNull ServerPlayer player) {
        return getCurrent0(player) - 1;
    }

    public int getNext(@NotNull ServerPlayer player) {
        return getCurrent0(player) + 1;
    }

    public static boolean isReserved(int slot) {
        return Arrays.binarySearch(SLOTS, slot) >= 0 || slot == PREV || slot == NEXT;
    }

    @FunctionalInterface
    public interface FunctionEntry<E> {
        @NotNull ItemStack apply(@NotNull ServerPlayer player, int currentPage, int maxPage, int slot, @NotNull E entry);
    }

    @FunctionalInterface
    public interface FunctionTitle {
        @NotNull Component apply(@NotNull ServerPlayer player, int currentPage, int maxPage);
    }

    @FunctionalInterface
    public interface FunctionItem {
        @NotNull ItemStack apply(@NotNull ServerPlayer player, int currentPage, int maxPage);
    }

    @FunctionalInterface
    public interface FunctionClick {
        @NotNull ItemStack apply(@NotNull ServerPlayer player, @NotNull ItemStack item, @NotNull GuiContext context, int slot, @NotNull ClickType type, int button, int currentPage, int maxPage);
    }

    @FunctionalInterface
    public interface FunctionClickEntry<E> {
        @NotNull ItemStack apply(@NotNull ServerPlayer player, @NotNull ItemStack item, @NotNull GuiContext context, int slot, @NotNull ClickType type, int button, int currentPage, int maxPage, @NotNull E entry);
    }
}
