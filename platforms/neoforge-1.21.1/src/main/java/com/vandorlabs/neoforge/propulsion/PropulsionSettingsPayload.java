package com.vandorlabs.neoforge.propulsion;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
/** Explicit opt-in helper; this class never activates the engine family. */
public record PropulsionSettingsPayload(NativePropulsionSettingsEdit edit) implements CustomPacketPayload {
 public static final Type<PropulsionSettingsPayload> TYPE=new Type<>(ResourceLocation.parse("vandorlabs:propulsion_settings"));
 public static final StreamCodec<RegistryFriendlyByteBuf,PropulsionSettingsPayload> CODEC=new StreamCodec<>(){public PropulsionSettingsPayload decode(RegistryFriendlyByteBuf b){return new PropulsionSettingsPayload(NativePropulsionSettingsEdit.decode(b));}public void encode(RegistryFriendlyByteBuf b,PropulsionSettingsPayload payload){payload.edit.encode(b);}};
 public PropulsionSettingsPayload{java.util.Objects.requireNonNull(edit);}
 @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
 public static boolean apply(ServerPlayer player,NativePropulsionSettingsEdit edit){return edit!=null&&NativePropulsionMenu.apply(player,edit.pos(),edit.window(),edit.choices());}
 public static void register(RegisterPayloadHandlersEvent event){event.registrar("1").playToServer(TYPE,CODEC,(p,c)->c.enqueueWork(()->{if(c.player() instanceof ServerPlayer player)apply(player,p.edit);}));}
}
