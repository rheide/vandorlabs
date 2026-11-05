package com.vandorlabs.client;

import com.vandorlabs.tiles.TileEntityProgrammableGlass;
import net.minecraft.nbt.*;
import net.minecraft.util.ResourceLocation;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.TreeSet;

/** Actual Forge save/getter oracle, frozen before extracting the scalar glass settings. */
public final class GlassSettingsDataChecks {
    private static final String GOLDEN="aa372c144022acc519ab9fa980d3a901884dea01bcce78c65fc8f25df5b7e0b4";
    private static final String[] KEYS={"GlassShade","GlassMaterial","GlassStretch","GlassTextureLayout","GlassTint","GlassFramed","GlassSize","GlassJoin","GlassPanelFacing"};
    private static void require(boolean value,String detail){if(!value)throw new AssertionError(detail);}
    private static String canonical(NBTBase value){if(!(value instanceof NBTTagCompound))return value.toString();NBTTagCompound tag=(NBTTagCompound)value;StringBuilder result=new StringBuilder("{");for(String key:new TreeSet<>(tag.getKeySet()))result.append(key).append(':').append(canonical(tag.getTag(key))).append(';');return result.append('}').toString();}
    private static void scalar(NBTTagCompound tag,String key,int type,int value){switch(type){case 1:tag.setInteger(key,value);break;case 2:tag.setByte(key,(byte)value);break;case 3:tag.setString(key,Integer.toString(value));break;case 4:tag.setLong(key,value);break;case 5:tag.setTag(key,new NBTTagCompound());break;case 6:tag.setFloat(key,value);}}
    private static String state(TileEntityProgrammableGlass tile)throws Exception{StringBuilder result=new StringBuilder();result.append(tile.getShade()).append(':').append(tile.getMaterial()).append(':').append(tile.isStretch()).append(':').append(tile.getTextureLayout()).append(':').append(tile.getTint()).append(':').append(tile.isFramed()).append(':').append(tile.getSize()).append(':').append(tile.isJoin());for(net.minecraft.util.EnumFacing face:net.minecraft.util.EnumFacing.HORIZONTALS)result.append(':').append(tile.getPanelFacing(face));for(String key:new String[]{"textureLayout","legacySize","panelFacing"}){java.lang.reflect.Field field=TileEntityProgrammableGlass.class.getDeclaredField(key);field.setAccessible(true);result.append(':').append(field.get(tile));}return result.toString();}
    public static void main(String[] ignored)throws Exception{
        net.minecraft.init.Bootstrap.register();net.minecraftforge.fml.common.registry.GameRegistry.registerTileEntity(TileEntityProgrammableGlass.class,new ResourceLocation("vandorlabs","glass_settings_fixture"));
        MessageDigest digest=MessageDigest.getInstance("SHA-256");int cases=0;
        TileEntityProgrammableGlass fresh=new TileEntityProgrammableGlass();digest.update((state(fresh)+canonical(fresh.writeToNBT(new NBTTagCompound()))).getBytes(StandardCharsets.UTF_8));
        int[] values={Integer.MIN_VALUE,-999,-1,0,1,2,3,4,15,255,Integer.MAX_VALUE};
        for(int key=0;key<=KEYS.length;key++)for(int type=0;type<7;type++)for(int value:values)for(boolean prior:new boolean[]{false,true}){
            TileEntityProgrammableGlass tile=new TileEntityProgrammableGlass();if(prior){tile.setSize(0);tile.setAppearance(2,false);tile.setJoin(false);tile.setStretch(true);tile.setPanelFacing(net.minecraft.util.EnumFacing.EAST);}
            NBTTagCompound input=new NBTTagCompound();for(int i=0;i<KEYS.length;i++)if(key==KEYS.length||i==key)scalar(input,KEYS[i],type,value);
            input.setBoolean("FrameBare",true);input.setBoolean("FrameTile",true);input.setByte("GlassPlaybackMode",(byte)2);input.setByte("GlassPlaybackSpeed",(byte)0);
            NBTTagCompound forge=new NBTTagCompound();forge.setString("Opaque","retained");input.setTag("ForgeData",forge);NBTTagCompound before=input.copy();tile.readFromNBT(input);require(input.equals(before),"source NBT mutated");
            NBTTagCompound saved=tile.writeToNBT(new NBTTagCompound());require(saved.getCompoundTag("ForgeData").getString("Opaque").equals("retained"),"opaque data lost");
            digest.update((key+":"+type+":"+value+":"+prior+":"+state(tile)+":"+canonical(saved)+"\n").getBytes(StandardCharsets.UTF_8));
            TileEntityProgrammableGlass again=new TileEntityProgrammableGlass();again.readFromNBT(saved);require(saved.equals(again.writeToNBT(new NBTTagCompound())),"saved canonical roundtrip "+key+":"+type+":"+value);cases++;
        }
        StringBuilder hash=new StringBuilder();for(byte part:digest.digest())hash.append(String.format("%02x",part&255));require(hash.toString().equals(GOLDEN),"actual pre-extraction glass NBT/getters changed: "+hash);
        System.out.println("GlassSettingsData actual adapter PASS cases="+cases+" golden="+hash+" typed fields fresh/prior defaults orientation and opaque state");
    }
}
