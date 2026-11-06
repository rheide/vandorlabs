package com.vandorlabs.client;

import com.google.gson.*;
import com.vandorlabs.items.ModItems;
import com.vandorlabs.tiles.TileEntityTokenSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.*;

/** Actual embedded reader load differs from a standalone reader's configurable policy. */
public final class EmbeddedChipLegacyChecks {
    public static void main(String[] args)throws Exception{
        net.minecraft.init.Bootstrap.register();net.minecraftforge.fml.common.registry.ForgeRegistries.ITEMS.register(ModItems.DATA_CHIP);net.minecraftforge.fml.common.registry.ForgeRegistries.ITEMS.register(ModItems.ACCESS_CARD);JsonArray rows=new JsonArray();
        for(int policy=0;policy<8;policy++)for(int kind=0;kind<3;kind++)for(int company:new int[]{-1,0,1,6,7,300})for(int count:new int[]{1,64}){
            NBTTagCompound raw=new NBTTagCompound();raw.setBoolean("AnyToken",false);raw.setInteger("AllowedTokens",2);raw.setBoolean("ChipSlot",false);raw.setInteger("TokenBehavior",policy%4);raw.setInteger("TokenLogo",policy-2);raw.setLong("TokenPulseUntil",100+policy);raw.setLong("TokenInsertStarted",200+policy);raw.setLong("TokenInsertionSequence",300+policy);raw.setLong("TokenRejectedUntil",400+policy);raw.setBoolean("TokenToggled",policy%2==0);raw.setBoolean("TokenToggleApplied",true);raw.setUniqueId("TokenReturnTo",new java.util.UUID(12,34));
            if(kind!=0){ItemStack chip=new ItemStack(kind==1?ModItems.DATA_CHIP:ModItems.ACCESS_CARD,count,company);NBTTagCompound tag=new NBTTagCompound();tag.setString("ChipType","journal");tag.setString("JournalTitle","Retained text");NBTTagList entries=new NBTTagList();entries.appendTag(new NBTTagString("Retained page"));tag.setTag("JournalPages",entries);tag.setString("ForeignTag","opaque");chip.setTagCompound(tag);NBTTagCompound item=chip.writeToNBT(new NBTTagCompound());item.setString("FutureStackRoot","retained");raw.setTag("InsertedToken",item);}
            TileEntityTokenSlot reader=new TileEntityTokenSlot(true);NBTTagCompound before=raw.copy();reader.readEmbedded(raw);if(!raw.equals(before))throw new AssertionError("Source mutated reader input");NBTTagCompound expected=reader.writeEmbedded();JsonObject row=new JsonObject();row.addProperty("input",raw.toString());row.addProperty("expected",expected.toString());row.addProperty("occupied",!reader.isEmpty());row.addProperty("output",reader.getOutputLevel());row.addProperty("company",reader.isEmpty()?-1:reader.getStackInSlot(0).getMetadata());rows.add(row);
        }
        byte[] bytes=rows.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);java.nio.file.Path path=java.nio.file.Paths.get("build/test-artifacts/embedded-chip-reference.json");java.nio.file.Files.createDirectories(path.getParent());java.nio.file.Files.write(path,bytes);StringBuilder hash=new StringBuilder();for(byte value:java.security.MessageDigest.getInstance("SHA-256").digest(bytes))hash.append(String.format("%02x",value&255));if(!hash.toString().equals("ce2f81d176ccac557892b7c4659c025298eb27cb910276be9dca6e10edece36f"))throw new AssertionError("Changed released reader corpus "+hash);System.out.println("Actual embedded chip reader PASS cases="+rows.size()+" sha256="+hash+" passive policy/count1/timelines/return cancellation/token payload");
    }
}
