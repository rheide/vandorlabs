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
        if(scene.equals("input_ceiling")) {
            for(int i=0;i<2;i++) {
                BlockProgrammableInput input=(BlockProgrammableInput)(i==0
                        ?ModBlocks.PROGRAMMABLE_INPUT:ModBlocks.PROGRAMMABLE_FULL_INPUT);
                BlockPos at=origin.add(3+i*4,5,0);
                world.setBlockState(at,input.getDefaultState()
                        .withProperty(BlockProgrammableInput.KEYBOARD,true)
                        .withProperty(BlockProgrammableInput.UPPER,true),3);
                ((TileEntityAnimatedScreenSelector)world.getTileEntity(at)).setCeilingMounted(true);
            }
        } else if(scene.equals("light_shapes")) {
            for(int i=0;i<4;i++) {
                Block block=i<2?ModBlocks.PROGRAMMABLE_LIGHT_FRAME:ModBlocks.PROGRAMMABLE_LIGHT_SLAB;
                BlockPos at=origin.add(2+i*3,3,0);
                IBlockState state=block.getDefaultState();
                if(i==3)state=state.withProperty(BlockProgrammableLightSlab.HALF,
                        net.minecraft.block.BlockSlab.EnumBlockHalf.TOP);
                world.setBlockState(at,state,3);
                ((TileEntityProgrammableLight)world.getTileEntity(at)).configure(i,15,true,0);
            }
        } else if(scene.startsWith("light_depth")) {
            for(int col=0;col<2;col++)for(int row=0;row<2;row++) {
                BlockPos p=origin.add(col,row+2,0);
                world.setBlockState(p,ModBlocks.PROGRAMMABLE_LIGHT.getDefaultState(),3);
                ((TileEntityProgrammableLight)world.getTileEntity(p)).configure(0,15,true,0,1+col+row*2);
            }
            for(int i=0;i<6;i++) {
                BlockPos p=origin.add(4+i%3,i/3+2,0);
                world.setBlockState(p,ModBlocks.PROGRAMMABLE_LIGHT.getDefaultState(),3);
                ((TileEntityProgrammableLight)world.getTileEntity(p)).configure(i,15,false,0,i);
            }
        } else if(scene.startsWith("round_glass")) {
            for(int col=0;col<3;col++)for(int row=0;row<2;row++) {
                BlockPos p=origin.add(col,row+1,0);
                world.setBlockState(p,block("programmable_porthole_block").getDefaultState(),3);
                TileEntityAnimatedScreenSelector tile=(TileEntityAnimatedScreenSelector)world.getTileEntity(p);
                tile.setPortholeShape(3);tile.setGlassShade(1);
            }
        } else if(scene.equals("stairs")) {
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
        } else if(scene.equals("portholes_stacked")||scene.startsWith("portholes_half_height")) {
            for(int shape=0;shape<4;shape++)for(int row=0;row<2;row++) {
                BlockPos p=origin.add(shape*3-1,scene.endsWith("stacked")?row:0,scene.endsWith("stacked")?0:row);
                Block block=block("programmable_diagonal_porthole");
                IBlockState state=block.getDefaultState().withProperty(BlockProgrammableWall.INVERTED,row==1);
                if(row==1)state=state.withProperty(BlockProgrammableWall.FACING,EnumFacing.SOUTH);
                world.setBlockState(p,state,3);
                TileEntityAnimatedScreenSelector tile=(TileEntityAnimatedScreenSelector)world.getTileEntity(p);
                tile.setPortholeShape(shape);tile.setGlassShade(1);
                if(scene.startsWith("portholes_half_height"))tile.setDiagonalGeometry(2,0);
                if(scene.endsWith("unjoined"))tile.setJoinPortholes(false);
            }
        } else if(scene.startsWith("shallow_fill")) {
            for(int i=0;i<6;i++) {
                BlockPos p=origin.add(i*2-2,2,0);
                world.setBlockState(p,ModBlocks.PROGRAMMABLE_DIAGONAL_WALL.getDefaultState()
                        .withProperty(BlockProgrammableWall.INVERTED,i>=3),3);
                TileEntityAnimatedScreenSelector tile=(TileEntityAnimatedScreenSelector)world.getTileEntity(p);
                tile.setDiagonalGeometry(2,1+i%3); tile.setHousingTexture(3);
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
        } else if (scene.startsWith("seating")) {
            for (int style=0;style<2;style++) for (int col=0;col<3;col++) {
                Block block=block(style==0?"luxury_seat":"military_seat");BlockPos p=origin.add(style*5+col,0,0);
                IBlockState state=block.getDefaultState();world.setBlockState(p,state,3);
                world.setBlockState(p.up(),state.withProperty(BlockBridgeChair.UPPER,true),3);
                TileEntityConnectedSeat tile=(TileEntityConnectedSeat)world.getTileEntity(p);
                if(scene.equals("seating_heights")){tile.setHeight(col);tile.setJoin(false);}
                if(scene.equals("seating_unjoined"))tile.setJoin(false);
            }
        } else if (scene.equals("gear_extra_large")) {
            BlockTelescopicLandingGear gear=(BlockTelescopicLandingGear)block("landing_gear");
            BlockPos p=origin.add(6,5,0);
            world.setBlockState(p,gear.getDefaultState(),3);
            TileEntityLandingGear tile=(TileEntityLandingGear)world.getTileEntity(p);
            tile.configure(0,0,32,3);
            gear.setExtended(world,p,true);
        } else if (scene.startsWith("gear")) {
            for(int i=0;i<3;i++) {
                BlockTelescopicLandingGear gear=(BlockTelescopicLandingGear)block("landing_gear");
                BlockPos p=origin.add(i*4,4,0);world.setBlockState(p,gear.getDefaultState(),3);
                TileEntityLandingGear tile=(TileEntityLandingGear)world.getTileEntity(p);
                tile.configure(0,0,scene.equals("gear_four")?64:scene.equals("gear_half")?8:16,i);
                if(!scene.equals("gear"))gear.setExtended(world,p,true);
                if(scene.equals("gear_retracted")){tile.progress=tile.previous=1;gear.setExtended(world,p,false);}
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
