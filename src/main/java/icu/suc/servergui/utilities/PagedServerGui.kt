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
    private val pages: List<ServerGui>
    private val current = mutableMapOf<UUID, Int>()

    init {
        val total = (content.size + SLOT_COUNT - 1) / SLOT_COUNT
        pages = List(total) { page ->
            ServerGui(TYPE).apply {
                SLOTS.forEach { setItem(it) { ItemStack.EMPTY } }
                if (page > 0) {
                    onClick(PREV) { player, _, result, _, _, _ ->
                        prev(player)
                        result
                    }
                }
                if (page < total - 1) {
                    onClick(NEXT) { player, _, result, _, _, _ ->
                        next(player)
                        result
                    }
                }
            }
        }
    }

    @JvmOverloads
    fun open(player: ServerPlayer, page: Int = 0) {
        if (page !in pages.indices) {
            return
        }
        current[player.uuid] = page
        pages[page].open(player)
    }

    fun prev(player: ServerPlayer) {
        open(player, getPrev(player))
    }

    fun next(player: ServerPlayer) {
        open(player, getNext(player))
    }

    val max: Int get() = pages.size

    fun setTitle(title: (player: ServerPlayer, current: Int, max: Int) -> Component): PagedServerGui<E> {
        pages.forEach { page ->
            page.setTitle { player -> title(player, getCurrent(player), this.max) }
        }
        return this
    }

    fun setCursor(cursor: (player: ServerPlayer, current: Int, max: Int) -> ItemStack): PagedServerGui<E> {
        pages.forEach { page ->
            page.setCursor { player -> cursor(player, getCurrent(player), this.max) }
        }
        return this
    }

    fun setItem(item: (player: ServerPlayer, current: Int, max: Int) -> ItemStack): PagedServerGui<E> {
        pages.forEach { page ->
            page.setItem { player -> item(player, getCurrent(player), this.max) }
        }
        return this
    }

    fun setItem(slot: Int, item: (player: ServerPlayer, current: Int, max: Int) -> ItemStack): PagedServerGui<E> {
        pages.forEach { page ->
            page.setItem { player -> item(player, getCurrent(player), this.max) }
        }
        return this
    }

    fun onClick(click: (player: ServerPlayer, context: Context, result: ItemStack, slot: Int, type: ClickType, button: Int, current: Int, max: Int) -> ItemStack): PagedServerGui<E> {
        pages.forEach { page ->
            page.onClick { player, context, result, slot, type, button ->
                click(player, context, result, slot, type, button, getCurrent(player), this.max)
            }
        }
        return this
    }

    fun onClick(slot: Int, click: (player: ServerPlayer, context: Context, result: ItemStack, slot: Int, type: ClickType, button: Int, current: Int, max: Int) -> ItemStack): PagedServerGui<E> {
        pages.forEach { page ->
            page.onClick(slot) { player, context, result, slot, type, button ->
                click(player, context, result, slot, type, button, getCurrent(player), this.max)
            }
        }
        return this
    }

    fun onOpen(open: (player: ServerPlayer, context: Context, current: Int, max: Int) -> Unit): PagedServerGui<E> {
        pages.forEach { page ->
            page.onOpen { player, context -> open(player, context, getCurrent(player), this.max) }
        }
        return this
    }

    fun onClose(open: (player: ServerPlayer, context: Context, current: Int, max: Int) -> Unit): PagedServerGui<E> {
        pages.forEach { page ->
            page.onClose { player, context -> open(player, context, getCurrent(player), this.max) }
        }
        return this
    }

    fun setPrev(prev: (player: ServerPlayer, current: Int, max: Int) -> ItemStack): PagedServerGui<E> {
        pages.forEachIndexed { index, page ->
            if (index > 0) {
                page.setItem(PREV) { player ->
                    prev(player, getCurrent(player), this.max)
                }
            }
        }
        return this
    }

    fun setNext(next: (player: ServerPlayer, current: Int, max: Int) -> ItemStack): PagedServerGui<E> {
        pages.forEachIndexed { index, page ->
            if (index < pages.size - 1) {
                page.setItem(NEXT) { player ->
                    next(player, getCurrent(player), this.max)
                }
            }
        }
        return this
    }

    fun setEntry(entry: (player: ServerPlayer, current: Int, max: Int, slot: Int, entry: E) -> ItemStack): PagedServerGui<E> {
        pages.forEachIndexed { pi, page ->
            SLOTS.forEachIndexed { si, slot ->
                val index = pi * SLOT_COUNT + si
                if (index < content.size) {
                    page.setItem(slot) { player ->
                        entry(player, getCurrent(player), max, slot, content[index])
                    }
                }
            }
        }
        return this
    }

    fun onClickPrev(click: (player: ServerPlayer, context: Context, result: ItemStack, slot: Int, type: ClickType, button: Int, current: Int, max: Int) -> ItemStack): PagedServerGui<E> {
        pages.forEachIndexed { index, page ->
            if (index > 0) {
                page.onClick (PREV) {  player, context, result, slot, type, button ->
                    click(player, context, result, slot, type, button, getCurrent(player), this.max)
                }
            }
        }
        return this
    }

    fun onClickNext(click: (player: ServerPlayer, context: Context, result: ItemStack, slot: Int, type: ClickType, button: Int, current: Int, max: Int) -> ItemStack): PagedServerGui<E> {
        pages.forEachIndexed { index, page ->
            if (index < pages.size - 1) {
                page.onClick (NEXT) {  player, context, result, slot, type, button ->
                    click(player, context, result, slot, type, button, getCurrent(player), this.max)
                }
            }
        }
        return this
    }

    fun onClickEntry(click: (player: ServerPlayer, context: Context, result: ItemStack, slot: Int, type: ClickType, button: Int, current: Int, max: Int, entry: E) -> ItemStack): PagedServerGui<E> {
        pages.forEachIndexed { pi, page ->
            SLOTS.forEachIndexed { si, slot ->
                val index = pi * SLOT_COUNT + si
                if (index < content.size) {
                    page.onClick(slot) { player, context, result, slot, type, button ->
                        click(player, context, result, slot, type, button, getCurrent(player), this.max, content[index])
                    }
                }
            }
        }
        return this
    }

    private fun getPage(player: ServerPlayer): Int =
        current[player.uuid] ?: 0

    fun getCurrent(player: ServerPlayer): Int =
        getPage(player) + 1

    fun getPrev(player: ServerPlayer): Int =
        getPage(player) - 1

    fun getNext(player: ServerPlayer): Int =
        getPage(player) + 1

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

        fun isReserved(slot: Int): Boolean =
            slot in SLOTS || slot == PREV || slot == NEXT
    }
}
