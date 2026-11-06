package com.vandorlabs.neoforge.trapdoor;

import com.vandorlabs.neoforge.door.NativeDoorTargets;
import com.vandorlabs.neoforge.world.NativeBlockUse;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** Nine-byte owner/hand intent; the server reconstructs the exact nearest leaf and vanilla item interaction. */
public record TrapdoorUsePayload(BlockPos owner,InteractionHand hand) implements CustomPacketPayload {
    public static final Type<TrapdoorUsePayload> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath("vandorlabs","trapdoor_use"));
    public static final StreamCodec<RegistryFriendlyByteBuf,TrapdoorUsePayload> CODEC=new StreamCodec<>() {
        public TrapdoorUsePayload decode(RegistryFriendlyByteBuf buffer){if(buffer.readableBytes()!=9)throw new IllegalArgumentException("Invalid trapdoor use length");var owner=buffer.readBlockPos();int hand=buffer.readUnsignedByte();if(hand>1)throw new IllegalArgumentException("Invalid trapdoor use hand");return new TrapdoorUsePayload(owner,InteractionHand.values()[hand]);}
        public void encode(RegistryFriendlyByteBuf buffer,TrapdoorUsePayload value){buffer.writeBlockPos(value.owner);buffer.writeByte(value.hand.ordinal());}
    };
    public TrapdoorUsePayload {java.util.Objects.requireNonNull(owner);java.util.Objects.requireNonNull(hand);owner=owner.immutable();}
    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
    public static boolean apply(ServerPlayer player,TrapdoorUsePayload intent){
        if(!player.getServer().isSameThread()||intent==null||!player.isAlive()||player.isSpectator())return false;
        var level=player.serverLevel();var tile=NativeTrapdoorEntity.at(level,intent.owner);if(tile==null)return false;
        double reach=player.blockInteractionRange();if(!Double.isFinite(reach)||reach<=0||reach>64)return false;
        var start=player.getEyePosition();var hit=NativeDoorTargets.pick(level,player,start,start.add(player.getViewVector(1).scale(reach)));
        if(hit==null||!hit.root().equals(intent.owner)||!level.mayInteract(player,intent.owner)
                ||!player.mayUseItemAt(intent.owner,hit.hit().getDirection(),player.getItemInHand(intent.hand)))return false;
        return NativeBlockUse.apply(player,intent.hand,hit.hit()).consumesAction();
    }
    public static void register(RegisterPayloadHandlersEvent event){event.registrar("1").playToServer(TYPE,CODEC,(packet,context)->context.enqueueWork(()->{if(context.player() instanceof ServerPlayer player)apply(player,packet);}));}
}
