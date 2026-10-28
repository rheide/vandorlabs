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
    public ContainerTransmitterItem(EntityPlayer player,EnumHand hand){this.hand=hand;member=null;selector=0;original=player.getHeldItem(hand);selected=player.inventory.currentItem;}
    public ContainerTransmitterItem(EntityPlayer player,com.vandorlabs.redstone.RedstoneChannelMember member,int selector){this.hand=null;this.member=member;this.selector=selector;original=TransmitterSlots.get(member);selected=-1;}
    public ItemStack stack(EntityPlayer player){return member==null?player.getHeldItem(hand):TransmitterSlots.get(member);}
    @Override public boolean canInteractWith(EntityPlayer player){ItemStack stack=stack(player);if(!ItemRedstoneTransmitter.valid(stack))return false;if(member==null)return player.inventory.currentItem==selected&&stack==original;return ContainerTransmitterSlot.usable(player,member)&&ItemStack.areItemStacksEqual(original,stack);}
    public void configure(EntityPlayer player,com.vandorlabs.redstone.ChannelList channels){if(!canInteractWith(player))return;ItemStack stack=stack(player);ItemRedstoneTransmitter.configure(stack,channels);if(member!=null){TransmitterSlots.set(member,stack);ItemRedstoneTransmitter.configure(original,channels);}else player.inventory.markDirty();detectAndSendChanges();}
}
