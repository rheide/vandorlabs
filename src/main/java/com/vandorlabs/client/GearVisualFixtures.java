package com.vandorlabs.client;

import com.vandorlabs.blocks.*;
import com.vandorlabs.tiles.TileEntityLandingGear;
import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

final class GearVisualFixtures {
    static void build(World world){
        for(int x=188;x<=211;x++)for(int z=116;z<=130;z++)world.setBlockState(new BlockPos(x,27,z),Blocks.STONE.getDefaultState(),2);
        for(int size:new int[]{0,4})for(int kind=0;kind<3;kind++){
            BlockPos p=new BlockPos(190+kind*4,30,size==0?120:126);BlockNonWheeledLandingGear block=(BlockNonWheeledLandingGear)Block.getBlockFromName("vandorlabs:"+new String[]{"ski_landing_gear","landing_strut","angled_landing_strut"}[kind]);
            world.setBlockState(p,block.getDefaultState(),2);if(!((TileEntityLandingGear)world.getTileEntity(p)).configure(0,0,16,size))throw new IllegalStateException("gear visual fixture");
        }
        Block ski=Block.getBlockFromName("vandorlabs:ski_landing_gear");for(int z=118;z<=120;z++)world.setBlockState(new BlockPos(204,30,z),ski.getDefaultState(),2);for(int y=30;y<=32;y++)world.setBlockState(new BlockPos(208,y,120),ski.getDefaultState(),2);
        pose(world,false);
    }
    static void pose(World world,boolean extended){for(int x=190;x<=208;x++)for(int z=118;z<=126;z++){BlockPos p=new BlockPos(x,30,z);if(world.getBlockState(p).getBlock() instanceof BlockNonWheeledLandingGear){TileEntityLandingGear tile=(TileEntityLandingGear)world.getTileEntity(p);if(tile!=null && tile.isRoot())((BlockTelescopicLandingGear)world.getBlockState(p).getBlock()).setExtended(world,p,extended);}}}
}
