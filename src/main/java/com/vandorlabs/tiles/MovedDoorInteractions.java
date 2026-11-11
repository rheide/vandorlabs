package com.vandorlabs.tiles;

import com.vandorlabs.blocks.BlockLargeProgrammableDoor;
import com.vandorlabs.blocks.BlockConfigurableSpaceDoor;
import net.minecraft.block.BlockDoor;
import com.vandorlabs.blocks.BlockVandorDoor;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.*;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.event.world.GetCollisionBoxesEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import java.util.*;

/** Find moved leaves in loaded chunks even when their destination cells contain air. */
public final class MovedDoorInteractions {
    public static final com.vandorlabs.world.LoadedTileIndex<TileEntitySpaceDoor> INDEX=new com.vandorlabs.world.LoadedTileIndex<>(TileEntitySpaceDoor.class);
    public static final MovedDoorInteractions INSTANCE=new MovedDoorInteractions();
    // Native Large Door roles are local fields. Reject follower tiles before
    // the index performs loaded-chunk and tile-identity reads. Evaluate on each
    // query so assign()/NBT changes need no index re-registration. Extensions
    // retain their original loaded-world validation before calling isAnchor().
    private static final java.util.function.Predicate<TileEntitySpaceDoor> ROOT_CANDIDATE=
            door->door.getClass()!=TileEntityLargeProgrammableDoor.class
                    ||((TileEntityLargeProgrammableDoor)door).isAnchor();
    private MovedDoorInteractions() { }

