package com.vandorlabs.tiles;

import com.vandorlabs.blocks.BlockTelescopicLandingGear;
import com.vandorlabs.redstone.*;
import net.minecraft.block.state.IBlockState;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ITickable;
import net.minecraft.util.math.*;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SPacketUpdateTileEntity;
import net.minecraft.world.World;

/** Progress is extension distance in blocks. Lower cells only store their owner. */
public final class TileEntityLandingGear extends TileEntity implements ITickable, RedstoneChannelMember {
    public float progress, previous;
    private int extensionPixels=16, mode=1, channel;
    private boolean channelSignal, lastSignal;
    private BlockPos owner;
    public BlockPos owner(){return owner;}
    public void setOwner(BlockPos root){owner=root;markDirty();sync();}
    public int getExtensionPixels(){return extensionPixels;}
    public int getMode(){return mode;}
    public boolean isRoot(){return world!=null && world.getBlockState(pos).getBlock() instanceof BlockTelescopicLandingGear
            && !world.getBlockState(pos).getValue(BlockTelescopicLandingGear.LOWER);}
    @Override public boolean shouldRefresh(World world,BlockPos pos,IBlockState before,IBlockState after){
        return before.getBlock()!=after.getBlock() || before.getValue(BlockTelescopicLandingGear.LOWER)!=after.getValue(BlockTelescopicLandingGear.LOWER);
    }
    public boolean configure(int nextMode,int nextChannel,int pixels){
        if(!isRoot()||nextMode<0||nextMode>2||nextChannel<0||pixels<0||pixels>64)return false;
        BlockTelescopicLandingGear block=(BlockTelescopicLandingGear)getBlockType();
        if(world.getBlockState(pos).getValue(BlockTelescopicLandingGear.EXTENDED)
                && !block.reserve(world,pos,Math.max(progress,pixels/16F)))return false;
        mode=nextMode;extensionPixels=pixels;setRedstoneChannel(nextChannel);
        evaluateSignal(true);markDirty();sync();return true;
    }
    public void update(){
        if(!isRoot())return;
        boolean extended=world.getBlockState(pos).getValue(BlockTelescopicLandingGear.EXTENDED);
        float target=extended?extensionPixels/16F:0;
        previous=progress;
        progress=progress<target?Math.min(target,progress+.05F):Math.max(target,progress-.05F);
        if(!world.isRemote){
            if(progress!=previous){markDirty();
                ((BlockTelescopicLandingGear)getBlockType()).releaseBelow(world,pos,(int)Math.ceil(Math.max(progress,target)));}
        }
    }
    public void inputChanged(){RedstoneChannels.inputChanged(this);evaluateSignal(false);}
    private void evaluateSignal(boolean force){
        if(!isRoot()||world.isRemote)return;
        boolean signal=hasLocalRedstoneSignal()||channelSignal;
        if(mode!=0 && (force||signal!=lastSignal))
            ((BlockTelescopicLandingGear)getBlockType()).setExtended(world,pos,mode==1?signal:!signal);
        lastSignal=signal;
    }
    public TileEntity channelTile(){return this;}
    public int getRedstoneChannel(){return channel;}
    public void setRedstoneChannel(int value){
        int old=channel;channel=Math.max(0,value);
        if(old!=channel){channelSignal=false;RedstoneChannels.channelChanged(this,old);markDirty();sync();}
    }
    public boolean hasLocalRedstoneSignal(){return isRoot()&&world.isBlockPowered(pos);}
    public void setChannelSignal(boolean value){if(channelSignal!=value){channelSignal=value;evaluateSignal(false);}}
    public void onLoad(){super.onLoad();if(isRoot()){RedstoneChannels.register(this);evaluateSignal(true);}}
    public void invalidate(){RedstoneChannels.unregister(this);super.invalidate();}
    public void onChunkUnload(){RedstoneChannels.unregister(this);super.onChunkUnload();}
    public void sync(){if(world!=null&&!world.isRemote){IBlockState s=world.getBlockState(pos);world.notifyBlockUpdate(pos,s,s,2);}}
    public NBTTagCompound writeToNBT(NBTTagCompound tag){
        super.writeToNBT(tag);tag.setFloat("Progress",progress);tag.setInteger("ExtensionPixels",extensionPixels);
        tag.setInteger("RedstoneMode",mode);tag.setInteger("RedstoneChannel",channel);tag.setBoolean("LastSignal",lastSignal);
        if(owner!=null)tag.setLong("GearOwner",owner.toLong());return tag;
    }
    public void readFromNBT(NBTTagCompound tag){
        super.readFromNBT(tag);float value=tag.getFloat("Progress");progress=Float.isFinite(value)?Math.max(0,Math.min(4,value)):0;previous=progress;
        extensionPixels=tag.hasKey("ExtensionPixels")?Math.max(0,Math.min(64,tag.getInteger("ExtensionPixels"))):16;
        mode=tag.hasKey("RedstoneMode")?Math.max(0,Math.min(2,tag.getInteger("RedstoneMode"))):1;
        channel=Math.max(0,tag.getInteger("RedstoneChannel"));lastSignal=tag.getBoolean("LastSignal");
        owner=tag.hasKey("GearOwner")?BlockPos.fromLong(tag.getLong("GearOwner")):null;
    }
    public NBTTagCompound getUpdateTag(){return writeToNBT(new NBTTagCompound());}
    public SPacketUpdateTileEntity getUpdatePacket(){return new SPacketUpdateTileEntity(pos,0,getUpdateTag());}
    public void onDataPacket(NetworkManager net,SPacketUpdateTileEntity packet){readFromNBT(packet.getNbtCompound());}
    public AxisAlignedBB getRenderBoundingBox(){return new AxisAlignedBB(pos.down(4),pos.add(1,1,1));}
}
