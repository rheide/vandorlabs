package com.vandorlabs.neoforge.trapdoor;

import com.vandorlabs.persistence.TrapdoorData;
import com.vandorlabs.redstone.ChannelList;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.*;
import net.minecraft.world.phys.*;
import net.minecraft.world.phys.shapes.*;
import java.util.*;

/** Actual installed hosts: overhang discovery, ray arithmetic, lazy mutation and cache reuse. */
public final class TrapdoorTargetChecks {
    private static int checks;
    private static void need(boolean ok,String why){checks++;if(!ok)throw new AssertionError("Trapdoor targets: "+why);}
    private static AABB outside(AABB b,BlockPos owner){
        if(b.maxX>owner.getX()+1)return new AABB(Math.max(b.minX,owner.getX()+1),b.minY,b.minZ,b.maxX,b.maxY,b.maxZ);
        if(b.minX<owner.getX())return new AABB(b.minX,b.minY,b.minZ,Math.min(b.maxX,owner.getX()),b.maxY,b.maxZ);
        if(b.maxY>owner.getY()+1)return new AABB(b.minX,Math.max(b.minY,owner.getY()+1),b.minZ,b.maxX,b.maxY,b.maxZ);
        if(b.minY<owner.getY())return new AABB(b.minX,b.minY,b.minZ,b.maxX,Math.min(b.maxY,owner.getY()),b.maxZ);
        if(b.maxZ>owner.getZ()+1)return new AABB(b.minX,b.minY,Math.max(b.minZ,owner.getZ()+1),b.maxX,b.maxY,b.maxZ);
        if(b.minZ<owner.getZ())return new AABB(b.minX,b.minY,b.minZ,b.maxX,b.maxY,Math.min(b.maxZ,owner.getZ()));
        throw new AssertionError("Fixture must overhang its owner cell: "+b);
    }
    public static void run(ServerLevel level,NativeTrapdoorBlock regular,NativeTrapdoorBlock diagonal){
        var actor=new net.neoforged.neoforge.common.util.FakePlayer(level,new com.mojang.authlib.GameProfile(UUID.fromString("a5a9d46e-7f72-4fa4-a7d8-a080a0504ef1"),"TrapdoorTargets"));
        Iterable<VoxelShape> empty=List.of();var far=new AABB(-100,200,-100,-99,201,-99);need(NativeTrapdoorTargets.blockCollisions(level,far,empty)==empty,"unaffected collision iterable identity");
        for(int family=0;family<2;family++)for(var facing:new Direction[]{Direction.NORTH,Direction.EAST,Direction.SOUTH,Direction.WEST})for(int motion=0;motion<3;motion++){
            BlockPos owner=new BlockPos(3196,80,3196);level.getChunkAt(owner);var block=family==0?regular:diagonal;
            level.setBlock(owner,block.defaultBlockState().setValue(NativeTrapdoorBlock.FACING,facing),18);var tile=(NativeTrapdoorEntity)level.getBlockEntity(owner);tile.onLoad();
            actor.setPos(owner.getX()+.5,owner.getY(),owner.getZ()+.5);var old=tile.settings();var c=old.choices();
            var next=new NativeTrapdoorSettings(new TrapdoorData(c.texture,0,1,0,true,family==0&&motion!=1,motion!=0,family==1&&motion==2,family==0&&motion==1),ChannelList.EMPTY,facing.get2DDataValue(),0,false,family==1);
            need(tile.configure(actor,next),"configure actual installed sliding host");tile.requestOpen(true);
            boolean initialOpen=true;var geometry=tile.geometry();var bounds=geometry.bounds();if(bounds.minX>=0&&bounds.minY>=0&&bounds.minZ>=0&&bounds.maxX<=1&&bounds.maxY<=1&&bounds.maxZ<=1){tile.requestOpen(false);initialOpen=false;geometry=tile.geometry();}var query=outside(geometry.bounds().move(owner),owner);need(query.getSize()>0,"outside-cell query");
            var first=NativeTrapdoorTargets.blockCollisions(level,query,empty).iterator();need(first.hasNext(),"source broad offset collision outside owner");VoxelShape shape=first.next();
            need(Shapes.joinIsNotEmpty(shape,Shapes.create(query),BooleanOp.AND),"outside collision intersects physical query");
            var warmed=NativeTrapdoorTargets.blockCollisions(level,query,empty).iterator();need(warmed.hasNext()&&warmed.next()==shape,"warm publication reuses exact voxel shape");
            Vec3 center=geometry.bounds().move(owner).getCenter();boolean traced=false;double[] expected=new double[4];
            for(var axis:Direction.Axis.values()){
                double dx=axis==Direction.Axis.X?4:0,dy=axis==Direction.Axis.Y?4:0,dz=axis==Direction.Axis.Z?4:0;
                Vec3 start=center.add(-dx,-dy,-dz),end=center.add(dx,dy,dz);
                if(!geometry.offsetTrace(owner,start,end,expected))continue;
                var actual=NativeTrapdoorTargets.trace(level,start,end);need(actual!=null&&actual.owner().equals(owner),"loaded moved-leaf target ownership");
                need(actual.hit().getLocation().x==expected[0]&&actual.hit().getLocation().y==expected[1]&&actual.hit().getLocation().z==expected[2]&&actual.hit().getDirection().get3DDataValue()==(int)expected[3],"source-matched exact ray hit and facing");traced=true;
            }
            need(traced,"endpoint crossed by fixture ray");
            var pending=NativeTrapdoorTargets.blockCollisions(level,query,empty).iterator();need(pending.hasNext(),"lazy pending collision before state change");tile.requestOpen(!initialOpen);need(!pending.hasNext(),"lazy old publication retires after motion state replacement");
            tile.requestOpen(initialOpen);var removed=NativeTrapdoorTargets.blockCollisions(level,query,empty).iterator();need(removed.hasNext(),"lazy pending collision before removal");level.removeBlock(owner,false);need(!removed.hasNext(),"removed owner cannot publish cached collision");
        }
        need(NativeTrapdoorTargets.trace(level,new Vec3(Double.NaN,0,0),Vec3.ZERO)==null,"nonfinite ray refused");need(NativeTrapdoorTargets.trace(level,Vec3.ZERO,new Vec3(65,0,0))==null,"oversized ray refused");
        need(NativeTrapdoorTargets.retainedBytes()<=4*1024*1024,"world-free collision cache byte bound");
        System.out.println("[vandorlabs][trapdoor-targets] PASS checks="+checks+" 24 rotating/sliding/cover endpoints/outside-cell rays/lazy replacement/removal warm shape identity byte bound");
    }
}
