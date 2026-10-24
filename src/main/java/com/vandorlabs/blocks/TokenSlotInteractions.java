package com.vandorlabs.blocks;

import com.vandorlabs.tiles.TileEntityTokenSlot;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/** The same physical-item interaction for standalone and model-integrated mounts. */
public final class TokenSlotInteractions {
    private TokenSlotInteractions(){}
    public static boolean activate(TileEntityTokenSlot tile,EntityPlayer player,EnumHand hand){
        World world=tile.getWorld();BlockPos pos=tile.getPos();
        if(!tile.isUsableByPlayer(player))return false;
        ItemStack held=player.getHeldItem(hand);
        if(player.isSneaking()||held.getItem()==com.vandorlabs.items.ModItems.CONFIGURIZER){
            if(!world.isRemote)player.openGui(com.vandorlabs.VandorLabs.instance,com.vandorlabs.GuiHandler.GUI_TOKEN_SLOT,world,pos.getX(),pos.getY(),pos.getZ());return true;
        }
        if(held.isEmpty()){
            if(tile.isEmpty()&&hand==EnumHand.MAIN_HAND&&tile.accepts(player.getHeldItemOffhand()))return false;
            if(!world.isRemote&&tile.isEmpty())tile.cycleLogo();
            else if(!world.isRemote){ItemStack removed=tile.removeStackFromSlot(0);if(!player.inventory.addItemStackToInventory(removed)&&!removed.isEmpty())player.dropItem(removed,false);}return true;
        }
        if(!tile.accepts(held))return false;
        if(!tile.authorized(held)){tile.reject();return true;}
        if(!tile.isEmpty())return true;
        if(tile.behavior>=2){if(!world.isRemote)tile.insertToggle(player,held);return true;}
        if(!world.isRemote){ItemStack inserted=held.copy();inserted.setCount(1);tile.setInventorySlotContents(0,inserted);if(!player.capabilities.isCreativeMode)held.shrink(1);}return true;
    }
    public static void tick(World world,BlockPos pos){
        net.minecraft.tileentity.TileEntity raw=world.getTileEntity(pos);if(raw instanceof TileEntityTokenSlot)((TileEntityTokenSlot)raw).scheduledTick();
    }
    public static void drop(World world,BlockPos pos){
        net.minecraft.tileentity.TileEntity raw=world.getTileEntity(pos);
        if(!world.isRemote&&!com.vandorlabs.vehicle.VehicleTransfer.isMutating(world)&&raw instanceof TileEntityTokenSlot){
            ItemStack stack=((TileEntityTokenSlot)raw).takeForDrop();if(!stack.isEmpty())net.minecraft.inventory.InventoryHelper.spawnItemStack(world,pos.getX()+.5,pos.getY()+.5,pos.getZ()+.5,stack);
        }
    }
}
