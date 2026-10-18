package com.vandorlabs.blocks;

import com.vandorlabs.tiles.TileEntityConstructor;
import net.minecraft.block.Block;
import net.minecraft.block.properties.PropertyBool;
import net.minecraft.block.state.*;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.*;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/** Creative-only assembly seed. The complete model is drawn once by the formed core. */
public final class BlockConstructorCore extends BlockVandorDirectional {
    public static final PropertyBool FORMED=PropertyBool.create("formed");
    public final String design;public final int width,height;public static final int DEPTH=3;
    public BlockConstructorCore(String design,int width,int height){super(design+"_core");this.design=design;this.width=width;this.height=height;setDefaultState(getDefaultState().withProperty(FORMED,false));}
    @Override protected BlockStateContainer createBlockState(){return new BlockStateContainer(this,FACING,FORMED);}
    @Override public IBlockState getStateFromMeta(int meta){return super.getStateFromMeta(meta&3).withProperty(FORMED,(meta&4)!=0);}
    @Override public int getMetaFromState(IBlockState state){return super.getMetaFromState(state)|(state.getValue(FORMED)?4:0);}
    @Override public boolean hasTileEntity(IBlockState state){return true;}
    @Override public TileEntity createTileEntity(World world,IBlockState state){return new TileEntityConstructor();}
    @Override public boolean onBlockActivated(World world,BlockPos pos,IBlockState state,EntityPlayer player,EnumHand hand,EnumFacing side,float x,float y,float z){
        if(!world.isRemote){TileEntityConstructor tile=(TileEntityConstructor)world.getTileEntity(pos);
            if(state.getValue(FORMED) && player.isSneaking())tile.dismantle();else if(!state.getValue(FORMED) && !tile.assemble())player.sendStatusMessage(new net.minecraft.util.text.TextComponentString(width+" x 3 x "+height+" frame: "+tile.assemblyProblem()),true);
        }return true;
    }
    @Override public void breakBlock(World world,BlockPos pos,IBlockState state){TileEntity tile=world.getTileEntity(pos);if(tile instanceof TileEntityConstructor)((TileEntityConstructor)tile).dismantle(state);super.breakBlock(world,pos,state);}
    @Override public int damageDropped(IBlockState state){return 0;}
}
