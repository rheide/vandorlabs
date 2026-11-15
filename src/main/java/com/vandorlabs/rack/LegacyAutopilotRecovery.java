package com.vandorlabs.rack;

import net.minecraft.block.state.IBlockState;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;

/** Same-cell last resort. No live navigation/providers; a cold load retries the canonical old owner. */
public final class LegacyAutopilotRecovery extends TileEntity {
    private final NBTTagCompound original;
    public LegacyAutopilotRecovery(NBTTagCompound original){
        if(!"vandorlabs:autopilot".equals(original.getString("id")))throw new IllegalArgumentException("Not an Autopilot recovery owner");
        this.original=original.copy();setPos(new BlockPos(original.getInteger("x"),original.getInteger("y"),original.getInteger("z")));
    }
    @Override public NBTTagCompound writeToNBT(NBTTagCompound tag){tag.merge(original.copy());return tag;}
    @Override public NBTTagCompound getUpdateTag(){NBTTagCompound tag=new NBTTagCompound();tag.setString("id","vandorlabs:autopilot");tag.setInteger("x",pos.getX());tag.setInteger("y",pos.getY());tag.setInteger("z",pos.getZ());return tag;}
    @Override public boolean hasCapability(net.minecraftforge.common.capabilities.Capability<?> type,net.minecraft.util.EnumFacing side){return false;}
    @Override public <T>T getCapability(net.minecraftforge.common.capabilities.Capability<T> type,net.minecraft.util.EnumFacing side){return null;}

    /** Install the inert raw owner before reporting any failure of the ordinary world setter. */
    public static LegacyAutopilotRecovery install(World world,BlockPos pos,IBlockState before,NBTTagCompound original){
        if(world.isRemote||!world.isBlockLoaded(pos,false)||pos.getY()<0||pos.getY()>=256)throw new IllegalStateException("Autopilot recovery chunk unavailable");
        LegacyAutopilotRecovery recovery=new LegacyAutopilotRecovery(original);recovery.setWorld(world);recovery.setPos(pos);
        Chunk chunk=world.getChunkFromBlockCoords(pos);ExtendedBlockStorage[] sections=chunk.getBlockStorageArray();int index=pos.getY()>>4;
        if(sections[index]==Chunk.NULL_BLOCK_STORAGE)sections[index]=new ExtendedBlockStorage(index<<4,world.provider.hasSkyLight());
        sections[index].set(pos.getX()&15,pos.getY()&15,pos.getZ()&15,before);chunk.markDirty();
        RuntimeException setterFailure=null;TileEntity replaced=chunk.getTileEntityMap().get(pos);
        try{world.removeTileEntity(pos);}catch(RuntimeException failure){setterFailure=failure;}
        if(replaced!=null&&replaced!=recovery){try{replaced.invalidate();}catch(RuntimeException failure){if(setterFailure==null)setterFailure=failure;else setterFailure.addSuppressed(failure);}finally{world.loadedTileEntityList.remove(replaced);world.tickableTileEntities.remove(replaced);}}
        // Chunk serialization must already see the complete original if setTileEntity throws too.
        recovery.validate();chunk.getTileEntityMap().put(pos.toImmutable(),recovery);chunk.markDirty();
        try{world.setTileEntity(pos,recovery);if(world.getTileEntity(pos)!=recovery)throw new IllegalStateException("Autopilot recovery installation refused");}
        catch(RuntimeException failure){if(setterFailure==null)setterFailure=failure;else setterFailure.addSuppressed(failure);}
        finally{recovery.validate();chunk.getTileEntityMap().put(pos.toImmutable(),recovery);chunk.markDirty();}
        if(setterFailure!=null)throw new IllegalStateException("Autopilot recovery retained in its loaded chunk",setterFailure);
        return recovery;
    }
}
