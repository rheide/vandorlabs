package com.vandorlabs.container;

import com.vandorlabs.items.ConfigurationAccess;
import com.vandorlabs.items.ItemProgrammableArmor;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumHand;

/** Bind edits to the exact held inventory stack or mounted equipment that opened the dialog. */
public final class ContainerProgrammableArmor extends Container {
    public final EnumHand hand;
    public final ItemStack armor;
    public final net.minecraft.entity.item.EntityArmorStand stand;
    public final net.minecraft.inventory.EntityEquipmentSlot slot;
    private final EntityPlayer owner;
    private final int hotbar;
    public ContainerProgrammableArmor(EntityPlayer player, EnumHand hand) {
        this.stand=null; this.slot=null;
        this.owner = player;
        this.hand = hand;
        this.armor = player.getHeldItem(hand);
        this.hotbar = player.inventory.currentItem;
    }
    public ContainerProgrammableArmor(EntityPlayer player, net.minecraft.entity.item.EntityArmorStand stand,
            net.minecraft.inventory.EntityEquipmentSlot slot) {
        this.owner=player; this.hand=EnumHand.MAIN_HAND; this.hotbar=-1;
        this.stand=stand; this.slot=slot; this.armor=stand.getItemStackFromSlot(slot);
    }
    public ItemStack stack(EntityPlayer player) { return stand==null?player.getHeldItem(hand):stand.getItemStackFromSlot(slot); }
    public static ContainerProgrammableArmor resolve(EntityPlayer player, net.minecraft.world.World world,int x,int y,int z) {
        if(z==1) {
            net.minecraft.entity.Entity entity=world.getEntityByID(x);
            if(!(entity instanceof net.minecraft.entity.item.EntityArmorStand) || y<0 || y>3)return null;
            return new ContainerProgrammableArmor(player,(net.minecraft.entity.item.EntityArmorStand)entity,
                    net.minecraft.inventory.EntityEquipmentSlot.values()[y+2]);
        }
        return z==0 && x>=0 && x<EnumHand.values().length?new ContainerProgrammableArmor(player,EnumHand.values()[x]):null;
    }
    @Override public boolean canInteractWith(EntityPlayer player) {
        return player == owner && player.isEntityAlive() && ConfigurationAccess.canConfigure(player)
                && armor.getItem() instanceof ItemProgrammableArmor && !armor.isEmpty()
                && (stand==null ? player.getHeldItem(hand)==armor
                    && (hand==EnumHand.OFF_HAND || player.inventory.currentItem==hotbar)
                    : !player.isSpectator() && stand.isEntityAlive() && !stand.hasMarker()
                    && stand.world==player.world && player.world.getEntityByID(stand.getEntityId())==stand
                    && player.getDistanceSq(stand)<=64 && stand.getItemStackFromSlot(slot)==armor
                    && player.getHeldItemMainhand().getItem()==com.vandorlabs.items.ModItems.CONFIGURIZER);
    }
    @Override public ItemStack transferStackInSlot(EntityPlayer player, int index) { return ItemStack.EMPTY; }
}
