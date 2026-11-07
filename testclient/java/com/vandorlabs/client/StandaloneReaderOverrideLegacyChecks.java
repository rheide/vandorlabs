package com.vandorlabs.client;

import com.google.gson.*;
import com.vandorlabs.blocks.BlockTokenSlot;
import java.nio.file.*;
import java.util.*;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.block.model.*;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.item.*;
import net.minecraft.nbt.*;
import net.minecraft.util.EnumFacing;

/** Actual released wrapper handoff for configured resource-model overrides, including flat items. */
public final class StandaloneReaderOverrideLegacyChecks {
 private static final class Model implements IBakedModel {
  final boolean selected;final int[] calls;Model(boolean selected,int[] calls){this.selected=selected;this.calls=calls;}
  public List<BakedQuad> getQuads(IBlockState state,EnumFacing side,long seed){return Collections.emptyList();}public boolean isAmbientOcclusion(){return true;}public boolean isGui3d(){return !selected;}public boolean isBuiltInRenderer(){return false;}public TextureAtlasSprite getParticleTexture(){return null;}public ItemCameraTransforms getItemCameraTransforms(){return ItemCameraTransforms.DEFAULT;}
  public ItemOverrideList getOverrides(){return new ItemOverrideList(Collections.emptyList()){@Override public IBakedModel handleItemState(IBakedModel original,ItemStack stack,net.minecraft.world.World world,net.minecraft.entity.EntityLivingBase entity){calls[0]++;return new Model(true,calls);}};}
 }
 public static void main(String[] args)throws Exception {
  net.minecraft.init.Bootstrap.register();JsonArray rows=new JsonArray();BlockTokenSlot block=new BlockTokenSlot("access_card_slot",false);Item item=new ItemBlock(block);
  for(int profile=-8;profile<64;profile++){ItemStack stack=new ItemStack(item);NBTTagCompound config=new NBTTagCompound();if(profile>=0){config.setInteger("BaseHeight",profile%4);config.setInteger("BaseTilt",profile/4%4);config.setInteger("TiltDirection",profile/16);config.setInteger("ControlMountVersion",1);}else if(profile==-6)config.setInteger("BaseHeight",4);else if(profile==-5)config.setInteger("BaseTilt",4);else if(profile==-4)config.setInteger("TiltDirection",4);else if(profile==-3)config.setInteger("PowerLeverSize",2);else if(profile==-2)config.setInteger("PowerLeverSize",1);else if(profile==-1)config.setShort("PowerLeverSize",(short)2);if(profile==-7)stack.setTagInfo("RedstoneChannelSettings",new NBTTagString("not a compound"));else if(profile!=-8)stack.setTagInfo("RedstoneChannelSettings",config);ItemStack before=stack.copy();int[] calls={0};IBakedModel original=new MountedBinaryControlModel(new Model(false,calls),block);IBakedModel result=original.getOverrides().handleItemState(original,stack,null,null);if(!ItemStack.areItemStacksEqual(before,stack))throw new AssertionError("Source override mutated item");JsonObject row=new JsonObject();row.addProperty("profile",profile);row.addProperty("calls",calls[0]);row.addProperty("selected",!result.isGui3d());row.addProperty("same",result==original);rows.add(row);}
  String json=new Gson().toJson(rows);java.security.MessageDigest digest=java.security.MessageDigest.getInstance("SHA-256");StringBuilder hash=new StringBuilder();for(byte b:digest.digest(json.getBytes("UTF-8")))hash.append(String.format("%02x",b&255));if(!hash.toString().equals("e234d81ea0a130e8e07917d7d92bd3bff6464485c67e95eec40b4a5d5652bd8c"))throw new AssertionError("Released reader override handoff changed "+hash);JsonObject root=new JsonObject();root.addProperty("schema",1);root.addProperty("golden",hash.toString());root.add("cases",rows);Path path=Paths.get(System.getProperty("vandorlabs.standaloneReaderOverrideReference","build/test-artifacts/standalone-reader-override-forge.json"));Files.createDirectories(path.toAbsolutePath().getParent());try(java.io.Writer writer=Files.newBufferedWriter(path)){new Gson().toJson(root,writer);}System.out.println("Actual standalone reader item overrides PASS cases="+rows.size()+" golden="+hash);
 }
}
