package com.vandorlabs.neoforge.light;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
/** Opt-in register helper only; no public bootstrap binding. */
public record LightSettingsPayload(NativeLightSettingsEdit edit) implements CustomPacketPayload {
 public static final Type<LightSettingsPayload> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath("vandorlabs","light_settings"));
 public static final StreamCodec<RegistryFriendlyByteBuf,LightSettingsPayload> CODEC=new StreamCodec<>(){public LightSettingsPayload decode(RegistryFriendlyByteBuf b){return new LightSettingsPayload(NativeLightSettingsEdit.decode(b));}public void encode(RegistryFriendlyByteBuf b,LightSettingsPayload payload){payload.edit.encode(b);}};
 public LightSettingsPayload {java.util.Objects.requireNonNull(edit);}
 @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
 public static boolean apply(ServerPlayer player,NativeLightSettingsEdit edit){return edit!=null&&NativeLightMenu.apply(player,edit.pos(),edit.window(),edit.choices(),edit.channels());}
 public static void register(RegisterPayloadHandlersEvent event){event.registrar("1").playToServer(TYPE,CODEC,(payload,context)->context.enqueueWork(()->{if(context.player() instanceof ServerPlayer player)apply(player,payload.edit);}));}
}
