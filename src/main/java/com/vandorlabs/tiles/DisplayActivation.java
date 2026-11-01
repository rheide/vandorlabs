package com.vandorlabs.tiles;

import net.minecraft.nbt.NBTTagCompound;
import java.util.Arrays;

/** Four bounded primary-artwork states; secondary console controls stay shared. */
public final class DisplayActivation {
    /** -1 retains the previous release's redstone gate until explicitly configured. */
    public int mode=-1;
    private final int[] textures={-1,-1,-1,-1},playback={-1,-1,-1,-1},speeds={-1,-1,-1,-1};
    public int texture(int state){return textures[state];}
    public int playback(int state){return playback[state];}
    public int speed(int state){return speeds[state];}
    public static boolean valid(int mode,int state,int texture,int playback,int speed){return mode>=0&&mode<=2&&state>=0&&state<4&&(texture==-1||ScreenHousingTextures.validChoice(texture))&&playback>=-1&&playback<=2&&speed>=-1&&speed<3;}
    public void configure(int mode,int state,int texture,int playback,int speed){if(!valid(mode,state,texture,playback,speed))throw new IllegalArgumentException("Invalid display activation");this.mode=mode;textures[state]=texture;this.playback[state]=playback;speeds[state]=speed;}
    public void write(NBTTagCompound tag){if(mode<0){tag.removeTag("DisplayActivation");return;}NBTTagCompound saved=new NBTTagCompound();saved.setInteger("Mode",mode);saved.setIntArray("Textures",textures.clone());saved.setIntArray("Playback",playback.clone());saved.setIntArray("Speeds",speeds.clone());tag.setTag("DisplayActivation",saved);}
    public void read(NBTTagCompound tag){mode=-1;Arrays.fill(textures,-1);Arrays.fill(playback,-1);Arrays.fill(speeds,-1);if(!tag.hasKey("DisplayActivation",10))return;NBTTagCompound saved=tag.getCompoundTag("DisplayActivation");int m=saved.getInteger("Mode");int[] t=saved.getIntArray("Textures"),p=saved.getIntArray("Playback"),s=saved.getIntArray("Speeds");if(t.length!=4||p.length!=4||s.length!=4)return;for(int i=0;i<4;i++)if(!valid(m,i,t[i],p[i],s[i]))return;mode=m;for(int i=0;i<4;i++){textures[i]=t[i]<0?-1:ScreenHousingTextures.clamp(t[i]);playback[i]=p[i];speeds[i]=s[i];}}
}
