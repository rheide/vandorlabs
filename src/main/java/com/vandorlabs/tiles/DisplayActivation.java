package com.vandorlabs.tiles;

import net.minecraft.nbt.NBTTagCompound;

/** Four bounded primary-artwork states; secondary console controls stay shared. */
public final class DisplayActivation {
    /** -1 retains the previous release's redstone gate until explicitly configured. */
    public int mode=-1;
    private com.vandorlabs.persistence.DisplayActivationData data=com.vandorlabs.persistence.DisplayActivationData.LEGACY;
    public int texture(int state){return data.texture(state);}
    public int playback(int state){return data.playback(state);}
    public int speed(int state){return data.speed(state);}
    public static boolean valid(int mode,int state,int texture,int playback,int speed){return com.vandorlabs.persistence.DisplayActivationData.valid(mode,state,texture,playback,speed,ScreenHousingTextures::validChoice);}
    public void configure(int mode,int state,int texture,int playback,int speed){data=data.configure(mode,state,texture,playback,speed,ScreenHousingTextures::validChoice);this.mode=mode;}
    public void inheritUnset(int texture,int playback,int speed){data=data.inheritUnset(mode,texture,playback,speed);}
    public void write(NBTTagCompound tag){if(mode<0){tag.removeTag("DisplayActivation");return;}NBTTagCompound saved=new NBTTagCompound();saved.setInteger("Mode",mode);saved.setIntArray("Textures",data.textures());saved.setIntArray("Playback",data.playback());saved.setIntArray("Speeds",data.speeds());tag.setTag("DisplayActivation",saved);}
    public void read(NBTTagCompound tag){NBTTagCompound saved=tag.getCompoundTag("DisplayActivation");data=com.vandorlabs.persistence.DisplayActivationData.read(tag.hasKey("DisplayActivation",10),saved.getInteger("Mode"),saved.getIntArray("Textures"),saved.getIntArray("Playback"),saved.getIntArray("Speeds"),ScreenHousingTextures::validChoice,ScreenHousingTextures::clamp);mode=data.mode;}

}
