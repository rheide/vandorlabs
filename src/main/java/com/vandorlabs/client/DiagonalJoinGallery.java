package com.vandorlabs.client;

import com.vandorlabs.blocks.*;
import com.vandorlabs.tiles.*;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import java.util.*;

/** Three slope sizes per screenshot; names record the complete neighbor arrangement. */
final class DiagonalJoinGallery {
    static void build(World world,String name,int x,int y){
        String[] parts=name.substring("gallery_join_matrix_".length()).split("_");
        boolean glass=parts[0].equals("glass"),reverse=parts[4].equals("reverse");
        int front=Integer.parseInt(parts[1]),back=Integer.parseInt(parts[2]),tails=Integer.parseInt(parts[3]);
        BlockProgrammableWall block=(BlockProgrammableWall)(glass?ModBlocks.PROGRAMMABLE_DIAGONAL_GLASS:ModBlocks.PROGRAMMABLE_DIAGONAL_WALL);
        for(int mode=0;mode<3;mode++){
            BlockPos root=new BlockPos(x-5+mode*5,y+1,-18);
            List<BlockPos> positions=new ArrayList<>();List<IBlockState> states=new ArrayList<>();
            IBlockState state=block.getDefaultState();positions.add(root);states.add(state);
            for(int side=0;side<2;side++){
                int turn=side==0?front:back;if(turn==0)continue;
                positions.add(root.offset(side==0?EnumFacing.NORTH:EnumFacing.SOUTH));
                states.add(state.withProperty(BlockProgrammableWall.FACING,turn==1?EnumFacing.WEST:EnumFacing.EAST));
            }
            for(int side=0;side<2;side++)if((tails&(1<<side))!=0){positions.add(root.offset(side==0?EnumFacing.WEST:EnumFacing.EAST));states.add(state);}
            for(int j=0;j<positions.size();j++){
                int i=reverse?positions.size()-1-j:j;
                world.setBlockState(positions.get(i),states.get(i),3);
                TileEntityAnimatedScreenSelector tile=(TileEntityAnimatedScreenSelector)world.getTileEntity(positions.get(i));
                tile.setDiagonalGeometry(mode,0);if(glass)tile.setGlassShade(12);
            }
        }
    }
    private DiagonalJoinGallery() { }
}
