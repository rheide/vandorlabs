package com.vandorlabs.client;

import com.vandorlabs.blocks.*;
import com.vandorlabs.tiles.*;
import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.util.*;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

final class ConstructorRuntimeChecks {
    static void run(World world){
        Block matter=Block.REGISTRY.getObject(new ResourceLocation("vandorlabs:programmable_matter_block"));BlockPos root=new BlockPos(150,225,150);
        for(String id:new String[]{"assembly_press","synthesis_tower","enclosed_gantry"})for(EnumFacing facing:EnumFacing.HORIZONTALS){
            BlockConstructorCore block=(BlockConstructorCore)Block.REGISTRY.getObject(new ResourceLocation("vandorlabs:"+id+"_core"));
            world.setBlockState(root,block.getDefaultState().withProperty(BlockVandorDirectional.FACING,facing));TileEntityConstructor tile=(TileEntityConstructor)world.getTileEntity(root);
            try{
                for(int y=0;y<block.height;y++)for(int x=0;x<block.width;x++)for(int z=0;z<3;z++){BlockPos pos=tile.cell(x,y,z);if(!pos.equals(root))world.setBlockState(pos,tile.frame(x,y,z)?matter.getDefaultState():Blocks.AIR.getDefaultState());}
                BlockPos middle=tile.cell(1,1,1);world.setBlockState(middle,Blocks.DIRT.getDefaultState());require(!tile.assemble(),"occupied interior rejects assembly "+id+facing);world.setBlockToAir(middle);
                require(tile.assemble() && tile.formed(),"complete frame assembles "+id+facing);
                java.util.Map<BlockPos,Boolean> original=new java.util.HashMap<>();
                for(int y=0;y<block.height;y++)for(int x=0;x<block.width;x++)for(int z=0;z<3;z++){BlockPos pos=tile.cell(x,y,z);if(pos.equals(root))continue;original.put(pos,tile.frame(x,y,z));require(world.getTileEntity(pos) instanceof TileEntityConstructorPart,"owned footprint cell");}
                require(tile.getRenderBoundingBox().contains(new net.minecraft.util.math.Vec3d(tile.cell(block.width-1,block.height-1,2)).addVector(.5,.5,.5)),"rotated render bounds");
                world.setBlockToAir(root);
                for(java.util.Map.Entry<BlockPos,Boolean> entry:original.entrySet())require(entry.getValue()?world.getBlockState(entry.getKey()).getBlock()==matter:world.isAirBlock(entry.getKey()),"core break restores frame without matter loss "+id+facing);
            }finally{
                // The same rotation remains available on the retained tile after root removal.
                world.setBlockState(root,block.getDefaultState().withProperty(BlockVandorDirectional.FACING,facing));TileEntityConstructor cleanup=(TileEntityConstructor)world.getTileEntity(root);
                java.util.List<BlockPos> cells=new java.util.ArrayList<>();for(int y=0;y<block.height;y++)for(int x=0;x<block.width;x++)for(int z=0;z<3;z++)cells.add(cleanup.cell(x,y,z));for(BlockPos cell:cells)world.setBlockToAir(cell);
            }
        }
        System.out.println("[vandorlabs][reprolab] constructors PASS 3 designs four facings obstruction ownership dismantling bounds");
    }
    private static void require(boolean value,String message){if(!value)throw new IllegalStateException(message);}
}
