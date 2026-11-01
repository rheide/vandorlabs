package com.vandorlabs.tiles;

import net.minecraft.block.state.IBlockState;
import net.minecraft.nbt.NBTTagCompound;

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
        if(frontOnly)tag.setBoolean("TriggerFrontOnly",true);else tag.removeTag("TriggerFrontOnly");
        if(sidesTexture!=0)tag.setInteger("TriggerSidesTexture",sidesTexture);else tag.removeTag("TriggerSidesTexture");
        writePlayback(tag,"TriggerArtworkModes",artworkModes,MODE_STATIC);
        writePlayback(tag,"TriggerArtworkSpeeds",artworkSpeeds,1);
        tag.setBoolean("TriggerLevelStates",levelStates);tag.setInteger("TriggerExactLevel",exactLevel);tag.setInteger("TriggerLowTexture",lowTexture);tag.setInteger("TriggerMediumTexture",mediumTexture);
        return tag;
    }

    @Override public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag);
        artworkModes=readPlayback(tag,"TriggerArtworkModes",MODE_STATIC,true);
        artworkSpeeds=readPlayback(tag,"TriggerArtworkSpeeds",1,false);
        frontOnly=tag.getBoolean("TriggerFrontOnly");sidesTexture=ScreenHousingTextures.clamp(tag.getInteger("TriggerSidesTexture"));
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
