package com.vandorlabs.container;

import com.vandorlabs.items.ItemRedstoneTransmitter;
import com.vandorlabs.redstone.TransmitterSlots;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumHand;

/** Editing is bound to the exact held stack or exact installed slot. */
public final class ContainerTransmitterItem extends Container {
    public final EnumHand hand;
    public final com.vandorlabs.redstone.RedstoneChannelMember member;
    public final int selector;
    private final ItemStack original;
    private final int selected;
    private final net.minecraft.inventory.InventoryBasic preview=new net.minecraft.inventory.InventoryBasic("Transmitter",false,1);
    public boolean synchronizedStack;
    private void initialize(){preview.setInventorySlotContents(0,original.copy());addSlotToContainer(new net.minecraft.inventory.Slot(preview,0,-10000,-10000){public boolean isItemValid(ItemStack stack){return false;}public boolean canTakeStack(EntityPlayer player){return false;}});}
    public ItemStack editorStack(){return preview.getStackInSlot(0);}
    @Override public void putStackInSlot(int index,ItemStack stack){super.putStackInSlot(index,stack);synchronizedStack=true;}
    @Override public void setAll(java.util.List<ItemStack> items){super.setAll(items);synchronizedStack=true;}
    public ContainerTransmitterItem(EntityPlayer player,EnumHand hand){this.hand=hand;member=null;selector=0;original=player.getHeldItem(hand);selected=player.inventory.currentItem;initialize();}
    public ContainerTransmitterItem(EntityPlayer player,com.vandorlabs.redstone.RedstoneChannelMember member,int selector){this.hand=null;this.member=member;this.selector=selector;original=TransmitterSlots.get(member);selected=-1;initialize();}
    public ItemStack stack(EntityPlayer player){return member==null?player.getHeldItem(hand):TransmitterSlots.get(member);}
    @Override public boolean canInteractWith(EntityPlayer player){if(player==null||player.isDead||player.isSpectator())return false;ItemStack stack=stack(player);if(!ItemRedstoneTransmitter.valid(stack))return false;if(member==null)return player.inventory.currentItem==selected&&stack==original;return ContainerTransmitterSlot.usable(player,member)&&ItemStack.areItemStacksEqual(original,stack);}
    public void configure(EntityPlayer player,com.vandorlabs.redstone.ChannelList channels){if(!canInteractWith(player))return;ItemStack stack=stack(player);ItemRedstoneTransmitter.configure(stack,channels);if(member!=null){TransmitterSlots.set(member,stack);ItemRedstoneTransmitter.configure(original,channels);}else player.inventory.markDirty();preview.setInventorySlotContents(0,stack.copy());detectAndSendChanges();}
}
