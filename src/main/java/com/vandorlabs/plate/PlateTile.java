package com.vandorlabs.plate;

import com.vandorlabs.redstone.*;
import com.vandorlabs.tiles.*;
import net.minecraft.block.state.IBlockState;
import net.minecraft.nbt.NBTTagCompound;

/** One independent analog source; channel modes never latch a physical press. */
public final class PlateTile extends TileEntityRedstoneChannel {
    private long nextCarriedSample=Long.MIN_VALUE;
    private int texture=ScreenHousingTextures.screenIndex("door_inner"),mode=1,sensitivity,output;
    public int texture(){return texture;}
    public int mode(){return mode;}
    public int sensitivity(){return sensitivity;}
    public static String sensitivityName(int value){return new String[]{"Wood","Gold","Iron"}[Math.max(0,Math.min(2,value))];}
    /** Native weighted plates count entities, not item stack sizes, and ignore the wooden trigger-exempt flag. */
    public static int measure(int sensitivity,java.util.Collection<? extends net.minecraft.entity.Entity> entities){
        if(sensitivity==0){for(net.minecraft.entity.Entity entity:entities)if(!entity.doesEntityNotTriggerPressurePlate())return 15;return 0;}
        int maximum=sensitivity==1?15:150,count=Math.min(entities.size(),maximum);return count==0?0:net.minecraft.util.math.MathHelper.ceil(((float)count/(float)maximum)*15F);
    }
    public int physicalPower(){return world!=null&&world.getBlockState(pos).getBlock() instanceof PlateBlock?world.getBlockState(pos).getValue(PlateBlock.POWER):0;}
    public boolean pressed(){return physicalPower()>0;}
    public int outputFor(int power){return mode==1?power:mode==2&&power==0?15:0;}
    @Override public boolean isChannelLatch(){return false;}
    @Override public int getOutputLevel(){return output;}
    @Override public int localSignalLevel(int channel){return output;}
    @Override public boolean hasLocalRedstoneSignal(){return output>0;}
    @Override public boolean hasLocalRedstoneSignal(int channel){return output>0;}
    private void publish(int physical){int next=outputFor(Math.max(0,Math.min(15,physical))),before=output;output=next;super.setLocalOn(next>0);if(before!=next){markDirty();if(world!=null&&!world.isRemote)RedstoneChannels.inputChanged(this);}}
    public void sample(){if(world!=null&&!world.isRemote&&world.getTileEntity(pos)==this)publish(physicalPower());}
    public void carriedPower(int power){publish(power);}
    /** Native plates sense collisions immediately while raised, then poll only while depressed. */
    public boolean carriedSampleDue(long tick){return physicalPower()==0||tick>=nextCarriedSample;}
    public int carriedMeasurement(int measured,long tick){
        int current=physicalPower();if(current>0&&tick<nextCarriedSample)return current;
        if(measured>0)nextCarriedSample=tick+(sensitivity==0?20:10);else nextCarriedSample=Long.MIN_VALUE;
        return measured;
    }
    public void configure(int texture,int mode,ChannelList channels){configure(texture,mode,sensitivity,channels);}
    public void configure(int texture,int mode,int sensitivity,ChannelList channels){
        if(!ScreenHousingTextures.validChoice(texture)||mode<0||mode>2||sensitivity<0||sensitivity>2||channels==null)throw new IllegalArgumentException("Invalid pressure plate settings");
        this.texture=texture;this.mode=mode;this.sensitivity=sensitivity;nextCarriedSample=Long.MIN_VALUE;setRedstoneChannels(channels);
        if(world!=null&&!world.isRemote&&world.getBlockState(pos).getBlock() instanceof PlateBlock)((PlateBlock)world.getBlockState(pos).getBlock()).resample(world,pos);sample();markDirty();
        if(world!=null&&!world.isRemote){IBlockState state=world.getBlockState(pos);world.notifyBlockUpdate(pos,state,state,3);}
    }
    @Override public void onLoad(){super.onLoad();DeferredTileLoad.schedule(this,()->{sample();if(pressed())world.scheduleUpdate(pos,world.getBlockState(pos).getBlock(),((PlateBlock)world.getBlockState(pos).getBlock()).releaseTicks(world,pos));});}
    @Override public NBTTagCompound writeToNBT(NBTTagCompound tag){super.writeToNBT(tag);tag.setInteger("PlateTexture",texture);tag.setInteger("PlateMode",mode);tag.setInteger("PlateSensitivity",sensitivity);tag.setInteger("PlateOutput",output);return tag;}
    @Override public void readFromNBT(NBTTagCompound tag){super.readFromNBT(tag);texture=tag.hasKey("PlateTexture",3)?ScreenHousingTextures.clamp(tag.getInteger("PlateTexture")):ScreenHousingTextures.screenIndex("door_inner");mode=tag.hasKey("PlateMode",3)?Math.max(0,Math.min(2,tag.getInteger("PlateMode"))):1;sensitivity=Math.max(0,Math.min(2,tag.getInteger("PlateSensitivity")));output=tag.hasKey("PlateOutput",3)?Math.max(0,Math.min(15,tag.getInteger("PlateOutput"))):isLocalOn()?15:0;}
}
