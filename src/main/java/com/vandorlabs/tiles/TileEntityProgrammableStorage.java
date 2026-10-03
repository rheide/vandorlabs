package com.vandorlabs.tiles;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.ItemStackHelper;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.NonNullList;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.wrapper.InvWrapper;

/** Shared programmable finish with a separate, persistent chest-sized inventory. */
public class TileEntityProgrammableStorage extends TileEntityAnimatedScreenSelector implements IInventory {
    private final NonNullList<ItemStack> items = NonNullList.withSize(27, ItemStack.EMPTY);
    private final InvWrapper itemHandler = new InvWrapper(this);

    public TileEntityProgrammableStorage() { setHousingTexture(ScreenHousingTextures.DEFAULT_STORAGE); }
    @Override protected void finishLoading() { } // Storage has no redstone-channel behavior.
    @Override public boolean shouldRenderInPass(int pass) { return false; }
    // Explicit bridge is required: the inherited mod helper has an unmapped name,
    // whereas IInventory's method is remapped to func_70300_a in the shipped jar.
    @Override public boolean isUsableByPlayer(EntityPlayer player) { return super.isUsableByPlayer(player); }
    @Override public int getSizeInventory() { return items.size(); }
    @Override public boolean isEmpty() { for (ItemStack item : items) if (!item.isEmpty()) return false; return true; }
    @Override public ItemStack getStackInSlot(int slot) { return items.get(slot); }
    @Override public ItemStack decrStackSize(int slot, int count) {
        ItemStack result = ItemStackHelper.getAndSplit(items, slot, count);
        if (!result.isEmpty()) markDirty();
        return result;
    }
    @Override public ItemStack removeStackFromSlot(int slot) {
        ItemStack result = ItemStackHelper.getAndRemove(items, slot);
        if (!result.isEmpty()) markDirty();
        return result;
    }
    @Override public void setInventorySlotContents(int slot, ItemStack stack) {
        items.set(slot, stack);
        if (!stack.isEmpty() && stack.getCount() > Math.min(getInventoryStackLimit(), stack.getMaxStackSize()))
            stack.setCount(Math.min(getInventoryStackLimit(), stack.getMaxStackSize()));
        markDirty();
    }
    @Override public int getInventoryStackLimit() { return 64; }
    @Override public boolean isItemValidForSlot(int slot, ItemStack stack) { return true; }
    @Override public void openInventory(EntityPlayer player) { }
    @Override public void closeInventory(EntityPlayer player) { }
    @Override public int getField(int id) { return 0; }
    @Override public void setField(int id, int value) { }
    @Override public int getFieldCount() { return 0; }
    @Override public void clear() { items.clear(); markDirty(); }
    @Override public String getName() { return "tile.vandorlabs.programmable_storage.name"; }
    @Override public boolean hasCustomName() { return false; }
    @Override public net.minecraft.util.text.ITextComponent getDisplayName() {
        return new net.minecraft.util.text.TextComponentTranslation(getName());
    }
    @Override public NBTTagCompound writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag);
        ItemStackHelper.saveAllItems(tag, items);
        return tag;
    }
    @Override public void readFromNBT(NBTTagCompound tag) {
        if (!tag.hasKey("housingTexture", 3)) tag.setInteger("housingTexture", ScreenHousingTextures.DEFAULT_STORAGE);
        super.readFromNBT(tag);
        if (tag.hasKey("Items", 9)) { items.clear(); ItemStackHelper.loadAllItems(tag, items); }
    }
    /** Inventory contents travel through ContainerChest, never appearance update packets. */
    @Override public NBTTagCompound getUpdateTag() {
        NBTTagCompound tag = super.getUpdateTag(); tag.removeTag("Items"); return tag;
    }
    @Override public boolean hasCapability(Capability<?> capability, EnumFacing facing) {
        return capability == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY || super.hasCapability(capability, facing);
    }
    @Override public <T> T getCapability(Capability<T> capability, EnumFacing facing) {
        return capability == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY
                ? CapabilityItemHandler.ITEM_HANDLER_CAPABILITY.cast(itemHandler) : super.getCapability(capability, facing);
    }
}
