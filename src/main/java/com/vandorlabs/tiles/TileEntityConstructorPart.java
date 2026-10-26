package com.vandorlabs.tiles;

import net.minecraft.tileentity.TileEntity;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;

/** Relative owner identity survives moving a complete machine with world-edit tools. */
public final class TileEntityConstructorPart extends TileEntity implements net.minecraft.util.ITickable {
    private BlockPos offset=BlockPos.ORIGIN;private boolean matter;private int ticks;
    public void owner(BlockPos root,boolean matter){offset=root.subtract(pos);this.matter=matter;markDirty();if(world!=null&&!world.isRemote)world.notifyBlockUpdate(pos,world.getBlockState(pos),world.getBlockState(pos),3);}
    @Override public void update(){
        if(world==null || world.isRemote || ++ticks%20!=0 || !world.isBlockLoaded(owner()))return;
        TileEntity root=world.getTileEntity(owner());
        if(root instanceof TileEntityConstructor && ((TileEntityConstructor)root).formed())return;
        world.setBlockState(pos,matter?net.minecraft.block.Block.REGISTRY.getObject(new net.minecraft.util.ResourceLocation("vandorlabs:programmable_matter_block")).getDefaultState():net.minecraft.init.Blocks.AIR.getDefaultState(),3);
    }
    public BlockPos owner(){return pos.add(offset);}
    @Override public NBTTagCompound writeToNBT(NBTTagCompound tag){super.writeToNBT(tag);tag.setLong("ConstructorOwner",offset.toLong());tag.setBoolean("ConstructorMatter",matter);return tag;}
    @Override public NBTTagCompound getUpdateTag(){return writeToNBT(new NBTTagCompound());}
    @Override public net.minecraft.network.play.server.SPacketUpdateTileEntity getUpdatePacket(){return new net.minecraft.network.play.server.SPacketUpdateTileEntity(pos,0,getUpdateTag());}
    @Override public void onDataPacket(net.minecraft.network.NetworkManager manager,net.minecraft.network.play.server.SPacketUpdateTileEntity packet){readFromNBT(packet.getNbtCompound());}
    @Override public void readFromNBT(NBTTagCompound tag){super.readFromNBT(tag);offset=BlockPos.fromLong(tag.getLong("ConstructorOwner"));matter=tag.getBoolean("ConstructorMatter");}
}
