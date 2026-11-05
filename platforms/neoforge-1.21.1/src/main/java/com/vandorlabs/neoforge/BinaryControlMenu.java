package com.vandorlabs.neoforge;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

/** Exact installed mounted control session; survival shift configuration remains available. */
public final class BinaryControlMenu extends AbstractContainerMenu {
    public final NativeBinaryControlBlockEntity tile;
    public BinaryControlMenu(int id,Inventory inventory,BlockPos pos){super(VandorLabsBootstrap.BINARY_CONTROL_MENU.get(),id);if(!inventory.player.level().hasChunkAt(pos)||!(inventory.player.level().getBlockEntity(pos) instanceof NativeBinaryControlBlockEntity entity))throw new IllegalArgumentException("Missing binary control");tile=entity;}
    @Override public boolean stillValid(Player player){return tile.getBlockState().getBlock() instanceof NativeMountedSwitchBlock&&NativeConfigurationAccess.canEditDialog(player,tile);}
    @Override public ItemStack quickMoveStack(Player player,int index){return ItemStack.EMPTY;}
}
