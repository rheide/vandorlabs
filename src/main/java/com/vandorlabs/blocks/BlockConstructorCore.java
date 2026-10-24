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
    public final String design;public final int width,height,depth;public static final int DEPTH=3;
    public BlockConstructorCore(String design,int width,int height){this(FrameMachineDesign.get(design));}
    public BlockConstructorCore(FrameMachineDesign spec){super(spec.block);this.design=spec.design;this.width=spec.width;this.height=spec.height;this.depth=spec.depth;setDefaultState(getDefaultState().withProperty(FORMED,false));}
    public FrameMachineDesign specification(){return FrameMachineDesign.get(design);}
    @Override protected BlockStateContainer createBlockState(){return new BlockStateContainer(this,FACING,FORMED);}
    @Override public IBlockState getStateFromMeta(int meta){return super.getStateFromMeta(meta&3).withProperty(FORMED,(meta&4)!=0);}
    @Override public int getMetaFromState(IBlockState state){return super.getMetaFromState(state)|(state.getValue(FORMED)?4:0);}
    @Override public boolean hasTileEntity(IBlockState state){return true;}
    @Override public TileEntity createTileEntity(World world,IBlockState state){return new TileEntityConstructor();}
    @Override public boolean isOpaqueCube(IBlockState state){return !state.getValue(FORMED);}
    @Override public boolean isFullCube(IBlockState state){return !state.getValue(FORMED);}
    @Override public int getLightOpacity(IBlockState state){return state.getValue(FORMED)?0:super.getLightOpacity(state);}
    @Override public boolean onBlockActivated(World world,BlockPos pos,IBlockState state,EntityPlayer player,EnumHand hand,EnumFacing side,float x,float y,float z){
        if(!world.isRemote){TileEntityConstructor tile=(TileEntityConstructor)world.getTileEntity(pos);
            if(state.getValue(FORMED) && tile.mount()!=null && tile.mount().hit(player))return TokenSlotInteractions.activate(tile,player,hand);
            if(state.getValue(FORMED) && player.isSneaking())tile.dismantle();else if(!state.getValue(FORMED) && !tile.assemble())player.sendStatusMessage(new net.minecraft.util.text.TextComponentString(width+" x "+depth+" x "+height+" frame: "+tile.assemblyProblem()),true);
            else if(state.getValue(FORMED)&&specification().state_models!=null){tile.cycleActivity();player.sendStatusMessage(new net.minecraft.util.text.TextComponentString("Activity: "+new String[]{"Off","Idle","Computing"}[tile.activity()]),true);}
        }return true;
    }
    @Override public void breakBlock(World world,BlockPos pos,IBlockState state){TileEntity tile=world.getTileEntity(pos);if(tile instanceof TileEntityConstructor)((TileEntityConstructor)tile).dismantle(state);TokenSlotInteractions.drop(world,pos);super.breakBlock(world,pos,state);}
    @Override public int damageDropped(IBlockState state){return 0;}
    @Override public void updateTick(World world,BlockPos pos,IBlockState state,java.util.Random random){TokenSlotInteractions.tick(world,pos);}
    @Override public boolean canProvidePower(IBlockState state){return specification().hasChip();}
    @Override public int getWeakPower(IBlockState state,net.minecraft.world.IBlockAccess world,BlockPos pos,EnumFacing side){TileEntity tile=world.getTileEntity(pos);return tile instanceof TileEntityConstructor?((TileEntityConstructor)tile).getOutputLevel():0;}
}
