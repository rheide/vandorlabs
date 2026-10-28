package com.vandorlabs.network;

import io.netty.buffer.*;
import java.util.Arrays;
import net.minecraft.util.math.BlockPos;

/** Optional bounded metadata preserves ordinary motion bytes and avoids full-craft start packets. */
public final class GearTaxiPacketChecks {
    private GearTaxiPacketChecks(){}
    private static void require(boolean ok,String text){if(!ok)throw new IllegalStateException("Taxi gear packet: "+text);}
    private static byte[] encode(MessageGearMotion packet){ByteBuf buffer=Unpooled.buffer();try{packet.toBytes(buffer);byte[] bytes=new byte[buffer.readableBytes()];buffer.readBytes(bytes);return bytes;}finally{buffer.release();}}
    private static MessageGearMotion decode(byte[] bytes){ByteBuf buffer=Unpooled.wrappedBuffer(bytes);try{MessageGearMotion message=new MessageGearMotion();message.fromBytes(buffer);return message;}finally{buffer.release();}}
    public static void run(){
        MessageGearMotion packet=new MessageGearMotion();MessageGearMotion.Pose pose=new MessageGearMotion.Pose();pose.pos=BlockPos.ORIGIN;pose.revision=7;pose.progress=.5;packet.poses.add(pose);
        byte[] ordinary=encode(packet);require(ordinary.length==55&&Arrays.equals(ordinary,encode(decode(ordinary))),"ordinary 55-byte motion remains byte-identical");
        pose.taxiPhase=0;pose.taxiHeight=3;pose.taxiAmount=2;pose.taxiLevel=.5;byte[] taxi=encode(packet);MessageGearMotion restored=decode(taxi);
        require(taxi.length==72&&restored.poses.get(0).taxiPhase==0&&restored.poses.get(0).taxiAmount==2&&restored.poses.get(0).taxiHeight==3&&restored.poses.get(0).taxiLevel==.5&&Arrays.equals(taxi,encode(restored)),"bounded Taxi state round-trips without tile NBT");
        pose.taxiPhase=-1;byte[] clear=encode(packet);require(clear.length==62&&decode(clear).poses.get(0).taxiPhase==-1,"one clear entry retires the client motion basis");
        for(int phase:new int[]{1,3}){pose.taxiPhase=phase;boolean refused=false;try{decode(encode(packet));}catch(IllegalArgumentException expected){refused=true;}require(refused,"unknown or retained-owner phase is rejected from thin metadata");}
        pose.taxiPhase=2;pose.taxiAmount=9;boolean refused=false;try{decode(encode(packet));}catch(IllegalArgumentException expected){refused=true;}require(refused,"invalid amounts rejected");
        pose.taxiAmount=2;pose.taxiLevel=Double.NaN;refused=false;try{decode(encode(packet));}catch(IllegalArgumentException expected){refused=true;}require(refused,"nonfinite rise rejected");
        byte[] invalid=taxi.clone();invalid[60]=4;refused=false;try{decode(invalid);}catch(IllegalArgumentException expected){refused=true;}require(refused,"out-of-range row index rejected");
        ByteBuf huge=Unpooled.buffer();try{huge.writeZero(32);huge.writeByte(65);huge.writeZero(65*22);MessageGearMotion bounded=new MessageGearMotion();refused=false;try{bounded.fromBytes(huge);}catch(IllegalArgumentException expected){refused=true;}require(refused&&bounded.poses.isEmpty(),"row count bounded before allocation");}finally{huge.release();}
        System.out.println("[vandorlabs][reprolab] gear-taxi-packet PASS ordinary-before=55 ordinary-after=55 taxi=72 clear=62 byte-roundtrip bounded-count phase index amount finite-level no-full-craft-start-packet");
    }
}
