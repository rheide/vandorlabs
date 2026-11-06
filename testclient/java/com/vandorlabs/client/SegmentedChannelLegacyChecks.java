package com.vandorlabs.client;

import com.google.gson.*;
import com.vandorlabs.segmented.SegmentedDoorTile;
import java.nio.file.*;
import net.minecraft.nbt.NBTTagCompound;

/** Actual source loader ignores the unrelated scalar and falls back to EMPTY for malformed channel lists. */
public final class SegmentedChannelLegacyChecks {
    public static void main(String[] args)throws Exception{
        net.minecraft.init.Bootstrap.register();com.vandorlabs.VandorLabs.proxy=new com.vandorlabs.CommonProxy();net.minecraftforge.fml.common.registry.GameRegistry.registerTileEntity(SegmentedDoorTile.class,new net.minecraft.util.ResourceLocation("vandorlabs","segmented_door"));JsonArray rows=new JsonArray();
        for(int kind=0;kind<10;kind++){NBTTagCompound input=new NBTTagCompound();if(kind!=0)input.setInteger("RedstoneChannel",kind==2?-41:78111);if(kind==3)input.setIntArray("RedstoneChannels",new int[]{7,3,7,0});if(kind==4)input.setIntArray("RedstoneChannels",new int[]{-1});if(kind==5){int[] tooMany=new int[65];for(int i=0;i<tooMany.length;i++)tooMany[i]=i+1;input.setIntArray("RedstoneChannels",tooMany);}if(kind==6)input.setString("RedstoneChannels","future:channel-bank");if(kind==7)input.setInteger("RedstoneChannels",812);if(kind==8)input.setIntArray("RedstoneChannels",new int[0]);if(kind==9)input.setIntArray("RedstoneChannels",new int[]{Integer.MAX_VALUE,2,2});NBTTagCompound caps=new NBTTagCompound();caps.setString("Owner","channel-fixture");input.setTag("ForgeCaps",caps);SegmentedDoorTile tile=new SegmentedDoorTile();tile.readFromNBT(input.copy());JsonObject row=new JsonObject();row.addProperty("kind",kind);row.addProperty("input",input.toString());JsonArray channels=new JsonArray();for(int value:tile.getRedstoneChannels().toArray())channels.add(value);row.add("channels",channels);JsonArray saved=new JsonArray();for(int value:tile.writeToNBT(new NBTTagCompound()).getIntArray("RedstoneChannels"))saved.add(value);row.add("saved",saved);rows.add(row);}
        String json=rows.toString();StringBuilder hash=new StringBuilder();for(byte b:java.security.MessageDigest.getInstance("SHA-256").digest(json.getBytes(java.nio.charset.StandardCharsets.UTF_8)))hash.append(String.format("%02x",b&255));if(rows.size()!=10||!hash.toString().equals("b1b737df7c6ca0dd069a6e9098463e554405017c52dd48b78a0a910cc085b051"))throw new AssertionError("Segmented channel source changed "+hash);Path output=Paths.get(System.getProperty("vandorlabs.segmentedChannelReference"));Files.createDirectories(output.toAbsolutePath().getParent());Files.write(output,json.getBytes(java.nio.charset.StandardCharsets.UTF_8));System.out.println("Actual Segmented channel fallback PASS cases="+rows.size()+" golden="+hash);
    }
}
