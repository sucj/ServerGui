package icu.suc.servergui

import icu.suc.serverevents.ServerEvents.Connection.Receive
import net.minecraft.core.NonNullList
import net.minecraft.network.PacketListener
import net.minecraft.network.chat.Component
import net.minecraft.network.protocol.Packet
import net.minecraft.network.protocol.game.ClientboundContainerClosePacket
import net.minecraft.network.protocol.game.ClientboundContainerSetContentPacket
import net.minecraft.network.protocol.game.ClientboundOpenScreenPacket
import net.minecraft.network.protocol.game.ClientboundSetPlayerInventoryPacket
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket
import net.minecraft.network.protocol.game.ServerboundContainerClosePacket
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
    private var close: (player: ServerPlayer, context: Context) -> Unit = DEFAULT_OPEN

    fun open(player: ServerPlayer): Int {
        val container: Int = nextContainerCounter(player)

        val type = this.type.invoke(player)
        val title = this.title.invoke(player)
        val cursor = this.cursor.invoke(player)
        val item = this.item.invoke(player)
        val items = NonNullList.withSize(type.size(), item)
        this.items.forEach { (slot: Int, function: (ServerPlayer) -> ItemStack) ->
            items[slot] = function.invoke(player)
        }

        val context = Context(container, type, title, cursor, item, items, click, clicks, open, close)
        CONTEXTS[player.uuid] = context

        player.connection.send(ClientboundOpenScreenPacket(container, type.menu(), title))
        player.connection.send(ClientboundContainerSetContentPacket(container, 0, items, cursor))

        open.invoke(player, context)

        return container
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
        this.items.put(slot, item)
        return this
    }

    fun onClick(click: (player: ServerPlayer, context: Context, result: ItemStack, slot: Int, type: ClickType, button: Int) -> ItemStack): ServerGui {
        this.click = click
        return this
    }

    fun onClick(slot: Int, click: (player: ServerPlayer, context: Context, result: ItemStack, slot: Int, type: ClickType, button: Int) -> ItemStack): ServerGui {
        this.clicks.put(slot, click)
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
        val DEFAULT_TITLE: (player: ServerPlayer) -> Component = { Component.empty() }
        val DEFAULT_CURSOR: (player: ServerPlayer) -> ItemStack = { ItemStack.EMPTY }
        val DEFAULT_ITEM: (player: ServerPlayer) -> ItemStack = { ItemStack.EMPTY }
        val DEFAULT_CLICK: (player: ServerPlayer, context: Context, result: ItemStack, slot: Int, type: ClickType, button: Int) -> ItemStack = { player, context, item, slot, type, button -> item }
        val DEFAULT_OPEN: (player: ServerPlayer, context: Context) -> Unit = { player, context -> }
        val DEFAULT_CLOSE: (player: ServerPlayer, context: Context) -> Unit = { player, context -> }

        private val CONTEXTS: MutableMap<UUID, Context> = ConcurrentHashMap()

        fun close(player: ServerPlayer) {
            val uuid = player.uuid
            val context = CONTEXTS[uuid]!!
            player.connection.send(ClientboundContainerClosePacket(context.container))
            context.close(player, context)
            CONTEXTS.remove(uuid)
        }

        private fun nextContainerCounter(player: ServerPlayer): Int {
            player.nextContainerCounter()
            return player.containerCounter
        }

        init {
            Receive.ALLOW.register(
                ResourceLocation.tryBuild("servergui", "listener"),
                Receive.Allow { listener: PacketListener?, packet: Packet<*>? ->
                    if (listener is ServerGamePacketListenerImpl) {
                        val player = listener.player
                        val context: Context? = CONTEXTS[player.uuid]
                        if (context == null) {
                            return@Allow true
                        }

                        val container = context.container

                        if (packet is ServerboundContainerClickPacket) {
                            if (container != packet.containerId) {
                                return@Allow true
                            }

                            val size = context.type.size()
                            val slot = packet.slotNum.toInt()
                            val button = packet.buttonNum
                            val type = ClickType.of(packet.clickType, button)
                            val items = context.items

                            if (type == ClickType.SWAP && Inventory.EQUIPMENT_SLOT_MAPPING.containsKey(button.toInt())) {
                                player.connection.send(
                                    ClientboundSetPlayerInventoryPacket(
                                        button.toInt(),
                                        player.inventory.getItem(button.toInt())
                                    )
                                )
                            }
                            else {
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

                            if (type != null && slot < size) {
                                val click = context.clicks.getOrDefault(slot, context.click)
                                val result =
                                    click.invoke(player, context, items[slot], slot, type, button.toInt())
                                items[slot] = result
                            }

                            player.connection.send(ClientboundContainerSetContentPacket(container, 0, items, context.cursor))

                            return@Allow false
                        }
                        else if (packet is ServerboundContainerClosePacket) {
                            if (container != packet.containerId) {
                                return@Allow true
                            }

                            context.close(player, context)

                            return@Allow false
                        }
                    }
                    true
                })
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
