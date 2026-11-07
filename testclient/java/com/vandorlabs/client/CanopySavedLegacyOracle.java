package com.vandorlabs.client;

import com.google.gson.*;
import com.vandorlabs.blocks.BlockCanopy;
import com.vandorlabs.tiles.TileEntityCanopy;
import java.io.*;
import java.util.*;
import net.minecraft.nbt.*;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;

/** Actual legacy link offsets, absent placement flags, bounded footprints and saved motion decode. */
public final class CanopySavedLegacyOracle {
    private static final BlockPos ROOT=new BlockPos(-9,80,17);
    private static JsonArray pos(BlockPos p){JsonArray a=new JsonArray();a.add(p.getX());a.add(p.getY());a.add(p.getZ());return a;}
    private static JsonArray positions(List<BlockPos> values){JsonArray a=new JsonArray();for(BlockPos p:values)a.add(pos(p));return a;}
    public static void main(String[] args)throws Exception {
        List<BlockCanopy> blocks=CanopyPlacementLegacyOracle.initialize();List<NBTBase> values=new ArrayList<>();for(int n:new int[]{-1,0,1,2,9,10,999,1000,Integer.MAX_VALUE}){values.add(new NBTTagInt(n));values.add(new NBTTagByte((byte)n));}for(long n:new long[]{0,ROOT.toLong(),new BlockPos(-1,2,3).toLong()})values.add(new NBTTagLong(n));for(float n:new float[]{-.5F,0,.5F,1,2,Float.NaN,Float.NEGATIVE_INFINITY,Float.POSITIVE_INFINITY})values.add(new NBTTagFloat(n));values.add(new NBTTagDouble(Double.NaN));values.add(new NBTTagString("future:canopy"));values.add(new NBTTagCompound());values.add(new NBTTagIntArray(new int[0]));values.add(new NBTTagIntArray(new int[]{0,0,0,-1,2,3,8,-8,8,9,0,0}));values.add(new NBTTagIntArray(new int[]{0,1}));values.add(new NBTTagIntArray(new int[]{-1,0,1,999,1000,2,2}));values.add(new NBTTagIntArray(new int[3003]));
        String[] keys={"Anchor","AnchorOffset","Pair","PairOffset","CanopyWidth","Progress","CenteredPlacement","HollowPlacement","Open","CanopyWaitingForReservations","CanopyFootprint","CanopyReleasedHelpers","CanopyPowerKnown","CanopyLastPower","CanopyLastPolarity"};File file=new File(args[0]);file.getAbsoluteFile().getParentFile().mkdirs();Gson gson=new Gson();int profiles=0;
        try(PrintWriter out=new PrintWriter(file,"UTF-8")) {
            for(BlockCanopy block:blocks)if(block.kind.equals("canopy")||block.kind.equals("regular_canopy_glass")||block.kind.equals("large_front_canopy_glass"))for(int index=-1;index<keys.length;index++)for(int value=0;value<(index<0?1:values.size());value++) {
                CanopyPlacementLegacyOracle.World world=new CanopyPlacementLegacyOracle.World();world.setBlockState(ROOT,block.getDefaultState().withProperty(BlockCanopy.FACING,EnumFacing.NORTH),2);TileEntityCanopy tile=(TileEntityCanopy)world.getTileEntity(ROOT);NBTTagCompound input=new NBTTagCompound();input.setInteger("x",ROOT.getX());input.setInteger("y",ROOT.getY());input.setInteger("z",ROOT.getZ());if(index>=0)input.setTag(keys[index],values.get(value).copy());tile.readFromNBT(input.copy());JsonObject row=new JsonObject();row.addProperty("id",block.kind);row.add("input",CanopySettingsLegacyOracle.tags(input));row.add("anchor",tile.anchor==null?JsonNull.INSTANCE:pos(tile.anchor));row.add("pair",tile.pair==null?JsonNull.INSTANCE:pos(tile.pair));row.addProperty("width",tile.width());row.addProperty("centered",tile.centeredPlacement);row.addProperty("hollow",tile.hollowPlacement);row.addProperty("savedFootprint",tile.hasSavedFootprint());row.addProperty("progress",Float.floatToRawIntBits(tile.progress));row.addProperty("previous",Float.floatToRawIntBits(tile.previous));row.addProperty("open",tile.targetOpen);row.addProperty("waiting",tile.waitingForReservations);row.add("cells",positions(tile.cells()));row.add("reserved",positions(tile.reservedCells()));row.add("saved",CanopySettingsLegacyOracle.tags(tile.writeToNBT(new NBTTagCompound())));out.println(gson.toJson(row));profiles++;
            }
        }
        SegmentedReferenceGolden.check(file,"232a6a6e8269f9ae510c51e8b397e76d42d7cd064ce0cceabb70a91754a7898e");
        System.out.println("Actual canopy saved links PASS profiles="+profiles+" file="+file);
    }
}
