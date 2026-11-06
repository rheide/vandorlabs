package com.vandorlabs.client;

import com.google.gson.*;
import java.lang.reflect.*;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.*;
import net.minecraft.client.renderer.texture.*;
import net.minecraft.item.*;
import net.minecraft.nbt.*;
import net.minecraft.util.EnumFacing;

/** Actual released diagonal inventory wrapper, with a deterministic CPU atlas and original overrides. */
public final class ScreenDiagonalItemLegacyChecks {
 private static final Map<String,TextureAtlasSprite> SPRITES=new LinkedHashMap<>();
 private static TextureAtlasSprite sprite(String name){TextureAtlasSprite value=SPRITES.get(name);if(value==null){value=new TextureAtlasSprite(name){};value.setIconWidth(16);value.setIconHeight(16);value.initSprite(256,256,32,64,false);SPRITES.put(name,value);}return value;}
 public static void main(String[] args)throws Exception{
  net.minecraft.init.Bootstrap.register();Field field=sun.misc.Unsafe.class.getDeclaredField("theUnsafe");field.setAccessible(true);sun.misc.Unsafe unsafe=(sun.misc.Unsafe)field.get(null);Minecraft mc=(Minecraft)unsafe.allocateInstance(Minecraft.class);
  for(Field f:Minecraft.class.getDeclaredFields())if(Modifier.isStatic(f.getModifiers())&&f.getType()==Minecraft.class){f.setAccessible(true);f.set(null,mc);}
  TextureMap atlas=new TextureMap(""){@Override public TextureAtlasSprite getAtlasSprite(String name){return sprite(name);}};for(Field f:Minecraft.class.getDeclaredFields())if(!Modifier.isStatic(f.getModifiers())&&f.getType()==TextureMap.class){f.setAccessible(true);f.set(mc,atlas);}
  IBakedModel delegate=new IBakedModel(){public List<BakedQuad> getQuads(net.minecraft.block.state.IBlockState state,EnumFacing side,long seed){return Collections.emptyList();}public boolean isAmbientOcclusion(){return false;}public boolean isGui3d(){return true;}public boolean isBuiltInRenderer(){return false;}public TextureAtlasSprite getParticleTexture(){return sprite("oracle/fallback");}public ItemCameraTransforms getItemCameraTransforms(){return ItemCameraTransforms.DEFAULT;}public ItemOverrideList getOverrides(){return ItemOverrideList.NONE;}};
  DiagonalScreenItemModel base=new DiagonalScreenItemModel(delegate);String[] inputs={"{}","{RedstonePrimary:{Enabled:1b}}","{RedstonePrimary:{Enabled:0b}}","{RedstonePrimary:{Enabled:1}}","{RedstonePrimary:42}","{housingTexture:14,selectedScreen:unrelated,smallInput:1b,RedstonePrimary:{Enabled:1b}}"};JsonArray cases=new JsonArray();
  for(String input:inputs){ItemStack stack=new ItemStack(net.minecraft.init.Items.STICK);stack.setTagInfo("BlockEntityTag",JsonToNBT.getTagFromJson(input));IBakedModel model=base.getOverrides().handleItemState(base,stack,null,null);JsonObject row=new JsonObject();row.addProperty("input",input);JsonArray quads=new JsonArray();for(BakedQuad quad:model.getQuads(null,null,0)){JsonObject q=new JsonObject();q.addProperty("sprite",quad.getSprite().getIconName());q.addProperty("face",quad.getFace().getIndex());JsonArray data=new JsonArray();for(int value:quad.getVertexData())data.add(value);q.add("vertices",data);quads.add(q);}for(EnumFacing side:EnumFacing.values())if(!model.getQuads(null,side,0).isEmpty())throw new AssertionError("Source item emitted side bucket");row.add("quads",quads);
   boolean enabled=stack.getSubCompound("BlockEntityTag").getCompoundTag("RedstonePrimary").getBoolean("Enabled");List<com.vandorlabs.render.ScreenDiagonalItemMesh.Quad> shared=com.vandorlabs.render.ScreenDiagonalItemMesh.quads(enabled);List<BakedQuad> original=model.getQuads(null,null,0);if(shared.size()!=original.size())throw new AssertionError("Shared silhouette count");
   for(int index=0;index<shared.size();index++){com.vandorlabs.render.ScreenDiagonalItemMesh.Quad authored=shared.get(index);BakedQuad actual=original.get(index);if(!authored.texture.equals(actual.getSprite().getIconName())||authored.face()!=actual.getFace().getIndex())throw new AssertionError("Shared material/face");int[] raw=actual.getVertexData();for(int vertex=0;vertex<4;vertex++){for(int axis=0;axis<3;axis++)if(raw[vertex*7+axis]!=Float.floatToRawIntBits((float)(authored.value(vertex,axis)/16)))throw new AssertionError("Shared position");if(raw[vertex*7+4]!=Float.floatToRawIntBits(actual.getSprite().getInterpolatedU(authored.value(vertex,3)))||raw[vertex*7+5]!=Float.floatToRawIntBits(actual.getSprite().getInterpolatedV(authored.value(vertex,4))))throw new AssertionError("Shared UV");}}
   cases.add(row);}
  java.nio.file.Path path=java.nio.file.Paths.get("build/test-artifacts/screen-diagonal-item-reference.json");java.nio.file.Files.createDirectories(path.getParent());byte[] bytes=cases.toString().getBytes("UTF-8");java.nio.file.Files.write(path,bytes);StringBuilder hash=new StringBuilder();for(byte b:java.security.MessageDigest.getInstance("SHA-256").digest(bytes))hash.append(String.format("%02x",b&255));if(!hash.toString().equals("7195ed0a87c0285f96e3ffff30b7b0e17cb6d12cac4597eed65b4088ee51d485"))throw new AssertionError("Changed released Screen inventory: "+hash);System.out.println("Actual diagonal Screen inventory PASS cases="+cases.size()+" sha256="+hash+" original override/model/UV/face records");
 }
}
