package com.vandorlabs.client;
import com.google.gson.*;
import com.vandorlabs.blocks.*;
import com.vandorlabs.tiles.*;
import java.nio.file.*;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;

/** Actual glass factory, missing-save defaults and frame emission, without a game window. */
public final class DiagonalGlassRegistrationLegacyChecks {
 public static void main(String[] args)throws Exception{
  net.minecraft.init.Bootstrap.register();BlockProgrammableDiagonalGlass block=new BlockProgrammableDiagonalGlass();int lit=-1;for(int i=0;i<ScreenHousingTextures.IDS.length;i++){JsonObject entry=ScreenHousingTextures.entry(i);if(entry!=null&&entry.has("frameLight")&&entry.get("frameLight").getAsInt()>0){lit=ScreenHousingTextures.choiceAt(i);break;}}if(lit<0)throw new AssertionError("Actual emissive frame required");
  JsonArray rows=new JsonArray();for(int index=-2;index<32;index++){
   NonRenderingChecks.MemoryWorld world=new NonRenderingChecks.MemoryWorld(false);BlockPos pos=new BlockPos(0,80,0);world.setBlockState(pos,block.getDefaultState(),2);TileEntityAnimatedScreenSelector tile=(TileEntityAnimatedScreenSelector)world.getTileEntity(pos);NBTTagCompound input=new NBTTagCompound();boolean fresh=index==-2;
   if(index>=0){input.setBoolean("GlassFramed",(index&1)!=0);input.setBoolean("FrameBare",(index&2)!=0);input.setInteger("FrameFront",(index&4)!=0?lit:-1);input.setInteger("FrameInterior",(index&8)!=0?lit:-1);input.setInteger("FrameExterior",(index&16)!=0?lit:-1);input.setString("FutureDiagonalGlass","opaque");NBTTagCompound caps=new NBTTagCompound();caps.setInteger("private",271828);input.setTag("ForgeCaps",caps);}
   if(!fresh){tile.readFromNBT(input);tile.setPos(pos);}JsonObject row=new JsonObject();row.addProperty("fresh",fresh);row.addProperty("input",input.toString());row.addProperty("shade",tile.getGlassShade());row.addProperty("tint",tile.getGlassTint());row.addProperty("material",tile.getGlassMaterial());row.addProperty("layout",tile.getGlassTextureLayout());row.addProperty("framed",tile.isGlassFramed());row.addProperty("join",tile.isGlassTextureJoin());row.addProperty("bare",tile.frameAppearance.bare);row.addProperty("front",tile.frameAppearance.front);row.addProperty("interior",tile.frameAppearance.interior);row.addProperty("exterior",tile.frameAppearance.exterior);row.addProperty("light",block.getLightValue(block.getDefaultState(),world,pos));rows.add(row);
  }
  String content=new Gson().toJson(rows);java.security.MessageDigest digest=java.security.MessageDigest.getInstance("SHA-256");StringBuilder hash=new StringBuilder();for(byte b:digest.digest(content.getBytes(java.nio.charset.StandardCharsets.UTF_8)))hash.append(String.format("%02x",b&255));String expected="fa0eba54db333b89db552b04c7bdf20290cd0280471248fe3256b5d37e7bf39e";if(!expected.isEmpty()&&!expected.equals(hash.toString()))throw new AssertionError("Actual glass defaults/light changed "+hash);JsonObject root=new JsonObject();root.addProperty("schema",1);root.addProperty("golden",hash.toString());root.addProperty("litFrame",lit);root.add("cases",rows);Path file=Paths.get(System.getProperty("vandorlabs.diagonalGlassRegistrationReference","build/test-artifacts/diagonal-glass-registration-forge.json"));Files.createDirectories(file.toAbsolutePath().getParent());try(java.io.Writer writer=Files.newBufferedWriter(file)){new Gson().toJson(root,writer);}System.out.println("Actual diagonal glass factory/light PASS cases="+rows.size()+" golden="+hash+" litFrame="+lit);
 }
}
