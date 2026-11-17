package com.vandorlabs.neoforge.navigation;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
/** Request coordinates never define sampling area; authoritative display geometry does. */
public record NativeNavigationPayload(BlockPos pos,int slot,int serial,int action,int known) implements CustomPacketPayload {
 public static final Type<NativeNavigationPayload> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath("vandorlabs","navigation_request"));
 public NativeNavigationPayload{if(slot<0||slot>1||serial<0||action<0||action>8||known<0)throw new IllegalArgumentException("Navigation request bounds");}
 public static final StreamCodec<RegistryFriendlyByteBuf,NativeNavigationPayload> CODEC=new StreamCodec<>(){public NativeNavigationPayload decode(RegistryFriendlyByteBuf in){if(in.readableBytes()!=21)throw new IllegalArgumentException("Navigation request size");return new NativeNavigationPayload(in.readBlockPos(),in.readUnsignedByte(),in.readInt(),in.readInt(),in.readInt());}public void encode(RegistryFriendlyByteBuf out,NativeNavigationPayload p){out.writeBlockPos(p.pos);out.writeByte(p.slot);out.writeInt(p.serial);out.writeInt(p.action);out.writeInt(p.known);}};
 public Type<NativeNavigationPayload> type(){return TYPE;}
 public static void register(RegisterPayloadHandlersEvent e){e.registrar("1").playToServer(TYPE,CODEC,(p,c)->c.enqueueWork(()->{if(c.player() instanceof ServerPlayer actor)NativeNavigationRuntime.request(actor,p);}));e.registrar("1").playToClient(NativeNavigationImage.TYPE,NativeNavigationImage.CODEC,(p,c)->c.enqueueWork(()->NativeNavigationImage.RECEIVER.accept(p)));}
}
