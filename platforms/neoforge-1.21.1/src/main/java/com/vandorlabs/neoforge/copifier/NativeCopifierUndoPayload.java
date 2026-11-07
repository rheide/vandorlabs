package com.vandorlabs.neoforge.copifier;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Settings undo is authenticated against the current exact held-tool window. */
public record NativeCopifierUndoPayload(int window) implements CustomPacketPayload {
 public static final Type<NativeCopifierUndoPayload> TYPE=new Type<>(ResourceLocation.parse("vandorlabs:copifier_undo"));
 public static final StreamCodec<RegistryFriendlyByteBuf,NativeCopifierUndoPayload> CODEC=new StreamCodec<>(){
  public NativeCopifierUndoPayload decode(RegistryFriendlyByteBuf buffer){if(buffer.readableBytes()!=4)throw new IllegalArgumentException("Invalid Copifier undo length");return new NativeCopifierUndoPayload(buffer.readInt());}
  public void encode(RegistryFriendlyByteBuf buffer,NativeCopifierUndoPayload packet){buffer.writeInt(packet.window);}
 };
 public Type<? extends CustomPacketPayload> type(){return TYPE;}
 public static void register(RegisterPayloadHandlersEvent event){event.registrar("1").playToServer(TYPE,CODEC,NativeCopifierUndoPayload::handle);}
 public static boolean apply(ServerPlayer player,NativeCopifierUndoPayload packet){
  if(!player.getServer().isSameThread()||player.containerMenu.containerId!=packet.window||!(player.containerMenu instanceof NativeCopifierMenu menu)||!menu.stillValid(player))return false;
  boolean restored=NativeCopifierUndo.undo(player);player.displayClientMessage(net.minecraft.network.chat.Component.literal(restored?"Duplifier: previous settings restored":"Cannot undo: no previous operation, unavailable blocks or settings changed"),true);return restored;
 }
 private static void handle(NativeCopifierUndoPayload packet,IPayloadContext context){context.enqueueWork(()->{if(context.player() instanceof ServerPlayer player)apply(player,packet);});}
}
