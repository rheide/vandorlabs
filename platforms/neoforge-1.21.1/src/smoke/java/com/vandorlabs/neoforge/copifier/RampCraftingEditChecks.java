package com.vandorlabs.neoforge.copifier;

import com.google.gson.*;
import com.vandorlabs.neoforge.ramp.*;
import java.nio.file.*;
import java.util.zip.GZIPInputStream;
import net.minecraft.nbt.*;

/** Released world-free Ramp recipe differs intentionally from actor-authorized installed editing. */
public final class RampCraftingEditChecks {
    public static void main(String[] args)throws Exception{
        JsonArray rows;try(var in=new GZIPInputStream(Files.newInputStream(Path.of(args[0])))){rows=JsonParser.parseString(new String(in.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8)).getAsJsonArray();}
        int count=0,outputs=0,emptySelections=0;
        for(var element:rows){
            var row=element.getAsJsonObject();var input=TagParser.parseTag(row.get("input").getAsString());var id=input.getString("id");if(!id.equals("vandorlabs:programmable_ramp")&&!id.equals("vandorlabs:programmable_ramp_slab"))continue;
            var root=input.getCompound("tag");var tool=TagParser.parseTag(row.get("tool").getAsString()).getCompound("tag");var values=NativeCopifierOptions.selected(NativeCopifierClipboard.settings(tool),NativeCopifierOptions.maskWords(tool));var expected=TagParser.parseTag(row.get("output").getAsString());boolean empty=expected.isEmpty()||expected.getString("id").equals("minecraft:air");
            if(NativeCopifierClipboard.hasCopy(tool)==empty)throw new AssertionError("Ramp recipe raw clipboard admission case="+count);
            if(!empty){
                var before=root.contains("BlockEntityTag",Tag.TAG_COMPOUND)?NativeRampSettings.read(root.getCompound("BlockEntityTag")):NativeRampSettings.defaults();var original=values.copy();var plan=NativeRampConfiguration.copied(before,2,2,values,false,false,false,false);var wanted=NativeRampSettings.read(expected.getCompound("tag").getCompound("BlockEntityTag"));
                if(!wanted.capture(2).equals(plan.result().capture(2)))throw new AssertionError("Ramp recipe channel-only edit case="+count+" expected="+wanted.capture(2)+" actual="+plan.result().capture(2));
                if(!expected.getCompound("tag").getCompound("CopiedRampSettings").equals(values)||!values.equals(original))throw new AssertionError("Ramp copied placement marker/caller case="+count);
                if(values.isEmpty())emptySelections++;outputs++;
            }
            count++;
        }
        if(count!=384||outputs==0||emptySelections==0)throw new AssertionError("Ramp Source recipe coverage="+count+" outputs="+outputs+" emptySelected="+emptySelections);
        System.out.println("Ramp copied item plan PASS Source="+count+" outputs="+outputs+" emptySelected="+emptySelections+" raw-clipboard admission/actor-free channel footer/complete placement marker; actual dispatch pending");
    }
}
