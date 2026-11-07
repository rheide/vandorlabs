package com.vandorlabs.neoforge.storage;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
/** Opt-in bounded material transport. Public bindings remain separate. */
public record StorageSettingsPayload(NativeStorageEdit edit) implements CustomPacketPayload {
 public StorageSettingsPayload{java.util.Objects.requireNonNull(edit);}
 public static final Type<StorageSettingsPayload> TYPE=new Type<>(ResourceLocation.parse("vandorlabs:storage_settings"));
 public static final StreamCodec<RegistryFriendlyByteBuf,StorageSettingsPayload> CODEC=new StreamCodec<>(){public StorageSettingsPayload decode(RegistryFriendlyByteBuf b){return new StorageSettingsPayload(NativeStorageEdit.decode(b));}public void encode(RegistryFriendlyByteBuf b,StorageSettingsPayload value){value.edit.encode(b);}};
 @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
 public static void register(RegisterPayloadHandlersEvent event){event.registrar("1").playToServer(TYPE,CODEC,(packet,context)->context.enqueueWork(()->{if(context.player() instanceof ServerPlayer player)NativeStorageMenu.apply(player,packet.edit);}));}
}
