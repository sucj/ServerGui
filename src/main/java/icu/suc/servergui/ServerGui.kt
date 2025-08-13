package icu.suc.servergui

import icu.suc.serverevents.ServerEvents
import net.minecraft.core.NonNullList
import net.minecraft.network.chat.Component
import net.minecraft.network.protocol.game.*
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import net.minecraft.server.network.ServerGamePacketListenerImpl
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.item.ItemStack
import java.util.*
import java.util.concurrent.ConcurrentHashMap

open class ServerGui(private var type: (player: ServerPlayer) -> GuiType) {
    private var title: (player: ServerPlayer) -> Component = DEFAULT_TITLE
    private var cursor: (player: ServerPlayer) -> ItemStack = DEFAULT_CURSOR
    private var item: (player: ServerPlayer) -> ItemStack = DEFAULT_ITEM
    private val items: MutableMap<Int, (player: ServerPlayer) -> ItemStack> = HashMap<Int, (ServerPlayer) -> ItemStack>()
    private var click: (player: ServerPlayer, context: Context, result: ItemStack, slot: Int, type: ClickType, button: Int) -> ItemStack = DEFAULT_CLICK
    private val clicks = HashMap<Int, (player: ServerPlayer, context: Context, result: ItemStack, slot: Int, type: ClickType, button: Int) -> ItemStack>()
    private var open: (player: ServerPlayer, context: Context) -> Unit = DEFAULT_OPEN
    private var close: (player: ServerPlayer, context: Context) -> Unit = DEFAULT_CLOSE

    fun open(player: ServerPlayer) {
        nextContainerCounter(player) {
            val type = this.type.invoke(player)
            val title = this.title.invoke(player)
            val cursor = this.cursor.invoke(player)
            val item = this.item.invoke(player)
            val items = NonNullList.withSize(type.size(), item)
            this.items.forEach { (slot: Int, function: (ServerPlayer) -> ItemStack) ->
                if (slot in items.indices) items[slot] = function.invoke(player)
            }

            val context = Context(it, type, title, cursor, item, items, click, clicks, open, close)
            CONTEXTS[player.uuid] = context

            player.connection.send(ClientboundOpenScreenPacket(it, type.menu(), title))
            player.connection.send(ClientboundContainerSetContentPacket(it, 0, items, cursor))

            open.invoke(player, context)
        }
    }

    fun setType(type: (ServerPlayer) -> GuiType): ServerGui {
        this.type = type
        return this
    }

    fun setTitle(title: (ServerPlayer) -> Component): ServerGui {
        this.title = title
        return this
    }

    fun setCursor(cursor: (ServerPlayer) -> ItemStack): ServerGui {
        this.cursor = cursor
        return this
    }

    fun setItem(item: (ServerPlayer) -> ItemStack): ServerGui {
        this.item = item
        return this
    }

    fun setItem(slot: Int, item: (ServerPlayer) -> ItemStack): ServerGui {
        this.items[slot] = item
        return this
    }

    fun onClick(click: (player: ServerPlayer, context: Context, result: ItemStack, slot: Int, type: ClickType, button: Int) -> ItemStack): ServerGui {
        this.click = click
        return this
    }

    fun onClick(slot: Int, click: (player: ServerPlayer, context: Context, result: ItemStack, slot: Int, type: ClickType, button: Int) -> ItemStack): ServerGui {
        this.clicks[slot] = click
        return this
    }

    fun onOpen(open: (player: ServerPlayer, context: Context) -> Unit): ServerGui {
        this.open = open
        return this
    }

    fun onClose(close: (player: ServerPlayer, context: Context) -> Unit): ServerGui {
        this.close = close
        return this
    }

