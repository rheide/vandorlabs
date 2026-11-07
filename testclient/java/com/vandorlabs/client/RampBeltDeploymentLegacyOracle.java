package com.vandorlabs.client;

import com.google.gson.*;
import com.vandorlabs.blocks.*;
import com.vandorlabs.tiles.*;
import com.vandorlabs.ramp.*;
import java.io.*;
import java.lang.reflect.*;
import java.util.*;
import net.minecraft.block.state.IBlockState;
import net.minecraft.block.BlockSlab;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.*;
import net.minecraft.util.math.BlockPos;

/** Actual belt deployment reservations and configurePart geometry/UV callbacks; no substituted geometry kernel. */
public final class RampBeltDeploymentLegacyOracle {
    private static final BlockPos ROOT=new BlockPos(4,80,4),BASE=new BlockPos(8,80,8);
    private static Field field(String name)throws Exception{Field f=TileEntityRampController.class.getDeclaredField(name);f.setAccessible(true);return f;}
    private static void set(TileEntityRampController tile,String name,Object value)throws Exception{field(name).set(tile,value);}
    private static JsonArray position(BlockPos p){JsonArray a=new JsonArray();a.add(p.getX());a.add(p.getY());a.add(p.getZ());return a;}
    private static List<BlockPos> sorted(Collection<BlockPos> values){List<BlockPos> list=new ArrayList<>(values);list.sort(Comparator.comparingInt(BlockPos::getX).thenComparingInt(BlockPos::getY).thenComparingInt(BlockPos::getZ));return list;}
    @SuppressWarnings("unchecked") public static void main(String[] args)throws Exception{
        RampSettingsLegacyOracle.initialize();net.minecraftforge.fml.common.registry.ForgeRegistries.BLOCKS.register(ModBlocks.CONTROLLED_RAMP);net.minecraftforge.fml.common.registry.GameRegistry.registerTileEntity(TileEntityControlledRamp.class,new ResourceLocation("vandorlabs","controlled_ramp"));
        Method occupied=TileEntityRampController.class.getDeclaredMethod("occupied",double.class,double.class),configure=TileEntityRampController.class.getDeclaredMethod("configurePart",TileEntityControlledRamp.class,BlockPos.class),origin=TileEntityControlledRamp.class.getDeclaredMethod("originFor",RampGeometry.Box.class,double.class);occupied.setAccessible(true);configure.setAccessible(true);origin.setAccessible(true);
        File file=new File(args[0]);file.getAbsoluteFile().getParentFile().mkdirs();Gson gson=new Gson();int cases=0,parts=0,boxes=0,cells=0;NonRenderingChecks.MemoryWorld world=new NonRenderingChecks.MemoryWorld(false);
        try(PrintWriter out=new PrintWriter(file,"UTF-8")){
            for(EnumFacing facing:EnumFacing.HORIZONTALS)for(boolean vertical:new boolean[]{false,true})for(int material=0;material<4;material++)for(int profile=0;profile<7;profile++)for(int phaseIndex=0;phaseIndex<6;phaseIndex++){
                int length=new int[]{2,3,5,2,3,5,3}[profile],pixels=new int[]{1,4,8,16,8,4,3}[profile],interpolation=profile%3,rise=new int[]{2,-2,0,2,-2,2,1}[profile];double low=material==2?.5:0,high=material==1?.5:1;IBlockState state=material==0?Blocks.STONE.getDefaultState():material==3?Blocks.STONE_STAIRS.getDefaultState():Blocks.STONE_SLAB.getDefaultState().withProperty(BlockSlab.HALF,material==1?BlockSlab.EnumBlockHalf.BOTTOM:BlockSlab.EnumBlockHalf.TOP);
                boolean moving=phaseIndex<5;double requestedPhase=moving?phaseIndex/4D:1D,distance=0;int duration=ControllerPlatform.duration(length,Math.abs(rise),1);world.clear();world.time=100+(long)(requestedPhase*duration);world.setBlockState(ROOT,ModBlocks.PROGRAMMABLE_RAMP.getDefaultState().withProperty(BlockVandorDirectional.FACING,facing),2);TileEntityRampController tile=(TileEntityRampController)world.getTileEntity(ROOT);tile.treadPixels=pixels;tile.segments=ControllerPlatform.treadCount(pixels);tile.interpolation=interpolation;tile.escalator=1;tile.verticalLayout=vertical;
                set(tile,"facing",facing);set(tile,"length",length);set(tile,"minAlong",vertical?BASE.getY():BASE.getX()*facing.getFrontOffsetX()+BASE.getZ()*facing.getFrontOffsetZ());set(tile,"low",low);set(tile,"high",high);set(tile,"original",state);set(tile,"startHalfSteps",0);set(tile,"endHalfSteps",rise*2);set(tile,"open",true);set(tile,"startPose",moving?0D:1D);set(tile,"targetPose",1D);set(tile,"startTick",100L);set(tile,"duration",duration);set(tile,"moving",moving);set(tile,"beltDistance",distance);
                List<BlockPos> sources=(List<BlockPos>)field("sources").get(tile);for(int lane=0;lane<2;lane++)for(int row=0;row<length;row++)sources.add((vertical?BASE.up(row):BASE.offset(facing,row)).offset(facing.rotateY(),lane));
                Set<BlockPos> needed=(Set<BlockPos>)occupied.invoke(tile,tile.pose(0),tile.pose(1));JsonObject result=new JsonObject();result.addProperty("facing",facing.getIndex());result.addProperty("vertical",vertical);result.addProperty("material",material);result.addProperty("length",length);result.addProperty("pixels",pixels);result.addProperty("interpolation",interpolation);result.addProperty("rise",rise);result.addProperty("distance",distance);result.addProperty("moving",moving);result.addProperty("from",tile.pose(0));result.addProperty("to",tile.pose(1));JsonArray origins=new JsonArray();for(BlockPos p:sources)origins.add(position(p));result.add("origins",origins);JsonArray footprint=new JsonArray();for(BlockPos p:sorted(needed))footprint.add(position(p));result.add("footprint",footprint);JsonArray records=new JsonArray();
                for(BlockPos p:sorted(needed)){world.setBlockState(p,ModBlocks.CONTROLLED_RAMP.getDefaultState().withProperty(BlockVandorDirectional.FACING,facing),2);TileEntityControlledRamp part=(TileEntityControlledRamp)world.getTileEntity(p);configure.invoke(tile,part,p);JsonObject record=new JsonObject();record.add("cell",position(p));record.addProperty("row",part.row);record.addProperty("pose",part.pose(.25));record.addProperty("saved",part.writeToNBT(new NBTTagCompound()).toString());JsonArray shapes=new JsonArray();for(RampGeometry.Box b:part.geometry(.25)){BlockPos selected=(BlockPos)origin.invoke(part,b,.25);double[] shift=part.textureShift(b,.25);JsonArray values=new JsonArray();for(double value:new double[]{b.minX,b.minY,b.minZ,b.maxX,b.maxY,b.maxZ,selected.getX(),selected.getY(),selected.getZ(),shift[0],shift[1],part.sideTextureV(b,b.minY,.25),part.sideTextureV(b,b.maxY,.25)})values.add(value);shapes.add(values);boxes++;}record.add("boxes",shapes);records.add(record);parts++;}result.add("parts",records);out.println(gson.toJson(result));cases++;cells+=needed.size();
            }
        }
        if(cases!=1344||parts!=16432||cells!=16432||boxes!=60614)throw new AssertionError("Source belt coverage");SegmentedReferenceGolden.check(file,"f957ade994a2707112273a548b0b96dab8a37d2c9d0193a833c9fc88b28c66fb");System.out.println("Actual Ramp belt deployment PASS profiles="+cases+" cells="+cells+" partSnapshots="+parts+" boxes="+boxes+" moving0-quarter-half-threequarter-one/stopped1/actualReservations/actualPartGeometry/sideUV");
    }
}
