package com.vandorlabs.tiles;

import net.minecraft.block.state.IBlockState;
import net.minecraft.nbt.NBTTagCompound;

/** The inherited housing finish is the off finish. */
public final class TileEntityProgrammableTrigger extends TileEntityAnimatedScreenSelector {
    private int onTexture,lowTexture,mediumTexture;
    private boolean levelStates;
    private int exactLevel=-1;
    public int getLowTexture(){return lowTexture;}
    public int getMediumTexture(){return mediumTexture;}
    public boolean isLevelStates(){return levelStates;}
    public int getExactLevel(){return exactLevel;}
    public void configureLevels(boolean states,int exact,int low,int medium){
        if(exact< -1 || exact>15 || !ScreenHousingTextures.validChoice(low) || !ScreenHousingTextures.validChoice(medium))return;
        levelStates=states;exactLevel=exact;lowTexture=low;mediumTexture=medium;markDirty();
        if(world!=null)world.notifyBlockUpdate(pos,world.getBlockState(pos),world.getBlockState(pos),3);
    }


    public int getOnTexture() { return onTexture; }
    public int getVisibleTexture() {
        int signal=getSignalLevel();
        if(exactLevel>=0 && signal!=exactLevel)return getHousingTexture();
        if(!levelStates)return (exactLevel>=0 || signal>0)?onTexture:getHousingTexture();
        return signal==0?getHousingTexture():signal<=5?lowTexture:signal<=10?mediumTexture:onTexture;
    }

    public void configure(int off, int on, int channel) {
        if(channel<0)return;
        configure(off,on,com.vandorlabs.redstone.ChannelList.of(channel));
    }
    public void configure(int off,int on,com.vandorlabs.redstone.ChannelList channels) {
        if (off < 0 || !com.vandorlabs.tiles.ScreenHousingTextures.validChoice(off)
                || on < 0 || !com.vandorlabs.tiles.ScreenHousingTextures.validChoice(on)
                || channels == null) return;
        setHousingTexture(off);
        onTexture = on;
        setRedstoneChannels(channels);
        markDirty();
        if (world != null && pos != null) {
            IBlockState state = world.getBlockState(pos);
            world.notifyBlockUpdate(pos, state, state, 3);
        }
    }

    @Override public NBTTagCompound writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag);
        tag.setInteger("TriggerOnTexture", onTexture);
        tag.setBoolean("TriggerLevelStates",levelStates);tag.setInteger("TriggerExactLevel",exactLevel);tag.setInteger("TriggerLowTexture",lowTexture);tag.setInteger("TriggerMediumTexture",mediumTexture);
        return tag;
    }

    @Override public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag);
        onTexture = ScreenHousingTextures.clamp(tag.getInteger("TriggerOnTexture"));
        levelStates=tag.getBoolean("TriggerLevelStates");exactLevel=tag.hasKey("TriggerExactLevel",3)?Math.max(-1,Math.min(15,tag.getInteger("TriggerExactLevel"))):-1;
        lowTexture=tag.hasKey("TriggerLowTexture",3)?ScreenHousingTextures.clamp(tag.getInteger("TriggerLowTexture")):onTexture;mediumTexture=tag.hasKey("TriggerMediumTexture",3)?ScreenHousingTextures.clamp(tag.getInteger("TriggerMediumTexture")):onTexture;
    }
}
