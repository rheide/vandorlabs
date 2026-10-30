package com.vandorlabs.client;
import com.vandorlabs.blocks.*;
import com.vandorlabs.tiles.*;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.block.Block;
public final class SolidFastPathChecks {
 static void run(){
  for(Block block:new Block[]{new BlockProgrammableBlock(),new BlockProgrammableSlab(),new BlockProgrammableStairs()}){
   TileEntityAnimatedScreenSelector original=(TileEntityAnimatedScreenSelector)block.createTileEntity(null,block.getDefaultState());
   for(boolean custom:new boolean[]{false,true}){
    original.setFaceTextures(custom?new FaceTextures(false,new int[]{1,2,3,4,5,6}):FaceTextures.DEFAULT);
    NBTTagCompound saved=original.writeToNBT(new NBTTagCompound());
    TileEntityAnimatedScreenSelector loaded=new TileEntityAnimatedScreenSelector();loaded.readFromNBT(saved);
    if(!loaded.getFaceTextures().equals(original.getFaceTextures()))throw new AssertionError("lost disabled saved face choices");
    if(!custom && loaded.getFaceTextures()!=FaceTextures.DEFAULT)throw new AssertionError("default not canonical");
   }
  }
  net.minecraftforge.fluids.FluidRegistry.enableUniversalBucket();
  if(net.minecraftforge.fluids.FluidRegistry.WATER==null)throw new AssertionError("water unavailable");
  TileEntityAnimatedScreenSelector a=new TileEntityAnimatedScreenSelector(),b=new TileEntityAnimatedScreenSelector();
  a.fluids.set(0,new net.minecraftforge.fluids.FluidStack(net.minecraftforge.fluids.FluidRegistry.WATER,1000));
  if(b.fluids.any()||!a.fluids.occupied(0))throw new AssertionError("shared mutable fluid slots");
  NBTTagCompound saved=a.writeToNBT(new NBTTagCompound());b.readFromNBT(saved);
  a.fluids.set(0,null);if(a.fluids.any()||!b.fluids.occupied(0))throw new AssertionError("fluid clear lost other owner");
  b.readFromNBT(a.writeToNBT(new NBTTagCompound()));if(b.fluids.any())throw new AssertionError("empty reload retained fluids");
  System.out.println("solid-fast-path PASS default sharing disabled saved choices fluid ownership and reload");
 }
}
