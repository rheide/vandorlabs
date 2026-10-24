package com.vandorlabs.blocks;

import com.vandorlabs.navigation.*;
import com.vandorlabs.tiles.TileEntityNavigationTable;
import net.minecraft.block.properties.*;
import net.minecraft.block.state.*;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.*;
import net.minecraft.util.math.*;
import net.minecraft.world.*;

/** Supplied single-cell OBJ variants, joined through cached assembly assignments. */
public final class BlockNavigationTable extends BlockVandorDirectional {
    public static final PropertyInteger CONNECTIONS=PropertyInteger.create("connections",0,15);
    public static final PropertyBool CHIP=PropertyBool.create("chip");
    public final NavigationLayout.Kind kind;
    public BlockNavigationTable(String name,NavigationLayout.Kind kind){super(name);this.kind=kind;setLightOpacity(0);setDefaultState(getDefaultState().withProperty(CONNECTIONS,0).withProperty(CHIP,false));}
    @Override protected BlockStateContainer createBlockState(){return new BlockStateContainer(this,FACING,CONNECTIONS,CHIP);}
    @Override public IBlockState getActualState(IBlockState state,IBlockAccess world,BlockPos pos){TileEntity tile=world.getTileEntity(pos);return tile instanceof TileEntityNavigationTable?((TileEntityNavigationTable)tile).renderState(state):state;}
    @Override public boolean hasTileEntity(IBlockState state){return true;}
    @Override public TileEntity createTileEntity(World world,IBlockState state){return new TileEntityNavigationTable();}
    @Override public boolean isOpaqueCube(IBlockState state){return false;}
    @Override public boolean isFullCube(IBlockState state){return false;}
    @Override public AxisAlignedBB getBoundingBox(IBlockState state,IBlockAccess world,BlockPos pos){return new AxisAlignedBB(0,0,0,1,14D/16,1);}
    @Override public void onBlockAdded(World world,BlockPos pos,IBlockState state){NavigationAssemblies.around(world,pos);}
    @Override public void neighborChanged(IBlockState state,World world,BlockPos pos,net.minecraft.block.Block block,BlockPos from){NavigationAssemblies.around(world,pos);}
    @Override public void breakBlock(World world,BlockPos pos,IBlockState state){TokenSlotInteractions.drop(world,pos);super.breakBlock(world,pos,state);NavigationAssemblies.around(world,pos);}
    @Override public void updateTick(World world,BlockPos pos,IBlockState state,java.util.Random random){TokenSlotInteractions.tick(world,pos);}
    @Override public boolean canProvidePower(IBlockState state){return true;}
    @Override public int getWeakPower(IBlockState state,IBlockAccess world,BlockPos pos,EnumFacing side){TileEntity tile=world.getTileEntity(pos);return tile instanceof TileEntityNavigationTable?((TileEntityNavigationTable)tile).getOutputLevel():0;}
    @Override public boolean onBlockActivated(World world,BlockPos pos,IBlockState state,EntityPlayer player,EnumHand hand,EnumFacing side,float x,float y,float z){
        TileEntity tile=world.getTileEntity(pos);if(!(tile instanceof TileEntityNavigationTable))return false;
        TileEntityNavigationTable reader=((TileEntityNavigationTable)tile).reader();
        return reader!=null&&(player.isSneaking()||player.getHeldItem(hand).getItem()==com.vandorlabs.items.ModItems.CONFIGURIZER||reader.mount()!=null&&reader.mount().hit(player))&&TokenSlotInteractions.activate(reader,player,hand);
    }
}
