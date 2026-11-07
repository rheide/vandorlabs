package com.vandorlabs.neoforge.storage;
import com.vandorlabs.neoforge.NativeConfigurationAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
/** Dedicated material session, separate from the vanilla inventory window. */
public final class NativeStorageMenu extends AbstractContainerMenu {
 public final NativeStorageEntity tile;private final NativeStorageBlock block;
 public NativeStorageMenu(MenuType<?> type,int id,NativeStorageEntity tile){super(type,id);this.tile=java.util.Objects.requireNonNull(tile);if(!(tile.getBlockState().getBlock() instanceof NativeStorageBlock owner))throw new IllegalArgumentException("Missing Storage family");block=owner;}
 public NativeStorageMenu(MenuType<?> type,int id,Inventory inventory,BlockPos pos){this(type,id,require(inventory.player,pos));}
 private static NativeStorageEntity require(Player player,BlockPos pos){if(!player.level().hasChunkAt(pos)||!(player.level().getBlockEntity(pos) instanceof NativeStorageEntity tile))throw new IllegalArgumentException("Missing loaded Storage");return tile;}
 @Override public boolean stillValid(Player player){return tile.available()&&tile.getBlockState().getBlock()==block&&(!(tile.getLevel() instanceof ServerLevel)||tile.installed())&&NativeConfigurationAccess.canEditDialog(player,tile);}
 @Override public ItemStack quickMoveStack(Player player,int slot){return ItemStack.EMPTY;}
 public static boolean apply(ServerPlayer player,NativeStorageEdit edit){if(edit==null||!player.getServer().isSameThread()||!NativeConfigurationAccess.canConfigure(player)||!(player.containerMenu instanceof NativeStorageMenu menu)||menu.containerId!=edit.window()||!menu.tile.getBlockPos().equals(edit.pos())||!menu.stillValid(player))return false;return menu.tile.configure(player,edit.apply(menu.tile.settings()));}
}
