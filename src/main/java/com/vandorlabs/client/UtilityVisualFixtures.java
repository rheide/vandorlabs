package com.vandorlabs.client;

import com.vandorlabs.blocks.*;
import com.vandorlabs.tiles.*;
import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.util.*;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fluids.*;

final class UtilityVisualFixtures {
    static void build(World world){
        for(int x=138;x<170;x++)for(int z=119;z<139;z++)world.setBlockState(new BlockPos(x,20,z),Blocks.STONE.getDefaultState(),2);
        String[] ids={"programmable_matter_infuser","programmable_matter_block","battery_block","hexagonal_fuel_tank"};
        for(int i=0;i<ids.length;i++){BlockPos p=new BlockPos(140+i*2,21,120);world.setBlockState(p,Block.getBlockFromName("vandorlabs:"+ids[i]).getDefaultState(),2);if(i==3){world.setBlockState(p.up(),world.getBlockState(p),2);((TileEntityHexTank)world.getTileEntity(p)).fill(new FluidStack(FluidRegistry.WATER,23000),true);}}
        for(int i=0;i<4;i++){
            BlockPos p=new BlockPos(140+i*2,21,123);net.minecraft.block.state.IBlockState state=i<2?ModBlocks.PROGRAMMABLE_SLAB.getDefaultState().withProperty(BlockProgrammableSlab.HALF,i==1?net.minecraft.block.BlockSlab.EnumBlockHalf.TOP:net.minecraft.block.BlockSlab.EnumBlockHalf.BOTTOM):ModBlocks.PROGRAMMABLE_STAIRS.getDefaultState().withProperty(net.minecraft.block.BlockStairs.HALF,i==3?net.minecraft.block.BlockStairs.EnumHalf.TOP:net.minecraft.block.BlockStairs.EnumHalf.BOTTOM).withProperty(net.minecraft.block.BlockStairs.FACING,EnumFacing.SOUTH);
            world.setBlockState(p,state,2);((TileEntityAnimatedScreenSelector)world.getTileEntity(p)).setWaterlogged(true);
        }
        for(int i=0;i<3;i++){
            BlockPos root=new BlockPos(140+i*10,21,132);String id=new String[]{"assembly_press","synthesis_tower","enclosed_gantry"}[i];BlockConstructorCore block=(BlockConstructorCore)Block.getBlockFromName("vandorlabs:"+id+"_core");world.setBlockState(root,block.getDefaultState(),2);TileEntityConstructor tile=(TileEntityConstructor)world.getTileEntity(root);
            for(int y=0;y<block.height;y++)for(int x=0;x<block.width;x++)for(int z=0;z<3;z++)if(tile.frame(x,y,z) && !tile.cell(x,y,z).equals(root))world.setBlockState(tile.cell(x,y,z),Block.getBlockFromName("vandorlabs:programmable_matter_block").getDefaultState(),2);if(!tile.assemble())throw new IllegalStateException("constructor preview assembly "+id);
        }
    }
}
