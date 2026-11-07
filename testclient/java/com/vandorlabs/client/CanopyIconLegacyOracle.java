package com.vandorlabs.client;

import com.google.gson.*;
import java.io.*;
import java.lang.reflect.*;
import java.nio.*;
import java.security.MessageDigest;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.*;
import net.minecraft.client.renderer.texture.*;
import net.minecraft.item.*;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;

/** Actual configured Icon override, raw authored meshes, packed emissions and fitted hand/GUI transforms. */
public final class CanopyIconLegacyOracle {
    private static double aspect=1;
    private static NBTTagCompound choices(int profile){
        if(profile==0)return null;NBTTagCompound tag=new NBTTagCompound();
        if(profile==1)return tag;
        if(profile==2){NBTTagCompound ignored=new NBTTagCompound();ignored.setInteger("CanopyDepth",80);ignored.setInteger("CanopyShade",18);tag.setTag("BlockEntityTag",ignored);return tag;}
        tag.setInteger("CanopyDepth",profile==4?0:profile==5?80:17);tag.setInteger("CanopyScale",profile==4?10:profile==5?100:90);tag.setInteger("CanopyShade",profile==7?18:2);tag.setInteger("CanopyTint",profile==4?0:profile==7?2:3);tag.setInteger("CanopyMaterial",profile==3||profile==5?64:-1);tag.setInteger("CanopyTextureLayout",profile==5?2:1);tag.setBoolean("CanopyTileMaterial",profile!=5);
        if(profile==6){tag.setInteger("CanopyAngle",3);tag.setInteger("CanopyMotion",1);tag.setInteger("CanopyHinge",3);tag.setInteger("CanopyMount",2);tag.setBoolean("CanopySlideForward",false);tag.setBoolean("CanopyOpenable",true);}return tag;
    }
    private static void integer(MessageDigest digest,int value){digest.update((byte)(value>>>24));digest.update((byte)(value>>>16));digest.update((byte)(value>>>8));digest.update((byte)value);}
    private static JsonObject observe(String kind,int profile)throws Exception{
        Field cache=CanopyModels.Icon.class.getDeclaredField("CONFIGURED");cache.setAccessible(true);((Map<?,?>)cache.get(null)).clear();
        CanopyModels.Icon base=new CanopyModels.Icon(kind);ItemStack stack=new ItemStack(Item.REGISTRY.getObject(new ResourceLocation("vandorlabs",kind)));NBTTagCompound tag=choices(profile);if(tag!=null)stack.setTagCompound(tag);IBakedModel actual=base.getOverrides().handleItemState(base,stack,null,null);MessageDigest digest=MessageDigest.getInstance("SHA-256");int quads=0;
        for(BakedQuad quad:actual.getQuads(null,null,0)){digest.update((quad.getSprite().getIconName()+"\n").getBytes("UTF-8"));integer(digest,quad.getFace().getIndex());int[] data=quad.getVertexData();if(data.length!=28)throw new AssertionError("Source item format");for(int value:data)integer(digest,value);quads++;}
        StringBuilder hex=new StringBuilder();for(byte b:digest.digest())hex.append(String.format(Locale.ROOT,"%02x",b&255));JsonObject row=new JsonObject();row.addProperty("id",kind);row.addProperty("profile",profile);row.addProperty("aspect",aspect);row.addProperty("input",tag==null?"":tag.toString());row.addProperty("quads",quads);row.addProperty("sha256",hex.toString());row.addProperty("particle",actual.getParticleTexture().getIconName());JsonObject transforms=new JsonObject();
        for(ItemCameraTransforms.TransformType type:ItemCameraTransforms.TransformType.values()){ItemTransformVec3f t=actual.getItemCameraTransforms().getTransform(type);JsonArray fields=new JsonArray();for(float v:new float[]{t.rotation.x,t.rotation.y,t.rotation.z,t.translation.x,t.translation.y,t.translation.z,t.scale.x,t.scale.y,t.scale.z})fields.add(Float.floatToRawIntBits(v));transforms.add(type.name(),fields);}row.add("transforms",transforms);return row;
    }
    public static void main(String[] args)throws Exception{
        CanopyPlacementLegacyOracle.initialize();com.vandorlabs.VandorLabs.proxy=new com.vandorlabs.CommonProxy(){@Override public double materialAspect(int id){return aspect;}};
        Field unsafeField=sun.misc.Unsafe.class.getDeclaredField("theUnsafe");unsafeField.setAccessible(true);Minecraft mc=(Minecraft)((sun.misc.Unsafe)unsafeField.get(null)).allocateInstance(Minecraft.class);
        for(Field field:Minecraft.class.getDeclaredFields())if(Modifier.isStatic(field.getModifiers())&&field.getType()==Minecraft.class){field.setAccessible(true);field.set(null,mc);}
        final Map<String,TextureAtlasSprite> sprites=new HashMap<>();TextureMap atlas=new TextureMap(""){@Override public TextureAtlasSprite getAtlasSprite(String name){TextureAtlasSprite sprite=sprites.get(name);if(sprite==null){sprite=new TextureAtlasSprite(name){@Override public float getMinU(){return 0;}@Override public float getMaxU(){return 1;}@Override public float getMinV(){return 0;}@Override public float getMaxV(){return 1;}@Override public float getInterpolatedU(double u){return (float)u/16F;}@Override public float getInterpolatedV(double v){return (float)v/16F;}};sprites.put(name,sprite);}return sprite;}};
        for(Field field:Minecraft.class.getDeclaredFields())if(!Modifier.isStatic(field.getModifiers())&&field.getType()==TextureMap.class){field.setAccessible(true);field.set(mc,atlas);}
        JsonArray rows=new JsonArray();for(String kind:new String[]{"angled_front_1x1","angled_front_1x2","canopy","long_canopy","regular_canopy_glass","front_canopy_glass","large_front_canopy_glass","space_visor_2x1","space_visor_3x1","space_visor_5x1","space_visor_5x1_double_height"})for(double value:new double[]{1,4})for(int profile=0;profile<8;profile++){aspect=value;rows.add(observe(kind,profile));}
        File target=new File(args[0]);target.getAbsoluteFile().getParentFile().mkdirs();try(Writer writer=new OutputStreamWriter(new FileOutputStream(target),"UTF-8")){new Gson().toJson(rows,writer);writer.write('\n');}SegmentedReferenceGolden.check(target,"45d1e9f51d5291475cdfa6e37910d4b72f41421a79d9ae10151d74e1986d6551");System.out.println("Actual Canopy item Icon PASS profiles="+rows.size()+" raw meshes/actual override/default vs generic/configured/triangles/alpha/normals/padding/all hand transforms/aspect");
    }
}
