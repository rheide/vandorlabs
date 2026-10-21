package com.vandorlabs.blocks;

import com.vandorlabs.tiles.TileEntityTokenSlot;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.*;
import net.minecraft.util.math.*;
import net.minecraft.world.*;

/** Compact surface-mounted card/chip reader, using the Wall Slider's mount rules. */
public final class BlockTokenSlot extends BlockVandorSwitch {
    public final boolean chip;
    public BlockTokenSlot(String name,boolean chip){super(name);this.chip=chip;}
    @Override public net.minecraft.tileentity.TileEntity createTileEntity(World world,IBlockState state){return new TileEntityTokenSlot(chip);}
    @Override public AxisAlignedBB getBoundingBox(IBlockState state,IBlockAccess world,BlockPos pos){
        state=getActualState(state,world,pos);EnumFacing facing=state.getValue(FACING);int rotation=state.getValue(ROTATION);double half=3D/16,h=(chip?3:4.4)/16;
        Vec3d a=TokenSlotGeometry.point(facing,rotation,-half,-h,0),b=TokenSlotGeometry.point(facing,rotation,half,h,.10);return new AxisAlignedBB(a.x,a.y,a.z,b.x,b.y,b.z);
    }
    @Override public int getLightValue(IBlockState state,IBlockAccess world,BlockPos pos){return 0;}
    @Override public boolean onBlockActivated(World world,BlockPos pos,IBlockState state,EntityPlayer player,EnumHand hand,EnumFacing facing,float x,float y,float z){
        net.minecraft.tileentity.TileEntity raw=world.getTileEntity(pos);if(!(raw instanceof TileEntityTokenSlot))return false;TileEntityTokenSlot tile=(TileEntityTokenSlot)raw;
        if(!tile.isUsableByPlayer(player))return false;
        ItemStack held=player.getHeldItem(hand);
        if(player.isSneaking()||held.getItem()==com.vandorlabs.items.ModItems.CONFIGURIZER){if(!world.isRemote)player.openGui(com.vandorlabs.VandorLabs.instance,com.vandorlabs.GuiHandler.GUI_TOKEN_SLOT,world,pos.getX(),pos.getY(),pos.getZ());return true;}
        if(held.isEmpty()){
            if(tile.isEmpty()&&hand==EnumHand.MAIN_HAND&&tile.accepts(player.getHeldItemOffhand()))return false;
            if(!world.isRemote&&tile.isEmpty())tile.cycleLogo();
            else if(!world.isRemote){ItemStack removed=tile.removeStackFromSlot(0);if(!player.inventory.addItemStackToInventory(removed)&&!removed.isEmpty())player.dropItem(removed,false);}return true;
        }
        if(!tile.accepts(held))return false;
        if(!tile.isEmpty())return true;
        if(tile.behavior>=2){if(!world.isRemote)tile.insertToggle(player,held);return true;}
        if(!world.isRemote){ItemStack inserted=held.copy();inserted.setCount(1);tile.setInventorySlotContents(0,inserted);if(!player.capabilities.isCreativeMode)held.shrink(1);}return true;
    }
    @Override public void updateTick(World world,BlockPos pos,IBlockState state,java.util.Random random){net.minecraft.tileentity.TileEntity raw=world.getTileEntity(pos);if(raw instanceof TileEntityTokenSlot)((TileEntityTokenSlot)raw).scheduledTick();}
    @Override public void breakBlock(World world,BlockPos pos,IBlockState state){
        net.minecraft.tileentity.TileEntity raw=world.getTileEntity(pos);
        if(!world.isRemote&&!com.vandorlabs.vehicle.VehicleTransfer.isMutating(world)&&raw instanceof TileEntityTokenSlot){ItemStack stack=((TileEntityTokenSlot)raw).takeForDrop();if(!stack.isEmpty())net.minecraft.inventory.InventoryHelper.spawnItemStack(world,pos.getX()+.5,pos.getY()+.5,pos.getZ()+.5,stack);}
        super.breakBlock(world,pos,state);
    }
}
