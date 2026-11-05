package com.vandorlabs.client;

import com.vandorlabs.tiles.*;
import net.minecraft.nbt.*;
import net.minecraft.util.ResourceLocation;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/** Actual unmodified legacy tile outputs frozen before moving its bank codec/getters. */
public final class ControlLatchDataChecks {
    private static final String GOLDEN="4daaa74c043e3954dfefa6eae92f4ab6baa87f2c2fa939745852515a0174b0d4";
    private static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
    private static String canonical(NBTBase value){if(!(value instanceof NBTTagCompound))return value.toString();NBTTagCompound tag=(NBTTagCompound)value;StringBuilder result=new StringBuilder("{");for(String key:new TreeSet<>(tag.getKeySet()))result.append(key).append(':').append(canonical(tag.getTag(key))).append(';');return result.append('}').toString();}
    private static NBTTagCompound tag(int bank,boolean local,boolean initialized,int latch,int level){
        NBTTagCompound tag=new NBTTagCompound();tag.setInteger("Channel",7);tag.setBoolean("LocalOn",local);tag.setBoolean("ChannelInitialized",initialized);tag.setInteger("ControlStep",2);
        if(bank==0)tag.setIntArray("RedstoneChannels",new int[0]);if(bank==1)tag.setIntArray("RedstoneChannels",new int[]{3,7,9});if(bank==2)tag.setIntArray("RedstoneChannels",new int[]{9,3,7,3});if(bank==4)tag.setString("RedstoneChannels","wrong type");
        if(latch==1)tag.setString("LatchedChannels","wrong type");if(latch==2)tag.setIntArray("LatchedChannels",new int[0]);if(latch==3)tag.setIntArray("LatchedChannels",new int[]{7});if(latch==4)tag.setIntArray("LatchedChannels",new int[]{9,3,3});if(latch==5)tag.setIntArray("LatchedChannels",new int[]{-1});if(latch==6){int[] tooMany=new int[65];Arrays.fill(tooMany,7);tag.setIntArray("LatchedChannels",tooMany);}if(latch==7)tag.setIntArray("LatchedChannels",new int[]{11});
        if(level==1)tag.setString("LatchedLevels","wrong type");if(level==2)tag.setIntArray("LatchedLevels",new int[0]);if(level==3)tag.setIntArray("LatchedLevels",new int[]{0});if(level==4)tag.setIntArray("LatchedLevels",new int[]{3,8});if(level==5)tag.setIntArray("LatchedLevels",new int[]{-9,30});if(level==6)tag.setIntArray("LatchedLevels",new int[]{-1,0,8,99});if(level==7)tag.setIntArray("LatchedLevels",new int[]{Integer.MIN_VALUE,Integer.MAX_VALUE,4});return tag;
    }
    private static String output(TileEntityRedstoneChannel tile){NBTTagCompound saved=new NBTTagCompound();saved.setString("UnrelatedTargetField","retained");tile.writeToNBT(saved);StringBuilder result=new StringBuilder(canonical(saved)).append(canonical(tile.configuration())).append('/').append(tile.getOutputLevel()).append('/').append(tile.isLocalOn()).append('/').append(tile.latchedChannels());for(int channel:new int[]{0,3,7,9,11})result.append('/').append(tile.latchedLevel(channel)).append(':').append(tile.localSignalLevel(channel));return result.toString();}
    public static final class OverrideTile extends TileEntityRedstoneChannel {
        @Override public int latchedLevel(int channel){return channel==3?6:9;}
        @Override public boolean isChannelLatch(){return true;}
    }
    private static volatile NBTTagCompound sink;
    @SuppressWarnings("deprecation") private static long threadId(){return Thread.currentThread().getId();}
    private static void allocation()throws Exception{
        NBTTagCompound input=tag(1,true,true,4,4);int[] channels=new int[32];for(int i=0;i<channels.length;i++)channels[i]=i+3;input.setIntArray("RedstoneChannels",channels);TileEntityRedstoneChannel tile=new TileEntityRedstoneChannel();tile.readFromNBT(input);
        Object bean=java.lang.management.ManagementFactory.getThreadMXBean();Class<?> type=Class.forName("com.sun.management.ThreadMXBean");type.getMethod("setThreadAllocatedMemoryEnabled",boolean.class).invoke(bean,true);java.lang.reflect.Method allocated=type.getMethod("getThreadAllocatedBytes",long.class);long thread=threadId();
        for(int i=0;i<20000;i++)sink=tile.writeToNBT(new NBTTagCompound());long[] bytes=new long[5];int count=20000;
        for(int round=0;round<5;round++){long before=(Long)allocated.invoke(bean,thread);for(int i=0;i<count;i++)sink=tile.writeToNBT(new NBTTagCompound());bytes[round]=(Long)allocated.invoke(bean,thread)-before;}Arrays.sort(bytes);System.out.println("Control latch actual write allocation PASS channels=32 rounds=5 writes="+count+" median-bytes/write="+bytes[2]/(double)count);
    }
    public static void main(String[] args)throws Exception{
        net.minecraft.init.Bootstrap.register();net.minecraftforge.fml.common.registry.GameRegistry.registerTileEntity(TileEntityRedstoneChannel.class,new ResourceLocation("vandorlabs","control_latch_fixture"));net.minecraftforge.fml.common.registry.GameRegistry.registerTileEntity(TileEntitySignalControl.class,new ResourceLocation("vandorlabs","control_latch_signal_fixture"));net.minecraftforge.fml.common.registry.GameRegistry.registerTileEntity(OverrideTile.class,new ResourceLocation("vandorlabs","control_latch_override_fixture"));if(args.length>0&&args[0].equals("allocation")){allocation();return;}MessageDigest digest=MessageDigest.getInstance("SHA-256");int cases=0;
        OverrideTile override=new OverrideTile();override.readFromNBT(tag(1,true,true,0,0));require(override.getOutputLevel()==6&&override.localSignalLevel(3)==6&&override.localSignalLevel(7)==9,"output/local preserve virtual latchedLevel dispatch");require(Arrays.equals(override.writeToNBT(new NBTTagCompound()).getIntArray("LatchedLevels"),new int[]{6,9,9}),"writer preserves virtual latchedLevel dispatch");
        for(boolean signal:new boolean[]{false,true})for(int bank=0;bank<5;bank++)for(boolean local:new boolean[]{false,true})for(boolean initialized:new boolean[]{false,true})for(int latch=0;latch<8;latch++)for(int level=0;level<8;level++){
            NBTTagCompound input=tag(bank,local,initialized,latch,level);NBTTagCompound original=input.copy();TileEntityRedstoneChannel tile=signal?new TileEntitySignalControl():new TileEntityRedstoneChannel();tile.readFromNBT(input);String expected=output(tile);require(input.equals(original),"read mutated source NBT");tile.readFromNBT(tag(1,!local,true,4,5));tile.readFromNBT(input);require(output(tile).equals(expected),"previous tile bank leaked across read");digest.update((expected+'\n').getBytes(StandardCharsets.UTF_8));cases++;
        }
        StringBuilder hash=new StringBuilder();for(byte value:digest.digest())hash.append(String.format("%02x",value&255));require(hash.toString().equals(GOLDEN),"pre-extraction NBT/getter changed: "+hash);
        System.out.println("Control latch actual NBT PASS cases="+cases+" golden="+hash+" typed malformed defaults partial prefix clamps exact writes getters reused tile signal subclass");
    }
}
