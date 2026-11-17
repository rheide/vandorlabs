package com.vandorlabs.neoforge.navigation;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
public final class NativeNavigationTableMenu extends AbstractContainerMenu {
 public final NativeNavigationTableEntity tile;
 public NativeNavigationTableMenu(int window,NativeNavigationTableEntity t){super(NativeNavigationTableBindings.MENU,window);tile=t;}
 public NativeNavigationTableMenu(int window,Inventory i,BlockPos p){this(window,require(i.player,p));}
 private static NativeNavigationTableEntity require(Player p,BlockPos pos){if(!p.level().hasChunkAt(pos)||!(p.level().getBlockEntity(pos) instanceof NativeNavigationTableEntity t))throw new IllegalArgumentException("Missing table");return t;}
 @Override public boolean stillValid(Player p){return !tile.isRemoved()&&com.vandorlabs.neoforge.NativeConfigurationAccess.canEditDialog(p,tile);}
 @Override public ItemStack quickMoveStack(Player p,int slot){return ItemStack.EMPTY;}
}
