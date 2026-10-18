package com.vandorlabs.client;

import com.vandorlabs.blocks.*;
import com.vandorlabs.tiles.*;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

final class TextureLayoutVisualFixtures {
    static void build(World world){
        for(int x=257;x<276;x++)for(int z=118;z<132;z++)world.setBlockState(new BlockPos(x,20,z),Blocks.STONE.getDefaultState(),2);
        Block[] blocks={ModBlocks.PROGRAMMABLE_BLOCK,ModBlocks.PROGRAMMABLE_SLAB,ModBlocks.PROGRAMMABLE_STAIRS,ModBlocks.PROGRAMMABLE_WALL};
        for(int mode=0;mode<3;mode++)for(int shape=0;shape<blocks.length;shape++){
            BlockPos p=new BlockPos(260+mode*5,21,120+shape*3);world.setBlockState(p,blocks[shape].getDefaultState(),2);
            TileEntityAnimatedScreenSelector tile=(TileEntityAnimatedScreenSelector)world.getTileEntity(p);tile.setHousingTexture(8);tile.setSideTexture(8);tile.setMainTextureLayout(mode);
        }
        for(int mode=0;mode<3;mode++){
            BlockPos base=new BlockPos(300+mode*8,21,120);
            for(int x=-2;x<5;x++)for(int z=-2;z<5;z++)world.setBlockState(base.add(x,-1,z),Blocks.STONE.getDefaultState(),2);
            for(int row=0;row<3;row++)for(int col=0;col<2;col++){
                BlockPos p=base.east(col);if(mode==2)p=p.south(row);else p=p.up(row);
                IBlockState state=ModBlocks.PROGRAMMABLE_DIAGONAL_GLASS.getDefaultState().withProperty(BlockProgrammableWall.FACING,mode==2 && row%2==1?EnumFacing.SOUTH:EnumFacing.NORTH).withProperty(BlockProgrammableWall.INVERTED,mode!=2 && row%2==1);
                world.setBlockState(p,state,2);TileEntityAnimatedScreenSelector tile=(TileEntityAnimatedScreenSelector)world.getTileEntity(p);tile.setDiagonalGeometry(mode,0);tile.setGlassMaterial(8);tile.setGlassLayout(true,true);tile.setGlassTextureLayout(0);tile.setGlassAppearance(2,true);
            }
        }
    }
}
