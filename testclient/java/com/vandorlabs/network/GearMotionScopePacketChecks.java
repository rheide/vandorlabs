package com.vandorlabs.network;
import io.netty.buffer.*;
import java.nio.ByteBuffer;
import java.util.*;
import net.minecraft.util.math.BlockPos;
public final class GearMotionScopePacketChecks {
 private static int checks;private static final UUID SCOPE=new UUID(11,12);
 private static void need(boolean b,String why){checks++;if(!b)throw new AssertionError(why);}
 private static byte[] encode(MessageGearMotion p){ByteBuf b=Unpooled.buffer();try{p.toBytes(b);byte[] a=new byte[b.readableBytes()];b.readBytes(a);return a;}finally{b.release();}}
 private static void decodeInto(MessageGearMotion p,byte[] bytes){ByteBuf b=Unpooled.wrappedBuffer(bytes);try{p.fromBytes(b);need(!b.isReadable(),"complete frame consumed");}finally{b.release();}}
 private static MessageGearMotion decode(byte[] bytes){MessageGearMotion p=new MessageGearMotion();decodeInto(p,bytes);return p;}
 private static void refused(byte[] a){MessageGearMotion reused=new MessageGearMotion();reused.associationGeneration=SCOPE;try{decodeInto(reused,a);throw new AssertionError("malformed packet accepted");}catch(IllegalArgumentException expected){need(reused.poses.isEmpty()&&reused.associationGeneration==null&&reused.entity==-1,"failure resets reused packet atomically");}}
 public static void main(String[] args){
  MessageGearMotion p=new MessageGearMotion();MessageGearMotion.Pose pose=new MessageGearMotion.Pose();pose.pos=BlockPos.ORIGIN;pose.revision=7;pose.progress=.5;p.poses.add(pose);byte[] nativeBytes=encode(p);byte[] frozen=ByteBuffer.allocate(55).putInt(0).putInt(-1).putLong(0).putLong(0).putLong(0).put((byte)1).putLong(0).putInt(7).putDouble(.5).put((byte)0).put((byte)0).array();need(Arrays.equals(nativeBytes,frozen),"independent legacy native 55-byte oracle");
  p.entity=8;p.craft=new UUID(21,22);p.tick=100;byte[] oldCarried=encode(p);p.associationGeneration=SCOPE;byte[] scoped=encode(p);need(scoped.length==76&&Arrays.equals(oldCarried,Arrays.copyOf(scoped,55)),"ordinary carried adds exactly21B");need(SCOPE.equals(decode(scoped).associationGeneration)&&Arrays.equals(scoped,encode(decode(scoped))),"scope exact byte roundtrip");
  for(int n=0;n<55;n++)refused(Arrays.copyOf(scoped,n));for(int n=56;n<76;n++)refused(Arrays.copyOf(scoped,n));need(decode(Arrays.copyOf(scoped,55)).associationGeneration==null,"whole removed optional tail decodes legacy; client known-scope guard refuses it");
  byte[] bad=scoped.clone();bad[59]=2;refused(bad);bad=scoped.clone();bad[55]^=1;refused(bad);bad=scoped.clone();Arrays.fill(bad,60,76,(byte)0);refused(bad);refused(Arrays.copyOf(scoped,77));bad=scoped.clone();ByteBuffer.wrap(bad).putInt(4,-1);refused(bad);
  pose.taxiPhase=0;pose.taxiHeight=3;pose.taxiAmount=2;pose.taxiLevel=.5;byte[] taxi=encode(p);need(taxi.length==93&&decode(taxi).poses.get(0).taxiLevel==.5,"scoped Taxi72+21 body");pose.taxiPhase=-1;need(encode(p).length==83,"scoped clear62+21 body");pose.taxiPhase=-2;for(int i=1;i<64;i++){MessageGearMotion.Pose row=new MessageGearMotion.Pose();row.pos=new BlockPos(i,2,3);row.progress=.25;p.poses.add(row);}need(encode(p).length==1462&&decode(encode(p)).poses.size()==64,"64 bounded scoped rows");for(MessageGearMotion.Pose row:p.poses){row.taxiPhase=2;row.taxiHeight=8;row.taxiAmount=7;row.taxiLevel=6.5;}byte[] maximum=encode(p);need(maximum.length==2235&&decode(maximum).poses.size()==64,"maximum Taxi and scope frame2235B");
  MessageGearMotion reused=decode(maximum),copy=reused.copyForClientQueue();decodeInto(reused,nativeBytes);need(reused.associationGeneration==null&&reused.poses.size()==1&&reused.poses.get(0).taxiPhase==-2,"reuse clears scope and Taxi rows");need(copy.associationGeneration.equals(SCOPE)&&copy.poses.size()==64&&copy.poses.get(0).taxiPhase==2,"deferred owned copy survives decoder reuse");
  GearTaxiPacketChecks.run();System.out.println("Gear motion scope packet PASS checks="+checks+" body native55/legacycarried55 unchanged; scoped76 Taxi93 clear83 max2235; SimpleImpl discriminator+1 =>native56 scoped77; bounded/truncation/version/zero/reuse/ownedqueue");
 }
}
