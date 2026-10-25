package com.vandorlabs.copier;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.items.*;

/** A transaction owns this exact serialized tile; inventory consumers can neither extract nor insert. */
final class CopyFrozenTile extends TileEntity implements IInventory {
    private final NBTTagCompound saved;
    private static final IItemHandler EMPTY=new ItemStackHandler(0);
    CopyFrozenTile(NBTTagCompound saved){this.saved=saved.copy();super.readFromNBT(saved);}
    NBTTagCompound saved(){return saved.copy();}
    @Override public NBTTagCompound writeToNBT(NBTTagCompound ignored){return saved.copy();}
    @Override public NBTTagCompound getUpdateTag(){return saved.copy();}
    @Override public boolean hasCapability(Capability<?> cap,EnumFacing side){return cap==CapabilityItemHandler.ITEM_HANDLER_CAPABILITY;}
    @Override public <T>T getCapability(Capability<T> cap,EnumFacing side){return cap==CapabilityItemHandler.ITEM_HANDLER_CAPABILITY?CapabilityItemHandler.ITEM_HANDLER_CAPABILITY.cast(EMPTY):null;}
    @Override public int getSizeInventory(){return 0;}
    @Override public boolean isEmpty(){return true;}
    @Override public ItemStack getStackInSlot(int slot){return ItemStack.EMPTY;}
    @Override public ItemStack decrStackSize(int slot,int count){return ItemStack.EMPTY;}
    @Override public ItemStack removeStackFromSlot(int slot){return ItemStack.EMPTY;}
    @Override public void setInventorySlotContents(int slot,ItemStack stack){}
    @Override public int getInventoryStackLimit(){return 0;}
    @Override public boolean isUsableByPlayer(EntityPlayer player){return false;}
    @Override public void openInventory(EntityPlayer player){}
    @Override public void closeInventory(EntityPlayer player){}
    @Override public boolean isItemValidForSlot(int slot,ItemStack stack){return false;}
    @Override public int getField(int id){return 0;}
    @Override public void setField(int id,int value){}
    @Override public int getFieldCount(){return 0;}
    @Override public void clear(){}
    @Override public String getName(){return "Copy checkpoint";}
    @Override public boolean hasCustomName(){return false;}
    @Override public net.minecraft.util.text.ITextComponent getDisplayName(){return new net.minecraft.util.text.TextComponentString(getName());}
}
