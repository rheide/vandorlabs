package com.vandorlabs.blocks;

import com.vandorlabs.tiles.TileEntityTurntableController;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.*;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.*;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public final class BlockTurntableController extends BlockVandorDirectional {
    public static final net.minecraft.block.properties.PropertyBool ACTIVE=net.minecraft.block.properties.PropertyBool.create("active"),CLOCKWISE=net.minecraft.block.properties.PropertyBool.create("clockwise");
    public BlockTurntableController(){super("turntable_controller");setDefaultState(getDefaultState().withProperty(ACTIVE,false).withProperty(CLOCKWISE,true));}
    @Override protected net.minecraft.block.state.BlockStateContainer createBlockState(){return new net.minecraft.block.state.BlockStateContainer(this,FACING,ACTIVE,CLOCKWISE);}
    @Override public IBlockState getActualState(IBlockState state,net.minecraft.world.IBlockAccess world,BlockPos pos){TileEntity raw=world.getTileEntity(pos);return raw instanceof TileEntityTurntableController?state.withProperty(ACTIVE,((TileEntityTurntableController)raw).running()).withProperty(CLOCKWISE,((TileEntityTurntableController)raw).clockwise):state;}
    @Override public boolean hasTileEntity(IBlockState state){return true;}
    @Override public TileEntity createTileEntity(World world,IBlockState state){return new TileEntityTurntableController();}
    @Override public void onBlockPlacedBy(World world,BlockPos pos,IBlockState state,EntityLivingBase placer,ItemStack stack){super.onBlockPlacedBy(world,pos,state,placer,stack);TileEntity tile=world.getTileEntity(pos);if(tile instanceof TileEntityTurntableController){((TileEntityTurntableController)tile).actor=placer.getUniqueID();tile.markDirty();}}
    @Override public boolean onBlockActivated(World world,BlockPos pos,IBlockState state,EntityPlayer player,EnumHand hand,EnumFacing face,float x,float y,float z){if(hand!=EnumHand.MAIN_HAND)return false;if(!world.isRemote&&com.vandorlabs.items.ConfigurationAccess.canEditDialog(player,world,pos))player.openGui(com.vandorlabs.VandorLabs.instance,com.vandorlabs.GuiHandler.GUI_TURNTABLE,world,pos.getX(),pos.getY(),pos.getZ());return true;}
    @Override public boolean removedByPlayer(IBlockState state,World world,BlockPos pos,EntityPlayer player,boolean harvest){TileEntity tile=world.getTileEntity(pos);if(tile instanceof TileEntityTurntableController&&((TileEntityTurntableController)tile).captureId!=null){if(!world.isRemote)player.sendStatusMessage(new net.minecraft.util.text.TextComponentString("Restore the turntable contents before removing its controller"),true);return false;}return super.removedByPlayer(state,world,pos,player,harvest);}
    @Override public void breakBlock(World world,BlockPos pos,IBlockState state){TileEntity tile=world.getTileEntity(pos);if(tile instanceof TileEntityTurntableController&&!world.isRemote)((TileEntityTurntableController)tile).release();super.breakBlock(world,pos,state);}
    @Override public boolean canDropFromExplosion(net.minecraft.world.Explosion explosion){return false;}
    @Override public float getExplosionResistance(World world,BlockPos pos,net.minecraft.entity.Entity exploder,net.minecraft.world.Explosion explosion){TileEntity tile=world.getTileEntity(pos);return tile instanceof TileEntityTurntableController&&((TileEntityTurntableController)tile).captureId!=null?6000000:super.getExplosionResistance(world,pos,exploder,explosion);}
}
