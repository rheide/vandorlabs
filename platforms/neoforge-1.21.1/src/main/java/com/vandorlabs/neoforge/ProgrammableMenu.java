package com.vandorlabs.neoforge;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

/** Server-owned editing session bound to the exact installed tile identity. */
public final class ProgrammableMenu extends AbstractContainerMenu {
    public final ProgrammableBlockEntity tile;
    public ProgrammableMenu(int id,Inventory inventory,BlockPos pos) {
        super(VandorLabsModern.PROGRAMMABLE_MENU.get(),id);
        if(!(inventory.player.level().getBlockEntity(pos) instanceof ProgrammableBlockEntity entity))throw new IllegalArgumentException("Missing programmable block");
        tile=entity;
    }
    public static boolean canConfigure(Player player) { return player.isCreative()||player.getMainHandItem().is(VandorLabsModern.CONFIGURIZER.get()); }
    @Override public boolean stillValid(Player player) {
        BlockPos pos=tile.getBlockPos();
        return player.isAlive()&&!player.isSpectator()&&canConfigure(player)&&player.mayBuild()&&player.level()==tile.getLevel()
            &&player.level().hasChunkAt(pos)&&player.level().getBlockEntity(pos)==tile
            &&player.distanceToSqr(pos.getCenter())<=64&&player.level().mayInteract(player,pos);
    }
    @Override public ItemStack quickMoveStack(Player player,int index) { return ItemStack.EMPTY; }
}
