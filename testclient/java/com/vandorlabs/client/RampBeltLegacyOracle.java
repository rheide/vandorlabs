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

/** Actual deployed occupied(1,1), configurePart and part geometry/UV callbacks, without runtime mutation. */
public final class RampBeltLegacyOracle {
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
            for(EnumFacing facing:EnumFacing.HORIZONTALS)for(boolean vertical:new boolean[]{false,true})for(int material=0;material<4;material++)for(int profile=0;profile<7;profile++)for(double distance:new double[]{-.25,0,.25,1.5,100.125}){
                int length=new int[]{2,3,5,2,3,5,3}[profile],pixels=new int[]{1,4,8,16,8,4,3}[profile],interpolation=profile%3,rise=new int[]{2,-2,0,2,-2,2,1}[profile];double low=material==2?.5:0,high=material==1?.5:1;IBlockState state=material==0?Blocks.STONE.getDefaultState():material==3?Blocks.STONE_STAIRS.getDefaultState():Blocks.STONE_SLAB.getDefaultState().withProperty(BlockSlab.HALF,material==1?BlockSlab.EnumBlockHalf.BOTTOM:BlockSlab.EnumBlockHalf.TOP);
                world.clear();world.time=100;world.setBlockState(ROOT,ModBlocks.PROGRAMMABLE_RAMP.getDefaultState().withProperty(BlockVandorDirectional.FACING,facing),2);TileEntityRampController tile=(TileEntityRampController)world.getTileEntity(ROOT);tile.treadPixels=pixels;tile.segments=ControllerPlatform.treadCount(pixels);tile.interpolation=interpolation;tile.escalator=1;tile.verticalLayout=vertical;
                set(tile,"facing",facing);set(tile,"length",length);set(tile,"minAlong",vertical?BASE.getY():BASE.getX()*facing.getFrontOffsetX()+BASE.getZ()*facing.getFrontOffsetZ());set(tile,"low",low);set(tile,"high",high);set(tile,"original",state);set(tile,"startHalfSteps",0);set(tile,"endHalfSteps",rise*2);set(tile,"open",true);set(tile,"startPose",1D);set(tile,"targetPose",1D);set(tile,"beltDistance",distance);
                List<BlockPos> sources=(List<BlockPos>)field("sources").get(tile);for(int lane=0;lane<2;lane++)for(int row=0;row<length;row++)sources.add((vertical?BASE.up(row):BASE.offset(facing,row)).offset(facing.rotateY(),lane));
                Set<BlockPos> needed=(Set<BlockPos>)occupied.invoke(tile,1D,1D);JsonObject result=new JsonObject();result.addProperty("facing",facing.getIndex());result.addProperty("vertical",vertical);result.addProperty("material",material);result.addProperty("length",length);result.addProperty("pixels",pixels);result.addProperty("interpolation",interpolation);result.addProperty("rise",rise);result.addProperty("distance",distance);JsonArray origins=new JsonArray();for(BlockPos p:sources)origins.add(position(p));result.add("origins",origins);JsonArray footprint=new JsonArray();for(BlockPos p:sorted(needed))footprint.add(position(p));result.add("footprint",footprint);JsonArray records=new JsonArray();
                for(BlockPos p:sorted(needed)){world.setBlockState(p,ModBlocks.CONTROLLED_RAMP.getDefaultState().withProperty(BlockVandorDirectional.FACING,facing),2);TileEntityControlledRamp part=(TileEntityControlledRamp)world.getTileEntity(p);configure.invoke(tile,part,p);JsonObject record=new JsonObject();record.add("cell",position(p));record.addProperty("row",part.row);record.addProperty("saved",part.writeToNBT(new NBTTagCompound()).toString());JsonArray shapes=new JsonArray();for(RampGeometry.Box b:part.geometry(.25)){BlockPos selected=(BlockPos)origin.invoke(part,b,.25);double[] shift=part.textureShift(b,.25);JsonArray values=new JsonArray();for(double value:new double[]{b.minX,b.minY,b.minZ,b.maxX,b.maxY,b.maxZ,selected.getX(),selected.getY(),selected.getZ(),shift[0],shift[1],part.sideTextureV(b,b.minY,.25),part.sideTextureV(b,b.maxY,.25)})values.add(value);shapes.add(values);boxes++;}record.add("boxes",shapes);records.add(record);parts++;}result.add("parts",records);out.println(gson.toJson(result));cases++;cells+=needed.size();
            }
        }
        if(cases!=1120||parts==0||boxes==0)throw new AssertionError("Source belt coverage");SegmentedReferenceGolden.check(file,"be701ed5af478bbb4807caabaea18b9b36b0dcf429ee8ef2de25ad0fdbe3fb69");System.out.println("Actual Ramp belt PASS profiles="+cases+" cells="+cells+" partSnapshots="+parts+" boxes="+boxes+" occupied11/currentNativeConfigure/lane/filter/wrap/materialOrigin/sideUV");
    }
}
