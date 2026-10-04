package com.vandorlabs.blocks;

import com.vandorlabs.tiles.TileEntityRedstoneScreen;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.*;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public final class BlockProgrammableDiagonalRedstoneScreen extends BlockProgrammableDiagonalScreen {
    public BlockProgrammableDiagonalRedstoneScreen(){super("programmable_diagonal_redstone_screen");}
    @Override public TileEntity createNewTileEntity(World world,int meta){return new TileEntityRedstoneScreen();}
    @Override public boolean onBlockActivated(World world,BlockPos pos,IBlockState state,EntityPlayer player,
            EnumHand hand,EnumFacing facing,float hitX,float hitY,float hitZ){
        return RedstoneScreenInteractions.activate(world,pos,state,player,hand);
    }
}
