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
    private int extensionPixels=16, mode=1, channel, configurationRevision, size;
    public int getSize(){return size;}
    public int getConfigurationRevision(){return configurationRevision;}
    private boolean channelSignal, lastSignal;
    private int ownerDistance,ownerX,ownerZ;
    private boolean hasOwner;
    public BlockPos owner(){return hasOwner?pos.add(ownerX,ownerDistance,ownerZ):null;}
    public void setOwner(BlockPos root){
        if(Math.abs(root.getX()-pos.getX())>1||Math.abs(root.getZ()-pos.getZ())>1
                ||root.getY()<pos.getY()||root.getY()-pos.getY()>5||root.equals(pos))
            throw new IllegalArgumentException("Landing gear owner outside reserved footprint");
        ownerDistance=root.getY()-pos.getY();ownerX=root.getX()-pos.getX();ownerZ=root.getZ()-pos.getZ();
        hasOwner=true;markDirty();sync();
    }
    public int getExtensionPixels(){return extensionPixels;}
    public int getMode(){return mode;}
    public boolean isRoot(){return world!=null && world.getBlockState(pos).getBlock() instanceof BlockTelescopicLandingGear
            && !world.getBlockState(pos).getValue(BlockTelescopicLandingGear.LOWER);}
    @Override public boolean shouldRefresh(World world,BlockPos pos,IBlockState before,IBlockState after){
        return before.getBlock()!=after.getBlock() || before.getValue(BlockTelescopicLandingGear.LOWER)!=after.getValue(BlockTelescopicLandingGear.LOWER);
    }
    public boolean configure(int nextMode,int nextChannel,int pixels){return configure(nextMode,nextChannel,pixels,size);}
    public boolean configure(int nextMode,int nextChannel,int pixels,int nextSize){
        if(nextSize<0||nextSize>=BlockTelescopicLandingGear.SIZES.length||!isRoot()||nextMode<0||nextMode>2||nextChannel<0||pixels<0||pixels>64||pixels%8!=0)return false;
        BlockTelescopicLandingGear block=(BlockTelescopicLandingGear)getBlockType();
        boolean extended=world.getBlockState(pos).getValue(BlockTelescopicLandingGear.EXTENDED);
        if((extended||nextSize>=3) && !block.reserve(world,pos,
                extended?Math.max(progress,pixels/16F):progress,nextSize))return false;
        boolean automationChanged=mode!=nextMode||channel!=nextChannel;
        mode=nextMode;extensionPixels=pixels;size=nextSize;setRedstoneChannel(nextChannel);
        if(!world.isRemote)block.releaseBelow(world,pos,
                nextSize>=3||extended?(int)Math.ceil(Math.max(progress,extended?pixels/16F:0)):0);
        evaluateSignal(automationChanged);LandingGearCovers.refresh(world,pos);markDirty();sync();return true;
    }
    public void update(){
        if(!isRoot())return;
        boolean extended=world.getBlockState(pos).getValue(BlockTelescopicLandingGear.EXTENDED);
        float target=extended?extensionPixels/16F:0;
        previous=progress;
        progress=progress<target?Math.min(target,progress+.05F):Math.max(target,progress-.05F);
        if(!world.isRemote){
            if(progress!=previous){markDirty();
                ((BlockTelescopicLandingGear)getBlockType()).releaseBelow(world,pos,(int)Math.ceil(Math.max(progress,target)));
                if(progress==target)LandingGearCovers.refresh(world,pos);}
        }
    }
    public void placed(){if(isRoot()){RedstoneChannels.register(this);evaluateSignal(true);}}
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
    public boolean hasLocalRedstoneSignal(){return isRoot()&&com.vandorlabs.redstone.LoadedRedstonePower.isPowered(world,pos);}
    public void setChannelSignal(boolean value){if(channelSignal!=value){channelSignal=value;evaluateSignal(false);}}
    public void onLoad(){super.onLoad();DeferredTileLoad.schedule(this,this::placed);}
    public void invalidate(){RedstoneChannels.unregister(this);super.invalidate();}
    public void onChunkUnload(){RedstoneChannels.unregister(this);super.onChunkUnload();}
    public void sync(){if(world!=null&&!world.isRemote){IBlockState s=world.getBlockState(pos);world.notifyBlockUpdate(pos,s,s,2);}}
    public NBTTagCompound writeToNBT(NBTTagCompound tag){
        super.writeToNBT(tag);tag.setFloat("Progress",progress);tag.setInteger("ExtensionPixels",extensionPixels);
        tag.setInteger("GearSize",size);tag.setInteger("RedstoneMode",mode);tag.setInteger("RedstoneChannel",channel);tag.setBoolean("LastSignal",lastSignal);
        if(hasOwner){tag.setBoolean("GearHasOwner",true);tag.setInteger("GearOwnerDistance",ownerDistance);
            tag.setInteger("GearOwnerX",ownerX);tag.setInteger("GearOwnerZ",ownerZ);}return tag;
    }
    public void readFromNBT(NBTTagCompound tag){
        super.readFromNBT(tag);float value=tag.getFloat("Progress");progress=Float.isFinite(value)?Math.max(0,Math.min(4,value)):0;previous=progress;
        extensionPixels=tag.hasKey("ExtensionPixels")?Math.round(Math.max(0,Math.min(64,tag.getInteger("ExtensionPixels")))/8F)*8:16;
        size=Math.max(0,Math.min(BlockTelescopicLandingGear.SIZES.length-1,tag.getInteger("GearSize")));
        mode=tag.hasKey("RedstoneMode")?Math.max(0,Math.min(2,tag.getInteger("RedstoneMode"))):1;
        channel=Math.max(0,tag.getInteger("RedstoneChannel"));lastSignal=tag.getBoolean("LastSignal");
        ownerDistance=Math.max(0,Math.min(5,tag.getInteger("GearOwnerDistance")));
        ownerX=Math.max(-1,Math.min(1,tag.getInteger("GearOwnerX")));
        ownerZ=Math.max(-1,Math.min(1,tag.getInteger("GearOwnerZ")));
        hasOwner=tag.getBoolean("GearHasOwner")||ownerDistance>0;
    }
    public NBTTagCompound getUpdateTag(){return writeToNBT(new NBTTagCompound());}
    public SPacketUpdateTileEntity getUpdatePacket(){return new SPacketUpdateTileEntity(pos,0,getUpdateTag());}
    public void onDataPacket(NetworkManager net,SPacketUpdateTileEntity packet){readFromNBT(packet.getNbtCompound());configurationRevision++;}
    public AxisAlignedBB getRenderBoundingBox(){return size==4?new AxisAlignedBB(pos.down(5),pos.add(2,1,2)):size==3
            ?new AxisAlignedBB(pos.add(-1,-5,-1),pos.add(2,1,2))
            :new AxisAlignedBB(pos.down(4),pos.add(1,1,1));}
}
