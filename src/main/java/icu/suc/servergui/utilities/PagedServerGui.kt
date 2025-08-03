package icu.suc.servergui.utilities

import icu.suc.servergui.ClickType
import icu.suc.servergui.GuiType
import icu.suc.servergui.ServerGui
import icu.suc.servergui.ServerGui.Context
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.item.ItemStack
import java.util.*

open class PagedServerGui<E>(private val content: Array<E>) {
    private val pages = arrayOfNulls<ServerGui>(content.size / SLOT_COUNT + (if (content.size % SLOT_COUNT == 0) 0 else 1))
    private val current = Collections.synchronizedMap(WeakHashMap<UUID, Int>())

    init {
        for (i in pages.indices) {
            val page = ServerGui(TYPE)
            for (slot in SLOTS) {
                page.setItem(slot) { ItemStack.EMPTY }
            }
            if (i != 0) {
                page.onClick(PREV) { player, context, result, slot, type, button ->
                    prev(player)
                    result
                }
            }
            if (i != pages.size - 1) {
                page.onClick(NEXT) { player, context, result, slot, type, button ->
                    next(player)
                    result
                }
            }
            pages[i] = page
        }
    }

    @JvmOverloads
    fun open(player: ServerPlayer, page: Int = 0) {
        if (page < 0 || page >= pages.size) {
            return
        }
        val gui = pages[page]
        if (gui == null) {
            return
        }
        current.put(player.uuid, page)
        gui.open(player)
    }

    fun prev(player: ServerPlayer) {
        open(player, getPrev(player))
    }

    fun next(player: ServerPlayer) {
        open(player, getNext(player))
    }

    fun setTitle(title: (player: ServerPlayer, current: Int, max: Int) -> Component): PagedServerGui<E> {
        for (page in pages) {
            page?.setTitle { player -> title.invoke(player, getCurrent(player), this.max) }
        }
        return this
    }

    fun setCursor(cursor: (player: ServerPlayer, current: Int, max: Int) -> ItemStack): PagedServerGui<E> {
        for (page in pages) {
            page?.setCursor { player -> cursor.invoke(player, getCurrent(player), this.max) }
        }
        return this
    }

    fun setItem(item: (player: ServerPlayer, current: Int, max: Int) -> ItemStack): PagedServerGui<E> {
        for (page in pages) {
            page?.setItem { player -> item.invoke(player, getCurrent(player), this.max) }
        }
        return this
    }

    fun setItem(slot: Int, item: (player: ServerPlayer, current: Int, max: Int) -> ItemStack): PagedServerGui<E> {
        for (page in pages) {
            page?.setItem(slot) { player -> item.invoke(player, getCurrent(player), this.max) }
        }
        return this
    }

    fun onClick(click: (player: ServerPlayer, context: Context, result: ItemStack, slot: Int, type: ClickType, button: Int, current: Int, max: Int) -> ItemStack): PagedServerGui<E> {
        for (page in pages) {
            page?.onClick { player, context, result, slot, type, button ->
                click.invoke(player, context, result, slot, type, button, getCurrent(player), this.max)
            }
        }
        return this
    }

    fun onClick(slot: Int, click: (player: ServerPlayer, context: Context, result: ItemStack, slot: Int, type: ClickType, button: Int, current: Int, max: Int) -> ItemStack): PagedServerGui<E> {
        for (page in pages) {
            page?.onClick(slot) { player, context, result, slot, type, button ->
                click.invoke(player, context, result, slot, type, button, getCurrent(player), this.max)
            }
        }
        return this
    }

    fun onOpen(open: (player: ServerPlayer, context: Context, current: Int, max: Int) -> Unit): PagedServerGui<E> {
        for (page in pages) {
            page?.onOpen { player, context -> open(player, context, getCurrent(player), this.max) }
        }
        return this
    }

    fun onClose(open: (player: ServerPlayer, context: Context, current: Int, max: Int) -> Unit): PagedServerGui<E> {
        for (page in pages) {
            page?.onClose { player, context -> open(player, context, getCurrent(player), this.max) }
        }
        return this
    }

