package icu.suc.serverguis;

import icu.suc.serverevents.ServerEvents;
import net.fabricmc.api.ModInitializer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundOpenScreenPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.MenuType;
import org.jetbrains.annotations.NotNull;

public class ServerGuis implements ModInitializer {

    private static int nextContainerCounter(@NotNull ServerPlayer player) {
        player.nextContainerCounter();
        return player.containerCounter;
    }

    @Override
    public void onInitialize() {
        ServerEvents.Message.Chat.ALLOW.register((message, player, params) -> {
            player.connection.send(new ClientboundOpenScreenPacket(nextContainerCounter(player), MenuType.CRAFTING, Component.literal("ServerGuis")));
            return true;
        });
    }
}
