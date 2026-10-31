package com.vandorlabs.client;

import com.vandorlabs.network.MessageProgrammableTrigger;
import com.vandorlabs.redstone.ChannelList;
import com.vandorlabs.tiles.TileEntityProgrammableTrigger;
import io.netty.buffer.*;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;
import java.lang.reflect.Field;
import java.util.Arrays;

/** Playback defaults, independent NBT, scalar/list compatibility and bounded extensions. */
public final class TriggerArtworkChecks {
    public static void run(){
        TileEntityProgrammableTrigger tile=new TileEntityProgrammableTrigger();
        NBTTagCompound legacy=tile.writeToNBT(new NBTTagCompound());require(!legacy.hasKey("TriggerArtworkModes")&&!legacy.hasKey("TriggerArtworkSpeeds"),"legacy shape changed");
        TileEntityProgrammableTrigger loaded=new TileEntityProgrammableTrigger();loaded.readFromNBT(legacy);
        for(int i=0;i<4;i++)require(loaded.getArtworkMode(i)==1&&loaded.getArtworkSpeed(i)==1,"legacy static/normal defaults");
        int[] modes={0,2,1,2},speeds={2,0,1,2};
        require(tile.configureArtwork(4,5,true,-1,6,7,ChannelList.of(41,42),modes,speeds),"valid settings refused");
        NBTTagCompound saved=tile.writeToNBT(new NBTTagCompound());loaded.readFromNBT(saved);
        for(int i=0;i<4;i++)require(loaded.getArtworkMode(i)==modes[i]&&loaded.getArtworkSpeed(i)==speeds[i]&&loaded.getTexture(i)==tile.getTexture(i),"independent state save/reload "+i);
        modes[0]=2;require(tile.getArtworkMode(0)==0,"caller array retained");modes[0]=0;
        saved.getIntArray("TriggerArtworkModes")[0]=2;require(tile.getArtworkMode(0)==0&&loaded.getArtworkMode(0)==0,"NBT array retained");
        NBTTagCompound before=tile.writeToNBT(new NBTTagCompound());require(!tile.configureArtwork(0,0,false,-1,0,0,ChannelList.EMPTY,new int[3],speeds)&&before.equals(tile.writeToNBT(new NBTTagCompound())),"invalid settings partially applied");
        saved.setIntArray("TriggerArtworkModes",new int[]{-1,9,1,2});NBTTagCompound original=saved.copy();loaded.readFromNBT(saved);require(original.equals(saved)&&loaded.getArtworkMode(0)==0&&loaded.getArtworkMode(1)==2,"malformed NBT mutated or unclamped");
        loaded.readFromNBT(legacy);require(!loaded.writeToNBT(new NBTTagCompound()).hasKey("TriggerArtworkModes"),"absent fields retained old settings");
        MessageProgrammableTrigger old=new MessageProgrammableTrigger(BlockPos.ORIGIN,4,5,41).withChannels(ChannelList.of(41,42)).withLevels(true,-1,6,7);
        MessageProgrammableTrigger modern=new MessageProgrammableTrigger(BlockPos.ORIGIN,4,5,41).withChannels(ChannelList.of(41,42)).withLevels(true,-1,6,7).withArtwork(modes,speeds);
        ByteBuf oldWire=Unpooled.buffer(),wire=Unpooled.buffer();
        try{
            old.toBytes(oldWire);modern.toBytes(wire);require(oldWire.readableBytes()==42&&wire.readableBytes()==51,"wire byte cost changed");
            MessageProgrammableTrigger decoded=new MessageProgrammableTrigger();decoded.fromBytes(wire.duplicate());require(ChannelList.of(41,42).equals(decoded.getRedstoneChannels()),"channel field swallowed playback extension");
            require(Arrays.equals(modes,array(decoded,"modes"))&&Arrays.equals(speeds,array(decoded,"speeds")),"playback round trip");
            for(int length=0;length<wire.readableBytes();length++){
                decoded=new MessageProgrammableTrigger();decoded.fromBytes(wire.slice(0,length));
                require((length==33||length==42)==(decoded.getRedstoneChannels()!=null),"truncated/legacy acceptance "+length);
            }
            for(int corrupt:new int[]{42,43,50}){
                ByteBuf input=wire.copy();try{input.setByte(corrupt,255);decoded=new MessageProgrammableTrigger();decoded.fromBytes(input);require(decoded.getRedstoneChannels()==null,"invalid playback accepted "+corrupt);}finally{input.release();}
            }
            ByteBuf trailing=wire.copy();try{trailing.writeByte(0);decoded=new MessageProgrammableTrigger();decoded.fromBytes(trailing);require(decoded.getRedstoneChannels()==null,"trailing byte accepted");}finally{trailing.release();}
            decoded=new MessageProgrammableTrigger();decoded.fromBytes(oldWire.duplicate());require(decoded.getRedstoneChannels()!=null&&array(decoded,"modes")==null,"old packet rejected or reset playback");
            tile.configureArtwork(4,5,false,-1,6,7,decoded.getRedstoneChannels(),null,null);require(tile.getArtworkMode(0)==0&&tile.getArtworkMode(3)==2,"legacy update erased playback");
        }finally{oldWire.release();wire.release();}
        tile.configureLevels(true,-1,6,7);
        for(int level=0;level<16;level++){tile.setChannelLevel(level);require(tile.getVisibleSlot()==(level==0?0:level<=5?1:level<=10?2:3),"analog slot "+level);}
        tile.configureLevels(false,6,6,7);
        for(int level=0;level<16;level++){tile.setChannelLevel(level);require(tile.getVisibleSlot()==(level==6?3:0),"exact slot "+level);}
        tile.configureLevels(false,0,6,7);tile.setChannelLevel(0);require(tile.getVisibleSlot()==3,"exact zero binary behavior changed");
        System.out.println("Trigger artwork data PASS legacy-NBT independent-modes-speeds defensive-copies atomic-rejection wire=42->51 channel-field-roundtrip legacy-packets truncated-version-mode-speed-trailing-rejection");
    }
    private static int[] array(Object value,String name){try{Field field=value.getClass().getDeclaredField(name);field.setAccessible(true);return (int[])field.get(value);}catch(Exception failure){throw new IllegalStateException(failure);}}
    private static void require(boolean value,String message){if(!value)throw new IllegalStateException("trigger artwork: "+message);}
}
