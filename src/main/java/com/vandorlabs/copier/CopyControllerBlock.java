package com.vandorlabs.copier;

import com.vandorlabs.blocks.BlockVandorDirectional;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.*;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/** Input is left, output right and the supply chest behind the authored front. */
public final class CopyControllerBlock extends BlockVandorDirectional {
    public CopyControllerBlock(){super("copy_controller");}
    @Override public boolean hasTileEntity(IBlockState state){return true;}
    @Override public TileEntity createTileEntity(World world,IBlockState state){return new CopyControllerTile();}
    @Override public void onBlockPlacedBy(World world,BlockPos pos,IBlockState state,EntityLivingBase placer,ItemStack stack){super.onBlockPlacedBy(world,pos,state,placer,stack);TileEntity tile=world.getTileEntity(pos);if(tile instanceof CopyControllerTile){((CopyControllerTile)tile).actor=placer.getUniqueID();if(placer instanceof net.minecraft.entity.player.EntityPlayer)com.vandorlabs.tiles.WorldActionAuthorization.authorize(tile,(net.minecraft.entity.player.EntityPlayer)placer);tile.markDirty();}}
    @Override public boolean onBlockActivated(World world,BlockPos pos,IBlockState state,EntityPlayer player,EnumHand hand,EnumFacing side,float x,float y,float z){if(hand!=EnumHand.MAIN_HAND||player.getHeldItem(hand).getItem() instanceof net.minecraft.item.ItemBlock)return false;if(!world.isRemote&&com.vandorlabs.items.ConfigurationAccess.canEditDialog(player,world,pos))player.openGui(com.vandorlabs.VandorLabs.instance,26,world,pos.getX(),pos.getY(),pos.getZ());return true;}
    @Override public boolean removedByPlayer(IBlockState state,World world,BlockPos pos,EntityPlayer player,boolean harvest){TileEntity raw=world.getTileEntity(pos);if(raw instanceof CopyControllerTile&&((CopyControllerTile)raw).busy()){if(!world.isRemote)player.sendStatusMessage(new net.minecraft.util.text.TextComponentString("Finish the copy transaction before removing its controller"),true);return false;}return super.removedByPlayer(state,world,pos,player,harvest);}
    @Override public void breakBlock(World world,BlockPos pos,IBlockState state){TileEntity raw=world.getTileEntity(pos);if(raw instanceof CopyControllerTile&&!world.isRemote)((CopyControllerTile)raw).release();super.breakBlock(world,pos,state);}
    @Override public float getExplosionResistance(World world,BlockPos pos,net.minecraft.entity.Entity exploder,net.minecraft.world.Explosion explosion){TileEntity raw=world.getTileEntity(pos);return raw instanceof CopyControllerTile&&((CopyControllerTile)raw).busy()?6000000:super.getExplosionResistance(world,pos,exploder,explosion);}
}
