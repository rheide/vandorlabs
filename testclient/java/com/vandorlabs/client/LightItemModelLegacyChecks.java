package com.vandorlabs.client;

import com.google.gson.*;
import com.vandorlabs.blocks.ModBlocks;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.item.*;
import net.minecraft.nbt.*;
import net.minecraft.util.ResourceLocation;

/** Freeze the actual mesh-definition closure before sharing its typed on/off selection policy. */
public final class LightItemModelLegacyChecks {
    public static void main(String[] args)throws Exception {
        net.minecraft.init.Bootstrap.register();
        java.lang.reflect.Method selector=null;
        for(java.lang.reflect.Method method:ModBlocks.class.getDeclaredMethods())if(method.getName().startsWith("lambda$registerLightItemModels$")&&method.getReturnType()==ModelResourceLocation.class){selector=method;break;}
        if(selector==null)throw new AssertionError("Missing actual Light model selector");selector.setAccessible(true);
        List<NBTBase> styles=new ArrayList<>();styles.add(null);for(int i=-1;i<8;i++)styles.add(new NBTTagInt(i));styles.add(new NBTTagByte((byte)5));styles.add(new NBTTagFloat(4.9F));styles.add(new NBTTagString("2"));
        NBTBase[] states={null,new NBTTagByte((byte)0),new NBTTagByte((byte)1),new NBTTagInt(1),new NBTTagString("true")};
        NBTBase[] levels={null,new NBTTagInt(-1),new NBTTagInt(0),new NBTTagInt(1),new NBTTagInt(15),new NBTTagByte((byte)0),new NBTTagFloat(0),new NBTTagCompound()};
        Item item=new Item();JsonArray rows=new JsonArray();
        for(int family=0;family<3;family++){
            ResourceLocation[] variants=new ResourceLocation[12];for(int i=0;i<12;i++)variants[i]=new ResourceLocation("vandorlabs","light_selector_"+family+"_"+i);
            for(int style=0;style<styles.size();style++)for(int on=0;on<states.length;on++)for(int level=0;level<levels.length;level++){
                ItemStack stack=new ItemStack(item);NBTTagCompound tag=new NBTTagCompound();if(styles.get(style)!=null)tag.setTag("LightTexture",styles.get(style).copy());if(states[on]!=null)tag.setTag("LightOn",states[on].copy());if(levels[level]!=null)tag.setTag("LightLevel",levels[level].copy());tag.setBoolean("SignalBrightness",true);tag.setInteger("LightOffset",-15);stack.setTagInfo("BlockEntityTag",tag);
                ModelResourceLocation result=(ModelResourceLocation)selector.invoke(null,variants,stack);JsonObject row=new JsonObject();row.addProperty("family",family);row.addProperty("tag",tag.toString());row.addProperty("choice",Integer.parseInt(result.getResourcePath().substring(result.getResourcePath().lastIndexOf('_')+1)));rows.add(row);
            }
            for(boolean absent:new boolean[]{false,true}){ItemStack stack=new ItemStack(item);NBTTagCompound tag=new NBTTagCompound();if(!absent)stack.setTagInfo("BlockEntityTag",tag);ModelResourceLocation result=(ModelResourceLocation)selector.invoke(null,variants,stack);JsonObject row=new JsonObject();row.addProperty("family",family);row.addProperty("tag",absent?"absent":tag.toString());row.addProperty("choice",Integer.parseInt(result.getResourcePath().substring(result.getResourcePath().lastIndexOf('_')+1)));rows.add(row);}
        }
        byte[] bytes=new Gson().toJson(rows).getBytes(StandardCharsets.UTF_8);StringBuilder hash=new StringBuilder();for(byte value:java.security.MessageDigest.getInstance("SHA-256").digest(bytes))hash.append(String.format(Locale.ROOT,"%02x",value&255));if(rows.size()!=1566||!hash.toString().equals("05a39308680f0d42444e24563da3104f9bd7c8f9853f5882876b965db7675c6f"))throw new AssertionError("Released Light item selector changed "+hash);Path output=Paths.get(args[0]);Files.createDirectories(output.toAbsolutePath().getParent());Files.write(output,bytes);System.out.println("Actual Light item model selector PASS cases="+rows.size()+" golden="+hash);
    }
}
