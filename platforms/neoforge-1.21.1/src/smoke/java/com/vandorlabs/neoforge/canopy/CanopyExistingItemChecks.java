package com.vandorlabs.neoforge.canopy;

import com.google.gson.*;
import com.vandorlabs.canopy.CanopyPlacementPolicy;
import com.vandorlabs.persistence.CanopySettingsData;
import java.nio.file.*;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.nbt.*;

/** Scalar plan plus full native footprint against independently frozen specialized item callbacks. */
public final class CanopyExistingItemChecks {
    private static long checks;private static String context;
    private static void need(boolean value,String message){checks++;if(!value)throw new AssertionError("Existing canopy "+context+" "+message);}
    private static BlockPos point(JsonArray p){return new BlockPos(p.get(0).getAsInt(),p.get(1).getAsInt(),p.get(2).getAsInt());}
    public static void main(String[] args)throws Exception {
        int profiles=0,successes=0;try(var reader=Files.newBufferedReader(Path.of(args[0]))){for(String line;(line=reader.readLine())!=null;){var row=JsonParser.parseString(line).getAsJsonObject();String kind=row.get("id").getAsString();int face=row.get("facing").getAsInt(),side=row.get("side").getAsInt();context=kind+"/"+face+"/"+side+"/"+row.get("mount")+"/"+row.get("helper")+"/"+row.get("profile");var h=row.getAsJsonArray("hit");var cell=CanopyPlacementPolicy.existingCell(8,80,8,side,Float.intBitsToFloat(h.get(0).getAsInt()),Float.intBitsToFloat(h.get(1).getAsInt()),Float.intBitsToFloat(h.get(2).getAsInt()));var occupied=new HashSet<BlockPos>();for(var p:row.getAsJsonArray("occupied"))occupied.add(point(p.getAsJsonArray()));
            var plan=CanopyPlacementPolicy.existing(kind,row.get("width").getAsInt(),row.get("length").getAsInt(),row.get("height").getAsInt(),face,row.get("mount").getAsInt(),side,!occupied.contains(new BlockPos(cell.x,cell.y,cell.z)));var pos=new BlockPos(cell.x+plan.x,cell.y+plan.y,cell.z+plan.z);var raw=row.get("input");var tag=raw==null||raw.isJsonNull()?new CompoundTag():CanopySettingsChecks.tags(raw.getAsJsonObject());tag.putInt("CanopyMount",plan.mount);var settings=NativeCanopySettings.read(tag);var linksTag=new CompoundTag();linksTag.putBoolean("CenteredPlacement",CanopySettingsData.supports(kind)||kind.startsWith("space_visor_"));linksTag.putBoolean("HollowPlacement",CanopySettingsData.supports(kind));var links=NativeCanopyLinks.read(linksTag,pos);var cells=NativeCanopyFootprint.cells(kind,pos,Direction.from3DDataValue(plan.facing),settings,links);boolean fits=Collections.disjoint(cells,occupied);boolean success=row.get("result").getAsString().equals("SUCCESS");need(fits==success,"full admission from existing owned cells");need(row.get("remaining").getAsInt()==(fits?1:2),"held count");
            if(success){var expected=row.getAsJsonObject("placed");need(pos.equals(point(expected.getAsJsonArray("pos"))),"absolute anchor expected="+expected.get("pos")+" actual="+pos);need(plan.facing==expected.get("facing").getAsInt(),"owner facing priority");var actual=new CompoundTag();settings.write(actual);need(actual.equals(CanopySettingsChecks.tags(expected.getAsJsonObject("settings"))),"inherited mount and fresh configured settings");var expectedCells=expected.getAsJsonArray("cells");need(cells.size()==expectedCells.size(),"footprint size");for(int i=0;i<cells.size();i++)need(cells.get(i).equals(point(expectedCells.get(i).getAsJsonArray())),"footprint order index="+i);successes++;}profiles++;
        }}need(profiles==8448&&successes==5580,"Source callback coverage");System.out.println("[vandorlabs][canopy-existing-item] PASS profiles="+profiles+" successes="+successes+" checks="+checks);
    }
}
