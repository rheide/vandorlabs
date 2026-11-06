package com.vandorlabs.neoforge.trapdoor;

import com.vandorlabs.neoforge.ModernFinishCatalog;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** No world access during decode; all edits run through the server player's currently installed menu. */
public record TrapdoorSettingsPayload(NativeTrapdoorEdit edit) implements CustomPacketPayload {
    public static final Type<TrapdoorSettingsPayload> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath("vandorlabs","trapdoor_settings"));
    public static final StreamCodec<RegistryFriendlyByteBuf,TrapdoorSettingsPayload> CODEC=new StreamCodec<>() {
        public TrapdoorSettingsPayload decode(RegistryFriendlyByteBuf buffer){return new TrapdoorSettingsPayload(NativeTrapdoorEdit.decode(buffer,ModernFinishCatalog::validChoice));}
        public void encode(RegistryFriendlyByteBuf buffer,TrapdoorSettingsPayload payload){payload.edit.encode(buffer);}
    };
    public TrapdoorSettingsPayload {java.util.Objects.requireNonNull(edit);}
    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
    public static void register(RegisterPayloadHandlersEvent event){event.registrar("1").playToServer(TYPE,CODEC,(payload,context)->context.enqueueWork(()->{
        if(context.player() instanceof ServerPlayer player)NativeTrapdoorMenu.apply(player,payload.edit);
    }));}
}
