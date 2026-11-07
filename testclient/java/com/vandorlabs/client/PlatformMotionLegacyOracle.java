package com.vandorlabs.client;

import com.google.gson.*;
import com.vandorlabs.network.MessagePlatformMotion;
import io.netty.buffer.Unpooled;
import java.io.*;
import java.lang.reflect.Field;
import java.nio.file.*;
import java.util.*;
import net.minecraft.entity.item.EntityBoat;
import net.minecraft.entity.player.EntityPlayer;

/** Actual released packet decoder/apply results, including consumed ordering on refused corrections. */
public final class PlatformMotionLegacyOracle {
    private static final Gson JSON=new Gson();private static int count;
    private static JsonArray doubles(double... values){JsonArray out=new JsonArray();for(double value:values)out.add(Double.toHexString(value));return out;}
    @SuppressWarnings("unchecked")public static void main(String[] args)throws Exception {
        net.minecraft.init.Bootstrap.register();NonRenderingChecks.MemoryWorld world=new NonRenderingChecks.MemoryWorld(true);world.time=100;
        EntityPlayer player=new EntityPlayer(world,new com.mojang.authlib.GameProfile(new UUID(0,290713),"MotionOracle")){public boolean isCreative(){return false;}public boolean isSpectator(){return false;}};EntityBoat carrier=new EntityBoat(world);
        Field field=MessagePlatformMotion.class.getDeclaredField("RECEIVED");field.setAccessible(true);Map<EntityPlayer,Long> received=(Map<EntityPlayer,Long>)field.get(null);
        Path target=Paths.get(args[0]);Files.createDirectories(target.getParent());
        try(PrintWriter output=new PrintWriter(Files.newBufferedWriter(target,java.nio.charset.StandardCharsets.UTF_8))){
            for(int flags=0;flags<16;flags++)for(double velocity:new double[]{0,.1,.1000000001})for(int offset=0;offset<5;offset++)for(double feet:new double[]{79.499999,79.5,80,80.5,80.625,80.625001})for(int horizontal=0;horizontal<5;horizontal++)for(double delta:new double[]{.125,-.125}){
                double x=1.25+(offset==1?.75:offset==2?.750001:0),z=-3.5+(offset==3?.75:offset==4?.750001:0),dx=horizontal==1?4:horizontal==2?4.000001:0,dz=horizontal==3?4:horizontal==4?4.000001:0;
                emit(output,player,carrier,received,flags,velocity,x,feet,z,new double[]{1.25,-3.5,80,80+delta,dx,dz},0,11,8L);
            }
            for(Long last:new Long[]{null,1L,8L,Long.MAX_VALUE})for(long sequence:new long[]{-1,0,1,7,8,9,Long.MAX_VALUE-1,Long.MAX_VALUE})for(int dimension:new int[]{0,1})emit(output,player,carrier,received,0,0,1.25,80,-3.5,new double[]{1.25,-3.5,80,80.125,0,0},dimension,sequence,last);
            for(int fieldIndex=0;fieldIndex<6;fieldIndex++)for(double invalid:new double[]{Double.NaN,Double.POSITIVE_INFINITY,Double.NEGATIVE_INFINITY}){double[] values={1.25,-3.5,80,80.125,0,0};values[fieldIndex]=invalid;emit(output,player,carrier,received,0,0,1.25,80,-3.5,values,0,11,8L);}
            emit(output,player,carrier,received,0,Double.NaN,1.25,80,-3.5,new double[]{1.25,-3.5,80,80.125,0,0},0,11,8L);
        }
        if(count!=14483)throw new AssertionError("Motion coverage "+count);
        SegmentedReferenceGolden.check(target.toFile(),"8651842fa51549993a15cca2fb2085eefbb232e101157f7089253af8f52b9ca3");
        StringBuilder hash=new StringBuilder();for(byte b:java.security.MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(target)))hash.append(String.format(Locale.ROOT,"%02x",b&255));System.out.println("Platform Source motion reference PASS cases="+count+" packetBytes60 sha256="+hash);
    }
    private static void emit(PrintWriter output,EntityPlayer player,EntityBoat carrier,Map<EntityPlayer,Long> received,int flags,double velocity,double x,double feet,double z,double[] values,int dimension,long sequence,Long previous){
        player.isDead=false;player.dismountRidingEntity();if((flags&4)!=0&&!player.startRiding(carrier,true))throw new AssertionError("Motion mount fixture");player.isDead=(flags&1)!=0;player.noClip=(flags&2)!=0;player.capabilities.isFlying=(flags&8)!=0;player.dimension=0;player.setPosition(x,feet,z);player.motionX=.3;player.motionY=velocity;player.motionZ=-.2;player.onGround=false;player.fallDistance=7;
        received.clear();if(previous!=null)received.put(player,previous);
        varBytes(output,player,received,flags,velocity,x,feet,z,values,dimension,sequence,previous);
    }
    private static void varBytes(PrintWriter output,EntityPlayer player,Map<EntityPlayer,Long> received,int flags,double velocity,double x,double feet,double z,double[] values,int dimension,long sequence,Long previous){
        io.netty.buffer.ByteBuf bytes=Unpooled.buffer(60);bytes.writeInt(dimension);for(double value:values)bytes.writeDouble(value);bytes.writeLong(sequence);MessagePlatformMotion message=new MessagePlatformMotion();message.fromBytes(bytes);if(bytes.isReadable())throw new AssertionError("Unconsumed Source motion");bytes.release();boolean applied=message.apply(player);
        JsonObject row=new JsonObject();row.addProperty("flags",flags);row.addProperty("velocity",Double.toHexString(velocity));row.add("position",doubles(x,feet,z));row.add("packet",doubles(values));row.addProperty("dimension",dimension);row.addProperty("sequence",Long.toString(sequence));row.addProperty("previous",previous==null?"none":previous.toString());row.addProperty("applied",applied);row.addProperty("last",received.containsKey(player)?received.get(player).toString():"none");row.add("after",doubles(player.posX,player.posY,player.posZ,player.motionX,player.motionY,player.motionZ,player.fallDistance));row.addProperty("grounded",player.onGround);output.print(JSON.toJson(row));output.print('\n');count++;
    }
}
