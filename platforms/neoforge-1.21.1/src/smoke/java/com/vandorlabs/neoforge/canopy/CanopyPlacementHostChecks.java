package com.vandorlabs.neoforge.canopy;

import com.google.gson.*;
import com.vandorlabs.canopy.CanopyPlacementPolicy;
import java.nio.file.*;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;

/** Actual registered candidate preparation; no world writes or configured-item consumption yet. */
final class CanopyPlacementHostChecks {
    private static long checks;private static String context;
    private static void need(boolean value,String text){checks++;if(!value)throw new AssertionError("Canopy placement preparation "+context+" "+text);}
    private static BlockPos point(JsonArray p){return new BlockPos(p.get(0).getAsInt(),p.get(1).getAsInt(),p.get(2).getAsInt());}
    static void run(ServerLevel world)throws Exception {
        int profiles=0,successes=0,unsupported=0;int loaded=world.getChunkSource().getLoadedChunksCount();
        try(var reader=Files.newBufferedReader(Path.of(System.getenv("VANDOR_CANOPY_CONFIGURED_REFERENCE")))){for(String line;(line=reader.readLine())!=null;){var row=JsonParser.parseString(line).getAsJsonObject();String kind=row.get("id").getAsString();int profile=row.get("profile").getAsInt(),side=row.get("side").getAsInt();context=kind+"/"+row.get("playerFacing")+"/"+side+"/"+profile;var block=CanopyHostChecks.block(kind);var size=NativeCanopyModels.builtin().dimensions(kind);var plan=CanopyPlacementPolicy.initial(kind,size.width(),size.length(),size.height(),row.get("playerFacing").getAsInt(),side,false);var pos=new BlockPos(8+plan.x,80+plan.y,8+plan.z);var raw=row.get("input");var outer=raw==null||raw.isJsonNull()?new CompoundTag():CanopySettingsChecks.tags(raw.getAsJsonObject());var generic=outer.contains("BlockEntityTag",Tag.TAG_COMPOUND)?outer.getCompound("BlockEntityTag"):null;
            NativeCanopyPlacement.Prepared prepared;try{prepared=NativeCanopyPlacement.prepare(block,pos,block.defaultBlockState().setValue(NativeCanopyBlock.FACING,Direction.from3DDataValue(plan.facing)),generic,outer,plan.mount,world.registryAccess());}catch(IllegalArgumentException retained){need(profile==8&&size.width()*3>9,"only unsupported foreign-pair derived width quarantined");unsupported++;profiles++;continue;}
            BlockPos foreign=row.has("foreign")?point(row.getAsJsonArray("foreign")):null;boolean fits=!prepared.admission().contains(new BlockPos(8,80,8))&&(foreign==null||!prepared.admission().contains(foreign));boolean expected=row.get("result").getAsString().equals("SUCCESS");need(fits==expected,"corrected full merged admission");var saved=prepared.snapshot();need(saved.getInt("CanopyDepth")==prepared.snapshot().getInt("CanopyDepth"),"stable projection");var copy=prepared.snapshot();copy.putInt("CanopyDepth",0);need(prepared.snapshot().getInt("CanopyDepth")!=0,"defensive prepared NBT");
            if(expected){var source=CanopySettingsChecks.tags(row.getAsJsonObject("saved"));saved.remove("NativeCanopySchema");saved.putString("id","vandorlabs:canopy");if(profile==12){need(saved.getString("ForeignCanopyData").equals("opaque-original"),"valid unknown extra retained");saved.remove("ForeignCanopyData");}need(saved.equals(source),"actual Source typed own fields and placement precedence expected="+source+" actual="+saved);var cells=row.getAsJsonArray("cells");need(cells.size()==prepared.cells().size(),"actual merged footprint size");for(int i=0;i<cells.size();i++)need(prepared.cells().get(i).equals(point(cells.get(i).getAsJsonObject().getAsJsonArray("pos"))),"merged footprint order");successes++;}profiles++;
        }}need(profiles==2112&&unsupported>0,"Source configured coverage");need(world.getChunkSource().getLoadedChunksCount()==loaded,"candidate preparation never discovers/loads world chunks");System.out.println("[vandorlabs][canopy-placement-host] PASS profiles="+profiles+" supportedSuccesses="+successes+" quarantinedRole="+unsupported+" checks="+checks);
    }
}
