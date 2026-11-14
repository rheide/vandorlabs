package com.vandorlabs.copier;

import net.minecraft.block.state.IBlockState;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;

/** Prepare an unpublished native tile with its physical context available during capability decoding. */
public final class PhysicalTileFactory {
    private PhysicalTileFactory(){}
    public static TileEntity create(WorldServer world,IBlockState state,NBTTagCompound saved){
        if(world==null||state==null||saved==null||!saved.hasKey("id",8)||!saved.hasKey("x",3)||!saved.hasKey("y",3)||!saved.hasKey("z",3))throw new IllegalArgumentException("Missing physical tile identity");
        BlockPos pos=new BlockPos(saved.getInteger("x"),saved.getInteger("y"),saved.getInteger("z"));
        if(!world.isBlockLoaded(pos,false))throw new IllegalArgumentException("Load the physical tile target first");
        TileEntity tile=state.getBlock().createTileEntity(world,state);
        if(tile==null)throw new IllegalArgumentException("Missing physical block tile factory");
        ResourceLocation registered=TileEntity.getKey(tile.getClass());
        if(registered==null||!registered.toString().equals(saved.getString("id")))throw new IllegalArgumentException("Physical tile identity does not match its block factory");
        // TileEntity.create calls setWorldCreate, whose vanilla implementation is empty.
        // Binding after readFromNBT is too late for external capability deserializers.
        tile.setWorld(world);tile.setPos(pos);tile.readFromNBT(saved.copy());
        if(tile.getWorld()!=world||!tile.getPos().equals(pos))throw new IllegalArgumentException("Physical tile changed its prepared context");
        return tile;
    }
}
