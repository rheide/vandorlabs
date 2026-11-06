package com.vandorlabs.client;
import com.vandorlabs.segmented.SegmentedDoorSettings;
import com.vandorlabs.tiles.DoorGlassAppearance;
import net.minecraft.nbt.NBTTagCompound;
import io.netty.buffer.*;
/** Frozen actual legacy settings, before portable extraction. */
public final class SegmentedSettingsLegacyChecks {
 public static void main(String[] args)throws Exception{
  net.minecraft.init.Bootstrap.register();java.security.MessageDigest hash=java.security.MessageDigest.getInstance("SHA-256");int cases=0;
  for(int d=0;d<4;d++)for(int s=0;s<2;s++)for(int l=0;l<3;l++)for(int a=-1;a<3;a++)for(int w=0;w<2;w++)for(int sh=0;sh<4;sh++)for(int j=0;j<2;j++)for(int p=0;p<3;p++){
   SegmentedDoorSettings value=new SegmentedDoorSettings(d,s!=0,l,a,w!=0,sh,j!=0,new DoorGlassAppearance(-1,sh,1,l),p);
   NBTTagCompound tag=new NBTTagCompound();tag.setString("Foreign","retained");value.write(tag);if(!SegmentedDoorSettings.read(tag).equals(value)||!tag.getString("Foreign").equals("retained"))throw new AssertionError("save roundtrip");
   ByteBuf b=Unpooled.buffer();try{value.encode(b);if(b.readableBytes()!=15||!SegmentedDoorSettings.decode(b.copy()).equals(value))throw new AssertionError("wire15");hash.update(ByteBufUtil.getBytes(b));if(p==0&&!SegmentedDoorSettings.decode(b.copy(0,14)).equals(value))throw new AssertionError("wire14");}finally{b.release();}
   hash.update(tag.toString().getBytes("UTF-8"));hash.update((value.depthOffset()+":"+value.nativePanel()+":"+value.hashCode()).getBytes("UTF-8"));cases++;
  }
  String[] fields={"Direction","Straight","Layout","Activation","Window","Shape","Join","Position"};
  for(String field:fields)for(int kind=0;kind<8;kind++){
   NBTTagCompound root=new NBTTagCompound(),n=new NBTTagCompound();root.setTag("SegmentedSettings",n);switch(kind){case 0:break;case 1:n.setInteger(field,-1);break;case 2:n.setInteger(field,3);break;case 3:n.setInteger(field,255);break;case 4:n.setString(field,"1");break;case 5:n.setByte(field,(byte)1);break;case 6:n.setLong(field,1);break;default:n.setBoolean(field,true);}
   NBTTagCompound before=root.copy();SegmentedDoorSettings got=SegmentedDoorSettings.read(root);if(!before.equals(root))throw new AssertionError("read mutation");hash.update((field+kind+":"+got.direction+got.straight+got.layout+got.activation+got.window+got.shape+got.join+got.position).getBytes("UTF-8"));cases++;
  }
  if(SegmentedDoorSettings.read(new NBTTagCompound())!=SegmentedDoorSettings.LEGACY)throw new AssertionError("absent legacy");
  NBTTagCompound legacy=new NBTTagCompound();SegmentedDoorSettings.LEGACY.write(legacy);if(!legacy.hasNoTags())throw new AssertionError("legacy omission");
  StringBuilder out=new StringBuilder();for(byte b:hash.digest())out.append(String.format("%02x",b&255));if(!out.toString().equals("5696e2b7f9a15664e03ddc6da590f038227bd1a06cd19d09cb635ac40e86ce10"))throw new AssertionError("Legacy settings changed "+out);System.out.println("Actual Segmented Door settings frozen PASS cases="+cases+" sha256="+out);
 }
}
