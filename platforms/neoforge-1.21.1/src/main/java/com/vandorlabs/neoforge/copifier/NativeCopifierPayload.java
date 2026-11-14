package com.vandorlabs.neoforge.copifier;

import com.vandorlabs.items.*;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Fixed-size options draft. No decoded count controls allocation or world access. */
public record NativeCopifierPayload(int window,long[] words,boolean connected,boolean ignoreDirectionality,boolean respectJoins) implements CustomPacketPayload {
 private static final int WORDS=(DuplifierOptionCatalog.size()+63)/64;
 private static final DuplifierOptionMask MASK=new DuplifierOptionMask(DuplifierOptionCatalog.size());
 public static final Type<NativeCopifierPayload> TYPE=new Type<>(ResourceLocation.parse("vandorlabs:copifier_options"));
 public NativeCopifierPayload(int window,long[] words,boolean connected,boolean ignoreDirectionality){this(window,words,connected,ignoreDirectionality,false);}
 public NativeCopifierPayload {if(!MASK.valid(words))throw new IllegalArgumentException("Invalid Copifier mask");words=words.clone();}
 @Override public long[] words(){return words.clone();}
 public static final StreamCodec<RegistryFriendlyByteBuf,NativeCopifierPayload> CODEC=new StreamCodec<>(){
  public NativeCopifierPayload decode(RegistryFriendlyByteBuf buffer){if(buffer.readableBytes()!=4+8*WORDS+2)throw new IllegalArgumentException("Invalid Copifier options length");int window=buffer.readInt();long[] words=new long[WORDS];for(int i=0;i<WORDS;i++)words[i]=buffer.readLong();int connected=buffer.readUnsignedByte(),ignore=buffer.readUnsignedByte();if(connected>1||ignore>3)throw new IllegalArgumentException("Invalid Copifier flag");return new NativeCopifierPayload(window,words,connected==1,(ignore&1)!=0,(ignore&2)!=0);}
  public void encode(RegistryFriendlyByteBuf buffer,NativeCopifierPayload packet){buffer.writeInt(packet.window);for(long word:packet.words)buffer.writeLong(word);buffer.writeBoolean(packet.connected);buffer.writeByte((packet.ignoreDirectionality?1:0)|(packet.respectJoins?2:0));}
 };
 public Type<? extends CustomPacketPayload> type(){return TYPE;}
 public static void register(RegisterPayloadHandlersEvent event){event.registrar("1").playToServer(TYPE,CODEC,NativeCopifierPayload::handle);}
 public static boolean apply(ServerPlayer player,NativeCopifierPayload packet){return player.getServer().isSameThread()&&player.containerMenu.containerId==packet.window&&player.containerMenu instanceof NativeCopifierMenu menu&&menu.configure(player,packet.words,packet.connected,packet.ignoreDirectionality,packet.respectJoins);}
 private static void handle(NativeCopifierPayload packet,IPayloadContext context){context.enqueueWork(()->{if(context.player() instanceof ServerPlayer player)apply(player,packet);});}
}
