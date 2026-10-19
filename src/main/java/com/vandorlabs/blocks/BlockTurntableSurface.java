package com.vandorlabs.blocks;

import com.vandorlabs.tiles.TileEntityTurntableSurface;
import net.minecraft.block.properties.PropertyBool;
import net.minecraft.block.properties.PropertyDirection;
import net.minecraft.block.state.*;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.*;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.*;

/** The clicked face is the active face, including walls and ceilings. */
public final class BlockTurntableSurface extends BlockVandor {
    public static final PropertyDirection FACING=PropertyDirection.create("facing");
    public static final PropertyBool FORMED=PropertyBool.create("formed");
    public BlockTurntableSurface(){super("turntable_surface");setDefaultState(blockState.getBaseState().withProperty(FACING,EnumFacing.UP).withProperty(FORMED,false));}
    @Override protected BlockStateContainer createBlockState(){return new BlockStateContainer(this,FACING,FORMED);}
    @Override public IBlockState getStateFromMeta(int meta){return getDefaultState().withProperty(FACING,EnumFacing.getFront(Math.floorMod(meta,6)));}
    @Override public int getMetaFromState(IBlockState state){return state.getValue(FACING).getIndex();}
    @Override public IBlockState getStateForPlacement(World world,BlockPos pos,EnumFacing face,float x,float y,float z,int meta,EntityLivingBase placer){IBlockState adjacent=world.getBlockState(pos.offset(face.getOpposite()));if(adjacent.getBlock()==this&&adjacent.getValue(FACING).getAxis()!=face.getAxis())face=adjacent.getValue(FACING);return getDefaultState().withProperty(FACING,face);}
    @Override public boolean hasTileEntity(IBlockState state){return true;}
    @Override public TileEntity createTileEntity(World world,IBlockState state){return new TileEntityTurntableSurface();}
    public static boolean locked(IBlockAccess world,BlockPos pos){TileEntity raw=world.getTileEntity(pos);return raw instanceof TileEntityTurntableSurface&&((TileEntityTurntableSurface)raw).locked();}
    @Override public boolean removedByPlayer(IBlockState state,World world,BlockPos pos,net.minecraft.entity.player.EntityPlayer player,boolean harvest){
        if(locked(world,pos)){if(!world.isRemote)player.sendStatusMessage(new net.minecraft.util.text.TextComponentString("Restore the turntable contents before removing its Surface blocks"),true);return false;}
        return super.removedByPlayer(state,world,pos,player,harvest);
    }
    @Override public float getPlayerRelativeBlockHardness(IBlockState state,net.minecraft.entity.player.EntityPlayer player,World world,BlockPos pos){return locked(world,pos)?0:super.getPlayerRelativeBlockHardness(state,player,world,pos);}
    @Override public boolean canEntityDestroy(IBlockState state,IBlockAccess world,BlockPos pos,net.minecraft.entity.Entity entity){return !locked(world,pos)&&super.canEntityDestroy(state,world,pos,entity);}
    @Override public float getExplosionResistance(World world,BlockPos pos,net.minecraft.entity.Entity entity,net.minecraft.world.Explosion explosion){return locked(world,pos)?6000000:super.getExplosionResistance(world,pos,entity,explosion);}
    @Override public net.minecraft.block.material.EnumPushReaction getMobilityFlag(IBlockState state){return net.minecraft.block.material.EnumPushReaction.BLOCK;}
    @Override public IBlockState getActualState(IBlockState state,IBlockAccess world,BlockPos pos){
        TileEntity raw=world.getTileEntity(pos);
        if(raw instanceof TileEntityTurntableSurface){
            com.vandorlabs.tiles.TileEntityTurntableController controller=((TileEntityTurntableSurface)raw).controller();
            if(controller!=null && controller.layout!=null)return state.withProperty(FORMED,true).withProperty(FACING,controller.layout.normal);
        }
        return state.withProperty(FORMED,false);
    }
    @Override public void breakBlock(World world,BlockPos pos,IBlockState state){
        TileEntity raw=world.getTileEntity(pos);
        if(!world.isRemote && raw instanceof TileEntityTurntableSurface){com.vandorlabs.tiles.TileEntityTurntableController controller=((TileEntityTurntableSurface)raw).controller();if(controller!=null)controller.surfaceChanged();}
        super.breakBlock(world,pos,state);
    }
    @Override public IBlockState withRotation(IBlockState state,Rotation rotation){return state.withProperty(FACING,rotation.rotate(state.getValue(FACING)));}
    @Override public IBlockState withMirror(IBlockState state,Mirror mirror){return state.withProperty(FACING,mirror.mirror(state.getValue(FACING)));}
}
