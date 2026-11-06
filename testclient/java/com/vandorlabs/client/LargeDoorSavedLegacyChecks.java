package com.vandorlabs.client;

import com.google.gson.*;
import com.vandorlabs.blocks.*;
import com.vandorlabs.tiles.*;
import java.nio.file.*;
import net.minecraft.block.*;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.*;
import net.minecraft.util.math.BlockPos;

/** Complete emitted 1.12 Large layouts, including independent fluid/outlet ownership and an absent-schema input. */
public final class LargeDoorSavedLegacyChecks {
    public static void main(String[] args)throws Exception{
        net.minecraft.init.Bootstrap.register();net.minecraftforge.fml.common.registry.GameRegistry.registerTileEntity(TileEntityLargeProgrammableDoor.class,new ResourceLocation("vandorlabs","large_programmable_door"));net.minecraftforge.fml.common.registry.GameRegistry.registerTileEntity(TileEntityTokenSlot.class,new ResourceLocation("vandorlabs","token_slot"));
        BlockDetailedDoor pair=new BlockDetailedDoor("large_saved_template",BlockVandorDoor.DoorMotion.HINGED,false,true,false,1,15,11.24F,90,-90,-15,15);BlockLargeProgrammableDoor block=new BlockLargeProgrammableDoor("large_programmable_door",pair);JsonArray fixtures=new JsonArray();BlockPos anchor=new BlockPos(0,80,0);
        for(EnumFacing face:EnumFacing.HORIZONTALS)for(boolean absent:new boolean[]{false,true}){
            NonRenderingChecks.MemoryWorld world=new NonRenderingChecks.MemoryWorld(false);EnumFacing width=face.rotateYCCW();
            for(int x=0;x<3;x++)for(int y=0;y<3;y++){BlockPos at=anchor.offset(width,x).up(y);world.setBlockState(at,block.getDefaultState().withProperty(BlockVandorDoor.FACING,face).withProperty(BlockVandorDoor.OPEN,true),2);TileEntityLargeProgrammableDoor tile=(TileEntityLargeProgrammableDoor)world.getTileEntity(at);tile.assign(anchor);tile.configure(23,1,true,3,true,true,false,0,true);tile.setRedstoneChannels(com.vandorlabs.redstone.ChannelList.of(78101,78102));NBTTagCompound fluids=new NBTTagCompound(),slots=new NBTTagCompound(),water=new NBTTagCompound();water.setString("FluidName","water");water.setInteger("Amount",701+x*3+y);NBTTagCompound payload=new NBTTagCompound();payload.setString("Source","released-large-"+x+"-"+y);water.setTag("Tag",payload);slots.setTag("Side0",water);if(x==2&&y==2){NBTTagCompound opaque=new NBTTagCompound();opaque.setString("FluidName","future:retained_fluid");opaque.setInteger("Amount",432);opaque.setString("Owner","released-outer-fluid");slots.setTag("Side1",opaque);}fluids.setTag("ContainedFluids",slots);tile.fluids.read(fluids);}
            for(int x=0;x<3;x++)for(int y=0;y<3;y++)DoorWaterGush.opened(world,anchor.offset(width,x).up(y));
            JsonObject fixture=new JsonObject();fixture.addProperty("face",face.getIndex());fixture.addProperty("absentSchema",absent);JsonArray cells=new JsonArray(),outlets=new JsonArray();
            for(int x=0;x<3;x++)for(int y=0;y<3;y++){BlockPos at=anchor.offset(width,x).up(y);TileEntityLargeProgrammableDoor tile=(TileEntityLargeProgrammableDoor)world.getTileEntity(at);NBTTagCompound raw=tile.writeToNBT(new NBTTagCompound());if(absent)raw.removeTag("SpaceDoorSchema");NBTTagCompound caps=new NBTTagCompound();caps.setString("Owner","released-capability-"+x+"-"+y);raw.setTag("ForgeCaps",caps);JsonObject cell=new JsonObject();cell.addProperty("column",x);cell.addProperty("row",y);cell.addProperty("metadata",block.getMetaFromState(world.getBlockState(at)));cell.addProperty("nbt",raw.toString());tile.readFromNBT(raw.copy());cell.addProperty("choices",tile.itemSettings().toString());cell.addProperty("sliding",tile.isSliding());cells.add(cell);for(EnumFacing direction:new EnumFacing[]{face,face.getOpposite()}){BlockPos out=at.offset(direction);JsonObject emitted=new JsonObject();emitted.addProperty("dx",out.getX()-anchor.getX());emitted.addProperty("dy",out.getY()-anchor.getY());emitted.addProperty("dz",out.getZ()-anchor.getZ());net.minecraft.block.state.IBlockState state=world.getBlockState(out);emitted.addProperty("level",state.getMaterial()==net.minecraft.block.material.Material.WATER?state.getValue(BlockLiquid.LEVEL):-1);outlets.add(emitted);}}
            fixture.add("cells",cells);fixture.add("outlets",outlets);fixtures.add(fixture);
        }
        String json=fixtures.toString();java.security.MessageDigest digest=java.security.MessageDigest.getInstance("SHA-256");StringBuilder hash=new StringBuilder();for(byte b:digest.digest(json.getBytes(java.nio.charset.StandardCharsets.UTF_8)))hash.append(String.format("%02x",b&255));if(fixtures.size()!=8||!hash.toString().equals("ddc46072a2f0b833247e87dc8dc089e6bd5c81c83563305b3e60c40b8c57d706"))throw new AssertionError("Large saved source golden changed "+hash);Path output=Paths.get(System.getProperty("vandorlabs.largeDoorSavedReference"));Files.createDirectories(output.toAbsolutePath().getParent());Files.write(output,json.getBytes(java.nio.charset.StandardCharsets.UTF_8));System.out.println("Actual Large Door saved layout PASS fixtures="+fixtures.size()+" cells="+(fixtures.size()*9)+" golden="+hash);
    }
}
