package com.vandorlabs.neoforge.copifier;

import com.google.gson.*;
import com.vandorlabs.neoforge.canopy.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.GZIPInputStream;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.*;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;

/** Independent released installed-world choices and physical footprints, including grouped owners. */
final class CopifierCanopyWorldChecks {
    private static int checks;
    private static void need(boolean ok,String why){checks++;if(!ok)throw new AssertionError("Copied Canopy world: "+why);}
    private static BlockPos pos(JsonObject row){return new BlockPos(row.get("x").getAsInt(),row.get("y").getAsInt(),row.get("z").getAsInt());}
    private static Map<BlockPos,NativeCanopyEntity> installed(ServerLevel world){var result=new HashMap<BlockPos,NativeCanopyEntity>();for(int x=-1;x<=2;x++)for(int z=-1;z<=2;z++){var chunk=world.getChunkSource().getChunkNow(x,z);if(chunk!=null)for(var tile:chunk.getBlockEntities().values())if(tile instanceof NativeCanopyEntity host)result.put(host.getBlockPos(),host);}return result;}
    private static void matches(ServerLevel world,JsonArray expected,String context)throws Exception{
        var actual=installed(world);need(actual.size()==expected.size(),context+" physical footprint expected="+expected.size()+" actual="+actual.size());for(var element:expected){var row=element.getAsJsonObject();var pos=pos(row);need(actual.containsKey(pos),context+" physical member="+pos);var target=NativeCopifierHousingWorld.installed(world,pos);need(target!=null&&TagParser.parseTag(row.get("choices").getAsString()).equals(target.capture()),context+" Source choices="+pos+" expected="+row.get("choices")+" actual="+(target==null?null:target.capture()));}
    }
    static void run(ServerLevel world)throws Exception{
        JsonArray rows;var reference=Path.of(System.getProperty("vandorlabs.copifierCanopyReference"));try(var in=new GZIPInputStream(Files.newInputStream(reference))){rows=JsonParser.parseString(new String(in.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8)).getAsJsonArray();}
        var actor=new net.neoforged.neoforge.common.util.FakePlayer(world,new com.mojang.authlib.GameProfile(new UUID(0,1904),"CopiedCanopyWorld"));actor.setGameMode(GameType.SURVIVAL);int count=0;
        for(var element:rows){
            var row=element.getAsJsonObject();for(var old:new ArrayList<>(installed(world).keySet()))world.setBlock(old,Blocks.AIR.defaultBlockState(),18);String kind=row.get("id").getAsString();var block=(NativeCanopyBlock)BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath("vandorlabs",kind));var state=block.defaultBlockState().setValue(NativeCanopyBlock.FACING,Direction.from3DDataValue(row.get("facing").getAsInt()));
            for(var cell:row.getAsJsonArray("before")){var data=cell.getAsJsonObject();var pos=pos(data);world.getChunkAt(pos);world.setBlock(pos,state,18);}
            for(var cell:row.getAsJsonArray("before")){var data=cell.getAsJsonObject();var owner=NativeCanopyLifecycle.at(world,pos(data));owner.loadWithComponents(TagParser.parseTag(data.get("saved").getAsString()),world.registryAccess());}
            for(var owner:installed(world).values())NativeCanopyLifecycle.prepare(owner);matches(world,row.getAsJsonArray("before"),"before="+count+" "+kind);
            var selected=pos(row);actor.setPos(selected.getX()+.5,selected.getY(),selected.getZ()-2);var values=TagParser.parseTag(row.get("values").getAsString());NativeCopifierUndo.begin(actor,world);boolean applied;try{applied=NativeCopifierHousingWorld.applySingle(world,selected,actor,Direction.NORTH,values);}finally{NativeCopifierUndo.finish(actor);}need(applied==row.get("applied").getAsBoolean(),"Source result="+count+" "+kind);matches(world,row.getAsJsonArray("after"),"after="+count+" "+kind);
            if(applied){NativeCopifierUndo.undo(actor);matches(world,row.getAsJsonArray("undo"),"Undo="+count+" "+kind);}count++;
        }
        need(count==176,"all Source cases");System.out.println("[vandorlabs][copifier-canopy-world] PASS Source="+count+" checks="+checks+" all11roles/facings/joined-owner/partial choices/physical resized footprints/Undo");
    }
}
