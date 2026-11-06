package com.vandorlabs.neoforge.door;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** Nine-byte intent only: server recomputes reach, direction, target identity, occlusion and permissions. Inactive until Door activation. */
public record DoorUsePayload(BlockPos root,InteractionHand hand) implements CustomPacketPayload {
    public static final Type<DoorUsePayload> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath("vandorlabs","door_use"));
    public static final StreamCodec<RegistryFriendlyByteBuf,DoorUsePayload> CODEC=new StreamCodec<>(){
        @Override public DoorUsePayload decode(RegistryFriendlyByteBuf buffer){if(buffer.readableBytes()!=9)throw new IllegalArgumentException("Invalid Door use length");var pos=buffer.readBlockPos();int hand=buffer.readUnsignedByte();if(hand>1)throw new IllegalArgumentException("Invalid Door use hand");return new DoorUsePayload(pos,InteractionHand.values()[hand]);}
        @Override public void encode(RegistryFriendlyByteBuf buffer,DoorUsePayload value){if(value.root==null||value.hand==null)throw new IllegalArgumentException("Invalid Door use");buffer.writeBlockPos(value.root);buffer.writeByte(value.hand.ordinal());}
    };
    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
    public static boolean apply(ServerPlayer player,DoorUsePayload value){
        if(!player.getServer().isSameThread()||value==null||value.root==null||value.hand==null||!player.isAlive()||player.isSpectator())return false;
        var level=player.serverLevel();if(!NativeDoorLifecycle.loaded(level,value.root))return false;
        double reach=player.blockInteractionRange();if(!Double.isFinite(reach)||reach<=0||reach>64)return false;
        var start=player.getEyePosition();var target=NativeDoorTargets.pick(level,player,start,start.add(player.getViewVector(1).scale(reach)));
        if(target==null||!target.root().equals(value.root)||!level.mayInteract(player,target.hit().getBlockPos())||!player.mayUseItemAt(target.hit().getBlockPos(),target.hit().getDirection(),player.getItemInHand(value.hand)))return false;
        var host=NativeDoorLifecycle.root(level,target.root());return host!=null&&host.getBlockState().getBlock() instanceof NativeProgrammableDoorBlock block&&block.useTarget(player,value.hand,target.hit()).consumesAction();
    }
    public static void register(RegisterPayloadHandlersEvent event){event.registrar("1").playToServer(TYPE,CODEC,(packet,context)->context.enqueueWork(()->{if(context.player() instanceof ServerPlayer player)apply(player,packet);}));}
}