    companion object {
        @JvmField val PHASE: ResourceLocation = ResourceLocation.fromNamespaceAndPath("servergui", "listener")

        @JvmField val DEFAULT_TITLE: (player: ServerPlayer) -> Component = { Component.empty() }
        @JvmField val DEFAULT_CURSOR: (player: ServerPlayer) -> ItemStack = { ItemStack.EMPTY }
        @JvmField val DEFAULT_ITEM: (player: ServerPlayer) -> ItemStack = { ItemStack.EMPTY }
        @JvmField val DEFAULT_CLICK: (player: ServerPlayer, context: Context, result: ItemStack, slot: Int, type: ClickType, button: Int) -> ItemStack = { player, context, item, slot, type, button -> item }
        @JvmField val DEFAULT_OPEN: (player: ServerPlayer, context: Context) -> Unit = { _, _ -> }
        @JvmField val DEFAULT_CLOSE: (player: ServerPlayer, context: Context) -> Unit = { _, _ -> }

        private val CONTEXTS = ConcurrentHashMap<UUID, Context>()

        fun close(player: ServerPlayer) {
            CONTEXTS.remove(player.uuid)?.let { context ->
                player.connection.send(ClientboundContainerClosePacket(context.container))
                context.close(player, context)
            }
        }

        private fun nextContainerCounter(player: ServerPlayer, callback: (Int) -> Unit) {
            val server = player.server!!
            if (server.isSameThread) {
                player.nextContainerCounter()
                callback(player.containerCounter)
            } else server.execute {
                player.nextContainerCounter()
                callback(player.containerCounter)
            }
        }

        init {
            ServerEvents.Connection.Receive.ALLOW.register(PHASE) {
                listener, packet ->
                run {
                    if (listener !is ServerGamePacketListenerImpl) {
                        return@register true
                    }

                    val player = listener.player
                    val context: Context = CONTEXTS[player.uuid] ?: return@register true

                    val container = context.container

                    val server = player.server!!

                    when (packet) {
                        is ServerboundContainerClickPacket -> {
                            if (container != packet.containerId) {
                                return@register true
                            }

                            server.execute {
                                val size = context.type.size()
                                val slot = packet.slotNum.toInt()
                                val button = packet.buttonNum.toInt()
                                val type = ClickType.of(packet.clickType, button)
                                val items = context.items
                                if (type == ClickType.SWAP && Inventory.EQUIPMENT_SLOT_MAPPING.containsKey(button)) {
                                    player.connection.send(
                                        ClientboundSetPlayerInventoryPacket(
                                            button,
                                            player.inventory.getItem(button)
                                        )
                                    )
                                } else {
                                    for (entry in packet.changedSlots()) {
                                        var i = entry.key
                                        if (i >= size) {
                                            i -= size
                                            if (i < Inventory.INVENTORY_SIZE - Inventory.SELECTION_SIZE) {
                                                i += Inventory.SELECTION_SIZE
                                            } else {
                                                i -= Inventory.INVENTORY_SIZE - Inventory.SELECTION_SIZE
                                            }
                                            player.connection.send(
                                                ClientboundSetPlayerInventoryPacket(
                                                    i,
                                                    player.inventory.getItem(i)
                                                )
                                            )
                                        }
                                    }
                                }
                                if (type != null && slot in 0 until size) {
                                    val click = context.clicks.getOrDefault(slot, context.click)
                                    val result =
                                        click.invoke(player, context, items[slot], slot, type, button)
                                    items[slot] = result
                                }
                                player.connection.send(ClientboundContainerSetContentPacket(container, 0, items, context.cursor))
                            }

                            return@register false
                        }
                        is ServerboundContainerClosePacket -> {
                            if (container != packet.containerId) {
                                return@register true
                            }

                            server.execute { close(player) }

                            return@register false
                        }
                        else -> return@register true
                    }
                }
            }
            ServerEvents.Player.Leave.ALLOW_MESSAGE.register(PHASE) {
                player, _ ->
                run {
                    CONTEXTS.remove(player.uuid)
                    return@register true
                }
            }
        }
    }

    data class Context(
        val container: Int,
        val type: GuiType,
        val title: Component,
        val cursor: ItemStack,
        val item: ItemStack,
        val items: NonNullList<ItemStack>,
        val click: (player: ServerPlayer, context: Context, result: ItemStack, slot: Int, type: ClickType, button: Int) -> ItemStack,
        val clicks: Map<Int, (player: ServerPlayer, context: Context, result: ItemStack, slot: Int, type: ClickType, button: Int) -> ItemStack>,
        val open: (player: ServerPlayer, context: Context) -> Unit,
        val close: (player: ServerPlayer, context: Context) -> Unit,
    )
}
