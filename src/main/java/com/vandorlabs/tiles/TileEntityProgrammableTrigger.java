package com.vandorlabs.tiles;

import net.minecraft.block.state.IBlockState;
import net.minecraft.nbt.NBTTagCompound;

/** The inherited housing finish is the off finish. */
public final class TileEntityProgrammableTrigger extends TileEntityAnimatedScreenSelector {
    private int onTexture,lowTexture,mediumTexture;
    private boolean levelStates;
    private int exactLevel=-1;
    private int[] artworkModes={MODE_STATIC,MODE_STATIC,MODE_STATIC,MODE_STATIC};
    private int[] artworkSpeeds={1,1,1,1};
    public int getArtworkMode(int slot){return artworkModes[slot];}
    public int getArtworkSpeed(int slot){return artworkSpeeds[slot];}
    public static boolean validArtwork(int[] modes,int[] speeds){
        if(modes==null||speeds==null||modes.length!=4||speeds.length!=4)return false;
        for(int i=0;i<4;i++)if(modes[i]<MODE_OFF||modes[i]>MODE_ANIMATED||speeds[i]<0||speeds[i]>=ANIMATION_SPEEDS.length)return false;
        return true;
    }
    /** Publish all fields together; legacy packets omit playback and retain its settings. */
    public boolean configureArtwork(int off,int on,boolean states,int exact,int low,int medium,
            com.vandorlabs.redstone.ChannelList channels,int[] modes,int[] speeds){
        if(channels==null||exact< -1||exact>15||!ScreenHousingTextures.validChoice(off)||!ScreenHousingTextures.validChoice(on)
                ||!ScreenHousingTextures.validChoice(low)||!ScreenHousingTextures.validChoice(medium)
                ||(modes!=null||speeds!=null)&&!validArtwork(modes,speeds))return false;
        levelStates=states;exactLevel=exact;lowTexture=low;mediumTexture=medium;onTexture=on;
        if(modes!=null){artworkModes=modes.clone();artworkSpeeds=speeds.clone();}
        setHousingTexture(off);setRedstoneChannels(channels);markDirty();
        if(world!=null){IBlockState state=world.getBlockState(pos);world.notifyBlockUpdate(pos,state,state,3);}
        return true;
    }
    public boolean hasOnlyStaticArtwork(){for(int mode:artworkModes)if(mode!=MODE_STATIC)return false;return true;}
    @Override public boolean hasFastRenderer(){
        if(world==null)return false;
        if(hasOnlyStaticArtwork())return super.hasFastRenderer();
        int slot=getVisibleSlot(),mode=getArtworkMode(slot);
        return (mode==MODE_STATIC||mode==MODE_ANIMATED&&!"Screens".equals(ScreenHousingTextures.category(getTexture(slot))))&&super.hasFastRenderer();
    }

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
        int signal=getSignalLevel();
        if(exactLevel>=0&&signal!=exactLevel)return 0;
        if(!levelStates)return exactLevel>=0||signal>0?3:0;
        return signal==0?0:signal<=5?1:signal<=10?2:3;
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
        writePlayback(tag,"TriggerArtworkModes",artworkModes,MODE_STATIC);
        writePlayback(tag,"TriggerArtworkSpeeds",artworkSpeeds,1);
        tag.setBoolean("TriggerLevelStates",levelStates);tag.setInteger("TriggerExactLevel",exactLevel);tag.setInteger("TriggerLowTexture",lowTexture);tag.setInteger("TriggerMediumTexture",mediumTexture);
        return tag;
    }

    @Override public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag);
        artworkModes=readPlayback(tag,"TriggerArtworkModes",MODE_STATIC,true);
        artworkSpeeds=readPlayback(tag,"TriggerArtworkSpeeds",1,false);
        onTexture = ScreenHousingTextures.clamp(tag.getInteger("TriggerOnTexture"));
        levelStates=tag.getBoolean("TriggerLevelStates");exactLevel=tag.hasKey("TriggerExactLevel",3)?Math.max(-1,Math.min(15,tag.getInteger("TriggerExactLevel"))):-1;
        lowTexture=tag.hasKey("TriggerLowTexture",3)?ScreenHousingTextures.clamp(tag.getInteger("TriggerLowTexture")):onTexture;mediumTexture=tag.hasKey("TriggerMediumTexture",3)?ScreenHousingTextures.clamp(tag.getInteger("TriggerMediumTexture")):onTexture;
    }
    private static void writePlayback(NBTTagCompound tag,String key,int[] values,int fallback){
        for(int value:values)if(value!=fallback){tag.setIntArray(key,values.clone());return;}
        tag.removeTag(key); // Preserve the legacy NBT shape until playback is configured.
    }
    private static int[] readPlayback(NBTTagCompound tag,String key,int fallback,boolean mode){
        int[] values=tag.getIntArray(key).clone();
        if(values.length!=4){values=new int[4];java.util.Arrays.fill(values,fallback);return values;}
        for(int i=0;i<4;i++)values[i]=mode?com.vandorlabs.animation.ScreenBehavior.clampMode(values[i]):com.vandorlabs.animation.ScreenBehavior.clampSpeedIndex(values[i]);
        return values;
    }

}
