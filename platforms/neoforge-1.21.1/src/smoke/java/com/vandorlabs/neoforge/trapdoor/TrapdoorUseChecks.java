package com.vandorlabs.neoforge.trapdoor;

import com.vandorlabs.neoforge.door.NativeDoorTargets;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/** Real server game-mode interaction, including obstruction and exact nine-byte wire bounds. */
public final class TrapdoorUseChecks {
    private static int checks;
    private static void need(boolean value,String why){checks++;if(!value)throw new AssertionError("Trapdoor use: "+why);}
    private static void aim(ServerPlayer actor,Vec3 start,Vec3 toward){
        actor.setPos(start.x,start.y-actor.getEyeHeight(),start.z);Vec3 d=toward.subtract(start);
        float yaw=(float)(Math.atan2(d.z,d.x)*180/Math.PI)-90,pitch=(float)(-Math.atan2(d.y,Math.sqrt(d.x*d.x+d.z*d.z))*180/Math.PI);actor.setYRot(yaw);actor.setYHeadRot(yaw);actor.setYBodyRot(yaw);actor.setXRot(pitch);actor.yRotO=actor.yHeadRotO=actor.yBodyRotO=yaw;actor.xRotO=pitch;
    }
    public static void run(NativeTrapdoorEntity tile,ServerPlayer actor,Vec3 start,Vec3 toward){
        var level=actor.serverLevel();actor.setGameMode(GameType.SURVIVAL);actor.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);actor.setItemInHand(InteractionHand.OFF_HAND,ItemStack.EMPTY);aim(actor,start,toward);
        var owner=tile.getBlockPos();boolean before=tile.open();
        var intent=new TrapdoorUsePayload(owner,InteractionHand.MAIN_HAND);
        var wire=new RegistryFriendlyByteBuf(Unpooled.buffer(),level.registryAccess());TrapdoorUsePayload.CODEC.encode(wire,intent);need(wire.readableBytes()==9,"owner/hand body stays nine bytes");need(TrapdoorUsePayload.CODEC.decode(wire).equals(intent)&&!wire.isReadable(),"wire round trip");wire.release();
        for(int length=0;length<9;length++){var malformed=new RegistryFriendlyByteBuf(Unpooled.buffer().writeZero(length),level.registryAccess());try{TrapdoorUsePayload.CODEC.decode(malformed);throw new AssertionError("Accepted short use body");}catch(IllegalArgumentException expected){checks++;}finally{malformed.release();}}
        for(int length:new int[]{9,10}){var malformed=new RegistryFriendlyByteBuf(Unpooled.buffer().writeZero(length),level.registryAccess());if(length==9)malformed.setByte(8,2);try{TrapdoorUsePayload.CODEC.decode(malformed);throw new AssertionError("Accepted malformed use hand/tail");}catch(IllegalArgumentException expected){checks++;}finally{malformed.release();}}
        var target=NativeDoorTargets.pick(level,actor,actor.getEyePosition(),actor.getEyePosition().add(actor.getViewVector(1).scale(actor.blockInteractionRange())));
        if(target==null){var end=actor.getEyePosition().add(actor.getViewVector(1).scale(actor.blockInteractionRange()));var raw=NativeTrapdoorTargets.trace(level,actor.getEyePosition(),end);if(raw!=null){var barrier=level.clip(new net.minecraft.world.level.ClipContext(actor.getEyePosition(),raw.hit().getLocation(),net.minecraft.world.level.ClipContext.Block.OUTLINE,net.minecraft.world.level.ClipContext.Fluid.NONE,actor){@Override public net.minecraft.world.phys.shapes.VoxelShape getBlockShape(net.minecraft.world.level.block.state.BlockState state,net.minecraft.world.level.BlockGetter world,BlockPos pos){return state.getBlock() instanceof NativeTrapdoorBlock?net.minecraft.world.phys.shapes.Shapes.empty():super.getBlockShape(state,world,pos);}});System.out.println("[vandorlabs][trapdoor-use] diagnostic barrier="+barrier.getType()+" at="+barrier.getBlockPos()+" state="+(NativeTrapdoorEntity.loaded(level,barrier.getBlockPos())?level.getBlockState(barrier.getBlockPos()):"cold")+" rawHit="+raw.hit().getLocation()+" eyeChunk="+NativeTrapdoorEntity.loaded(level,BlockPos.containing(actor.getEyePosition())));}}
        need(target!=null&&target.root().equals(owner),"combined target selects actual moved leaf owner="+owner+" open="+before+" settings="+tile.settings()+" start="+start+" toward="+toward+" eye="+actor.getEyePosition()+" view="+actor.getViewVector(1)+" target="+target+" raw="+NativeTrapdoorTargets.trace(level,actor.getEyePosition(),actor.getEyePosition().add(actor.getViewVector(1).scale(actor.blockInteractionRange()))));
        need(!TrapdoorUsePayload.apply(actor,new TrapdoorUsePayload(owner,InteractionHand.OFF_HAND))&&tile.open()==before,"offhand cannot toggle");
        need(!TrapdoorUsePayload.apply(actor,new TrapdoorUsePayload(owner.above(8),InteractionHand.MAIN_HAND))&&tile.open()==before,"stale owner cannot toggle");
        Vec3 direction=toward.subtract(start).normalize();aim(actor,start.subtract(direction.scale(4)),toward);
        need(!TrapdoorUsePayload.apply(actor,intent)&&tile.open()==before,"out-of-reach owner cannot toggle");aim(actor,start,toward);
        BlockPos wall=BlockPos.containing(start.add(direction));need(level.isEmptyBlock(wall)&&!wall.equals(owner),"separate empty nearer-wall fixture");
        level.setBlock(wall,Blocks.STONE.defaultBlockState(),18);
        try{need(!TrapdoorUsePayload.apply(actor,intent)&&tile.open()==before,"nearer terrain refuses forged owner intent");}
        finally{level.removeBlock(wall,false);}
        need(TrapdoorUsePayload.apply(actor,intent)&&tile.open()!=before,"normal server game mode opens/closes authoritative owner");tile.requestOpen(before);
    }
    public static void report(){System.out.println("[vandorlabs][trapdoor-use] PASS checks="+checks+" nine-byte roundtrip/lengths/stale-owner/offhand/reach/nearer-wall/actual-game-mode");}
}
