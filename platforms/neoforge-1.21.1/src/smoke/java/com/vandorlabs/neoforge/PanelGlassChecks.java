package com.vandorlabs.neoforge;

import com.google.gson.*;
import com.vandorlabs.persistence.PanelGlassSettings;
import java.nio.file.*;
import com.vandorlabs.neoforge.shape.NativeWallEntity;
import net.minecraft.nbt.*;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

/** Dedicated actual native-NBT replay of the distinct current panel schema. */
@EventBusSubscriber(modid="vandorlabs")
public final class PanelGlassChecks {
    @SubscribeEvent public static void start(ServerStartedEvent event)throws Exception{
        if(!System.getProperty("vandorlabs.transmitterPhase","").equals("panel-glass"))return;String reference=System.getProperty("vandorlabs.panelGlassReference","");if(reference.isEmpty())throw new IllegalStateException("Panel glass requires actual Forge reference");var rows=JsonParser.parseString(Files.readString(Path.of(reference))).getAsJsonArray();if(rows.size()!=462)throw new AssertionError("Original panel reference count");
        for(int i=0;i<rows.size();i++){var row=rows.get(i).getAsJsonObject();var input=TagParser.parseTag(row.get("input").getAsString());var before=input.copy();var data=PanelGlassSettings.read(new ModernHousingTags(input),material->material==-1||ModernFinishCatalog.validChoice(material)?material:-1);var actual=new CompoundTag();data.write(new ModernHousingTags(actual));var expected=TagParser.parseTag(row.get("saved").getAsString());var host=new WallHostChecks.Counting(true);host.loadWithComponents(input,event.getServer().registryAccess());host.reset();var saved=host.saveWithoutMetadata(event.getServer().registryAccess());for(String key:expected.getAllKeys())if(!java.util.Objects.equals(saved.get(key),expected.get(key)))throw new AssertionError("Host panel field="+key+" row="+i);if(host.wallAppearance().glass()!=host.panelGlassSettings())throw new AssertionError("Published glass snapshot");for(int layout=0;layout<3;layout++)for(boolean stretch:new boolean[]{false,true})for(boolean join:new boolean[]{false,true}){host.loadWithComponents(input,event.getServer().registryAccess());host.reset();var old=host.panelGlassSettings();int raw=old.layout;boolean nextStretch=stretch;if(old.stretch!=stretch||old.textureJoin!=join)raw=-1;int effective=raw<0?(stretch?0:1):raw;if(effective!=layout){raw=layout;nextStretch=layout!=1;}var next=new PanelGlassSettings(old.shade,old.material,layout,stretch,old.tint,old.framed,join);if(!host.configurePanelGlass(next,host.wallSettings().joinPortholes,host.wallSettings().portholeShape)||host.panelGlassSettings().layout!=raw||host.panelGlassSettings().stretch!=nextStretch||host.dirty>1||host.models>1)throw new AssertionError("Atomic layout setter order row="+i);var stable=host.wallAppearance();host.reset();if(!host.configurePanelGlass(host.panelGlassSettings(),host.wallSettings().joinPortholes,host.wallSettings().portholeShape)||host.wallAppearance()!=stable||host.dirty!=0||host.models!=0)throw new AssertionError("No-op publication");if(host.configurePanelGlass(new PanelGlassSettings(99,-1,-1,false,1,true,true),false,0)||host.wallAppearance()!=stable)throw new AssertionError("Invalid atomic rejection");}if(!input.equals(before)||!actual.equals(expected))throw new AssertionError("Current panel/native NBT parity row="+i+" actual="+actual+" expected="+expected);}
        System.out.println("[vandorlabs][panel-glass] dedicated PASS cases="+rows.size()+" actual Forge/native typed defaults seven keys immutable host snapshot 5544 atomic layout transitions/no-op/rejection");event.getServer().halt(false);
    }
}
