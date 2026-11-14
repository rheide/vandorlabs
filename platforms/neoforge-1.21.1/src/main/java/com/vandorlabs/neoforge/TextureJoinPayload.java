package com.vandorlabs.neoforge;

import com.vandorlabs.surface.*;
import com.vandorlabs.neoforge.glass.NativeGlassMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** Fixed fourteen-byte join edit, bound to the current authorized configuration menu. */
public record TextureJoinPayload(BlockPos pos,int window,int direction,int parity) implements CustomPacketPayload {
 public static final Type<TextureJoinPayload> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath("vandorlabs","texture_join"));
 public static final StreamCodec<RegistryFriendlyByteBuf,TextureJoinPayload> CODEC=new StreamCodec<>(){
  public TextureJoinPayload decode(RegistryFriendlyByteBuf b){if(b.readableBytes()!=14)throw new IllegalArgumentException("Fixed texture join payload");var p=new TextureJoinPayload(b.readBlockPos(),b.readInt(),b.readUnsignedByte(),b.readUnsignedByte());if(!TextureJoinSettings.valid(p.direction,p.parity))throw new IllegalArgumentException("Invalid texture join");return p;}
  public void encode(RegistryFriendlyByteBuf b,TextureJoinPayload p){if(!TextureJoinSettings.valid(p.direction,p.parity))throw new IllegalArgumentException("Invalid texture join");b.writeBlockPos(p.pos);b.writeInt(p.window);b.writeByte(p.direction);b.writeByte(p.parity);}
 };
 public Type<? extends CustomPacketPayload> type(){return TYPE;}
 public static void register(RegisterPayloadHandlersEvent event){event.registrar("1").playToServer(TYPE,CODEC,(p,c)->c.enqueueWork(()->{if(c.player() instanceof ServerPlayer player)apply(player,p);}));}
 public static boolean apply(ServerPlayer player,TextureJoinPayload p){
  if(!player.getServer().isSameThread()||!TextureJoinSettings.valid(p.direction,p.parity)||player.containerMenu.containerId!=p.window||!player.containerMenu.stillValid(player)||!player.serverLevel().hasChunkAt(p.pos)||!player.serverLevel().mayInteract(player,p.pos)||player.distanceToSqr(p.pos.getCenter())>64)return false;
  var tile=player.serverLevel().getBlockEntity(p.pos);
  if(!(tile instanceof TextureJoinOwner owner)||!NativeConfigurationAccess.canEditDialog(player,tile)||!(player.containerMenu instanceof ProgrammableMenu menu&&menu.tile==tile||player.containerMenu instanceof NativeGlassMenu glass&&glass.tile==tile))return false;
  owner.setTextureJoin(new TextureJoinSettings(p.direction,p.parity));return true;
 }
}
