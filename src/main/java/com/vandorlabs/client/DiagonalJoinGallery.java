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
    static void glassWallCaps(World world,int x,int y){
        world.setWorldTime(6000);world.getGameRules().setOrCreateGameRule("doDaylightCycle","false");
        BlockProgrammableWall wall=(BlockProgrammableWall)ModBlocks.PROGRAMMABLE_DIAGONAL_WALL;
        BlockProgrammableWall glass=(BlockProgrammableWall)ModBlocks.PROGRAMMABLE_DIAGONAL_GLASS;
        for(int mode=0;mode<3;mode++)for(int path=0;path<2;path++){
            BlockPos root=new BlockPos(x-6+mode*5,y+1+path*3,-18);
            IBlockState state=wall.getDefaultState(),window=glass.getDefaultState();
            BlockPos next=mode==2?root.south():root.up();window=mode==2?window.withProperty(BlockProgrammableWall.FACING,EnumFacing.SOUTH):window.withProperty(BlockProgrammableWall.INVERTED,true);
            world.setBlockState(root,state,3);world.setBlockState(next,window,3);
            TileEntityAnimatedScreenSelector tile=(TileEntityAnimatedScreenSelector)world.getTileEntity(root),pane=(TileEntityAnimatedScreenSelector)world.getTileEntity(next);
            tile.setDiagonalGeometry(mode,0);pane.setDiagonalGeometry(mode,0);pane.setGlassAppearance(0,true);if(path==1)tile.setMainTextureLayout(1);
            if(DiagonalPanelGeometry.coveredEnd(world,root,state,true))throw new IllegalStateException("Glass hides the opaque diagonal wall cap");
        }
    }
    static void flatGlassWallCaps(World world,int x,int y){
        world.setWorldTime(6000);world.getGameRules().setOrCreateGameRule("doDaylightCycle","false");
        BlockProgrammableWall wall=(BlockProgrammableWall)ModBlocks.PROGRAMMABLE_DIAGONAL_WALL;
        BlockProgrammableGlass glass=(BlockProgrammableGlass)net.minecraft.block.Block.getBlockFromName("vandorlabs:programmable_glass");
        for(int column=0;column<3;column++)for(int path=0;path<2;path++){
            int mode=column==1?1:0;BlockPos root=new BlockPos(x-6+column*5,y+1+path*4,-18);
            IBlockState state=wall.getDefaultState().withProperty(BlockProgrammableWall.INVERTED,column==2);
            world.setBlockState(root,state,3);TileEntityAnimatedScreenSelector tile=(TileEntityAnimatedScreenSelector)world.getTileEntity(root);tile.setDiagonalGeometry(mode,0);if(path==1)tile.setMainTextureLayout(1);
            for(EnumFacing side:new EnumFacing[]{EnumFacing.UP,EnumFacing.DOWN}){
                int depth=PanelJoins.placementDepth(world,root,state,side);BlockPos next=root.offset(side);
                world.setBlockState(next,glass.getDefaultState().withProperty(BlockGlassWall.DEPTH,depth),3);
                ((TileEntityProgrammableGlass)world.getTileEntity(next)).setAppearance(0,column!=2);
                if(BlockProgrammableWall.coveredFlatEnd(state,world,root,side==EnumFacing.UP))throw new IllegalStateException("Flat glass hides opaque diagonal wall cap");
            }
        }
    }
    static void mixed(World world,int x,int y,boolean glass){
        world.setWorldTime(6000);world.getGameRules().setOrCreateGameRule("doDaylightCycle","false");
        for(int mode=0;mode<2;mode++)for(int material=0;material<2;material++){
            BlockPos root=new BlockPos(x-7+mode*5+material*10,y+1,-18);
            BlockProgrammableWall block=(BlockProgrammableWall)(glass?ModBlocks.PROGRAMMABLE_DIAGONAL_GLASS:ModBlocks.PROGRAMMABLE_DIAGONAL_WALL);
            world.setBlockState(root,block.getDefaultState(),3);
            TileEntityAnimatedScreenSelector tile=(TileEntityAnimatedScreenSelector)world.getTileEntity(root);tile.setDiagonalGeometry(mode,0);if(glass)tile.setGlassShade(12);
            IBlockState flat=material==0?ModBlocks.PROGRAMMABLE_WALL.getDefaultState():net.minecraft.block.Block.REGISTRY.getObject(new net.minecraft.util.ResourceLocation("vandorlabs:programmable_glass")).getDefaultState();
            int upper=PanelJoins.placementDepth(world,root,block.getDefaultState(),EnumFacing.UP),lower=PanelJoins.placementDepth(world,root,block.getDefaultState(),EnumFacing.DOWN);
            net.minecraft.block.properties.PropertyInteger depthProperty=material==0?BlockProgrammableWall.DEPTH:BlockGlassWall.DEPTH;
            world.setBlockState(root.up(),flat.withProperty(depthProperty,upper),3);world.setBlockState(root.down(),flat.withProperty(depthProperty,lower),3);
            IBlockState arm=material==0?flat.withProperty(BlockProgrammableWall.FACING,EnumFacing.EAST):flat.withProperty(BlockGlassWall.ROTATED,true);
            world.setBlockState(root.north(),arm,3);
        }
    }
    static void finishes(World world,int x,int y,int row){
        for(int i=0;i<4;i++){
            int shade=row*4+i;if(!GlassTextures.valid(shade))break;
            BlockPos root=new BlockPos(x-6+i*4,y+1,-18);
            world.setBlockState(root,net.minecraft.block.Block.REGISTRY.getObject(new net.minecraft.util.ResourceLocation("vandorlabs:programmable_glass")).getDefaultState(),3);
            ((TileEntityProgrammableGlass)world.getTileEntity(root)).setShade(shade);
            world.setBlockState(root.east(),ModBlocks.PROGRAMMABLE_DIAGONAL_GLASS.getDefaultState(),3);
            TileEntityAnimatedScreenSelector tile=(TileEntityAnimatedScreenSelector)world.getTileEntity(root.east());tile.setGlassShade(shade);tile.setDiagonalGeometry(1,0);
        }
    }
    static void build(World world,String name,int x,int y){
        world.setWorldTime(6000);world.getGameRules().setOrCreateGameRule("doDaylightCycle","false");
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
