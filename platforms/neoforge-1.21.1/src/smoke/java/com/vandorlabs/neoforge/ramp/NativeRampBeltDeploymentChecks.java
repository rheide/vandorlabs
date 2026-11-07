package com.vandorlabs.neoforge.ramp;

import com.google.gson.*;
import com.vandorlabs.redstone.ChannelList;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.GZIPInputStream;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

/** Actual Source sampled geometry through the detached native body, not a replacement selector. */
@EventBusSubscriber(modid="vandorlabs")
public final class NativeRampBeltDeploymentChecks {
 private static NativeRampPartBlock block;private static BlockEntityType<NativeRampPartEntity> type;
 private static boolean enabled(){return System.getProperty("vandorlabs.transmitterPhase","").equals("ramp-belt-deployment");}
 @EventBusSubscriber(modid="vandorlabs",bus=EventBusSubscriber.Bus.MOD)public static final class Registration {@SubscribeEvent public static void register(net.neoforged.neoforge.registries.RegisterEvent event){if(!enabled())return;event.register(Registries.BLOCK,h->{block=new NativeRampPartBlock(BlockBehaviour.Properties.of(),()->type);h.register(ResourceLocation.parse("vandorlabs:ramp_belt_deployment_part"),block);});event.register(Registries.BLOCK_ENTITY_TYPE,h->{type=BlockEntityType.Builder.<NativeRampPartEntity>of((p,s)->new NativeRampPartEntity(type,p,s),block).build(null);h.register(ResourceLocation.parse("vandorlabs:ramp_belt_deployment_part"),type);});}}
 private static BlockPos pos(JsonArray a){return new BlockPos(a.get(0).getAsInt(),a.get(1).getAsInt(),a.get(2).getAsInt());}
 private static List<AABB> shapes(int material,double low,double high){return material==3?List.of(new AABB(0,0,0,1,.5,1),new AABB(0,.5,0,1,1,.5)):List.of(new AABB(0,low,0,1,high,1));}
 @SubscribeEvent public static void start(ServerStartedEvent event)throws Exception{if(!enabled())return;var level=event.getServer().overworld();int rows=0,parts=0,boxes=0,different=0;double maximum=0;
  var source=Path.of(System.getProperty("vandorlabs.rampBeltDeploymentReference"));try(var reader=new BufferedReader(new InputStreamReader(new GZIPInputStream(Files.newInputStream(source)),java.nio.charset.StandardCharsets.UTF_8))){String line;while((line=reader.readLine())!=null){var row=JsonParser.parseString(line).getAsJsonObject();int material=row.get("material").getAsInt(),length=row.get("length").getAsInt(),pixels=row.get("pixels").getAsInt(),interpolation=row.get("interpolation").getAsInt();boolean vertical=row.get("vertical").getAsBoolean(),moving=row.get("moving").getAsBoolean();double end=row.get("rise").getAsDouble(),distance=row.get("distance").getAsDouble(),low=material==2?.5:0,high=material==1?.5:1;var facing=Direction.from3DDataValue(row.get("facing").getAsInt());var state=material==0?Blocks.STONE.defaultBlockState():material==3?Blocks.COBBLESTONE_STAIRS.defaultBlockState():Blocks.STONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE,material==1?SlabType.BOTTOM:SlabType.TOP);var materialView=new NativeRampPartMaterial(state,-1,-1,-1,0,false,false,new int[]{-1,-1,-1,-1,-1,-1});
   var sources=new ArrayList<NativeRampCapture.Source>();for(var p:row.getAsJsonArray("origins"))sources.add(new NativeRampCapture.Source(pos(p.getAsJsonArray()),state,shapes(material,low,high)));var root=new BlockPos(4,80,4);int minimum=vertical?80:8*facing.getStepX()+8*facing.getStepZ(),direction=facing==Direction.SOUTH?0:facing==Direction.WEST?1:facing==Direction.NORTH?2:3;var settings=new NativeRampSettings(0,(int)end*2,pixels,true,false,false,1,true,interpolation,1,false,vertical,0,true,direction,ChannelList.EMPTY);var capture=new NativeRampCapture.Plan(sources,facing,minimum,length,80,low,high);var assembly=NativeRampAssembly.prepare(root,settings,capture,Map.of());var expectedCells=new HashSet<BlockPos>();for(var p:row.getAsJsonArray("footprint"))expectedCells.add(pos(p.getAsJsonArray()));var actualCells=assembly.cells(row.get("from").getAsDouble(),row.get("to").getAsDouble());if(!actualCells.equals(expectedCells))throw new AssertionError("Belt deployment reservation row="+rows+" moving="+moving+" expected="+expectedCells.size()+" actual="+actualCells.size());
   for(var element:row.getAsJsonArray("parts")){var part=element.getAsJsonObject();var tag=TagParser.parseTag(part.get("saved").getAsString());var cell=pos(part.getAsJsonArray("cell"));var origins=new ArrayList<NativeRampPartData.Origin>();var geometryOrigins=new ArrayList<NativeRampGeometry.Origin>();for(var entry:tag.getList("Origins",Tag.TAG_COMPOUND)){var p=(CompoundTag)entry;var origin=new NativeRampPartData.Origin(new BlockPos(p.getInt("X"),p.getInt("Y"),p.getInt("Z")),8,materialView);origins.add(origin);geometryOrigins.add(origin.geometry());}double pose=part.get("pose").getAsDouble();var spec=new NativeRampGeometry.Spec(cell,facing,tag.getInt("SourceY"),part.get("row").getAsInt(),length,pixels,low,high,0,end,false,0,false,false,interpolation,vertical,false,material==3,geometryOrigins);var motion=new NativeRampPartData.Motion(1,tag.getLong("StartTick"),tag.getDouble("StartPose"),1,tag.getInt("Duration"),moving,pose);var belt=new NativeRampPartData.Belt(1,1,distance,0,false,distance);var data=new NativeRampPartData(spec,state,origins,motion,belt);data=NativeRampPartData.read(data.write(),cell,level.registryAccess());var body=new NativeRampPartEntity(type,cell,block.defaultBlockState().setValue(NativeRampPartBlock.FACING,facing));var actual=body.preparePublication(data,1,100);var expected=part.getAsJsonArray("boxes");if(actual.geometry().pieces().size()!=expected.size())throw new AssertionError("Belt deployment body row="+rows+" moving="+moving+" pose="+pose+" expected="+expected.size()+" actual="+actual.geometry().pieces().size());
    for(int i=0;i<expected.size();i++){var piece=actual.geometry().pieces().get(i);var b=piece.box();var frame=actual.beltGeometry()==null?NativeRampTextureCoordinates.frame(spec,piece,pose):actual.beltGeometry().frames().get(i);double[] values={b.minX,b.minY,b.minZ,b.maxX,b.maxY,b.maxZ,frame.origin().getX(),frame.origin().getY(),frame.origin().getZ(),frame.uShift(),frame.vShift(),frame.bottom(),frame.top()};var reference=expected.get(i).getAsJsonArray();for(int k=0;k<values.length;k++){double old=reference.get(k).getAsDouble(),delta=Math.abs(old-values[k]);if(delta>1e-12)throw new AssertionError("Belt deployment field row="+rows+" piece="+i+" field="+k+" expected="+old+" actual="+values[k]);if(Double.doubleToLongBits(old)!=Double.doubleToLongBits(values[k]))different++;maximum=Math.max(maximum,delta);}boxes++;}parts++;
   }rows++;
  }}if(rows!=1344||parts!=16432||boxes!=60614)throw new AssertionError("Belt deployment coverage "+rows+"/"+parts+"/"+boxes);System.out.println("[vandorlabs][ramp-belt-deployment] PASS profiles="+rows+" parts="+parts+" boxes="+boxes+" differentComponents="+different+" maximumDelta="+maximum+" detachedHostAndCodec/sampleGeometry/noGameplayClaim");event.getServer().halt(false);
 }
}
