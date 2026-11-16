package com.vandorlabs.neoforge;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/** Disposable connection for presentation-only menus; never publishes a gameplay transfer. */
@EventBusSubscriber(modid="vandorlabs")
public final class NativeDialogParityServer {
    public static boolean enabled(){return Boolean.getBoolean("vandorlabs.dialogParityCheck");}
    @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent event){if(enabled()&&event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player){player.setGameMode(net.minecraft.world.level.GameType.CREATIVE);player.getAbilities().flying=true;player.onUpdateAbilities();player.connection.teleport(.5,100,-3.5,0,0);System.out.println("[vandorlabs][dialog-parity] connection ready presentation-only");}}
    private NativeDialogParityServer(){}
}
