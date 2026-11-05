package com.vandorlabs.neoforge;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

/** Configuration is bound to one installed Trigger, including when its chunk or block is replaced. */
public final class TriggerMenu extends AbstractContainerMenu {
    public final NativeTriggerBlockEntity tile;
    public TriggerMenu(int id,Inventory inventory,BlockPos pos){
        super(VandorLabsBootstrap.TRIGGER_MENU.get(),id);
        if(!(inventory.player.level().getBlockEntity(pos) instanceof NativeTriggerBlockEntity entity))throw new IllegalArgumentException("Missing Trigger");
        tile=entity;
    }
    @Override public boolean stillValid(Player player){return NativeConfigurationAccess.canEdit(player,tile);}
    @Override public ItemStack quickMoveStack(Player player,int index){return ItemStack.EMPTY;}
}
