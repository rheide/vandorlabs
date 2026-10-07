package com.vandorlabs.tiles;

import com.vandorlabs.redstone.ChannelList;
import com.vandorlabs.redstone.RedstoneChannels;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.block.state.IBlockState;

/** Four detents share the normal channel latch; limits are configuration, not live power. */
public class TileEntitySignalControl extends TileEntityRedstoneChannel {
    private int low=5,high=15,step,baseHeight,baseTilt,tiltDirection;
    public int getControlType(){return world!=null && world.getBlockState(pos).getBlock() instanceof com.vandorlabs.blocks.BlockSignalControl?((com.vandorlabs.blocks.BlockSignalControl)world.getBlockState(pos).getBlock()).controlType():0;}
    public void configureType(int type){
        if(world==null || world.isRemote || type<0 || type>2)return;
        IBlockState before=world.getBlockState(pos);
        if(!(before.getBlock() instanceof com.vandorlabs.blocks.BlockSignalControl) || !((com.vandorlabs.blocks.BlockSignalControl)before.getBlock()).hasSelectableType())return;
        net.minecraft.block.Block next=net.minecraft.block.Block.getBlockFromName("vandorlabs:"+com.vandorlabs.blocks.BlockSignalControl.TYPES[type]);
        if(next==before.getBlock())return;
        IBlockState after=next.getDefaultState().withProperty(com.vandorlabs.blocks.BlockVandorSwitch.FACING,before.getValue(com.vandorlabs.blocks.BlockVandorSwitch.FACING)).withProperty(com.vandorlabs.blocks.BlockVandorSwitch.ON,before.getValue(com.vandorlabs.blocks.BlockVandorSwitch.ON)).withProperty(com.vandorlabs.blocks.BlockVandorSwitch.ROTATION,before.getValue(com.vandorlabs.blocks.BlockVandorSwitch.ROTATION));
        world.setBlockState(pos,after,3);markDirty();world.notifyBlockUpdate(pos,after,after,3);
    }
    @Override public boolean shouldRefresh(net.minecraft.world.World world,net.minecraft.util.math.BlockPos pos,IBlockState before,IBlockState after){
        if(before.getBlock() instanceof com.vandorlabs.blocks.BlockSignalControl && after.getBlock() instanceof com.vandorlabs.blocks.BlockSignalControl && ((com.vandorlabs.blocks.BlockSignalControl)before.getBlock()).hasSelectableType() && ((com.vandorlabs.blocks.BlockSignalControl)after.getBlock()).hasSelectableType())return false;
        return super.shouldRefresh(world,pos,before,after);
    }
    public int getBaseHeight(){return baseHeight;}
    public int getBaseTilt(){return baseTilt;}
    public int getTiltDirection(){return tiltDirection;}
    public void configureMount(int height,int tilt,int direction){
        if(!com.vandorlabs.blocks.SignalControlMount.valid(height,tilt,direction))return;
        if(world!=null && (!(world.getBlockState(pos).getBlock() instanceof com.vandorlabs.blocks.BlockSignalControl) || !((com.vandorlabs.blocks.BlockSignalControl)world.getBlockState(pos).getBlock()).hasAdjustableBase()))return;
        baseHeight=height;baseTilt=tilt;tiltDirection=direction;markDirty();
        if(world!=null){IBlockState state=world.getBlockState(pos);world.notifyBlockUpdate(pos,state,state,3);world.markBlockRangeForRenderUpdate(pos,pos);}
    }
    public void readMount(NBTTagCompound tag){if(tag.hasKey("ControlType",3))configureType(tag.getInteger("ControlType"));configureMount(tag.getInteger("BaseHeight"),com.vandorlabs.blocks.SignalControlMount.readTilt(tag),tag.getInteger("TiltDirection"));}
    private void writeMount(NBTTagCompound tag){tag.setInteger("ControlMountVersion",2);tag.setInteger("BaseHeight",baseHeight);tag.setInteger("BaseTilt",baseTilt);tag.setInteger("TiltDirection",tiltDirection);}
    public int getLowLimit(){return low;}
    public int getHighLimit(){return high;}
    public static boolean validLimits(int low,int high){return low>=1 && high<=15 && high-low>=2;}
    public static int levelForStep(int value,int low,int high){return value==0?0:value==1?low:value==2?(low+high+1)/2:high;}
    public int levelForStep(int value){return levelForStep(value,low,high);}
    public int getStep(){
        if(getRedstoneChannels().isEmpty())return step;
        return stepForLevel(getOutputLevel(),low,high);
    }
    public static int stepForLevel(int level,int low,int high){
        int nearest=0,distance=16;
        for(int i=level>0?1:0;i<4;i++){int d=Math.abs(level-levelForStep(i,low,high));if(d<distance){nearest=i;distance=d;}}
        return nearest;
    }
    @Override public int getOutputLevel(){return getRedstoneChannels().isEmpty()?levelForStep(step):super.getOutputLevel();}
    public void configureLimits(int low,int high){
        if(!validLimits(low,high))return;
        int selected=getStep();this.low=low;this.high=high;markDirty();
        setStep(selected);
    }
    public void setStep(int value){
        if(value<0 || value>3)return;
        step=value;
        if(getRedstoneChannels().isEmpty())super.setLocalOn(value>0);
        else RedstoneChannels.latchLevelChanged(this,levelForStep(value));
        markDirty();
        if(world!=null && !world.isRemote){
            IBlockState state=world.getBlockState(pos);
            ((com.vandorlabs.blocks.BlockSignalControl)state.getBlock()).applyLinkedState(world,pos,state,getOutputLevel()>0);
            world.notifyBlockUpdate(pos,state,world.getBlockState(pos),3);
            for(net.minecraft.util.EnumFacing facing:net.minecraft.util.EnumFacing.values()){
                net.minecraft.util.math.BlockPos neighbor=pos.offset(facing);
                if(world.isBlockLoaded(neighbor)){world.neighborChanged(neighbor,state.getBlock(),pos);
                    if(world.getBlockState(neighbor).isNormalCube())for(net.minecraft.util.EnumFacing supportSide:net.minecraft.util.EnumFacing.values()){net.minecraft.util.math.BlockPos target=neighbor.offset(supportSide);if(world.isBlockLoaded(target))world.neighborChanged(target,state.getBlock(),neighbor);}
                }
            }
        }
    }
    @Override public void setLocalOn(boolean on){setStep(on?3:0);}
    @Override public void setRedstoneChannels(ChannelList channels){
        if(getRedstoneChannels().equals(channels))return;
        int previous=getOutputLevel(),selected=getStep();
        super.setRedstoneChannels(channels);
        if(channels.isEmpty())setStep(selected);
        else if(previous>0)RedstoneChannels.latchLevelChanged(this,previous);
    }
    @Override public NBTTagCompound writeToNBT(NBTTagCompound tag){
        super.writeToNBT(tag);writeMount(tag);tag.setInteger("LowLimit",low);tag.setInteger("HighLimit",high);tag.setInteger("ControlStep",step);return tag;
    }
    @Override public void readFromNBT(NBTTagCompound tag){
        super.readFromNBT(tag);
        int h=tag.getInteger("BaseHeight"),t=com.vandorlabs.blocks.SignalControlMount.readTilt(tag),d=tag.getInteger("TiltDirection");
        baseHeight=baseTilt=tiltDirection=0;
        if(com.vandorlabs.blocks.SignalControlMount.valid(h,t,d)){baseHeight=h;baseTilt=t;tiltDirection=d;}
        int savedLow=tag.hasKey("LowLimit")?tag.getInteger("LowLimit"):5,savedHigh=tag.hasKey("HighLimit")?tag.getInteger("HighLimit"):15;
        if(validLimits(savedLow,savedHigh)){low=savedLow;high=savedHigh;}
        step=Math.max(0,Math.min(3,tag.getInteger("ControlStep")));
        if(world!=null && world.isRemote)world.markBlockRangeForRenderUpdate(pos,pos);
    }
    public NBTTagCompound configuration(){NBTTagCompound tag=new NBTTagCompound();if(world!=null && world.getBlockState(pos).getBlock() instanceof com.vandorlabs.blocks.BlockSignalControl && ((com.vandorlabs.blocks.BlockSignalControl)world.getBlockState(pos).getBlock()).hasSelectableType())tag.setInteger("ControlType",getControlType());writeMount(tag);tag.setInteger("LowLimit",low);tag.setInteger("HighLimit",high);com.vandorlabs.redstone.ChannelData.write(tag,getRedstoneChannels());return tag;}
}
