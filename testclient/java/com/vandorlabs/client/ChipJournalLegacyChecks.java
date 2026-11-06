package com.vandorlabs.client;

import com.google.gson.*;
import com.vandorlabs.items.ModItems;
import com.vandorlabs.programmer.*;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.*;

/** Original item decoder and view, including malformed tags and exact text/signature boundaries. */
public final class ChipJournalLegacyChecks {
    private static String repeat(String value,int count){StringBuilder out=new StringBuilder();for(int i=0;i<count;i++)out.append(value);return out.toString();}
    private static void need(boolean value,String why){if(!value)throw new AssertionError("Chip journal: "+why);}
    public static void main(String[] args)throws Exception{
        net.minecraft.init.Bootstrap.register();JsonArray result=new JsonArray();String[] texts={"","hello","\n","\r\n\t","a\u0000b",repeat("x",49),repeat("x",50),repeat("x",51),repeat("x",512),repeat("x",513),repeat("row\n",11),repeat("row\n",130),repeat("\ud83d\ude80",257),"a"+repeat("\ud83d\ude80",257)};
        for(int company:new int[]{-1,0,1,6,7})for(int mode=0;mode<7;mode++)for(int text=0;text<texts.length;text++){
            ItemStack stack=new ItemStack(mode==6?net.minecraft.init.Items.STICK:ModItems.DATA_CHIP,1,company);NBTTagCompound tag=new NBTTagCompound();if(mode!=0)tag.setString("ChipType",mode==1?"master":"journal");tag.setString("JournalTitle",texts[text]);NBTTagList pages=new NBTTagList();for(int entry=0;entry<(mode==5?10:8);entry++)pages.appendTag(new NBTTagString(entry==0||entry==7?texts[text]:""));tag.setTag("JournalPages",pages);if(mode==3)tag.setInteger("JournalPages",42);if(mode==4){pages=new NBTTagList();pages.appendTag(new NBTTagInt(42));tag.setTag("JournalPages",pages);}stack.setTagCompound(tag);
            ProgrammerJournalView actual=ProgrammerJournalView.from(stack);JsonObject row=new JsonObject();row.addProperty("company",company);row.addProperty("chip",mode!=6);row.addProperty("tag",tag.toString());
            if(actual==null)row.add("view",JsonNull.INSTANCE);else{
                String[] entries=new String[8];for(int entry=0;entry<8;entry++)entries[entry]=ChipJournal.page(stack,entry);JournalText shared=JournalText.layout(ChipJournal.title(stack),"Chip",entries);need(actual.title.equals(shared.title)&&actual.source.equals(shared.source)&&actual.key.equals(shared.key)&&actual.pages.size()==shared.pages.size(),"source view identity");
                JsonObject view=new JsonObject();view.addProperty("title",actual.title);view.addProperty("source",actual.source);view.addProperty("key",actual.key);JsonArray output=new JsonArray();for(int page=0;page<actual.pages.size();page++){ProgrammerJournalView.Page old=actual.pages.get(page);JournalText.Page next=shared.pages.get(page);need(old.entry==next.entry&&old.lines.equals(next.lines),"exact wrap/page/entry");JsonObject p=new JsonObject();p.addProperty("entry",old.entry);JsonArray lines=new JsonArray();for(String line:old.lines)lines.add(line);p.add("lines",lines);output.add(p);}view.add("pages",output);row.add("view",view);
            }result.add(row);
        }
        StringBuilder encoded=new StringBuilder();for(char unit:result.toString().toCharArray())if(Character.isSurrogate(unit))encoded.append(String.format("\\u%04x",(int)unit));else encoded.append(unit);byte[] bytes=encoded.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);java.nio.file.Path path=java.nio.file.Paths.get("build/test-artifacts/chip-journal-reference.json");java.nio.file.Files.createDirectories(path.getParent());java.nio.file.Files.write(path,bytes);StringBuilder hash=new StringBuilder();for(byte value:java.security.MessageDigest.getInstance("SHA-256").digest(bytes))hash.append(String.format("%02x",value&255));need(hash.toString().equals("5de4d94461d9e313d6f9640413383fb5f24a6aa1be7df793b1557950c1663d6d"),"frozen released corpus "+hash);System.out.println("Actual chip journal PASS cases="+result.size()+" sha256="+hash+" original item/metadata/malformed/UTF16/signature/pages");
    }
}
