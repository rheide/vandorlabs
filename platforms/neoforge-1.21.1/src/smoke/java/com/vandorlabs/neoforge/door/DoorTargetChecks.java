package com.vandorlabs.neoforge.door;

import com.vandorlabs.persistence.SpaceDoorData;
import com.vandorlabs.redstone.ChannelList;
import com.vandorlabs.render.LegacyBoxTrace;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.phys.*;

/** Installed owner routing to the already source-frozen geometry; no client packet/render acceptance claim. */
public final class DoorTargetChecks {
    private static int checks;
    private static void need(boolean pass,String message){checks++;if(!pass)throw new AssertionError("Door target: "+message);}
    private static double probe(double min,double max){return min<0?(min+Math.min(0,max))*.5:max>1?(Math.max(1,min)+max)*.5:(min+max)*.5;}
    private static void powerBoundary(ServerLevel level,BlockPos root){
        var oldProbe=new BlockPos(((root.getX()+1024)&~15)+8,root.getY(),((root.getZ()+1024)&~15)+15);
        level.getChunkAt(oldProbe);int x=oldProbe.getX()>>4,z=(oldProbe.getZ()>>4)+1;
        need(level.getChunkSource().getChunkNow(x,z)==null,"vanilla power baseline begins at a genuinely cold adjacent chunk");
        level.hasNeighborSignal(oldProbe);
        need(level.getChunkSource().getChunkNow(x,z)!=null,"vanilla neighbor sampler reproduces the forced cold-chunk read");
        var safeProbe=oldProbe.east(128);level.getChunkAt(safeProbe);x=safeProbe.getX()>>4;
        need(level.getChunkSource().getChunkNow(x,z)==null,"bounded power sample begins at a fresh cold boundary");
        need(com.vandorlabs.neoforge.redstone.NativeLoadedRedstonePower.level(level,safeProbe)==0
                &&level.getChunkSource().getChunkNow(x,z)==null,"shared power sampler ignores the cold boundary without loading it");
    }
    public static void run(ServerLevel level,NativeProgrammableDoorEntity prototype){
        var root=prototype.getBlockPos().east(64);level.getChunkAt(root);level.setBlockAndUpdate(root.below(),Blocks.STONE.defaultBlockState());
        var block=(NativeProgrammableDoorBlock)prototype.getBlockState().getBlock();var base=block.defaultBlockState();
        level.setBlock(root,base,Block.UPDATE_CLIENTS);level.setBlock(root.above(),base.setValue(NativeProgrammableDoorBlock.HALF,DoubleBlockHalf.UPPER),Block.UPDATE_CLIENTS);
        var host=(NativeProgrammableDoorEntity)level.getBlockEntity(root);NativeDoorTargets.add(host);int outside=0;
        try{
            for(var face:Direction.Plane.HORIZONTAL)for(boolean open:new boolean[]{false,true})for(boolean left:new boolean[]{false,true})for(int depth=0;depth<3;depth++)for(boolean sliding:new boolean[]{false,true}){
                var settings=NativeDoorSettings.fresh(false,sliding);var old=settings.door();
                settings=new NativeDoorSettings(new SpaceDoorData(old.design,old.detail,old.framed,old.direction,depth==0,sliding,old.hinges,old.trigger,false),settings.faceTexture(),settings.rawLayout(),settings.tileTexture(),depth,0,0,127,settings.frames(),settings.glass());host.installPlacement(settings,ChannelList.EMPTY);
                var lower=base.setValue(NativeProgrammableDoorBlock.FACING,face).setValue(NativeProgrammableDoorBlock.OPEN,open).setValue(NativeProgrammableDoorBlock.HINGE,left?DoorHingeSide.LEFT:DoorHingeSide.RIGHT);
                level.setBlock(root,lower,Block.UPDATE_CLIENTS);level.setBlock(root.above(),lower.setValue(NativeProgrammableDoorBlock.HALF,DoubleBlockHalf.UPPER),Block.UPDATE_CLIENTS);
                for(int half=0;half<2;half++){
                    var pos=half==0?root:root.above();var box=NativeDoorGeometry.outline(settings,face,open,left,false);double x=probe(box.minX,box.maxX),z=probe(box.minZ,box.maxZ);
                    boolean crossX=box.getXsize()<box.getZsize();var start=new Vec3(pos.getX()+x+(crossX?-2:0),pos.getY()+.5,pos.getZ()+z+(crossX?0:-2));var end=start.add(crossX?4:0,0,crossX?0:4);
                    var expected=LegacyBoxTrace.clip(box.minX+pos.getX(),box.minY+pos.getY(),box.minZ+pos.getZ(),box.maxX+pos.getX(),box.maxY+pos.getY(),box.maxZ+pos.getZ(),start.x,start.y,start.z,end.x,end.y,end.z);var target=NativeDoorTargets.trace(level,start,end);
                    need(expected!=null&&target!=null&&target.root().equals(root)&&target.hit().getBlockPos().equals(pos)&&target.hit().getDirection().get3DDataValue()==expected.face&&target.hit().getLocation().distanceToSqr(new Vec3(expected.x,expected.y,expected.z))<1e-20,"actual leaf ray face="+face+" open="+open+" left="+left+" depth="+depth+" sliding="+sliding+" half="+half);
                    if(expected.x<pos.getX()||expected.x>pos.getX()+1||expected.z<pos.getZ()||expected.z>pos.getZ()+1)outside++;
                }
            }
            need(outside>0,"moved leaves hit beyond owner voxel");
            host.installPlacement(NativeDoorSettings.fresh(false,false),ChannelList.EMPTY);var closed=base.setValue(NativeProgrammableDoorBlock.FACING,Direction.NORTH).setValue(NativeProgrammableDoorBlock.OPEN,false);level.setBlock(root,closed,Block.UPDATE_CLIENTS);level.setBlock(root.above(),closed.setValue(NativeProgrammableDoorBlock.HALF,DoubleBlockHalf.UPPER),Block.UPDATE_CLIENTS);
            var actor=net.neoforged.neoforge.common.util.FakePlayerFactory.get(level,new com.mojang.authlib.GameProfile(java.util.UUID.fromString("7de68150-9c76-4771-b765-5d251c0ba192"),"DoorTarget"));actor.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);actor.setPos(root.getX()+.5,root.getY()+.5-actor.getEyeHeight(),root.getZ()-2);actor.setYRot(0);actor.setXRot(0);actor.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,net.minecraft.world.item.ItemStack.EMPTY);
            var intent=new DoorUsePayload(root,net.minecraft.world.InteractionHand.MAIN_HAND);var buffer=new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(),level.registryAccess());try{DoorUsePayload.CODEC.encode(buffer,intent);need(buffer.readableBytes()==9&&DoorUsePayload.CODEC.decode(buffer).equals(intent),"bounded nine-byte intent round trip");buffer.clear();buffer.writeBlockPos(root);buffer.writeByte(2);boolean rejected=false;try{DoorUsePayload.CODEC.decode(buffer);}catch(IllegalArgumentException expected){rejected=true;}need(rejected,"invalid hand refused before world access");buffer.clear();buffer.writeBlockPos(root);buffer.writeByte(0);buffer.writeByte(0);rejected=false;try{DoorUsePayload.CODEC.decode(buffer);}catch(IllegalArgumentException expected){rejected=true;}need(rejected,"trailing bytes refused");}finally{buffer.release();}
            need(DoorUsePayload.apply(actor,intent)&&level.getBlockState(root).getValue(NativeProgrammableDoorBlock.OPEN),"authoritative eye/look intent reaches actual manual toggle");
            need(!DoorUsePayload.apply(actor,new DoorUsePayload(root.east(),intent.hand())),"stale or spoofed owner refused");
            level.setBlock(root,closed,Block.UPDATE_CLIENTS);level.setBlock(root.above(),closed.setValue(NativeProgrammableDoorBlock.HALF,DoubleBlockHalf.UPPER),Block.UPDATE_CLIENTS);
            actor.setItemInHand(intent.hand(),new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.WATER_BUCKET));
            need(DoorUsePayload.apply(actor,intent)&&host.containedFluids().occupied(0)!=host.containedFluids().occupied(1)&&!level.getBlockState(root).getValue(NativeProgrammableDoorBlock.OPEN)&&actor.getMainHandItem().is(net.minecraft.world.item.Items.BUCKET),"routed water bucket uses ordinary item hooks without toggling the leaf");
            need(DoorUsePayload.apply(actor,intent)&&!host.containedFluids().occupied(0)&&!host.containedFluids().occupied(1)&&actor.getMainHandItem().is(net.minecraft.world.item.Items.WATER_BUCKET),"routed empty bucket returns exactly the stored source through ordinary item hooks");
            actor.setItemInHand(intent.hand(),net.minecraft.world.item.ItemStack.EMPTY);

            level.setBlock(root,closed,Block.UPDATE_CLIENTS);level.setBlock(root.above(),closed.setValue(NativeProgrammableDoorBlock.HALF,DoubleBlockHalf.UPPER),Block.UPDATE_CLIENTS);level.setBlock(root.north(),Blocks.STONE.defaultBlockState(),Block.UPDATE_CLIENTS);need(!DoorUsePayload.apply(actor,intent)&&!level.getBlockState(root).getValue(NativeProgrammableDoorBlock.OPEN),"nearer solid block prevents interaction through wall");level.removeBlock(root.north(),false);
            level.setBlock(root.south(),Blocks.STONE.defaultBlockState(),Block.UPDATE_CLIENTS);need(DoorUsePayload.apply(actor,intent),"solid block behind leaf does not occlude it");level.removeBlock(root.south(),false);
            actor.setPos(root.getX()+.5,actor.getY(),root.getZ()-10);need(!DoorUsePayload.apply(actor,intent),"out-of-reach intent refused");
            NativeDoorTargets.remove(host);need(NativeDoorTargets.trace(level,new Vec3(root.getX()-2,root.getY()+.5,root.getZ()+.5),new Vec3(root.getX()+2,root.getY()+.5,root.getZ()+.5))==null,"removed owner cannot be routed");
            powerBoundary(level,root);
            System.out.println("[vandorlabs][door-target] dedicated PASS checks="+checks+" outsideOwnerRays="+outside+" installed two-half routing; client/occlusion/packets separate");
        }finally{NativeDoorTargets.remove(host);level.removeBlock(root,false);level.removeBlock(root.above(),false);}
    }
}
