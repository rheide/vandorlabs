package com.vandorlabs.neoforge;

import com.google.gson.*;
import com.vandorlabs.persistence.PanelGlassSettings;
import java.nio.file.*;
import net.minecraft.nbt.*;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

/** Dedicated actual native-NBT replay of the distinct current panel schema. */
@EventBusSubscriber(modid="vandorlabs")
public final class PanelGlassChecks {
    @SubscribeEvent public static void start(ServerStartedEvent event)throws Exception{
        if(!System.getProperty("vandorlabs.transmitterPhase","").equals("panel-glass"))return;String reference=System.getProperty("vandorlabs.panelGlassReference","");if(reference.isEmpty())throw new IllegalStateException("Panel glass requires actual Forge reference");var rows=JsonParser.parseString(Files.readString(Path.of(reference))).getAsJsonArray();if(rows.size()!=462)throw new AssertionError("Original panel reference count");
        for(int i=0;i<rows.size();i++){var row=rows.get(i).getAsJsonObject();var input=TagParser.parseTag(row.get("input").getAsString());var before=input.copy();var data=PanelGlassSettings.read(new ModernHousingTags(input),material->material==-1||ModernFinishCatalog.validChoice(material)?material:-1);var actual=new CompoundTag();data.write(new ModernHousingTags(actual));var expected=TagParser.parseTag(row.get("saved").getAsString());if(!input.equals(before)||!actual.equals(expected))throw new AssertionError("Current panel/native NBT parity row="+i+" actual="+actual+" expected="+expected);}
        System.out.println("[vandorlabs][panel-glass] dedicated PASS cases="+rows.size()+" actual Forge/native typed defaults seven keys immutable state scalar writer");event.getServer().halt(false);
    }
}
