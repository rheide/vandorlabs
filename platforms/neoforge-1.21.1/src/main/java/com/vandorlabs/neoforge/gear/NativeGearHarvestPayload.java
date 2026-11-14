package com.vandorlabs.neoforge.gear;

import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/** Fixed 25-byte start/abort intent; server chooses the cell from its own ray and tool timeline. */
public record NativeGearHarvestPayload(BlockPos root,UUID identity,boolean abort) implements CustomPacketPayload {
    public static final Type<NativeGearHarvestPayload> TYPE=new Type<>(ResourceLocation.parse("vandorlabs:gear_payload_dig"));
    public static final StreamCodec<RegistryFriendlyByteBuf,NativeGearHarvestPayload> CODEC=new StreamCodec<>(){public NativeGearHarvestPayload decode(RegistryFriendlyByteBuf buf){if(buf.readableBytes()!=25)throw new IllegalArgumentException("Arm dig packet length");var pos=buf.readBlockPos();var id=buf.readUUID();int operation=buf.readUnsignedByte();if(operation>1)throw new IllegalArgumentException("Arm dig operation");return new NativeGearHarvestPayload(pos,id,operation==1);}public void encode(RegistryFriendlyByteBuf buf,NativeGearHarvestPayload packet){buf.writeBlockPos(packet.root);buf.writeUUID(packet.identity);buf.writeByte(packet.abort?1:0);}};
    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
    static void register(net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent event){if(NativeGearBindings.enabled())event.registrar("1").playToServer(TYPE,CODEC,(packet,context)->context.enqueueWork(()->{if(context.player() instanceof ServerPlayer actor)NativeGearHarvest.dig(actor,packet);}));}
}
