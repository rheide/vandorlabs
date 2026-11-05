package com.vandorlabs.tiles;

import net.minecraft.block.state.IBlockState;
import net.minecraft.nbt.NBTTagCompound;
import com.vandorlabs.persistence.TriggerState;
import com.vandorlabs.persistence.NbtTriggerData;

/** The inherited housing finish is the off finish. */
public final class TileEntityProgrammableTrigger extends TileEntityAnimatedScreenSelector {
    private boolean frontOnly;
    private int sidesTexture;
    public boolean isFrontOnly(){return frontOnly;}
    public int getSidesTexture(){return sidesTexture;}
    private int onTexture,lowTexture,mediumTexture;
    private boolean levelStates;
    private int exactLevel=-1;
    // Retain old playback fields verbatim for saved vehicle/payload compatibility.
    // Trigger artwork is always static, including when those legacy fields exist.
    private int[] artworkModes={MODE_STATIC,MODE_STATIC,MODE_STATIC,MODE_STATIC};
    private int[] artworkSpeeds={1,1,1,1};
    public int getArtworkMode(int slot){return MODE_STATIC;}
    public int getArtworkSpeed(int slot){return 1;}
    public static boolean validArtwork(int[] modes,int[] speeds){
        if(modes==null||speeds==null||modes.length!=4||speeds.length!=4)return false;
        for(int i=0;i<4;i++)if(modes[i]<MODE_OFF||modes[i]>MODE_ANIMATED||speeds[i]<0||speeds[i]>=ANIMATION_SPEEDS.length)return false;
        return true;
    }
    /** Publish all fields together; legacy packets omit playback and retain its settings. */
    public boolean configureArtwork(int off,int on,boolean states,int exact,int low,int medium,
            com.vandorlabs.redstone.ChannelList channels,int[] modes,int[] speeds){
        return configureArtwork(off,on,states,exact,low,medium,channels,modes,speeds,null,null);
    }
    public boolean configureArtwork(int off,int on,boolean states,int exact,int low,int medium,
            com.vandorlabs.redstone.ChannelList channels,int[] modes,int[] speeds,Boolean front,Integer sides){
        if(sides!=null&&!ScreenHousingTextures.validChoice(sides))return false;
        if(channels==null||exact< -1||exact>15||!ScreenHousingTextures.validChoice(off)||!ScreenHousingTextures.validChoice(on)
                ||!ScreenHousingTextures.validChoice(low)||!ScreenHousingTextures.validChoice(medium)
                ||(modes!=null||speeds!=null)&&!validArtwork(modes,speeds))return false;
        if(front!=null)frontOnly=front;if(sides!=null)sidesTexture=sides;
        levelStates=states;exactLevel=exact;lowTexture=low;mediumTexture=medium;onTexture=on;
        if(modes!=null){artworkModes=modes.clone();artworkSpeeds=speeds.clone();}
        setHousingTexture(off);setRedstoneChannels(channels);markDirty();
        if(world!=null){IBlockState state=world.getBlockState(pos);world.notifyBlockUpdate(pos,state,state,3);}
        return true;
    }
    public boolean hasOnlyStaticArtwork(){return true;}
    @Override public boolean hasFastRenderer(){return world!=null&&!frontOnly&&super.hasFastRenderer();}

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
    public int getTexture(int slot){return slot==0?getHousingTexture():slot==1?lowTexture:slot==2?mediumTexture:onTexture;}
    public int getVisibleTexture(){return getTexture(getVisibleSlot());}
    public int getVisibleSlot(){
        return TriggerState.visibleSlot(levelStates,exactLevel,getSignalLevel());
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
        TriggerState.write(new NbtTriggerData(tag),onTexture,levelStates,exactLevel,lowTexture,mediumTexture,
                frontOnly,sidesTexture,artworkModes,artworkSpeeds);
        return tag;
    }

    @Override public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag);
        TriggerState saved=TriggerState.read(new NbtTriggerData(tag),ScreenHousingTextures::clamp);
        artworkModes=saved.artworkModes();artworkSpeeds=saved.artworkSpeeds();
        frontOnly=saved.frontOnly;sidesTexture=saved.sides;
        onTexture=saved.on;levelStates=saved.bands;exactLevel=saved.exact;
        lowTexture=saved.low;mediumTexture=saved.medium;
    }

}
