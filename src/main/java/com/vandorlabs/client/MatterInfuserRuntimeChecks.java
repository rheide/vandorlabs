package com.vandorlabs.client;

import com.vandorlabs.blocks.ModBlocks;
import com.vandorlabs.items.ModItems;
import com.vandorlabs.tiles.TileEntityMatterInfuser;
import net.minecraft.block.Block;
import net.minecraft.init.*;
import net.minecraft.item.*;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.*;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/** Exercise real solar exposure, furnace inventory, fuel accounting and persistence. */
final class MatterInfuserRuntimeChecks {
    static void run(World world){
        BlockPos pos=new BlockPos(140,230,140);long time=world.getWorldTime();boolean rain=world.getWorldInfo().isRaining();
        Block block=Block.REGISTRY.getObject(new ResourceLocation("vandorlabs:programmable_matter_infuser"));
        try{
            world.setWorldTime(6000);world.getWorldInfo().setRaining(false);world.calculateInitialSkylight();world.setBlockState(pos,block.getDefaultState());
            TileEntityMatterInfuser tile=(TileEntityMatterInfuser)world.getTileEntity(pos);
            tile.setInventorySlotContents(0,new ItemStack(Items.IRON_INGOT,2));tile.setInventorySlotContents(1,new ItemStack(Blocks.REDSTONE_BLOCK));
            world.setBlockState(pos.up(),Blocks.STONE.getDefaultState());tile.update();require(tile.getField(0)==0 && tile.getField(2)==0,"roof prevents processing and fuel use");
            world.setBlockToAir(pos.up());require(tile.hasSunlight(),"exposed lens receives sunlight");
            for(int i=0;i<100;i++)tile.update();require(tile.getField(0)==200 && tile.getField(2)==100,"redstone block lasts a log's 300 ticks");
            world.setWorldTime(18000);world.calculateInitialSkylight();tile.update();require(tile.getField(0)==200 && tile.getField(2)==100,"night pauses fuel and progress");
            world.setWorldTime(6000);world.calculateInitialSkylight();for(int i=0;i<100;i++)tile.update();
            require(tile.getStackInSlot(2).getItem()==ModItems.PROGRAMMABLE_MATTER_INGOT && tile.getStackInSlot(2).getCount()==1,"200 tick output");
            require(tile.getStackInSlot(0).getCount()==1 && tile.getStackInSlot(1).isEmpty(),"input and catalyst consumption");
            NBTTagCompound tag=tile.writeToNBT(new NBTTagCompound());TileEntityMatterInfuser copy=new TileEntityMatterInfuser();copy.readFromNBT(tag);
            require(copy.getField(0)==100 && copy.getStackInSlot(2).getCount()==1,"inventory and fuel persist");
            require(tile.isItemValidForSlot(1,new ItemStack(Items.REDSTONE)) && !tile.isItemValidForSlot(1,new ItemStack(Items.COAL)) && !tile.isItemValidForSlot(0,new ItemStack(Items.GOLD_INGOT)),"automation filters");
            tile.setInventorySlotContents(2,new ItemStack(ModItems.PROGRAMMABLE_MATTER_INGOT,64));tile.update();require(tile.getField(0)==100,"full output does not consume fuel");
            int on=com.vandorlabs.tiles.ScreenHousingTextures.DEFAULT_STORAGE,off=0;
            com.vandorlabs.tiles.TileEntityProgrammableLight light=new com.vandorlabs.tiles.TileEntityProgrammableLight();light.setFaceTexture(on);light.setOffFaceTexture(off);light.setOn(false);
            require(light.activeFaceTexture().equals(com.vandorlabs.tiles.ScreenHousingTextures.texture(off)),"explicit OFF texture");
            com.vandorlabs.tiles.TileEntityProgrammableLight restored=new com.vandorlabs.tiles.TileEntityProgrammableLight();restored.readFromNBT(light.writeToNBT(new NBTTagCompound()));require(restored.getOffFaceTexture()==off,"OFF texture persists");
            restored.setOn(true);require(restored.activeFaceTexture().equals(com.vandorlabs.tiles.ScreenHousingTextures.texture(on,true)),"ON texture remains independent");
            System.out.println("[vandorlabs][reprolab] matter-infuser PASS sunlight fuel processing automation persistence light-off-texture");
        }finally{world.setBlockToAir(pos.up());world.setBlockToAir(pos);world.setWorldTime(time);world.getWorldInfo().setRaining(rain);world.calculateInitialSkylight();}
    }
    private static void require(boolean value,String message){if(!value)throw new IllegalStateException(message);}
}
