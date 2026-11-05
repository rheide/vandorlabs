package com.vandorlabs.client;
import com.vandorlabs.tiles.*;
import com.vandorlabs.network.MessageDoorGlass;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import io.netty.buffer.*;
public final class DoorGlassDataChecks {
 public static void main(String[] args)throws Exception{net.minecraft.init.Bootstrap.register();golden();run();}
 static void golden()throws Exception{java.security.MessageDigest hash=java.security.MessageDigest.getInstance("SHA-256");int cases=0;String[] keys={"Material","Shade","Tint","Layout"};
  for(int code=0;code<4096;code++){NBTTagCompound root=new NBTTagCompound(),child=new NBTTagCompound();root.setString("ForeignDoor","opaque");child.setString("ForeignGlass","opaque");int value=code;for(String key:keys){int kind=value&7;value>>>=3;switch(kind){case 0:break;case 1:child.setInteger(key,-2);break;case 2:child.setInteger(key,0);break;case 3:child.setInteger(key,1);break;case 4:child.setInteger(key,19);break;case 5:child.setByte(key,(byte)1);break;case 6:child.setString(key,"1");break;default:child.setLong(key,1L);}}root.setTag("DoorGlass",child);NBTTagCompound before=root.copy();DoorGlassAppearance actual=DoorGlassAppearance.read(root);if(!root.equals(before))throw new AssertionError("read changed original glass data");hash.update((actual.material+","+actual.shade+","+actual.tint+","+actual.layout+";").getBytes(java.nio.charset.StandardCharsets.UTF_8));actual.write(root);if(!root.getString("ForeignDoor").equals("opaque"))throw new AssertionError("write touched unrelated field");hash.update(root.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));cases++;}
  StringBuilder text=new StringBuilder();for(byte b:hash.digest())text.append(String.format("%02x",b&255));if(!text.toString().equals("4fdc6a588ce927922f2a82f831fd00b509670d9281bd512baedbd016dcafde19"))throw new AssertionError("Actual DoorGlass codec changed "+text);System.out.println("Actual DoorGlass saved codec PASS cases="+cases+" golden="+text);
 }
 static void run(){
  net.minecraftforge.fml.common.registry.GameRegistry.registerTileEntity(TileEntitySpaceDoor.class,new net.minecraft.util.ResourceLocation("minecraft:door_glass_data_normal"));
  net.minecraftforge.fml.common.registry.GameRegistry.registerTileEntity(TileEntityLargeProgrammableDoor.class,new net.minecraft.util.ResourceLocation("minecraft:door_glass_data_large"));
  net.minecraftforge.fml.common.registry.GameRegistry.registerTileEntity(TileEntityTokenSlot.class,new net.minecraft.util.ResourceLocation("minecraft:door_glass_data_reader"));
  int cases=0;
  for(boolean large:new boolean[]{false,true})for(int design:new int[]{15,16,29})for(int material:new int[]{-2,-1,0})for(int tint=0;tint<GlassTextures.TINT_COUNT;tint++)for(int layout=0;layout<3;layout++){
   TileEntitySpaceDoor tile=large?new TileEntityLargeProgrammableDoor():new TileEntitySpaceDoor();tile.configure(design,1,true);DoorGlassAppearance a=new DoorGlassAppearance(material,18,tint,layout);tile.setGlassAppearance(a);
   NBTTagCompound saved=tile.writeToNBT(new NBTTagCompound());TileEntitySpaceDoor loaded=large?new TileEntityLargeProgrammableDoor():new TileEntitySpaceDoor();loaded.readFromNBT(saved);
   DoorGlassAppearance expected=material==-2?DoorGlassAppearance.DEFAULT:a;if(!loaded.glassAppearance().equals(expected))throw new AssertionError("door glass NBT lost finish");
   TileEntitySpaceDoor item=large?new TileEntityLargeProgrammableDoor():new TileEntitySpaceDoor();item.applyItemSettings(tile.itemSettings());if(!item.glassAppearance().equals(expected))throw new AssertionError("door glass item lost finish");
   ByteBuf bytes=Unpooled.buffer();new MessageDoorGlass(net.minecraft.util.math.BlockPos.ORIGIN,a).toBytes(bytes);if(bytes.readableBytes()!=15)throw new AssertionError("door glass packet not fixed-size");MessageDoorGlass message=new MessageDoorGlass();ByteBuf input=bytes.copy();message.fromBytes(input);input.release();ByteBuf round=Unpooled.buffer();message.toBytes(round);if(!bytes.equals(round))throw new AssertionError("door glass packet roundtrip");bytes.release();round.release();cases++;
  }
  for(int length:new int[]{0,14,16,1024}){ByteBuf b=Unpooled.buffer(length).writeZero(length);boolean rejected=false;try{new MessageDoorGlass().fromBytes(b);}catch(io.netty.handler.codec.DecoderException e){rejected=true;}finally{b.release();}if(!rejected)throw new AssertionError("unbounded door glass packet");}
  NBTTagCompound legacy=new NBTTagCompound();TileEntitySpaceDoor old=new TileEntitySpaceDoor();old.readFromNBT(legacy);if(old.glassAppearance()!=DoorGlassAppearance.DEFAULT||old.itemSettings().hasKey("DoorGlass"))throw new AssertionError("legacy door acquired a glass override");
  if(GlassMaterials.opaque(0,2)||!GlassMaterials.opaque(0,3)||GlassMaterials.opacity(0,2)>=GlassMaterials.opacity(0,3)||GlassTextures.validTint(4))throw new AssertionError("Strong/Opaque policy");
  System.out.println("door-glass-data PASS cases="+cases+" normal large light/dark all tints layouts save reload item packet15bytes malformed lengths legacy defaults");
 }
}
