package com.vandorlabs.client;

import com.google.gson.*;
import com.vandorlabs.tiles.TileEntityAnimatedScreenSelector;
import net.minecraft.nbt.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/** Frozen actual seven-key panel glass contract, distinct from standalone programmable glass. */
public final class PanelGlassLegacyChecks {
    private static final String GOLDEN="6c413e0cd6628be70cf5900d2788f13f95faddb73700a692b74a8e7ae96d2368";
    private static final String[] KEYS={"GlassShade","GlassTint","GlassMaterial","GlassStretch","GlassTextureJoin","GlassFramed","GlassTextureLayout"};
    private static void value(NBTTagCompound tag,String key,int kind,int value){switch(kind){case 1:tag.setInteger(key,value);break;case 2:tag.setByte(key,(byte)value);break;case 3:tag.setShort(key,(short)value);break;case 4:tag.setLong(key,value);break;case 5:tag.setFloat(key,value+.75F);break;case 6:tag.setString(key,Integer.toString(value));break;}}
    public static void main(String[] args)throws Exception{
        net.minecraft.init.Bootstrap.register();net.minecraftforge.fml.common.registry.GameRegistry.registerTileEntity(TileEntityAnimatedScreenSelector.class,new net.minecraft.util.ResourceLocation("vandorlabs","panel_glass_fixture"));JsonArray rows=new JsonArray();MessageDigest digest=MessageDigest.getInstance("SHA-256");int count=0;
        for(int kind=0;kind<7;kind++)for(int saved:new int[]{Integer.MIN_VALUE,-2,-1,0,1,2,3,4,18,19,Integer.MAX_VALUE})for(int booleans=0;booleans<6;booleans++){
            NBTTagCompound input=new NBTTagCompound();for(String key:new String[]{"GlassShade","GlassTint","GlassMaterial","GlassTextureLayout"})value(input,key,kind,saved);for(String key:new String[]{"GlassStretch","GlassTextureJoin","GlassFramed"})value(input,key,booleans==5?6:booleans,saved);NBTTagCompound before=input.copy();TileEntityAnimatedScreenSelector tile=new TileEntityAnimatedScreenSelector();tile.setGlassShade(18);tile.setGlassMaterial(7);tile.setGlassAppearance(3,false);tile.setGlassLayout(true,false);tile.setGlassTextureLayout(2);tile.readFromNBT(input);if(!input.equals(before))throw new AssertionError("read mutated original input");NBTTagCompound all=tile.writeToNBT(new NBTTagCompound()),selected=new NBTTagCompound();StringBuilder data=new StringBuilder();for(String key:KEYS){selected.setTag(key,all.getTag(key).copy());data.append(key).append('=').append(all.getTag(key)).append(';');}data.append(tile.getGlassShade()).append('/').append(tile.getGlassTint()).append('/').append(tile.getGlassMaterial()).append('/').append(tile.isGlassStretch()).append('/').append(tile.isGlassTextureJoin()).append('/').append(tile.isGlassFramed()).append('/').append(tile.getGlassTextureLayout());digest.update(data.toString().getBytes(StandardCharsets.UTF_8));JsonObject row=new JsonObject();row.addProperty("input",input.toString());row.addProperty("saved",selected.toString());rows.add(row);count++;
        }
        StringBuilder hash=new StringBuilder();for(byte b:digest.digest())hash.append(String.format("%02x",b&255));if(!GOLDEN.equals(hash.toString()))throw new AssertionError("frozen current panel glass policy changed "+hash);
        String export=System.getProperty("vandorlabs.panelGlassExport","");if(!export.isEmpty()){Path target=Paths.get(export);Files.createDirectories(target.toAbsolutePath().getParent());Files.write(target,new Gson().toJson(rows).getBytes(StandardCharsets.UTF_8));}
        System.out.println("Panel glass actual-NBT PASS cases="+count+" SHA256="+hash+" typed defaults raw layout/stretch fallback and missing-any-tag booleans");
    }
}
