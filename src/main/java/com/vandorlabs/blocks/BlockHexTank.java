package com.vandorlabs.blocks;

import com.vandorlabs.tiles.*;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.*;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.*;

public final class BlockHexTank extends BlockAnimatedScreenSelector {
    public BlockHexTank(){super("hexagonal_fuel_tank");}
    @Override public TileEntity createNewTileEntity(World world,int meta){return new TileEntityHexTank();}
    @Override public boolean isOpaqueCube(IBlockState state){return false;}
    @Override public boolean isFullCube(IBlockState state){return false;}
    @Override protected EnumFacing placementFacing(World world,BlockPos pos,EnumFacing clicked,net.minecraft.entity.EntityLivingBase placer){return clicked;}
    @Override public boolean onBlockActivated(World world,BlockPos pos,IBlockState state,EntityPlayer player,EnumHand hand,EnumFacing side,float x,float y,float z){
        TileEntity raw=world.getTileEntity(pos);if(!(raw instanceof TileEntityHexTank))return false;
        if(player.getHeldItem(hand).getItem() instanceof net.minecraft.item.ItemBlock)return false;
        if(net.minecraftforge.fluids.FluidUtil.interactWithFluidHandler(player,hand,(TileEntityHexTank)raw))return true;
        if(player.getHeldItem(hand).getItem()==com.vandorlabs.items.ModItems.CONFIGURIZER&&com.vandorlabs.items.ConfigurationAccess.canEditDialog(player,world,pos)){
            if(!world.isRemote)player.openGui(com.vandorlabs.VandorLabs.instance,com.vandorlabs.GuiHandler.GUI_ANIMATED_SCREEN_SELECTOR,world,pos.getX(),pos.getY(),pos.getZ());return true;
        }
        if(!world.isRemote)player.openGui(com.vandorlabs.VandorLabs.instance,com.vandorlabs.GuiHandler.GUI_STORAGE_STATUS,world,pos.getX(),pos.getY(),pos.getZ());return true;
    }
}
