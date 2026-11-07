package com.vandorlabs.neoforge.copifier;

import com.google.gson.*;
import com.vandorlabs.neoforge.canopy.NativeCanopySettings;
import java.nio.file.*;
import java.util.*;
import java.util.zip.GZIPInputStream;
import net.minecraft.nbt.*;

/** Independent actual released recipe outputs for all eleven Canopy kinds and item-envelope precedence. */
public final class CanopyCraftingEditChecks {
    public static void main(String[] args)throws Exception{
        JsonArray rows;try(var in=new GZIPInputStream(Files.newInputStream(Path.of(args[0])))){rows=JsonParser.parseString(new String(in.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8)).getAsJsonArray();}
        var kinds=Set.of("angled_front_1x1","angled_front_1x2","canopy","long_canopy","regular_canopy_glass","front_canopy_glass","large_front_canopy_glass","space_visor_2x1","space_visor_3x1","space_visor_5x1","space_visor_5x1_double_height");int count=0;
        for(var element:rows){var row=element.getAsJsonObject();var input=TagParser.parseTag(row.get("input").getAsString());String id=input.getString("id"),kind=id.substring(id.indexOf(':')+1);if(!kinds.contains(kind))continue;var root=input.getCompound("tag");var tool=TagParser.parseTag(row.get("tool").getAsString()).getCompound("tag");var values=NativeCopifierOptions.selected(NativeCopifierClipboard.settings(tool),NativeCopifierOptions.maskWords(tool));var current=input.contains("tag",Tag.TAG_COMPOUND)?NativeCanopySettings.read(root):NativeCanopySettings.fresh();var original=root.copy();var copy=values.copy();var plan=NativeCopifierCanopyEdit.prepare(kind,current,values);var expected=TagParser.parseTag(row.get("output").getAsString());boolean empty=expected.isEmpty()||expected.getString("id").equals("minecraft:air");boolean applies=NativeCopifierClipboard.hasCopy(tool)&&plan.applicable();if(applies==empty)throw new AssertionError("Canopy recipe applicability kind="+kind+" case="+count+" values="+values);if(applies){var wanted=NativeCopifierCanopyEdit.capture(kind,NativeCanopySettings.read(expected.getCompound("tag")));var actual=NativeCopifierCanopyEdit.capture(kind,plan.settings());if(!wanted.equals(actual))throw new AssertionError("Canopy recipe choices kind="+kind+" case="+count+" expected="+wanted+" actual="+actual);}if(!root.equals(original)||!values.equals(copy))throw new AssertionError("Canopy item caller changed");count++;}
        if(count!=2112)throw new AssertionError("Canopy Source coverage="+count);System.out.println("Canopy copied item plan PASS Source="+count+" eleven kinds/outer precedence/typed partial choices/masks; actual item/world dispatch pending");
    }
}
