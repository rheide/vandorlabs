package com.vandorlabs.neoforge.screen;

import com.google.gson.*;
import com.vandorlabs.neoforge.credentials.NativeCredentialItems;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.*;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

/** Released chip corpus through actual native items and both current/import payload envelopes. */
@EventBusSubscriber(modid="vandorlabs")
public final class ChipJournalChecks {
    private static int checks;
    private static void need(boolean value,String why){checks++;if(!value)throw new AssertionError("Native chip journal: "+why);}
    @SubscribeEvent public static void start(ServerStartedEvent event)throws Exception{
        if(!System.getProperty("vandorlabs.transmitterPhase","").equals("chip-journal"))return;
        JsonArray rows;try(var reader=java.nio.file.Files.newBufferedReader(java.nio.file.Path.of(System.getProperty("vandorlabs.chipJournalReference")))){rows=JsonParser.parseReader(reader).getAsJsonArray();}
        for(var raw:rows){var row=raw.getAsJsonObject();var tag=TagParser.parseTag(row.get("tag").getAsString());for(boolean imported:new boolean[]{false,true}){
            var stack=new ItemStack(row.get("chip").getAsBoolean()?NativeCredentialItems.DATA_CHIP.get():Items.STICK);stack.set(DataComponents.CUSTOM_MODEL_DATA,new CustomModelData(row.get("company").getAsInt()));CompoundTag root;
            if(imported){root=new CompoundTag();var legacy=new CompoundTag();legacy.put("tag",tag.copy());legacy.putString("ForeignRoot","opaque");root.put(NativeCredentialItems.LEGACY_KEY,legacy);}else root=tag.copy();stack.set(DataComponents.CUSTOM_DATA,CustomData.of(root));var before=stack.copy();var view=NativeChipJournal.view(stack);var expected=row.get("view");need((view==null)==expected.isJsonNull(),"original journal recognition");need(NativeChipJournal.journal(stack)==(view!=null),"predicate");
            if(view!=null){var wanted=expected.getAsJsonObject();need(view.title.equals(wanted.get("title").getAsString())&&view.source.equals(wanted.get("source").getAsString())&&view.key.equals(wanted.get("key").getAsString()),"original title/source/signature");var pages=wanted.getAsJsonArray("pages");need(view.pages.size()==pages.size(),"original page count");for(int index=0;index<pages.size();index++){var page=pages.get(index).getAsJsonObject();var actual=view.pages.get(index);need(actual.entry==page.get("entry").getAsInt(),"original entry index");var lines=page.getAsJsonArray("lines");need(lines.size()==actual.lines.size(),"original line count");for(int line=0;line<lines.size();line++)need(actual.lines.get(line).equals(lines.get(line).getAsString()),"original text line");}}
            need(ItemStack.isSameItemSameComponents(stack,before),"read does not mutate components");
            if(row.get("chip").getAsBoolean()){var legacy=root.get(NativeCredentialItems.LEGACY_KEY);NativeChipJournal.write(stack,true,"Current title",new String[]{"Current text"});var current=NativeChipJournal.view(stack);need(current!=null&&current.title.equals("Current title")&&current.pages.getFirst().lines.getFirst().equals("Current text"),"current edits replace imported text");need(NativeCredentialItems.company(stack)==row.get("company").getAsInt(),"company retained");if(legacy!=null)need(legacy.equals(stack.get(DataComponents.CUSTOM_DATA).copyTag().get(NativeCredentialItems.LEGACY_KEY)),"opaque import retained");NativeChipJournal.write(stack,false,"ignored",new String[0]);need(NativeChipJournal.view(stack)==null,"master overrides original journal");}
        }}
        System.out.println("[vandorlabs][chip-journal] dedicated PASS cases="+rows.size()+" checks="+checks+" source text/current+import envelope/metadata/immutable reads/edits");event.getServer().halt(false);
    }
}
