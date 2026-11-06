package com.vandorlabs.client;

import com.google.gson.*;
import com.vandorlabs.blocks.*;
import com.vandorlabs.tiles.*;
import net.minecraft.block.*;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import java.nio.file.*;

/** Actual inherited Large Door outlet ownership: every raw part owns its two-row byte. */
public final class LargeDoorGushLegacyChecks {
    private static final int[][] FLUIDS={{0,0},{1,0},{0,1},{1,1},{1,2},{1,3}};
    private static int state(net.minecraft.block.state.IBlockState value){return value.getMaterial()==net.minecraft.block.material.Material.WATER?value.getValue(BlockLiquid.LEVEL):value.getBlock() instanceof BlockLargeProgrammableDoor?-6:value.getBlock()==Blocks.AIR?-1:value.getMaterial()==net.minecraft.block.material.Material.LAVA?-3:value.getBlock()==Blocks.STONE?-4:-5;}
    public static void main(String[] args)throws Exception{
        net.minecraft.init.Bootstrap.register();net.minecraftforge.fml.common.registry.GameRegistry.registerTileEntity(TileEntityLargeProgrammableDoor.class,new net.minecraft.util.ResourceLocation("vandorlabs","large_gush_oracle"));net.minecraftforge.fml.common.registry.GameRegistry.registerTileEntity(TileEntityTokenSlot.class,new net.minecraft.util.ResourceLocation("vandorlabs","large_gush_reader_oracle"));BlockDetailedDoor pair=new BlockDetailedDoor("large_gush_template",BlockVandorDoor.DoorMotion.HINGED,false,true,false,1,15,11.24F,90,-90,-15,15);BlockLargeProgrammableDoor block=new BlockLargeProgrammableDoor("large_gush_fixture",pair);
        Path file=Paths.get(System.getProperty("vandorlabs.largeDoorGushReference"));Files.createDirectories(file.toAbsolutePath().getParent());int cases=0;java.security.MessageDigest hash=java.security.MessageDigest.getInstance("SHA-256");
        try(java.io.PrintWriter out=new java.io.PrintWriter(Files.newBufferedWriter(file))){
            for(EnumFacing face:EnumFacing.HORIZONTALS)for(int role=0;role<9;role++)for(boolean open:new boolean[]{false,true})for(boolean stop:new boolean[]{false,true})for(int half=0;half<2;half++)for(int profile=0;profile<FLUIDS.length;profile++)for(int target=0;target<6;target++)for(boolean previous:new boolean[]{false,true}){
                NonRenderingChecks.MemoryWorld world=new NonRenderingChecks.MemoryWorld(false);BlockPos anchor=new BlockPos(0,80,0);EnumFacing width=face.rotateYCCW();
                for(int x=0;x<3;x++)for(int y=0;y<3;y++){BlockPos at=anchor.offset(width,x).up(y);world.setBlockState(at,block.getDefaultState().withProperty(BlockVandorDoor.FACING,face).withProperty(BlockVandorDoor.OPEN,open),2);((TileEntityLargeProgrammableDoor)world.getTileEntity(at)).assign(anchor);if(world.getBlockState(at).getValue(BlockVandorDoor.HALF)!=BlockDoor.EnumDoorHalf.LOWER)throw new AssertionError("Large raw HALF changed");}
                BlockPos pos=anchor.offset(width,role/3).up(role%3);TileEntitySpaceDoor owner=(TileEntitySpaceDoor)world.getTileEntity(pos);owner.setWaterOutlets(previous?255:0);
                if(role%3+half<3){TileEntitySpaceDoor part=(TileEntitySpaceDoor)world.getTileEntity(pos.up(half));NBTTagCompound tags=new NBTTagCompound(),slots=new NBTTagCompound();for(int side=0;side<2;side++){int kind=FLUIDS[profile][side];if(kind!=0){NBTTagCompound fluid=new NBTTagCompound();fluid.setString("FluidName",kind==1?"water":kind==2?"lava":"fixture:unknown");fluid.setInteger("Amount",1000);slots.setTag("Side"+side,fluid);}}tags.setTag("ContainedFluids",slots);part.fluids.read(tags);}
                net.minecraft.block.state.IBlockState outlet=target==0?Blocks.AIR.getDefaultState():target==1?Blocks.WATER.getDefaultState():target==2?Blocks.FLOWING_WATER.getDefaultState().withProperty(BlockLiquid.LEVEL,3):target==3?Blocks.LAVA.getDefaultState():target==4?Blocks.STONE.getDefaultState():Blocks.TALLGRASS.getDefaultState();for(int h=0;h<2;h++){world.setBlockState(pos.up(h).offset(face),outlet,2);world.setBlockState(pos.up(h).offset(face.getOpposite()),outlet,2);}
                if(stop)DoorWaterGush.stop(world,pos);else block.updateTick(world,pos,world.getBlockState(pos),world.rand);
                JsonObject row=new JsonObject();row.addProperty("face",face.getIndex());row.addProperty("role",role);row.addProperty("open",open);row.addProperty("stop",stop);row.addProperty("half",half);row.addProperty("profile",profile);row.addProperty("target",target);row.addProperty("previous",previous);row.addProperty("mask",owner.waterOutlets());JsonArray states=new JsonArray();for(int h=0;h<2;h++)for(int d=0;d<4;d++)states.add(state(world.getBlockState(pos.up(h).offset(EnumFacing.getHorizontal(d)))));row.add("states",states);if(owner.writeToNBT(new NBTTagCompound()).getInteger("DoorWaterOutlets")!=owner.waterOutlets())throw new AssertionError("Per-part mask not saved");String line=row.toString();out.println(line);hash.update(line.getBytes(java.nio.charset.StandardCharsets.UTF_8));cases++;
            }
        }
        StringBuilder hex=new StringBuilder();for(byte b:hash.digest())hex.append(String.format("%02x",b&255));if(cases!=20736||!hex.toString().equals("5b7545eb1bb28f07953d4d260842fedef3e8bf5fa2143fba734476ffa2df2910"))throw new AssertionError("Large gush source golden changed "+hex);System.out.println("Actual Large DoorWaterGush PASS cases="+cases+" golden="+hex);
    }
}
