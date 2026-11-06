package com.vandorlabs.client;

import com.google.gson.*;
import com.vandorlabs.segmented.*;
import com.vandorlabs.redstone.ChannelList;
import com.vandorlabs.tiles.DoorGlassAppearance;
import io.netty.buffer.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;

/** Actual released menu-operation encoding, including the old fourteen-byte settings extension. */
public final class SegmentedEditLegacyChecks {
    private static final String GOLDEN="f94742d5f4e928abf048573cfbdd68f0424137828d022aeb42daaee7caa46c58";
    public static void main(String[] args)throws Exception {
        net.minecraft.init.Bootstrap.register();JsonArray fixtures=new JsonArray();
        for(int operation=0;operation<6;operation++)for(int ticks:new int[]{5,10,20,30})for(int flags=0;flags<4;flags++)for(int d=0;d<4;d++)for(int l=0;l<3;l++){
            ChannelList channels=(flags&1)==0?ChannelList.EMPTY:ChannelList.of(7,12,42);
            SegmentedDoorSettings settings=new SegmentedDoorSettings(d,(flags&2)!=0,l,flags%3,true,d,false,new DoorGlassAppearance(-1,d,1,l),d%3);
            SegmentedDoorMessage message=new SegmentedDoorMessage(37,operation,ticks,(flags&1)!=0,(flags&2)!=0,channels);
            if(operation==3)message.texture(-1);if(operation==4)message.settings(-1,settings);if(operation==5)message.partSettings(-1,settings);
            ByteBuf encoded=Unpooled.buffer();try{
                message.toBytes(encoded);String hex=ByteBufUtil.hexDump(encoded);SegmentedDoorMessage restored=new SegmentedDoorMessage();restored.fromBytes(encoded.duplicate());ByteBuf again=Unpooled.buffer();try{restored.toBytes(again);if(!hex.equals(ByteBufUtil.hexDump(again)))throw new AssertionError("Actual message roundtrip");}finally{again.release();}
                JsonObject row=new JsonObject();row.addProperty("input",hex);row.addProperty("canonical",hex);fixtures.add(row);
                if(operation==4&&settings.position==0){ByteBuf old=encoded.copy(0,encoded.readableBytes()-1);try{SegmentedDoorMessage previous=new SegmentedDoorMessage();previous.fromBytes(old.duplicate());JsonObject legacy=new JsonObject();legacy.addProperty("input",ByteBufUtil.hexDump(old));legacy.addProperty("canonical",hex);fixtures.add(legacy);}finally{old.release();}}
            }finally{encoded.release();}
        }
        String json=fixtures.toString();java.security.MessageDigest hash=java.security.MessageDigest.getInstance("SHA-256");StringBuilder hex=new StringBuilder();for(byte b:hash.digest(json.getBytes(StandardCharsets.UTF_8)))hex.append(String.format("%02x",b&255));if(!GOLDEN.equals(hex.toString()))throw new AssertionError("Released Segmented editor wire changed "+hex);
        Path output=Paths.get(args[0]);Files.createDirectories(output.toAbsolutePath().getParent());Files.write(output,json.getBytes(StandardCharsets.UTF_8));System.out.println("Actual Segmented editor wire PASS cases="+fixtures.size()+" sha256="+hex);
    }
}