    public static boolean isMoved(World world,BlockPos pos) {
        if(!world.isBlockLoaded(pos))return false;
        net.minecraft.block.state.IBlockState state=world.getBlockState(pos);
        if(state.getBlock() instanceof BlockLargeProgrammableDoor)return true;
        if(!(state.getBlock() instanceof BlockConfigurableSpaceDoor) || !state.getValue(BlockVandorDoor.OPEN))return false;
        BlockPos lower=state.getValue(BlockVandorDoor.HALF)==BlockDoor.EnumDoorHalf.UPPER?pos.down():pos;
        if(!world.isBlockLoaded(lower))return false;
        Chunk chunk=world.getChunkProvider().getLoadedChunk(lower.getX()>>4,lower.getZ()>>4);
        TileEntity tile=chunk==null?null:chunk.getTileEntityMap().get(lower);
        return tile instanceof TileEntitySpaceDoor && !((TileEntitySpaceDoor)tile).isSliding();
    }
    public static List<AxisAlignedBB> bounds(World world,BlockPos pos) {
        if(!world.isBlockLoaded(pos))return Collections.emptyList();
        net.minecraft.block.state.IBlockState state=world.getBlockState(pos);
        if(!(state.getBlock() instanceof BlockConfigurableSpaceDoor))return Collections.emptyList();
        if(state.getBlock() instanceof BlockLargeProgrammableDoor)return ((BlockLargeProgrammableDoor)state.getBlock()).geometry(world,pos);
        return Collections.singletonList(state.getBoundingBox(world,pos).offset(pos));
    }
    private static List<TileEntitySpaceDoor> doors(World world,AxisAlignedBB query) {
        List<TileEntitySpaceDoor> result=new ArrayList<>();
        // Leaves extend at most three blocks vertically or sideways from the assembly.
        AxisAlignedBB owners=query.grow(5);
        for(TileEntitySpaceDoor door:INDEX.query(world,owners,ROOT_CANDIDATE))if(isMoved(world,door.getPos())
                &&(door instanceof TileEntityLargeProgrammableDoor?((TileEntityLargeProgrammableDoor)door).isAnchor():world.getBlockState(door.getPos()).getValue(BlockVandorDoor.HALF)==BlockDoor.EnumDoorHalf.LOWER))result.add(door);
        return result;
    }
    private static net.minecraft.block.state.IBlockState nativeLargeState(World world,TileEntitySpaceDoor door){
        if(door.getClass()!=TileEntityLargeProgrammableDoor.class||!world.isBlockLoaded(door.getPos()))return null;
        net.minecraft.block.state.IBlockState state=world.getBlockState(door.getPos());
        return state.getBlock().getClass()==BlockLargeProgrammableDoor.class?state:null;
    }
    public static RayTraceResult trace(World world,Vec3d start,Vec3d end) {
        RayTraceResult nearest=null;double distance=Double.POSITIVE_INFINITY;
        for(TileEntitySpaceDoor door:doors(world,new AxisAlignedBB(start.x,start.y,start.z,end.x,end.y,end.z))) {
            if(!(door instanceof TileEntityLargeProgrammableDoor)) {
                for(int half=0;half<2;half++) {
                    BlockPos pos=door.getPos().up(half);if(!world.isBlockLoaded(pos))continue;
                    RayTraceResult hit=world.getBlockState(pos).collisionRayTrace(world,pos,start,end);
                    if(hit!=null && start.squareDistanceTo(hit.hitVec)<distance){nearest=hit;distance=start.squareDistanceTo(hit.hitVec);}
                }
                continue;
            }
            net.minecraft.block.state.IBlockState nativeState=nativeLargeState(world,door);
            if(nativeState!=null){
                RayTraceResult hit=((TileEntityLargeProgrammableDoor)door).collisionIndex(
                        nativeState.getValue(BlockVandorDoor.FACING),nativeState.getValue(BlockVandorDoor.OPEN)).trace(start,end);
                if(hit!=null&&start.squareDistanceTo(hit.hitVec)<distance){
                    distance=start.squareDistanceTo(hit.hitVec);
                    nearest=movedHit(door,nativeState.getValue(BlockVandorDoor.FACING).rotateYCCW(),hit);
                }
                continue;
            }
            for(AxisAlignedBB box:bounds(world,door.getPos())) {
                RayTraceResult hit=box.calculateIntercept(start,end);
                if(hit==null || start.squareDistanceTo(hit.hitVec)>=distance)continue;
                distance=start.squareDistanceTo(hit.hitVec);
                EnumFacing width=world.getBlockState(door.getPos()).getValue(BlockVandorDoor.FACING).rotateYCCW();
                nearest=movedHit(door,width,hit);
            }
        }
        return nearest;
    }
    private static RayTraceResult movedHit(TileEntitySpaceDoor door,EnumFacing width,RayTraceResult hit){
        double column=(hit.hitVec.x-door.getPos().getX())*width.getFrontOffsetX()
                +(hit.hitVec.z-door.getPos().getZ())*width.getFrontOffsetZ();
        if(width.getFrontOffsetX()<0 || width.getFrontOffsetZ()<0)column+=1;
        BlockPos owner=door.getPos().offset(width,MathHelper.clamp(MathHelper.floor(column),0,2))
                .up(MathHelper.clamp(MathHelper.floor(hit.hitVec.y-door.getPos().getY()),0,2));
        return new RayTraceResult(hit.hitVec,hit.sideHit,owner);
    }
    @SubscribeEvent public void collisions(GetCollisionBoxesEvent event) {
        World world=event.getWorld();AxisAlignedBB query=event.getAabb();
        for(TileEntitySpaceDoor door:doors(world,query)) {
            net.minecraft.block.state.IBlockState nativeState=nativeLargeState(world,door);
            if(nativeState!=null){
                ((TileEntityLargeProgrammableDoor)door).collisionIndex(nativeState.getValue(BlockVandorDoor.FACING),
                        nativeState.getValue(BlockVandorDoor.OPEN)).addIntersecting(query,event.getCollisionBoxesList());
                continue;
            }
            for(int half=0;half<(door instanceof TileEntityLargeProgrammableDoor?1:2);half++)
                for(AxisAlignedBB box:bounds(world,door.getPos().up(half)))
                    if(box.intersects(query) && !event.getCollisionBoxesList().contains(box))event.getCollisionBoxesList().add(box);
        }
    }
}
