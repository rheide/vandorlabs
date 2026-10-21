package com.vandorlabs.world;

import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.*;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.*;

/** Adapter for world observers interested in a subset of notifications. */
public abstract class WorldListener implements IWorldEventListener {
    public void notifyBlockUpdate(World world,BlockPos pos,IBlockState before,IBlockState after,int flags){}
    public void markBlockRangeForRenderUpdate(int x1,int y1,int z1,int x2,int y2,int z2){}
    public void notifyLightSet(BlockPos pos){}
    public void playSoundToAllNearExcept(EntityPlayer p,SoundEvent s,SoundCategory c,double x,double y,double z,float volume,float pitch){}
    public void playRecord(SoundEvent s,BlockPos p){}
    public void spawnParticle(int id,boolean range,double x,double y,double z,double sx,double sy,double sz,int... args){}
    public void spawnParticle(int id,boolean range,boolean level,double x,double y,double z,double sx,double sy,double sz,int... args){}
    public void onEntityAdded(Entity e){}
    public void onEntityRemoved(Entity e){}
    public void broadcastSound(int id,BlockPos p,int data){}
    public void playEvent(EntityPlayer p,int id,BlockPos pos,int data){}
    public void sendBlockBreakProgress(int id,BlockPos pos,int progress){}
}
