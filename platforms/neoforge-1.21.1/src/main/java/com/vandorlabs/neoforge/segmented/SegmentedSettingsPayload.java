package com.vandorlabs.neoforge.segmented;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** The released edit body, applied on the server thread through the player's current installed menu. */
public record SegmentedSettingsPayload(NativeSegmentedEdit edit) implements CustomPacketPayload {
    public static final Type<SegmentedSettingsPayload> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath("vandorlabs","segmented_settings"));
    public static final StreamCodec<RegistryFriendlyByteBuf,SegmentedSettingsPayload> CODEC=new StreamCodec<>(){
        public SegmentedSettingsPayload decode(RegistryFriendlyByteBuf buffer){
            return new SegmentedSettingsPayload(NativeSegmentedEdit.decode(buffer,NativeSegmentedDoorEntity::validTexture));
        }
        public void encode(RegistryFriendlyByteBuf buffer,SegmentedSettingsPayload payload){payload.edit.encode(buffer);}
    };
    public SegmentedSettingsPayload {java.util.Objects.requireNonNull(edit);}
    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
    public static void register(RegisterPayloadHandlersEvent event){
        event.registrar("1").playToServer(TYPE,CODEC,(payload,context)->context.enqueueWork(()->{
            if(context.player() instanceof ServerPlayer player)try{NativeSegmentedMenu.apply(player,payload.edit);}
            catch(IllegalArgumentException denied){player.displayClientMessage(Component.literal(denied.getMessage()),true);}
        }));
    }
}
