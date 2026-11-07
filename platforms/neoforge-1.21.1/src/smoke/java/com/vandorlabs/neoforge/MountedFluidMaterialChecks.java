package com.vandorlabs.neoforge;

import com.google.gson.*;
import java.nio.file.*;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

/** Audit only: records actual canonical material differences without changing family policy. */
@EventBusSubscriber(modid="vandorlabs")
public final class MountedFluidMaterialChecks {
 @SubscribeEvent public static void started(ServerStartedEvent event){if(!"mounted-fluid-material-audit".equals(System.getProperty("vandorlabs.transmitterPhase")))return;var server=event.getServer();try{
  JsonObject source;try(var input=Files.newBufferedReader(Path.of(System.getenv("VANDOR_MOUNTED_FLUID_MATERIAL_REFERENCE")))){source=JsonParser.parseReader(input).getAsJsonObject();}
  var expected=new LinkedHashMap<String,Boolean>();for(var entry:source.getAsJsonArray("cases")){var row=entry.getAsJsonObject();String id=row.get("id").getAsString();boolean flow=row.get("flow").getAsBoolean();if(row.get("bucket").getAsBoolean()!=flow||row.get("retained").getAsBoolean()==flow||expected.containsKey(id)&&expected.get(id)!=flow)throw new AssertionError("Source policy is not uniform: "+id);expected.put(id,flow);}
  var level=server.overworld();BlockPos pos=new BlockPos(7210,110,7210);level.getChunkAt(pos);var output=new JsonArray();int states=0,profiles=0,missing=0,differences=0;
  for(var entry:expected.entrySet()){var id=ResourceLocation.parse(entry.getKey());if(!BuiltInRegistries.BLOCK.containsKey(id)){missing++;continue;}Block block=BuiltInRegistries.BLOCK.get(id);int replacementDifferences=0;for(BlockState state:block.getStateDefinition().getPossibleStates())for(boolean lava:new boolean[]{false,true}){states++;if(state.canBeReplaced(lava?net.minecraft.world.level.material.Fluids.LAVA:net.minecraft.world.level.material.Fluids.WATER)!=entry.getValue())replacementDifferences++;}
   for(boolean lava:new boolean[]{false,true}){reset(level,pos);level.setBlock(pos,block.defaultBlockState(),3);var owner=level.getBlockEntity(pos);boolean replacement=level.getBlockState(pos).canBeReplaced(lava?net.minecraft.world.level.material.Fluids.LAVA:net.minecraft.world.level.material.Fluids.WATER);boolean bucket=((BucketItem)(lava?Items.LAVA_BUCKET:Items.WATER_BUCKET)).emptyContents(null,level,pos,null);boolean bucketRetained=level.getBlockState(pos).is(block)&&level.getBlockEntity(pos)==owner;
    reset(level,pos);level.setBlock(pos,block.defaultBlockState(),3);owner=level.getBlockEntity(pos);BlockPos above=pos.above();for(var side:Direction.Plane.HORIZONTAL)level.setBlock(above.relative(side),Blocks.STONE.defaultBlockState(),3);level.setBlock(above,(lava?Blocks.LAVA:Blocks.WATER).defaultBlockState(),3);level.getFluidState(above).tick(level,above);boolean flowed=!level.getBlockState(pos).is(block),flowRetained=level.getBlockState(pos).is(block)&&level.getBlockEntity(pos)==owner;
    var row=new JsonObject();row.addProperty("id",id.toString());row.addProperty("lava",lava);row.addProperty("sourceAllowsReplacement",entry.getValue());row.addProperty("replacement",replacement);row.addProperty("bucket",bucket);row.addProperty("bucketRetained",bucketRetained);row.addProperty("flowed",flowed);row.addProperty("flowRetained",flowRetained);row.addProperty("listedReplacementDifferences",replacementDifferences);output.add(row);profiles++;if(bucket!=entry.getValue()||flowed!=entry.getValue()||bucketRetained==entry.getValue()||flowRetained==entry.getValue()||replacementDifferences>0)differences++;reset(level,pos);
   }
  }
  var result=new JsonObject();result.addProperty("sourceCases",source.getAsJsonArray("cases").size());result.addProperty("sourceGolden",source.get("golden").getAsString());result.addProperty("nativeStates",states);result.addProperty("profiles",profiles);result.addProperty("missing",missing);result.addProperty("differences",differences);result.add("cases",output);Path path=Path.of(System.getenv("VANDOR_MOUNTED_FLUID_MATERIAL_OUTPUT"));try(var writer=Files.newBufferedWriter(path)){new Gson().toJson(result,writer);}System.out.println("[vandorlabs][fluid-material-audit] PASS states="+states+" profiles="+profiles+" missing="+missing+" differences="+differences);
 }catch(Throwable failure){failure.printStackTrace();System.out.println("[vandorlabs][fluid-material-audit] FAIL "+failure);}finally{server.halt(false);}}
 private static void reset(ServerLevel level,BlockPos pos){for(int x=-1;x<=1;x++)for(int y=0;y<=2;y++)for(int z=-1;z<=1;z++)level.setBlock(pos.offset(x,y,z),Blocks.AIR.defaultBlockState(),3);for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++)level.setBlock(pos.offset(x,-1,z),Blocks.STONE.defaultBlockState(),3);level.setBlock(pos.south(),Blocks.STONE.defaultBlockState(),3);}
}
