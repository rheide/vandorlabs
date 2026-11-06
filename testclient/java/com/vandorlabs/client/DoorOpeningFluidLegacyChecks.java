package com.vandorlabs.client;

import com.google.gson.*;
import com.vandorlabs.blocks.*;
import com.vandorlabs.tiles.TileEntitySpaceDoor;
import java.nio.file.*;
import java.security.MessageDigest;
import net.minecraft.block.BlockDoor;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fluids.*;

/** Frozen actual OpeningFluids.effective behavior, including opaque occupancy and upper OPEN disagreement. */
public final class DoorOpeningFluidLegacyChecks {
    private static NBTTagCompound slot(int kind){NBTTagCompound tag=new NBTTagCompound();tag.setString("FluidName",kind==1?"water":kind==2?"lava":"fixture:unknown");tag.setInteger("Amount",kind==1?777:333);NBTTagCompound extra=new NBTTagCompound();extra.setInteger("FixtureKind",kind);tag.setTag("Tag",extra);return tag;}
    public static void main(String[] args)throws Exception{
        net.minecraft.init.Bootstrap.register();if(FluidRegistry.WATER==null||FluidRegistry.LAVA==null)throw new AssertionError("Native Forge fluid initialization");
        BlockDetailedDoor pair=new BlockDetailedDoor("opening_door_template",BlockVandorDoor.DoorMotion.HINGED,false,true,false,1,15,11.24F,90,-90,-15,15);BlockConfigurableSpaceDoor block=new BlockConfigurableSpaceDoor("opening_door_fixture",true,pair);
        Path file=Paths.get(System.getProperty("vandorlabs.doorOpeningReference","build/test-artifacts/door-opening-forge.jsonl"));Files.createDirectories(file.toAbsolutePath().getParent());MessageDigest hash=MessageDigest.getInstance("SHA-256");int cases=0;
        try(java.io.PrintWriter out=new java.io.PrintWriter(Files.newBufferedWriter(file))){for(EnumFacing face:EnumFacing.HORIZONTALS)for(boolean bottomOpen:new boolean[]{false,true})for(boolean topOpen:new boolean[]{false,true}){
            NonRenderingChecks.MemoryWorld world=new NonRenderingChecks.MemoryWorld(false);BlockPos root=new BlockPos(0,80,0);world.setBlockState(root,block.getDefaultState().withProperty(BlockVandorDoor.FACING,face).withProperty(BlockVandorDoor.OPEN,bottomOpen),2);world.setBlockState(root.up(),block.getDefaultState().withProperty(BlockVandorDoor.HALF,BlockDoor.EnumDoorHalf.UPPER).withProperty(BlockVandorDoor.OPEN,topOpen),2);
            for(boolean upper:new boolean[]{false,true}){BlockPos pos=upper?root.up():root;for(int mask=0;mask<64;mask++){
                // Never replace the other door half; its vertical neighbor cannot be ambient water.
                int actualMask=mask&~(1<<(upper?EnumFacing.DOWN:EnumFacing.UP).getIndex());
                for(EnumFacing side:EnumFacing.values())if(side!=(upper?EnumFacing.DOWN:EnumFacing.UP))world.setBlockState(pos.offset(side),(actualMask&(1<<side.getIndex()))!=0?Blocks.WATER.getDefaultState():Blocks.AIR.getDefaultState(),2);
                for(int first=0;first<4;first++)for(int second=0;second<4;second++){
                    NBTTagCompound tag=new NBTTagCompound(),slots=new NBTTagCompound();if(first!=0)slots.setTag("Side0",slot(first));if(second!=0)slots.setTag("Side1",slot(second));tag.setTag("ContainedFluids",slots);((TileEntitySpaceDoor)world.getTileEntity(pos)).fluids.read(tag);
                    JsonObject row=new JsonObject();row.addProperty("face",face.getIndex());row.addProperty("open",bottomOpen);row.addProperty("upperOpen",topOpen);row.addProperty("upper",upper);row.addProperty("mask",actualMask);row.addProperty("first",first);row.addProperty("second",second);
                    for(int side=0;side<2;side++){FluidStack fluid=OpeningFluids.effective(world,pos,side);row.addProperty("side"+side,fluid==null?0:fluid.getFluid()==FluidRegistry.WATER?1:fluid.getFluid()==FluidRegistry.LAVA?2:-1);row.addProperty("amount"+side,fluid==null?0:fluid.amount);row.addProperty("tag"+side,fluid==null||fluid.tag==null?0:fluid.tag.getInteger("FixtureKind"));}
                    String json=row.toString();out.println(json);hash.update(json.getBytes(java.nio.charset.StandardCharsets.UTF_8));cases++;
                }
            }}
        }}
        StringBuilder golden=new StringBuilder();for(byte b:hash.digest())golden.append(String.format("%02x",b&255));if(cases!=32768||!golden.toString().equals("3bdc9d5bc03780d1078bc467149dc6e0de9f00f45fdac156539fec797685115e"))throw new AssertionError("Opening fluids source changed "+golden);System.out.println("Actual ordinary OpeningFluids.effective PASS cases="+cases+" golden="+golden);
    }
}
