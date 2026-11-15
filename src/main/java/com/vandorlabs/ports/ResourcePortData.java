package com.vandorlabs.ports;

import com.vandorlabs.redstone.ChannelData;
import com.vandorlabs.redstone.ChannelList;
import net.minecraft.nbt.NBTTagCompound;

/** Portable configuration only. Resource ownership belongs to the tile's private save data. */
public final class ResourcePortData {
    public static final String FACES="PortFaces", RATE="PortThroughput", PULL="PortPullInputs", ART="PortOnTexture", MODE="PortRedstoneMode", HOUSING="PortHousing";
    private ResourcePortData(){}
    public static void write(NBTTagCompound tag,ResourcePortSettings settings,int mode,ChannelList channels){
        int[] faces=new int[6];for(int i=0;i<6;i++)faces[i]=settings.face(i).ordinal();tag.setIntArray(FACES,faces);
        tag.setInteger(HOUSING,settings.housingChoice);tag.setLong(RATE,settings.throughput);tag.setBoolean(PULL,settings.pullInputs);tag.setInteger(ART,settings.onTexture.ordinal());tag.setInteger(MODE,mode);ChannelData.write(tag,channels);
    }
    public static ResourcePortSettings read(NBTTagCompound tag){
        if(!tag.hasKey(FACES,11)||!tag.hasKey(RATE,4)||!tag.hasKey(PULL,1)||!tag.hasKey(ART,3)||!tag.hasKey(MODE,3)||!tag.hasKey(ChannelData.CHANNELS,11))throw new IllegalArgumentException("Incomplete resource port settings");
        int[] faces=tag.getIntArray(FACES);if(faces.length!=6)throw new IllegalArgumentException("Choose all six port faces");
        ResourcePortSettings.Face[] values=new ResourcePortSettings.Face[6];for(int i=0;i<6;i++){if(faces[i]<0||faces[i]>=ResourcePortSettings.Face.values().length)throw new IllegalArgumentException("Invalid port face");values[i]=ResourcePortSettings.Face.values()[faces[i]];}
        int housing=tag.hasKey(HOUSING)?tag.getInteger(HOUSING):-1;if(tag.hasKey(HOUSING)&&(!tag.hasKey(HOUSING,3)||housing!= -1&&!com.vandorlabs.tiles.ScreenHousingTextures.validChoice(housing)))throw new IllegalArgumentException("Invalid port housing");
        long rate=tag.getLong(RATE);int art=tag.getInteger(ART);if(rate<0||art<0||art>=ResourcePortSettings.OnTexture.values().length)throw new IllegalArgumentException("Invalid port throughput or artwork");
        return new ResourcePortSettings(values,rate,tag.getBoolean(PULL),ResourcePortSettings.OnTexture.values()[art],housing);
    }
    public static int mode(NBTTagCompound tag){int value=tag.getInteger(MODE);if(!tag.hasKey(MODE,3)||value<0||value>2)throw new IllegalArgumentException("Invalid port Redstone mode");return value;}
    public static ChannelList channels(NBTTagCompound tag){if(!tag.hasKey(ChannelData.CHANNELS,11))throw new IllegalArgumentException("Missing port channels");return ChannelList.of(tag.getIntArray(ChannelData.CHANNELS));}
    /** Resource ownership is independent of whether any side currently exposes a handler. */
    public static boolean hasResources(NBTTagCompound tag){return tag!=null&&(tag.getInteger("PortEnergy")>0||tag.hasKey("PortFluid",10)&&tag.getCompoundTag("PortFluid").getInteger("Amount")>0||tag.hasKey("Items",9)&&tag.getTagList("Items",10).tagCount()>0);}
    /** Rotate only this typed configuration. Buffers, foreign capabilities and unrelated data stay opaque. */
    public static void rotate(NBTTagCompound tag,net.minecraft.util.Rotation rotation){ResourcePortSettings settings=read(tag);int[] mapped=new int[6];for(net.minecraft.util.EnumFacing side:net.minecraft.util.EnumFacing.values())mapped[rotation.rotate(side).getIndex()]=settings.face(side.getIndex()).ordinal();tag.setIntArray(FACES,mapped);}
}
