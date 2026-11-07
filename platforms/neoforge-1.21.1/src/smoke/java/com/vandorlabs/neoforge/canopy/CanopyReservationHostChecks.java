package com.vandorlabs.neoforge.canopy;

import com.google.gson.*;
import java.nio.file.*;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;

/** Source helper transactions in actual registered hosts, with isolated write/factory/cold-view seams. */
final class CanopyReservationHostChecks {
    private static int checks,profiles,released,refused,faults;private static String context;
    private static void need(boolean value,String text){checks++;if(!value)throw new AssertionError("Canopy reservation "+context+" "+text);}
    private static BlockPos point(JsonArray p){return new BlockPos(p.get(0).getAsInt(),p.get(1).getAsInt(),p.get(2).getAsInt());}
    private static String id(BlockState state){return state.getBlock() instanceof NativeCanopyBlock canopy?"vandorlabs:"+canopy.kind():BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();}
    private static void snapshot(ServerLevel world,NativeCanopyEntity root,JsonObject expected)throws Exception {
        need(root.links().progress()==expected.get("progress").getAsFloat()&&root.links().open()==expected.get("target").getAsBoolean()&&root.links().waiting()==expected.get("waiting").getAsBoolean(),"motion facts");var cells=NativeCanopyFootprint.cells(root.kind(),root.getBlockPos(),root.facing(),root.settings(),root.links());var wanted=expected.getAsJsonArray("cells");need(cells.size()==wanted.size(),"footprint size");
        for(int i=0;i<cells.size();i++){var row=wanted.get(i).getAsJsonObject();var cell=cells.get(i);need(cell.equals(point(row.getAsJsonArray("pos"))),"footprint order");need(root.links().released().contains(cells.indexOf(cell))==row.get("released").getAsBoolean(),"release flag index="+i);need(id(world.getBlockState(cell)).equals(row.get("id").getAsString()),"physical block index="+i);var tile=NativeCanopyLifecycle.at(world,cell);var anchor=tile==null?null:tile.links().anchor();var expectedAnchor=row.get("anchor");need(expectedAnchor==null||expectedAnchor.isJsonNull()?anchor==null:point(expectedAnchor.getAsJsonArray()).equals(anchor),"helper owner index="+i);}
        var actual=root.saveWithoutMetadata(world.registryAccess());actual.remove("NativeCanopySchema");var saved=CanopySettingsChecks.tags(expected.getAsJsonObject("saved"));need(actual.equals(saved),"typed saved snapshot expected="+saved+" actual="+actual);
    }
    private static final class Faults implements NativeCanopyReservations.Access {
        final ServerLevel world;final int variant;boolean restoring,cold;int writes;
        Faults(ServerLevel world,int variant){this.world=world;this.variant=variant;}
        @Override public boolean loaded(NativeCanopyEntity owner,BlockPos pos){return NativeCanopyReservations.Access.super.loaded(owner,pos)&&(!cold||(pos.getX()>>4)==0&&(pos.getZ()>>4)==0);}
        @Override public boolean write(NativeCanopyEntity owner,BlockPos pos,BlockState state){boolean helper=restoring&&state.getBlock() instanceof NativeCanopyBlock;if(helper&&++writes==2&&variant==5)return false;boolean changed=NativeCanopyReservations.Access.super.write(owner,pos,state);if(helper&&writes==2&&variant==6)world.removeBlockEntity(pos);return changed;}
    }
    static void run(ServerLevel world)throws Exception {
        Set<BlockPos> occupied=new HashSet<>();try(var reader=Files.newBufferedReader(Path.of(System.getenv("VANDOR_CANOPY_RESERVATION_REFERENCE")))){for(String line;(line=reader.readLine())!=null;){
            for(var cell:occupied)world.setBlock(cell,Blocks.AIR.defaultBlockState(),18);occupied.clear();var row=JsonParser.parseString(line).getAsJsonObject();String kind=row.get("id").getAsString();int variant=row.get("variant").getAsInt();context=kind+"/"+row.get("facing")+"/"+row.get("motion")+"/"+row.get("slide")+"/"+variant;
            var open=row.getAsJsonObject("open");var tag=CanopySettingsChecks.tags(open.getAsJsonObject("saved"));var pos=new BlockPos(tag.getInt("x"),tag.getInt("y"),tag.getInt("z"));var facing=Direction.from3DDataValue(row.get("facing").getAsInt());tag.putIntArray("CanopyReleasedHelpers",new int[0]);var root=CanopyHostChecks.put(world,pos,kind,facing,tag);var cells=NativeCanopyFootprint.cells(kind,pos,facing,root.settings(),root.links());
            for(var cell:cells){world.getChunkAt(cell);occupied.add(cell);if(cell.equals(pos))continue;var helper=CanopyHostChecks.put(world,cell,kind,facing,new CompoundTag());helper.helperOf(root);}
            var access=new Faults(world,variant);NativeCanopyReservations.releaseOpen(root,access);snapshot(world,root,open);var free=new ArrayList<BlockPos>();for(int i=0;i<cells.size();i++)if(root.links().released().contains(i))free.add(cells.get(i));released+=free.size();
            if(!free.isEmpty()){var cell=free.get(0);if(variant>=1&&variant<=3)world.setBlock(cell,(variant==1?Blocks.STONE:variant==2?Blocks.WATER:Blocks.LAVA).defaultBlockState(),18);if(variant==4){var foreign=new CompoundTag();foreign.putLong("Anchor",com.vandorlabs.persistence.LegacyBlockPosition.pack(cell.getX(),cell.getY(),cell.getZ()));CanopyHostChecks.put(world,cell,kind,facing,foreign);}if(variant==5||variant==6){access.restoring=true;faults++;}}
            root.stageLinks(root.links().withMotion(1,false,false));access.cold=row.get("cold").getAsBoolean();boolean ready=NativeCanopyReservations.restoreFootprint(root,access);access.cold=false;need(ready==row.get("ready").getAsBoolean(),"close helper admission");need(access.writes==row.get("writes").getAsInt(),"actual helper writes");snapshot(world,root,row.getAsJsonObject("close"));if(!ready)refused++;
            access.restoring=false;for(var cell:free){var tile=NativeCanopyLifecycle.at(world,cell);if(!world.getBlockState(cell).is(root.getBlockState().getBlock())||tile==null||!root.getBlockPos().equals(tile.links().anchor()))world.setBlock(cell,Blocks.AIR.defaultBlockState(),18);}need(NativeCanopyReservations.restoreFootprint(root,access)==row.get("retry").getAsBoolean(),"restoration retry");snapshot(world,root,row.getAsJsonObject("restored"));profiles++;
        }}for(var cell:occupied)world.setBlock(cell,Blocks.AIR.defaultBlockState(),18);need(profiles==512&&released==6400&&refused==200&&faults==64,"Source corpus coverage profiles="+profiles+" released="+released+" refused="+refused+" faults="+faults);System.out.println("[vandorlabs][canopy-reservations] PASS profiles="+profiles+" released="+released+" refused="+refused+" faultEligible="+faults+" checks="+checks);
    }
}