    fun setPrev(prev: (player: ServerPlayer, current: Int, max: Int) -> ItemStack): PagedServerGui<E> {
        for (i in pages.indices) {
            if (i != 0) {
                pages[i]?.setItem(PREV) { player ->
                    prev.invoke(
                        player, getCurrent(player),
                        this.max
                    )
                }
            }
        }
        return this
    }

    fun setNext(next: (player: ServerPlayer, current: Int, max: Int) -> ItemStack): PagedServerGui<E> {
        for (i in pages.indices) {
            if (i != pages.size - 1) {
                pages[i]?.setItem(NEXT) { player ->
                    next.invoke(
                        player, getCurrent(player),
                        this.max
                    )
                }
            }
        }
        return this
    }

    fun setEntry(entry: (player: ServerPlayer, current: Int, max: Int, slot: Int, entry: E) -> ItemStack): PagedServerGui<E> {
        for (i in pages.indices) {
            for (j in SLOTS.indices) {
                val slot: Int = SLOTS[j]
                val index: Int = i * SLOT_COUNT + j
                if (index >= content.size) {
                    break
                }
                pages[i]?.setItem(slot) { player ->
                    entry.invoke(
                        player, getCurrent(player),
                        this.max, slot, content[index]
                    )
                }
            }
        }
        return this
    }

    fun onClickPrev(click: (player: ServerPlayer, context: Context, result: ItemStack, slot: Int, type: ClickType, button: Int, current: Int, max: Int) -> ItemStack): PagedServerGui<E> {
        for (i in pages.indices) {
            if (i != 0) {
                pages[i]?.onClick(PREV) { player, context, result, slot, type, button ->
                    click.invoke(player, context, result, slot, type, button, getCurrent(player), this.max)
                }
            }
        }
        return this
    }

    fun onClickNext(click: (player: ServerPlayer, context: Context, result: ItemStack, slot: Int, type: ClickType, button: Int, current: Int, max: Int) -> ItemStack): PagedServerGui<E> {
        for (i in pages.indices) {
            if (i != 0) {
                pages[i]?.onClick(NEXT) { player, context, result, slot, type, button ->
                    click.invoke(player, context, result, slot, type, button, getCurrent(player), this.max)
                }
            }
        }
        return this
    }

    fun onClickEntry(click: (player: ServerPlayer, context: Context, result: ItemStack, slot: Int, type: ClickType, button: Int, current: Int, max: Int, entry: E) -> ItemStack): PagedServerGui<E> {
        for (i in pages.indices) {
            for (j in SLOTS.indices) {
                val slot: Int = SLOTS[j]
                val index: Int = i * SLOT_COUNT + j
                if (index >= content.size) {
                    break
                }
                pages[i]?.onClick(slot) { player, context, result, slot, type, button ->
                    click.invoke(player, context, result, slot, type, button, getCurrent(player), this.max, content[index])
                }
            }
        }
        return this
    }

    private fun getCurrent0(player: ServerPlayer): Int {
        return current.getOrDefault(player.getUUID(), 0)!!
    }

    fun getCurrent(player: ServerPlayer): Int {
        return getCurrent0(player) + 1
    }

    val max: Int
        get() = pages.size

    fun getPrev(player: ServerPlayer): Int {
        return getCurrent0(player) - 1
    }

    fun getNext(player: ServerPlayer): Int {
        return getCurrent0(player) + 1
    }

    companion object {
        private val SLOTS = intArrayOf(
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34
        )
        private val SLOT_COUNT: Int = SLOTS.size
        private const val PREV = 45
        private const val NEXT = 53
        private val TYPE: (player: ServerPlayer) -> GuiType = { GuiType.GENERIC_9x6 }

        fun isReserved(slot: Int): Boolean {
            return Arrays.binarySearch(SLOTS, slot) >= 0 || slot == PREV || slot == NEXT
        }
    }
}
