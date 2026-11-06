package com.vandorlabs.neoforge.screen;

import com.google.gson.*;
import com.vandorlabs.neoforge.credentials.*;
import net.minecraft.nbt.*;
import net.minecraft.world.level.block.entity.*;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

/** Local embedded load must neither publish a signal nor resolve its physical owner. */
@EventBusSubscriber(modid="vandorlabs")
public final class EmbeddedChipChecks {
    private static int checks;
    private static void need(boolean value,String why){checks++;if(!value)throw new AssertionError("Embedded chip: "+why);}
    @SubscribeEvent public static void start(ServerStartedEvent event)throws Exception{
        if(!System.getProperty("vandorlabs.transmitterPhase","").equals("embedded-chip"))return;var registries=event.getServer().registryAccess();
        var host=new NativeTokenReader.Host(){public BlockEntity owner(){throw new AssertionError("Local reader load queried world");}public boolean usable(net.minecraft.server.level.ServerPlayer player){throw new AssertionError("Load checked player");}public void changed(){throw new AssertionError("Load published");}public void schedule(int ticks){throw new AssertionError("Load scheduled");}};
        var reader=new NativeEmbeddedChipReader(host);JsonArray rows;try(var input=java.nio.file.Files.newBufferedReader(java.nio.file.Path.of(System.getProperty("vandorlabs.embeddedChipReference")))){rows=JsonParser.parseReader(input).getAsJsonArray();}
        for(var element:rows){var row=element.getAsJsonObject();var raw=TagParser.parseTag(row.get("input").getAsString());var before=raw.copy();reader.read(raw,registries);need(before.equals(raw),"input untouched");var expected=TagParser.parseTag(row.get("expected").getAsString());var actual=reader.write(registries);
            for(String key:expected.getAllKeys())if(!key.equals("InsertedToken"))need(expected.get(key).equals(actual.get(key)),"released passive policy/timeline "+key);for(String key:actual.getAllKeys())need(expected.contains(key),"no resurrected return state: "+key);
            need(reader.occupied()==row.get("occupied").getAsBoolean(),"source ownership");var token=reader.tokenCopy();need((token.isEmpty()?-1:NativeCredentialItems.company(token))==row.get("company").getAsInt(),"source metadata clamp");need(token.isEmpty()||token.getCount()==1,"one physical token");need(reader.visual(0).output()==row.get("output").getAsInt(),"source passive output");need(!reader.motion(0).returning(),"embedded return canceled");
            if(!token.isEmpty()){need(NativeCredentialItems.legacyCredentialData(token).equals(raw.getCompound("InsertedToken")),"complete imported item retained");var decoded=NativeCredentialItems.readStack(actual.getCompound("InsertedToken"),registries);need(decoded.getCount()==1&&net.minecraft.world.item.ItemStack.isSameItemSameComponents(decoded,token),"native owned item roundtrip");need((reader.journal()!=null)==token.is(NativeCredentialItems.DATA_CHIP.get()),"prepared journal available");var cached=reader.journal();for(int repeat=0;repeat<100;repeat++)need(reader.journal()==cached,"stable prepared journal");}
            var restored=new NativeEmbeddedChipReader(host);restored.read(actual,registries);need(restored.write(registries).equals(actual),"complete native embedded save/load");
            var stable=reader.write(registries);for(int failure=0;failure<3;failure++){var bad=raw.copy();if(failure==0)bad.putString("FutureReaderOwner","opaque");else if(failure==1){var item=new CompoundTag();item.putString("id","future:unknown_chip");item.putByte("Count",(byte)1);bad.put("InsertedToken",item);}else bad.putInt("InsertedToken",42);boolean refused=false;try{reader.read(bad,registries);}catch(IllegalArgumentException expectedFailure){refused=true;}need(refused&&reader.write(registries).equals(stable),"failed preparation leaves existing reader intact");}
        }
        ScreenChipViewChecks.run(registries);
        EmbeddedChipInventoryChecks.run(event.getServer().overworld());
        System.out.println("[vandorlabs][embedded-chip] dedicated PASS cases="+rows.size()+" checks="+checks+" released passive policy/one owner/local-only/opaque preparation failures");event.getServer().halt(false);
    }
}
