package com.vandorlabs.client;

import com.vandorlabs.tiles.GlassPlayback;
import net.minecraft.nbt.*;
import net.minecraft.tileentity.TileEntity;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;

/** Actual byte-typed playback defaults, sparse writes and explicit copy settings. */
public final class GlassPlaybackLegacyChecks {
    private static void value(NBTTagCompound tag,String key,int kind,int value){switch(kind){case 1:tag.setByte(key,(byte)value);break;case 2:tag.setInteger(key,value);break;case 3:tag.setShort(key,(short)value);break;case 4:tag.setLong(key,value);break;case 5:tag.setString(key,Integer.toString(value));break;case 6:tag.setBoolean(key,value!=0);break;}}
    public static void main(String[] args)throws Exception{
        MessageDigest digest=MessageDigest.getInstance("SHA-256");int cases=0;
        for(int kind=0;kind<7;kind++)for(int speedKind=0;speedKind<7;speedKind++)for(int value=-130;value<=130;value++){
            NBTTagCompound input=new NBTTagCompound();value(input,"GlassPlaybackMode",kind,value);value(input,"GlassPlaybackSpeed",speedKind,2-value);NBTTagCompound original=input.copy();GlassPlayback playback=new GlassPlayback(new TileEntity(){});playback.set(2,0);playback.read(input);NBTTagCompound saved=input.copy();playback.write(saved);NBTTagCompound settings=new NBTTagCompound();playback.writeSettings(settings);if(!input.equals(original))throw new AssertionError("Original input modified");String result=playback.mode()+"/"+playback.speed()+"/"+saved+"/"+settings;digest.update(result.getBytes(StandardCharsets.UTF_8));cases++;
        }
        StringBuilder hash=new StringBuilder();for(byte b:digest.digest())hash.append(String.format("%02x",b&255));if(!"e42f8306cdf2a4bb4bf02480ae0a443db3fd1c195bb9682ddd5c8f9a0a17e63c".equals(hash.toString()))throw new AssertionError("Frozen playback changed "+hash);System.out.println("Glass playback actual-NBT PASS cases="+cases+" SHA256="+hash);
    }
}
