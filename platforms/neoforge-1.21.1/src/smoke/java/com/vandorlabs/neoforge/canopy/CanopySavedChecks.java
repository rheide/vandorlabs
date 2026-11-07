package com.vandorlabs.neoforge.canopy;

import com.google.gson.*;
import java.nio.file.*;
import java.util.List;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;

/** Actual Source read/save records include absent flags and malformed typed link fields. */
public final class CanopySavedChecks {
    private static long checks;
    private static final BlockPos ROOT=new BlockPos(-9,80,17);
    private static void need(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    private static void pos(JsonElement expected,BlockPos actual){if(expected==null||expected.isJsonNull()){need(actual==null,"Source absent position");return;}var p=expected.getAsJsonArray();need(actual!=null&&p.get(0).getAsInt()==actual.getX()&&p.get(1).getAsInt()==actual.getY()&&p.get(2).getAsInt()==actual.getZ(),"Source decoded position");}
    private static void positions(JsonArray expected,List<BlockPos> actual){need(expected.size()==actual.size(),"Source footprint size");for(int i=0;i<actual.size();i++)pos(expected.get(i),actual.get(i));}
    public static void main(String[] args)throws Exception {
        int profiles=0;try(var reader=Files.newBufferedReader(Path.of(args[0]))){for(String line;(line=reader.readLine())!=null;){var row=JsonParser.parseString(line).getAsJsonObject();String kind=row.get("id").getAsString();CompoundTag input=CanopySettingsChecks.tags(row.getAsJsonObject("input"));var settings=NativeCanopySettings.read(input);var links=NativeCanopyLinks.read(input,ROOT);int width=NativeCanopyModels.builtin().dimensions(kind).width()*(links.pair()==null?1:links.joinedWidth());pos(row.get("anchor"),links.anchor());pos(row.get("pair"),links.pair());need(row.get("width").getAsInt()==width,"Source decoded joined width");need(row.get("centered").getAsBoolean()==links.centered()&&row.get("hollow").getAsBoolean()==links.hollow()&&row.get("savedFootprint").getAsBoolean()==links.savedFootprint(),"Source placement absence/footprint");need(row.get("progress").getAsInt()==Float.floatToRawIntBits(links.progress())&&row.get("previous").getAsInt()==Float.floatToRawIntBits(links.progress()),"Source finite float progress");need(row.get("open").getAsBoolean()==links.open()&&row.get("waiting").getAsBoolean()==links.waiting(),"Source motion flags");var cells=NativeCanopyFootprint.cells(kind,ROOT,Direction.NORTH,settings,links);positions(row.getAsJsonArray("cells"),cells);positions(row.getAsJsonArray("reserved"),NativeCanopyFootprint.reserved(cells,links.released()));CompoundTag saved=new CompoundTag();saved.putString("id","vandorlabs:canopy");saved.putInt("x",ROOT.getX());saved.putInt("y",ROOT.getY());saved.putInt("z",ROOT.getZ());settings.write(saved);links.write(saved,ROOT,0,0,width);need(saved.equals(CanopySettingsChecks.tags(row.getAsJsonObject("saved"))),"Source saved typed links profile="+profiles+" kind="+kind+" input="+input);profiles++;}}
        need(profiles==1668,"Source saved coverage");System.out.println("[vandorlabs][canopy-saved] PASS SourceProfiles="+profiles+" exactFields="+checks+" legacyCoordinates/absence/footprints/motion");
    }
}
