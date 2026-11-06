package com.vandorlabs.neoforge.door;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** Bounded Door transactions share the installed menu's authorization and loaded-link checks. Register with the functional Door lifecycle. */
public final class DoorPayloads {
    private DoorPayloads(){}
    public static void register(RegisterPayloadHandlersEvent event){
        var registrar=event.registrar("1");
        registrar.playToServer(DoorSettingsPayload.TYPE,DoorSettingsPayload.CODEC,(p,c)->c.enqueueWork(()->{if(c.player() instanceof ServerPlayer player)NativeDoorMenu.apply(player,p.pos(),p.window(),p.edit(),p.channels());}));
        registrar.playToServer(DoorFramePayload.TYPE,DoorFramePayload.CODEC,(p,c)->c.enqueueWork(()->{if(c.player() instanceof ServerPlayer player)NativeDoorMenu.applyFrames(player,p.pos(),p.window(),p.frames());}));
        registrar.playToServer(DoorGlassPayload.TYPE,DoorGlassPayload.CODEC,(p,c)->c.enqueueWork(()->{if(c.player() instanceof ServerPlayer player)NativeDoorMenu.applyGlass(player,p.pos(),p.window(),p.glass());}));
        DoorUsePayload.register(event);
    }
}
