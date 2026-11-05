package com.vandorlabs.client;

import com.vandorlabs.tiles.TileEntitySignalControl;
import net.minecraft.nbt.*;
import net.minecraft.util.ResourceLocation;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.TreeSet;

/** Frozen actual Forge tile serialization and getters captured before portable extraction. */
public final class SignalControlDataChecks {
    private static final String GOLDEN="77f78abc1fe00fa142027bc603687c065926d9287a152cb659efb51fb8ed27a8";
    private static void require(boolean value,String detail){if(!value)throw new AssertionError(detail);}
    private static String canonical(NBTBase value){if(!(value instanceof NBTTagCompound))return value.toString();NBTTagCompound tag=(NBTTagCompound)value;StringBuilder result=new StringBuilder("{");for(String key:new TreeSet<>(tag.getKeySet()))result.append(key).append(':').append(canonical(tag.getTag(key))).append(';');return result.append('}').toString();}
    private static void scalar(NBTTagCompound tag,String key,int type,int value){if(type==1)tag.setInteger(key,value);else if(type==2)tag.setByte(key,(byte)value);else if(type==3)tag.setString(key,Integer.toString(value));else if(type==4)tag.setLong(key,value);}
    public static void main(String[] ignored)throws Exception{
        net.minecraft.init.Bootstrap.register();net.minecraftforge.fml.common.registry.GameRegistry.registerTileEntity(TileEntitySignalControl.class,new ResourceLocation("vandorlabs","signal_control_fixture"));
        MessageDigest digest=MessageDigest.getInstance("SHA-256");int cases=0;String export=System.getProperty("vandorlabs.signalControlExport","");com.google.gson.JsonArray references=export.isEmpty()?null:new com.google.gson.JsonArray();
        int[] values={Integer.MIN_VALUE,-1,0,1,3,5,11,13,15,16,Integer.MAX_VALUE};
        for(int type=0;type<5;type++)for(int low:values)for(int high:values)for(int step:new int[]{Integer.MIN_VALUE,-1,0,1,2,3,4,Integer.MAX_VALUE}){
            TileEntitySignalControl tile=new TileEntitySignalControl();tile.configureLimits(3,11);tile.setStep(2);NBTTagCompound input=new NBTTagCompound();scalar(input,"LowLimit",type,low);scalar(input,"HighLimit",type,high);input.setInteger("ControlStep",step);
            NBTTagCompound forge=new NBTTagCompound();forge.setString("Opaque","retained");input.setTag("ForgeData",forge);NBTTagCompound before=input.copy();tile.readFromNBT(input);require(input.equals(before),"source NBT mutated");
            NBTTagCompound saved=tile.writeToNBT(new NBTTagCompound()),config=tile.configuration();require(!config.hasKey("ControlStep"),"configuration copied runtime step");require(saved.getCompoundTag("ForgeData").getString("Opaque").equals("retained"),"opaque data lost");
            if(references!=null){com.google.gson.JsonObject entry=new com.google.gson.JsonObject();entry.addProperty("input",before.toString());entry.addProperty("configuration",config.toString());entry.addProperty("low",tile.getLowLimit());entry.addProperty("high",tile.getHighLimit());entry.addProperty("rawStep",saved.getInteger("ControlStep"));entry.addProperty("visibleStep",tile.getStep());entry.addProperty("output",tile.getOutputLevel());entry.addProperty("localOn",tile.isLocalOn());references.add(entry);}
            digest.update((tile.getLowLimit()+":"+tile.getHighLimit()+":"+tile.getStep()+":"+tile.getOutputLevel()+":"+canonical(saved)+":"+canonical(config)+"\n").getBytes(StandardCharsets.UTF_8));cases++;
        }
        StringBuilder hash=new StringBuilder();for(byte part:digest.digest())hash.append(String.format("%02x",part&255));require(hash.toString().equals(GOLDEN),"actual pre-extraction signal NBT/getters changed: "+hash);
        if(references!=null){java.nio.file.Path path=java.nio.file.Paths.get(export);if(path.getParent()!=null)java.nio.file.Files.createDirectories(path.getParent());try(java.io.Writer writer=java.nio.file.Files.newBufferedWriter(path,StandardCharsets.UTF_8)){com.google.gson.JsonObject root=new com.google.gson.JsonObject();root.addProperty("schema",1);root.addProperty("legacyGolden",GOLDEN);root.addProperty("initialLow",3);root.addProperty("initialHigh",11);root.addProperty("initialStep",2);root.add("cases",references);new com.google.gson.Gson().toJson(root,writer);}System.out.println("Signal control Forge reference export PASS cases="+references.size());}
        System.out.println("SignalControlData actual adapter PASS cases="+cases+" golden="+hash+" typed/absent limits previous-state fallback step clamp item config and opaque data");
    }
}
