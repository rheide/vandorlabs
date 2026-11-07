package com.vandorlabs.neoforge.segmented;

import com.vandorlabs.neoforge.door.NativeDoorGlass;
import net.minecraft.nbt.CompoundTag;
import io.netty.buffer.*;
/** Frozen actual legacy settings, before portable extraction. */
public final class SegmentedSettingsChecks {
 public static void main(String[] args)throws Exception{
  java.security.MessageDigest hash=java.security.MessageDigest.getInstance("SHA-256");int cases=0;
  for(int d=0;d<4;d++)for(int s=0;s<2;s++)for(int l=0;l<3;l++)for(int a=-1;a<3;a++)for(int w=0;w<2;w++)for(int sh=0;sh<4;sh++)for(int j=0;j<2;j++)for(int p=0;p<3;p++){
   NativeSegmentedDoorSettings value=new NativeSegmentedDoorSettings(d,s!=0,l,a,w!=0,sh,j!=0,new NativeDoorGlass(-1,sh,1,l),p);
   CompoundTag tag=new CompoundTag();tag.putString("Foreign","retained");value.write(tag);if(!NativeSegmentedDoorSettings.read(tag).equals(value)||!tag.getString("Foreign").equals("retained"))throw new AssertionError("save roundtrip");
   ByteBuf b=Unpooled.buffer();try{value.encode(b);if(b.readableBytes()!=15||!NativeSegmentedDoorSettings.decode(b.duplicate()).equals(value))throw new AssertionError("wire15");hash.update(ByteBufUtil.getBytes(b));if(p==0&&!NativeSegmentedDoorSettings.decode(b.slice(0,14)).equals(value))throw new AssertionError("wire14");}finally{b.release();}
   hash.update(canonical(tag).getBytes("UTF-8"));hash.update((value.depthOffset()+":"+value.nativePanel()+":"+value.hashCode()).getBytes("UTF-8"));cases++;
  }
  String[] fields={"Direction","Straight","Layout","Activation","Window","Shape","Join","Position"};
  for(String field:fields)for(int kind=0;kind<8;kind++){
   CompoundTag root=new CompoundTag(),n=new CompoundTag();root.put("SegmentedSettings",n);switch(kind){case 0:break;case 1:n.putInt(field,-1);break;case 2:n.putInt(field,3);break;case 3:n.putInt(field,255);break;case 4:n.putString(field,"1");break;case 5:n.putByte(field,(byte)1);break;case 6:n.putLong(field,1);break;default:n.putBoolean(field,true);}
   CompoundTag before=root.copy();NativeSegmentedDoorSettings got=NativeSegmentedDoorSettings.read(root);if(!before.equals(root))throw new AssertionError("read mutation");hash.update((field+kind+":"+got.direction+got.straight+got.layout+got.activation+got.window+got.shape+got.join+got.position).getBytes("UTF-8"));cases++;
  }
  if(NativeSegmentedDoorSettings.read(new CompoundTag())!=NativeSegmentedDoorSettings.LEGACY)throw new AssertionError("absent legacy");
  CompoundTag legacy=new CompoundTag();NativeSegmentedDoorSettings.LEGACY.write(legacy);if(!legacy.isEmpty())throw new AssertionError("legacy omission");
  
  for(int length:new int[]{0,1,13,16,4096}){ByteBuf bad=Unpooled.buffer(length).writeZero(length);boolean rejected=false;try{NativeSegmentedDoorSettings.decode(bad);}catch(IllegalArgumentException expected){rejected=true;}finally{bad.release();}if(!rejected)throw new AssertionError("unbounded native settings");}
  CompoundTag opaque=new CompoundTag(),nested=new CompoundTag(),glass=new CompoundTag();opaque.putString("ForeignRoot","root");nested.putString("ForeignPart","part");nested.putInt("Direction",99);glass.putString("ForeignGlass","glass");nested.put("DoorGlass",glass);opaque.put("SegmentedSettings",nested);CompoundTag before=opaque.copy();var saved=new NativeSegmentedDoorSettings.Saved(opaque);opaque.putInt("after",1);if(!saved.unchanged().equals(before)||saved.settings!=NativeSegmentedDoorSettings.LEGACY)throw new AssertionError("invalid opaque snapshot changed");CompoundTag edited=saved.edited(NativeSegmentedDoorSettings.LEGACY);if(!NativeSegmentedDoorSettings.read(edited).same(NativeSegmentedDoorSettings.LEGACY)||!edited.getString("ForeignRoot").equals("root")||!edited.getCompound("SegmentedSettings").getString("ForeignPart").equals("part")||!edited.getCompound("SegmentedSettings").getCompound("DoorGlass").getString("ForeignGlass").equals("glass"))throw new AssertionError("edit lost unknown fields");
  var row=new NativeSegmentedDoorSettings(0,false,1,0,true,3,true,new NativeDoorGlass(-1,2,3,2),0);var shared=row.shared(new NativeSegmentedDoorSettings(3,true,2,2,false,0,false,NativeSegmentedDoorSettings.DEFAULT.glass,2));if(shared.direction!=3||!shared.straight||shared.layout!=2||shared.activation!=2||shared.position!=2||!shared.window||shared.shape!=3||!shared.join||shared.glass!=row.glass||shared.shared(shared)!=shared)throw new AssertionError("shared/per-row policy");
  for(int operation:new int[]{6,7}){var edit=new NativeSegmentedEdit(1,operation,10,true,true,com.vandorlabs.redstone.ChannelList.EMPTY,-1,null);ByteBuf bytes=Unpooled.buffer();try{edit.encode(bytes);if(bytes.readableBytes()!=(operation==7?8:12)||!NativeSegmentedEdit.decode(bytes.duplicate(),x->x==-1).equals(edit))throw new AssertionError("bounded appended cell command");bytes.writeByte(0);try{NativeSegmentedEdit.decode(bytes.duplicate(),x->true);throw new AssertionError("trailing cell command byte");}catch(IllegalArgumentException expected){}}finally{bytes.release();}}
  {var original=new com.vandorlabs.neoforge.copifier.NativeCopifierSegmentedEdit.State(NativeSegmentedDoorSettings.DEFAULT,com.vandorlabs.redstone.ChannelList.EMPTY,10,true,true,-1,com.vandorlabs.segmented.SegmentedCellArtwork.explicit(-1,x->true));var captured=com.vandorlabs.neoforge.copifier.NativeCopifierSegmentedEdit.capture(original);var target=new com.vandorlabs.neoforge.copifier.NativeCopifierSegmentedEdit.State(NativeSegmentedDoorSettings.DEFAULT,com.vandorlabs.redstone.ChannelList.EMPTY,10,true,true,-1);var plan=com.vandorlabs.neoforge.copifier.NativeCopifierSegmentedEdit.prepare(target,captured);if(!plan.state().artwork().equals(original.artwork())||com.vandorlabs.neoforge.copifier.NativeCopifierSegmentedEdit.peer(target,plan).artwork().overridden)throw new AssertionError("selected copied override versus inherited peer");}
  StringBuilder out=new StringBuilder();for(byte b:hash.digest())out.append(String.format("%02x",b&255));if(!out.toString().equals("230c9d0319086f5ecfbf8623f4199e829f4690069fb9114b6d8b98fc3563b7a8"))throw new AssertionError("Native differs from actual source "+out);System.out.println("Native Segmented Door settings parity PASS cases="+cases+" sha256="+out);
 }
 private static String canonical(CompoundTag tag){StringBuilder s=new StringBuilder("{");for(String key:new java.util.TreeSet<String>(tag.getAllKeys())){Object v=tag.get(key);s.append(key).append(':').append(v instanceof CompoundTag?canonical((CompoundTag)v):v.toString()).append(';');}return s.append('}').toString();}

}
