package com.vandorlabs.client;

import com.vandorlabs.tiles.TileEntityWirelessRedstone;
import net.minecraft.nbt.*;
import net.minecraft.util.ResourceLocation;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.TreeSet;

/** Actual Forge endpoint tags and getters frozen before extracting their scalar policy. */
public final class WirelessRedstoneDataChecks {
    private static final String GOLDEN="3c4d3d9f075a266cc6bac4b6a85f0a8fba07724e83db61a636f83855b19d0f65";
    private static void require(boolean value,String detail){if(!value)throw new AssertionError(detail);}
    private static String canonical(NBTBase value){if(!(value instanceof NBTTagCompound))return value.toString();NBTTagCompound tag=(NBTTagCompound)value;StringBuilder out=new StringBuilder("{");for(String key:new TreeSet<>(tag.getKeySet()))out.append(key).append(':').append(canonical(tag.getTag(key))).append(';');return out.append('}').toString();}
    private static void scalar(NBTTagCompound tag,String key,int type,int value){tag.removeTag(key);if(type==1)tag.setInteger(key,value);else if(type==2)tag.setByte(key,(byte)value);else if(type==3)tag.setLong(key,value);else if(type==4)tag.setString(key,Integer.toString(value));else if(type==5)tag.setDouble(key,value-.5);else if(type==6)tag.setBoolean(key,value>0);}
    public static void main(String[] ignored)throws Exception{
        net.minecraft.init.Bootstrap.register();net.minecraftforge.fml.common.registry.GameRegistry.registerTileEntity(TileEntityWirelessRedstone.class,new ResourceLocation("vandorlabs","wireless_data_fixture"));
        MessageDigest digest=MessageDigest.getInstance("SHA-256");int cases=0;String export=System.getProperty("vandorlabs.wirelessExport", "");com.google.gson.JsonArray references=export.isEmpty()?null:new com.google.gson.JsonArray();String[] keys={"WirelessAnalog","WirelessOn","WirelessOff","WirelessInput","WirelessReceived"};int[] values={Integer.MIN_VALUE,-129,-1,0,1,7,14,15,16,256,Integer.MAX_VALUE};
        for(String key:keys)for(int type=0;type<7;type++)for(int value:values){
            TileEntityWirelessRedstone tile=new TileEntityWirelessRedstone();tile.configure(false,8,3);tile.setChannelLevel(11);
            NBTTagCompound input=new NBTTagCompound();input.setBoolean("WirelessAnalog",false);input.setInteger("WirelessOn",8);input.setInteger("WirelessOff",3);input.setInteger("WirelessInput",7);input.setInteger("WirelessReceived",11);scalar(input,key,type,value);
            NBTTagCompound opaque=new NBTTagCompound();opaque.setString("external","keep");input.setTag("ForgeData",opaque.copy());input.setTag("ForgeCaps",opaque.copy());NBTTagCompound before=input.copy();tile.readFromNBT(input);require(input.equals(before),"input mutated");
            NBTTagCompound saved=tile.writeToNBT(new NBTTagCompound()),configuration=tile.configuration();require(saved.getCompoundTag("ForgeData").equals(opaque),"opaque ForgeData lost");require(!configuration.hasKey("WirelessInput")&&!configuration.hasKey("WirelessReceived"),"runtime levels leaked to configuration");
            require(saved.hasKey("WirelessAnalog",1)&&saved.hasKey("WirelessOn",3)&&saved.hasKey("WirelessOff",3)&&saved.hasKey("WirelessInput",3)&&saved.hasKey("WirelessReceived",3),"saved typed schema");
            if(references!=null){com.google.gson.JsonObject entry=new com.google.gson.JsonObject();entry.addProperty("input",before.toString());entry.addProperty("configuration",configuration.toString());entry.addProperty("analog",tile.analog());entry.addProperty("on",tile.onLevel());entry.addProperty("off",tile.offLevel());entry.addProperty("inputLevel",saved.getInteger("WirelessInput"));entry.addProperty("receivedLevel",saved.getInteger("WirelessReceived"));entry.addProperty("output",tile.getOutputLevel());references.add(entry);}
            digest.update((key+":"+type+":"+value+":"+tile.analog()+":"+tile.onLevel()+":"+tile.offLevel()+":"+tile.getOutputLevel()+":"+canonical(saved)+":"+canonical(configuration)+"\n").getBytes(StandardCharsets.UTF_8));cases++;
        }
        StringBuilder hash=new StringBuilder();for(byte part:digest.digest())hash.append(String.format("%02x",part&255));require(hash.toString().equals(GOLDEN),"actual wireless NBT/getters changed: "+hash);
        if(references!=null){java.nio.file.Path path=java.nio.file.Paths.get(export);if(path.getParent()!=null)java.nio.file.Files.createDirectories(path.getParent());try(java.io.Writer writer=java.nio.file.Files.newBufferedWriter(path,StandardCharsets.UTF_8)){com.google.gson.JsonObject root=new com.google.gson.JsonObject();root.addProperty("schema",1);root.addProperty("legacyGolden",GOLDEN);root.addProperty("role","receiver");root.addProperty("initialAnalog",false);root.addProperty("initialOn",8);root.addProperty("initialOff",3);root.addProperty("initialReceived",11);root.add("cases",references);new com.google.gson.Gson().toJson(root,writer);}System.out.println("Wireless Forge reference export PASS cases="+references.size());}
        System.out.println("WirelessRedstoneData actual adapters PASS cases="+cases+" golden="+hash+" exact typed defaults/configuration/runtime levels/opaque tags");
    }
}
