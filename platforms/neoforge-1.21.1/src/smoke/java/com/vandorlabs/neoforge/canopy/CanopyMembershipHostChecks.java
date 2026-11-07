package com.vandorlabs.neoforge.canopy;

import com.google.gson.*;
import java.nio.file.*;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;

/** Replays independently frozen released repair callbacks over actual installed native cells. */
final class CanopyMembershipHostChecks {
    private static int checks;private static String context;
    private static void need(boolean value,String why){checks++;if(!value)throw new AssertionError("Canopy membership "+context+" "+why);}
    private static BlockPos pos(JsonArray p,BlockPos offset){return new BlockPos(p.get(0).getAsInt(),p.get(1).getAsInt(),p.get(2).getAsInt()).offset(offset);}
    private static boolean cold(JsonObject row,BlockPos p,BlockPos offset){int x=(p.getX()-offset.getX())>>4,z=(p.getZ()-offset.getZ())>>4;for(var raw:row.getAsJsonArray("cold")){var chunk=raw.getAsJsonArray();if(chunk.get(0).getAsInt()==x&&chunk.get(1).getAsInt()==z)return true;}return false;}
    private static void clear(ServerLevel world,Set<BlockPos> cells){for(var p:cells){var host=NativeCanopyLifecycle.at(world,p);if(host!=null)host.removing(true);}for(var p:cells)world.setBlock(p,Blocks.AIR.defaultBlockState(),18);cells.clear();}
    private static void retained(ServerLevel world){
        context="native-retained";var root=new BlockPos(20000,100,20000);var helper=root.south();var tag=new CompoundTag();tag.putLong("AnchorOffset",0);tag.putIntArray("CanopyFootprint",new int[]{0,0,0,0,0,1});tag.putString("PrivateMembership","opaque");
        var host=CanopyHostChecks.put(world,root,"canopy",Direction.NORTH,tag);var bad=tag.copy();bad.putInt(NativeCanopyEntity.MOTION_REVISION,7);var child=CanopyHostChecks.put(world,helper,"canopy",Direction.NORTH,bad);child.configurationFootprint(List.of(helper));
        try{need(child.inert()&&!NativeCanopyMembership.repair(host)&&NativeCanopyLifecycle.at(world,root)==host,"inert helper cannot establish destructive missing ownership");host.loadWithComponents(bad,world.registryAccess());var owned=new CompoundTag();owned.putLong("AnchorOffset",com.vandorlabs.persistence.LegacyBlockPosition.pack(0,0,-1));child.loadWithComponents(owned,world.registryAccess());need(!NativeCanopyMembership.repair(child)&&NativeCanopyLifecycle.at(world,helper)==child,"inert parent retains exact helper");need(host.recoverySnapshot().equals(bad),"opaque failed parent remains exact");}
        finally{host.removing(true);child.removing(true);world.setBlock(root,Blocks.AIR.defaultBlockState(),18);world.setBlock(helper,Blocks.AIR.defaultBlockState(),18);}
    }
    static void run(ServerLevel world)throws Exception{
        var path=Path.of(System.getenv("VANDOR_CANOPY_REPAIR_REFERENCE"));need(java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path))).equals("26ebbc98d19510a231adf6c196bc9908f803d8ed91e186d571e7875aeb187995"),"pinned released corpus");var rows=new ArrayList<JsonObject>();for(String line:Files.readAllLines(path))rows.add(JsonParser.parseString(line).getAsJsonObject());
        Set<BlockPos> installed=new LinkedHashSet<>();int profiles=0,coldRows=0,legacy=0;
        try{for(var row:rows){clear(world,installed);boolean hasCold=!row.getAsJsonArray("cold").isEmpty();var offset=hasCold?new BlockPos(100000+128*coldRows++,0,100000):new BlockPos(10000,0,10000);context=row.get("id").getAsString()+"/"+row.get("scenario")+"/"+row.get("facing");
            for(var raw:row.getAsJsonArray("before")){var cell=raw.getAsJsonObject();var p=pos(cell.getAsJsonArray("pos"),offset);if(cold(row,p,offset))continue;String id=cell.get("block").getAsString();world.getChunkAt(p);if(id.startsWith("vandorlabs:")){var block=CanopyHostChecks.block(id.substring("vandorlabs:".length()));need(block!=null,"known released kind");world.setBlock(p,block.defaultBlockState().setValue(NativeCanopyBlock.FACING,Direction.from2DDataValue(cell.get("meta").getAsInt())),18);if(cell.has("saved")){var host=NativeCanopyLifecycle.at(world,p);need(host!=null,"actual factory");host.loadWithComponents(TagParser.parseTag(cell.get("saved").getAsString()),world.registryAccess());}else world.removeBlockEntity(p);}else {need(id.equals("minecraft:stone"),"known foreign state="+id);world.setBlock(p,Blocks.STONE.defaultBlockState(),18);}installed.add(p);}
            for(var raw:row.getAsJsonArray("cold")){var c=raw.getAsJsonArray();need(world.getChunkSource().getChunkNow(c.get(0).getAsInt()+(offset.getX()>>4),c.get(1).getAsInt()+(offset.getZ()>>4))==null,"actual cold chunk before repair");}
            var target=NativeCanopyLifecycle.at(world,pos(row.getAsJsonArray("target"),offset));need(target!=null,"installed selected callback owner");boolean actual=NativeCanopyMembership.repair(target);boolean exception=row.get("legacyAbsentRoot").getAsBoolean();need(actual==(exception?false:row.get("returned").getAsBoolean()),"callback return="+actual);if(exception)legacy++;
            var expected=new HashMap<BlockPos,JsonObject>();for(var raw:row.getAsJsonArray(exception?"before":"after")){var cell=raw.getAsJsonObject();expected.put(pos(cell.getAsJsonArray("pos"),offset),cell);}
            for(var p:installed){var cell=expected.get(p);String id=cell==null?"minecraft:air":cell.get("block").getAsString();var state=world.getBlockState(p);if(id.startsWith("vandorlabs:")){need(state.is(CanopyHostChecks.block(id.substring("vandorlabs:".length())))&&state.getValue(NativeCanopyBlock.FACING)==Direction.from2DDataValue(cell.get("meta").getAsInt()),"exact surviving state="+p);var host=NativeCanopyLifecycle.at(world,p);need((host!=null)==cell.has("saved"),"factory presence="+p);if(host!=null){var wanted=TagParser.parseTag(cell.get("saved").getAsString());var saved=host.saveWithoutMetadata(world.registryAccess());for(String key:new String[]{"AnchorOffset","PairOffset","CanopyWidth","Progress","Open","CanopyWaitingForReservations","CenteredPlacement","HollowPlacement","CanopyFootprint","CanopyReleasedHelpers"})need(key.equals("CanopyWidth")&&hasCold?wanted.getInt(key)==host.links().joinedWidth():Objects.equals(wanted.get(key),saved.get(key)),"saved ownership "+key+" pos="+p+" expected="+wanted.get(key)+" actual="+saved.get(key));}}else need(id.equals("minecraft:stone")?state.is(Blocks.STONE):state.isAir(),"exact foreign/removed state="+p+" expected="+id+" actual="+state);}
            for(var raw:row.getAsJsonArray("cold")){var c=raw.getAsJsonArray();need(world.getChunkSource().getChunkNow(c.get(0).getAsInt()+(offset.getX()>>4),c.get(1).getAsInt()+(offset.getZ()>>4))==null,"repair never requests cold chunk");}profiles++;
        }}finally{clear(world,installed);}need(profiles==520&&coldRows==68&&legacy==80,"complete released coverage");retained(world);System.out.println("[vandorlabs][canopy-host] membership PASS profiles="+profiles+" cold="+coldRows+" legacy="+legacy+" checks="+checks);
    }
}
