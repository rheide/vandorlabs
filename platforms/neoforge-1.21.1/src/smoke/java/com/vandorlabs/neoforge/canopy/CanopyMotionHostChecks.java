package com.vandorlabs.neoforge.canopy;

import com.google.gson.*;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;

/** Actual authoritative ticks with an explicit no-ramp private-world admission adapter. */
final class CanopyMotionHostChecks {
    private static long checks;private static String context;private static final BlockPos ROOT=new BlockPos(8,80,8);
    private static void need(boolean yes,String text){checks++;if(!yes)throw new AssertionError("Canopy motion "+context+" "+text);}
    private static BlockPos point(JsonArray p){return new BlockPos(p.get(0).getAsInt(),p.get(1).getAsInt(),p.get(2).getAsInt());}
    private static void compare(ServerLevel world,NativeCanopyEntity root,JsonObject frame)throws Exception{
        var expected=CanopySettingsChecks.tags(frame.getAsJsonObject("saved"));var actual=root.saveWithoutMetadata(world.registryAccess());for(String key:List.of("Open","Progress","CanopyWaitingForReservations","CanopyPowerKnown","CanopyLastPower","CanopyLastPolarity","CanopyReleasedHelpers"))need(Objects.equals(expected.get(key),actual.get(key)),"authoritative "+key+" expected="+expected.get(key)+" actual="+actual.get(key));
        var cells=NativeCanopyFootprint.cells(root.kind(),ROOT,root.facing(),root.settings(),root.links());var rows=frame.getAsJsonArray("cells");need(cells.size()==rows.size(),"footprint count");for(int i=0;i<cells.size();i++){var wanted=rows.get(i).getAsJsonObject();var cell=cells.get(i);need(cell.equals(point(wanted.getAsJsonArray("pos"))),"footprint order");need(root.links().released().contains(cells.indexOf(cell))==wanted.get("released").getAsBoolean(),"released helper");var block=world.getBlockState(cell).getBlock();String id=block instanceof NativeCanopyBlock canopy?"vandorlabs:"+canopy.kind():BuiltInRegistries.BLOCK.getKey(block).toString();need(id.equals(wanted.get("id").getAsString()),"physical member expected="+wanted.get("id")+" actual="+id);}
    }
    static void run(ServerLevel world)throws Exception{
        for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++)world.getChunk(x,z);for(var cell:BlockPos.betweenClosed(ROOT.offset(-12,-12,-12),ROOT.offset(12,12,12)))world.setBlock(cell,Blocks.AIR.defaultBlockState(),18);
        var installed=new HashSet<BlockPos>();int profiles=0,ticks=0;
        var access=new NativeCanopyControl.Access(){@Override public boolean write(NativeCanopyEntity owner,BlockPos cell,net.minecraft.world.level.block.state.BlockState state){installed.add(cell);return NativeCanopyControl.Access.super.write(owner,cell,state);}@Override public NativeCanopyControl.Close prepareClose(NativeCanopyEntity owner){if(System.getProperty("vandorlabs.transmitterPhase","").contains("real-motion"))return NativeCanopyCloseService.prepare(owner);return NativeCanopyReservations.restoreFootprint(owner,this)?NativeCanopyControl.Close.READY:NativeCanopyControl.Close.REFUSED;}};
        try(var reader=java.nio.file.Files.newBufferedReader(java.nio.file.Path.of(System.getenv("VANDOR_CANOPY_MOTION_REFERENCE")))){for(String line;(line=reader.readLine())!=null;){for(var cell:installed)world.setBlock(cell,Blocks.AIR.defaultBlockState(),18);installed.clear();var row=JsonParser.parseString(line).getAsJsonObject();String kind=row.get("id").getAsString();var facing=Direction.from3DDataValue(row.get("facing").getAsInt());context=kind+"/"+profiles;
            for(var raw:row.getAsJsonArray("members")){var member=raw.getAsJsonObject();var cell=point(member.getAsJsonArray("pos"));installed.add(cell);CanopyHostChecks.put(world,cell,kind,facing,CanopySettingsChecks.tags(member.getAsJsonObject("saved")));}
            var root=NativeCanopyLifecycle.at(world,ROOT);if(System.getProperty("vandorlabs.transmitterPhase","").contains("real-motion"))for(var member:NativeCanopyLifecycle.members(root))installed.addAll(NativeCanopyFootprint.cells(member.kind(),member.getBlockPos(),member.facing(),member.settings(),member.links()));compare(world,root,row.getAsJsonObject("initial"));BlockPos foreign=row.has("foreign")?point(row.getAsJsonArray("foreign")):null;
            for(var raw:row.getAsJsonArray("ticks")){var frame=raw.getAsJsonObject();int tick=frame.get("tick").getAsInt(),action=frame.get("action").getAsInt();context=kind+"/"+profiles+"/"+tick;if(row.get("pattern").getAsInt()==3){if(tick==0&&foreign!=null)world.setBlock(foreign,Blocks.STONE.defaultBlockState(),18);if(tick==8){if(foreign!=null)world.setBlock(foreign,Blocks.AIR.defaultBlockState(),18);NativeCanopyControl.retryClose(root);}}if(action==1)NativeCanopyControl.toggle(root);if(action==2)root.setChannelLevel(15);if(action==3)root.setChannelLevel(0);
                var all=new ArrayList<NativeCanopyEntity>();for(var cell:installed){var member=NativeCanopyLifecycle.at(world,cell);if(member!=null)all.add(member);}for(var member:all)NativeCanopyControl.tick(member,access);compare(world,root,frame);ticks++;
            }profiles++;
        }}for(var cell:installed)world.setBlock(cell,Blocks.AIR.defaultBlockState(),18);need(profiles==496&&ticks==19840,"actual Source coverage");
        // A pending asynchronous close is a held accepted target, never a permission to move closed.
        var tag=new CompoundTag();tag.putFloat("Progress",1);tag.putBoolean("Open",true);var held=CanopyHostChecks.put(world,ROOT,"canopy",Direction.SOUTH,tag);NativeCanopyControl.toggle(held);NativeCanopyControl.tick(held,new NativeCanopyControl.Access(){@Override public NativeCanopyControl.Close prepareClose(NativeCanopyEntity owner){return NativeCanopyControl.Close.PENDING;}});need(held.links().waiting()&&!held.links().open()&&held.links().progress()==1,"pending holds closed target at open pose");NativeCanopyControl.toggle(held);NativeCanopyControl.tick(held,access);need(!held.links().waiting()&&held.links().open()&&held.links().progress()==1,"opening clears reservation wait");world.setBlock(ROOT,Blocks.AIR.defaultBlockState(),18);
        System.out.println("[vandorlabs][canopy-motion-host] PASS profiles="+profiles+" ticks="+ticks+" checks="+checks);
    }
}
