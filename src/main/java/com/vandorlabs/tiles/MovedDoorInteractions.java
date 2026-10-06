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
    public static final MovedDoorInteractions INSTANCE=new MovedDoorInteractions();
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
        for(int x=MathHelper.floor(owners.minX)>>4;x<=MathHelper.floor(owners.maxX)>>4;x++)
            for(int z=MathHelper.floor(owners.minZ)>>4;z<=MathHelper.floor(owners.maxZ)>>4;z++) {
                Chunk chunk=world.getChunkProvider().getLoadedChunk(x,z);if(chunk==null)continue;
                for(TileEntity tile:chunk.getTileEntityMap().values())if(tile instanceof TileEntitySpaceDoor) {
                    TileEntitySpaceDoor door=(TileEntitySpaceDoor)tile;
                    if(owners.intersects(new AxisAlignedBB(door.getPos())) && isMoved(world,door.getPos())
                            && (door instanceof TileEntityLargeProgrammableDoor?((TileEntityLargeProgrammableDoor)door).isAnchor():world.getBlockState(door.getPos()).getValue(BlockVandorDoor.HALF)==BlockDoor.EnumDoorHalf.LOWER))result.add(door);
                }
            }
        return result;
    }
    public static RayTraceResult trace(World world,Vec3d start,Vec3d end) {
        RayTraceResult nearest=null;double distance=Double.POSITIVE_INFINITY;
        for(TileEntitySpaceDoor door:doors(world,new AxisAlignedBB(start,end))) {
            if(!(door instanceof TileEntityLargeProgrammableDoor)) {
                for(int half=0;half<2;half++) {
                    BlockPos pos=door.getPos().up(half);if(!world.isBlockLoaded(pos))continue;
                    RayTraceResult hit=world.getBlockState(pos).collisionRayTrace(world,pos,start,end);
                    if(hit!=null && start.squareDistanceTo(hit.hitVec)<distance){nearest=hit;distance=start.squareDistanceTo(hit.hitVec);}
                }
                continue;
            }
            for(AxisAlignedBB box:bounds(world,door.getPos())) {
                RayTraceResult hit=box.calculateIntercept(start,end);
                if(hit==null || start.squareDistanceTo(hit.hitVec)>=distance)continue;
                distance=start.squareDistanceTo(hit.hitVec);
                EnumFacing width=world.getBlockState(door.getPos()).getValue(BlockVandorDoor.FACING).rotateYCCW();
                double column=(hit.hitVec.x-door.getPos().getX())*width.getFrontOffsetX()
                        +(hit.hitVec.z-door.getPos().getZ())*width.getFrontOffsetZ();
                if(width.getFrontOffsetX()<0 || width.getFrontOffsetZ()<0)column+=1;
                BlockPos owner=door.getPos().offset(width,MathHelper.clamp(MathHelper.floor(column),0,2))
                        .up(MathHelper.clamp(MathHelper.floor(hit.hitVec.y-door.getPos().getY()),0,2));
                nearest=new RayTraceResult(hit.hitVec,hit.sideHit,owner);
            }
        }
        return nearest;
    }
    @SubscribeEvent public void collisions(GetCollisionBoxesEvent event) {
        World world=event.getWorld();AxisAlignedBB query=event.getAabb();
        for(TileEntitySpaceDoor door:doors(world,query)) {
            for(int half=0;half<(door instanceof TileEntityLargeProgrammableDoor?1:2);half++)
                for(AxisAlignedBB box:bounds(world,door.getPos().up(half)))
                    if(box.intersects(query) && !event.getCollisionBoxesList().contains(box))event.getCollisionBoxesList().add(box);
        }
    }
}
