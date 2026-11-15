package com.vandorlabs.neoforge;
import com.vandorlabs.surface.TextureJoinSettings;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import io.netty.buffer.Unpooled;
/** New fields and bounded packet codec keep every original join default and external material identity. */
public final class NativeTextureJoinChecks {
 private static void need(boolean value,String message){if(!value)throw new AssertionError(message);}
 public static void main(String[] args){
  need(TextureJoinSettings.read(new ModernHousingTags(new CompoundTag())).equals(TextureJoinSettings.DEFAULT),"older NBT defaults");int count=0;
  for(int direction=0;direction<6;direction++)for(int parity=0;parity<3;parity++){
   var settings=new TextureJoinSettings(direction,parity);var tag=new CompoundTag();tag.putString("opaque","retained");settings.write(new ModernHousingTags(tag));need(TextureJoinSettings.read(new ModernHousingTags(tag)).equals(settings)&&tag.getString("opaque").equals("retained"),"native tags round trip");
   var packet=new TextureJoinPayload(new BlockPos(-17,80,19),7,direction,parity);var b=new RegistryFriendlyByteBuf(Unpooled.buffer(),RegistryAccess.EMPTY);try{TextureJoinPayload.CODEC.encode(b,packet);need(b.readableBytes()==14,"bounded fixed payload bytes");need(TextureJoinPayload.CODEC.decode(b).equals(packet)&&b.readableBytes()==0,"packet round trip");}finally{b.release();}count++;
  }
  for(int bytes:new int[]{0,1,13,15,4096}){var b=new RegistryFriendlyByteBuf(Unpooled.wrappedBuffer(new byte[bytes]),RegistryAccess.EMPTY);try{boolean rejected=false;try{TextureJoinPayload.CODEC.decode(b);}catch(IllegalArgumentException expected){rejected=true;}need(rejected,"bad length rejected before reading");}finally{b.release();}}
  int mutual=0;var glass=com.vandorlabs.neoforge.glass.NativeGlassSettings.read(new CompoundTag());
  var cell=new com.vandorlabs.neoforge.glass.NativeGlassGroups.Cell(glass,false,1,-1,false);
  for(int a=0;a<6;a++)for(int z=0;z<6;z++)for(int axis=0;axis<3;axis++){
   final int first=a,second=z,dimension=axis;int dx=axis==1?0:1,dy=axis==1?1:0;
   com.vandorlabs.neoforge.glass.NativeGlassGroups.Cells cells=(x,y)->x==0&&y==0||x==dx&&y==dy?cell:null;
   var p=new TextureJoinSettings(first,0);var q=new TextureJoinSettings(second,0);int wx=axis==0?1:0,wy=axis==1?1:0,wz=axis==2?1:0;
   java.util.function.BiPredicate<Long,Long> edge=(from,to)->from==0?p.connectsWith(q,0,0,0,wx,wy,wz):q.connectsWith(p,wx,wy,wz,0,0,0);
   boolean joined=axis(first,dimension)&&axis(second,dimension);
   need(com.vandorlabs.neoforge.glass.NativeGlassGroups.contour(cells,0,0,edge)==(joined?(axis==1?7:13):15),"native mutual root contour "+first+"/"+second+" axis="+axis);
   need(com.vandorlabs.neoforge.glass.NativeGlassGroups.contour(cells,dx,dy,edge)==(joined?(axis==1?11:14):15),"native reverse endpoint contour "+first+"/"+second+" axis="+axis);mutual+=2;
  }
  System.out.println("Native mutual frame contours PASS checks="+mutual+" all36EndpointPolicies/threeAxes/bothOrders/Off");
  System.out.println("Native texture joins PASS cases="+count+" nativeNBT/defaults/opaqueFields/fixed14byteRoundtrip/malformedLengths; world authorization checked separately");
 }
 private static boolean axis(int policy,int axis){return policy==1||policy==2&&axis==1||policy==3&&axis!=1||policy==4&&axis==2||policy==5&&axis==0;}
}
