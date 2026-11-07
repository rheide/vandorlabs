package com.vandorlabs.vehicle;

import com.vandorlabs.physics.RigidTransform;
import io.netty.buffer.*;
import java.util.*;
import net.minecraft.nbt.*;
import net.minecraft.util.math.BlockPos;

/** Exact matrix/opaque ownership boundaries and append-only packet compatibility, without a world. */
public final class VehicleBodyPoseCodecChecks {
 private static int checks;
 private static void need(boolean value,String why){checks++;if(!value)throw new AssertionError("General body codec: "+why);}
 private static byte[] wire(VehicleNetwork.Packet packet){ByteBuf out=Unpooled.buffer();try{packet.toBytes(out);byte[] bytes=new byte[out.readableBytes()];out.readBytes(bytes);return bytes;}finally{out.release();}}
 private static VehicleNetwork.Packet decode(byte[] bytes){ByteBuf in=Unpooled.wrappedBuffer(bytes);try{VehicleNetwork.Packet packet=new VehicleNetwork.Packet();packet.fromBytes(in);return packet;}finally{in.release();}}
 private static void bad(byte[] bytes,String why){need(!decode(bytes).valid,why);}
 private static void badNBT(NBTTagCompound tag,String why){boolean rejected=false;try{VehicleBodyPoseFrame.readNBT(tag);}catch(IllegalArgumentException expected){rejected=true;}need(rejected,why);}
 private static void equal(VehicleBodyPoseFrame first,VehicleBodyPoseFrame second){need(first.epoch==second.epoch&&first.layout==second.layout&&first.bodies.size()==second.bodies.size(),"frame identity/count");for(int n=0;n<first.bodies.size();n++){VehicleBodyPoseFrame.Body a=first.bodies.get(n),b=second.bodies.get(n);need(a.carrier.equals(b.carrier),"carrier exact");for(int r=0;r<3;r++)for(int c=0;c<3;c++)need(Double.doubleToLongBits(a.rotation(r,c))==Double.doubleToLongBits(b.rotation(r,c)),"matrix double exact");for(int axis=0;axis<3;axis++)need(Double.doubleToLongBits(a.translation(axis))==Double.doubleToLongBits(b.translation(axis)),"translation double exact");}}
 public static void main(String[] args){
  net.minecraft.init.Bootstrap.register();VehicleNetwork.Packet packet=new VehicleNetwork.Packet(VehicleNetwork.STATE);byte[] legacy=wire(packet);need(legacy.length==82,"ordinary base packet unchanged82 bytes");packet.jointTopology=11;packet.jointAngles=new short[]{100,-200,300,-400,500,-600};byte[] joints=wire(packet);need(joints.length==108&&decode(joints).valid,"ordinary3joint packet unchanged108 bytes");
  for(int axis=0;axis<3;axis++)for(double degrees:new double[]{-75,-1,0,37.125,75}){
   RigidTransform rotation=RigidTransform.rotation(axis==0?1:0,axis==1?1:0,axis==2?1:0,degrees);RigidTransform transform=new RigidTransform(rotation.r,-12.25,0,64.125);VehicleBodyPoseFrame.Body body=new VehicleBodyPoseFrame.Body(new BlockPos(1,2,3),transform);List<VehicleBodyPoseFrame.Body> input=new ArrayList<>();input.add(body);VehicleBodyPoseFrame frame=new VehicleBodyPoseFrame(1,59,input);input.clear();transform.r[0]=123;RigidTransform exported=body.transform();exported.r[0]=456;need(body.rotation(0,0)==rotation.r[0],"input/export matrix mutation cannot change owned pose");packet.bodyPose=frame;byte[] encoded=wire(packet);need(encoded.length==joints.length+127&&frame.encodedBytes()==127,"onebody23+104 bytes");VehicleNetwork.Packet decoded=decode(encoded);need(decoded.valid,"general packet accepted");equal(frame,decoded.bodyPose);equal(frame,VehicleBodyPoseFrame.readNBT(frame.writeNBT()));
   packet.armMotion=new VehicleArmMotionFrame(17,Collections.singletonList(new VehicleArmMotionFrame.Arm(new BlockPos(4,5,6),2,.625,false)));decoded=decode(wire(packet));need(decoded.valid&&decoded.armMotion.arms.size()==1,"joint+arm+general ordered tails");equal(frame,decoded.bodyPose);packet.armMotion=VehicleArmMotionFrame.NONE;
  }
  VehicleBodyPoseFrame frame=packet.bodyPose;byte[] valid=wire(packet);int base=joints.length;ByteBuf bytes=Unpooled.wrappedBuffer(valid.clone());try{bytes.setShort(base+21,65535);byte[] malformed=new byte[bytes.readableBytes()];bytes.getBytes(0,malformed);bad(malformed,"oversized count refuses before records");}finally{bytes.release();}
  for(int cut=base+1;cut<valid.length;cut++)bad(Arrays.copyOf(valid,cut),"truncated tail"+cut);
  byte[] trailing=Arrays.copyOf(valid,valid.length+1);bad(trailing,"unadvertised trailing byte");byte[] duplicate=Arrays.copyOf(valid,valid.length+frame.encodedBytes());System.arraycopy(valid,base,duplicate,valid.length,frame.encodedBytes());bad(duplicate,"duplicate body tail");
  for(int mutation=0;mutation<12;mutation++){NBTTagCompound tag=frame.writeNBT(),row=tag.getTagList("Bodies",10).getCompoundTagAt(0);NBTTagList matrix=row.getTagList("Matrix",6);switch(mutation){case 0:tag.setString("Version","1");break;case 1:tag.setLong("Epoch",0);break;case 2:tag.setLong("Layout",0);break;case 3:tag.setBoolean("Future",true);break;case 4:row.setString("Carrier","wrong");break;case 5:row.setLong("Carrier",new BlockPos(-1,0,0).toLong());break;case 6:row.setBoolean("Future",true);break;case 7:row.setTag("Matrix",new NBTTagList());break;case 8:matrix.set(0,new NBTTagDouble(Double.NaN));break;case 9:matrix.set(9,new NBTTagDouble(65537));break;case 10:for(int n=0;n<9;n++)matrix.set(n,new NBTTagDouble(n%4==0?(n==0?-1:1):0));break;default:tag.getTagList("Bodies",10).appendTag(row.copy());}badNBT(tag,"malformed saved envelope"+mutation);}
  List<VehicleBodyPoseFrame.Body> maximum=new ArrayList<>();for(int n=0;n<127;n++)maximum.add(new VehicleBodyPoseFrame.Body(new BlockPos(n%64,n/64,0),RigidTransform.IDENTITY));packet.bodyPose=new VehicleBodyPoseFrame(Long.MAX_VALUE,-1,maximum);need(wire(packet).length==108+23+127*104,"maximum bounded packet bytes");equal(packet.bodyPose,decode(wire(packet)).bodyPose);
  packet.bodyPose=VehicleBodyPoseFrame.reset(2);need(wire(packet).length==108+23&&decode(wire(packet)).bodyPose.reset(),"explicitreset distinctfromabsenttail");equal(packet.bodyPose,VehicleBodyPoseFrame.readNBT(packet.bodyPose.writeNBT()));packet.bodyPose=VehicleBodyPoseFrame.NONE;need(Arrays.equals(joints,wire(packet)),"legacywireidenticalaftergeneralreset");
  System.out.println("General body pose codec PASS checks="+checks+" legacyBytes=82/108 generalHeader=23 bodyBytes=104 maxTail=13231 exactNBT/wire/roll/malformed/mutation; no runtime binding");
 }
}
