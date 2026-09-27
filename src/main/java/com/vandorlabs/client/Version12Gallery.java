package com.vandorlabs.client;

import com.vandorlabs.blocks.*;
import com.vandorlabs.tiles.*;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.*;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

final class Version12Gallery {
    static void build(World world,String scene,int x,int y) {
        BlockPos origin=new BlockPos(x-3,y+1,-18);
        if(scene.equals("stairs")) {
            for(int i=0;i<6;i++) {
                BlockPos p=origin.add(i*2-2,0,0);
                net.minecraft.block.state.IBlockState state=ModBlocks.PROGRAMMABLE_STAIRS.getDefaultState()
                        .withProperty(net.minecraft.block.BlockStairs.HALF,i>=3?net.minecraft.block.BlockStairs.EnumHalf.TOP:net.minecraft.block.BlockStairs.EnumHalf.BOTTOM)
                        .withProperty(net.minecraft.block.BlockStairs.FACING,EnumFacing.SOUTH);
                world.setBlockState(p,state,3);
                TileEntityAnimatedScreenSelector tile=(TileEntityAnimatedScreenSelector)world.getTileEntity(p);
                tile.setFaceTextures(new FaceTextures(true,new int[]{1,2,3,4,5,6}));
                if(i%3>0)world.setBlockState(p.offset(i%3==1?EnumFacing.SOUTH:EnumFacing.NORTH),state.withProperty(net.minecraft.block.BlockStairs.FACING,EnumFacing.EAST),3);
            }
        } else if(scene.equals("portholes_stacked")||scene.equals("portholes_half_height")) {
            for(int shape=0;shape<4;shape++)for(int row=0;row<2;row++) {
                BlockPos p=origin.add(shape*3-1,scene.endsWith("stacked")?row:0,scene.endsWith("stacked")?0:row*2);
                Block block=block("programmable_diagonal_porthole");
                IBlockState state=block.getDefaultState().withProperty(BlockProgrammableWall.INVERTED,row==1);
                if(scene.endsWith("stacked")&&row==1)state=state.withProperty(BlockProgrammableWall.FACING,EnumFacing.SOUTH);
                world.setBlockState(p,state,3);
                TileEntityAnimatedScreenSelector tile=(TileEntityAnimatedScreenSelector)world.getTileEntity(p);
                tile.setPortholeShape(shape);tile.setGlassShade(1);
                if(scene.endsWith("height"))tile.setDiagonalGeometry(2,0);
            }
        } else if(scene.startsWith("filled_corners")) {
            for(int i=0;i<4;i++) {
                BlockPos p=origin.add(i*3-2,0,0);Block block=ModBlocks.PROGRAMMABLE_DIAGONAL_WALL;
                IBlockState state=block.getDefaultState();world.setBlockState(p,state,3);
                BlockPos n=scene.endsWith("inside")?p.north():p.south();
                world.setBlockState(n,state.withProperty(BlockProgrammableWall.FACING,EnumFacing.EAST),3);
                ((TileEntityAnimatedScreenSelector)world.getTileEntity(p)).setDiagonalGeometry(1,i);
                ((TileEntityAnimatedScreenSelector)world.getTileEntity(n)).setDiagonalGeometry(1,i);
            }
        } else if (scene.equals("controller")) {
            BlockPos p=new BlockPos(x,y+1,-18);
            world.setBlockState(p,block("programmable_ramp").getDefaultState(),3);
            world.setBlockState(p.east(),net.minecraft.init.Blocks.STONE.getDefaultState(),3);
        } else if (scene.equals("seating")) {
            for (int style=0;style<2;style++) for (int col=0;col<3;col++) {
                Block block=block(style==0?"luxury_seat":"military_seat");BlockPos p=origin.add(style*5+col,0,0);
                IBlockState state=block.getDefaultState();world.setBlockState(p,state,3);
                world.setBlockState(p.up(),state.withProperty(BlockBridgeChair.UPPER,true),3);
            }
        } else if (scene.startsWith("gear")) {
            String[] ids={"landing_gear_top_small","landing_gear_top_large","landing_gear_side_small","landing_gear_side_large","landing_gear_top_small_telescopic"};
            for (int i=0;i<ids.length;i++) {
                Block gear=block(ids[i]);BlockPos p=origin.add(i*2-1,2,0);world.setBlockState(p,gear.getDefaultState(),3);
                if (i==4 && scene.endsWith("extended")) ((BlockTelescopicLandingGear)gear).setExtended(world,p,true);
            }
        } else if (scene.equals("faces")) {
            for (int i=0;i<4;i++) {
                Block block=i<2?ModBlocks.PROGRAMMABLE_BLOCK:ModBlocks.PROGRAMMABLE_SLAB;
                BlockPos p=origin.add(i*2,0,0);IBlockState state=block.getDefaultState();
                if (i==3) state=state.withProperty(BlockProgrammableSlab.HALF,net.minecraft.block.BlockSlab.EnumBlockHalf.TOP);
                world.setBlockState(p,state,3);TileEntityAnimatedScreenSelector tile=(TileEntityAnimatedScreenSelector)world.getTileEntity(p);
                tile.setHousingTexture(0);tile.setFaceTextures(new FaceTextures(i!=0,new int[]{-1,1,3,-1,4,2}));
            }
        } else if (scene.equals("portholes")) {
            for (int shape=0;shape<4;shape++) for (int col=0;col<2;col++) {
                BlockPos p=origin.add(shape*3-1+col,0,0);world.setBlockState(p,block("programmable_diagonal_porthole").getDefaultState(),3);
                TileEntityAnimatedScreenSelector tile=(TileEntityAnimatedScreenSelector)world.getTileEntity(p);
                tile.setPortholeShape(shape);tile.setDiagonalFullWidth(shape%2==1);tile.setGlassShade(1);
            }
        } else if (scene.equals("half_height") || scene.equals("fill")) {
            for (int i=0;i<4;i++) {
                BlockPos p=origin.add(scene.equals("half_height") ? (i/2)*4+i%2 : i*2,0,0);
                world.setBlockState(p,ModBlocks.PROGRAMMABLE_DIAGONAL_WALL.getDefaultState()
                        .withProperty(BlockProgrammableWall.INVERTED,scene.equals("half_height") && i>=2),3);
                TileEntityAnimatedScreenSelector tile=(TileEntityAnimatedScreenSelector)world.getTileEntity(p);
                tile.setDiagonalGeometry(scene.equals("half_height")?2:1,scene.equals("fill")?i:0);
            }
        } else if (scene.equals("half_console")) {
            for (int i=0;i<4;i++) {
                BlockPos p=origin.add(i*2,0,0);
                world.setBlockState(p,block("programmable_diagonal_half_console").getDefaultState().withProperty(BlockDiagonalHalfConsole.UPPER,i>=2),3);
                world.setBlockState(p.south(),ModBlocks.PROGRAMMABLE_SLAB.getDefaultState().withProperty(BlockProgrammableSlab.HALF,
                        i>=2?net.minecraft.block.BlockSlab.EnumBlockHalf.TOP:net.minecraft.block.BlockSlab.EnumBlockHalf.BOTTOM),3);
            }
        }
    }
    private static Block block(String id) { return Block.REGISTRY.getObject(new ResourceLocation("vandorlabs",id)); }
}
