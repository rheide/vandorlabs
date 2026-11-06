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
            need(outside>0,"moved leaves hit beyond owner voxel");NativeDoorTargets.remove(host);need(NativeDoorTargets.trace(level,new Vec3(root.getX()-2,root.getY()+.5,root.getZ()+.5),new Vec3(root.getX()+2,root.getY()+.5,root.getZ()+.5))==null,"removed owner cannot be routed");
            System.out.println("[vandorlabs][door-target] dedicated PASS checks="+checks+" outsideOwnerRays="+outside+" installed two-half routing; client/occlusion/packets separate");
        }finally{NativeDoorTargets.remove(host);level.removeBlock(root,false);level.removeBlock(root.above(),false);}
    }
}
