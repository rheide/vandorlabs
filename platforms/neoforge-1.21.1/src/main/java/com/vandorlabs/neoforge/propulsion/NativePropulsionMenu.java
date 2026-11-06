package com.vandorlabs.neoforge.propulsion;

import com.vandorlabs.neoforge.NativeConfigurationAccess;
import com.vandorlabs.neoforge.redstone.NativeRedstoneChannels;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;

/** Inactive dedicated editor. A shape replacement preserves its original physical menu owner. */
public final class NativePropulsionMenu extends AbstractContainerMenu {
 public final NativePropulsionEntity tile;
 public NativePropulsionMenu(MenuType<?> type,int id,NativePropulsionEntity tile){super(type,id);this.tile=java.util.Objects.requireNonNull(tile);}
 public NativePropulsionMenu(MenuType<?> type,int id,Inventory inventory,BlockPos pos){this(type,id,require(inventory.player,pos));}
 private static NativePropulsionEntity require(Player player,BlockPos pos){if(!NativePropulsionService.loaded(player.level(),pos)||!(player.level().getBlockEntity(pos) instanceof NativePropulsionEntity tile))throw new IllegalArgumentException("Missing loaded engine");return tile;}
 @Override public boolean stillValid(Player player){return tile.available()&&NativePropulsionService.installed(tile)&&(!(tile.getLevel() instanceof ServerLevel server)||NativeRedstoneChannels.open(server))&&NativeConfigurationAccess.canEditDialog(player,tile);}
 @Override public ItemStack quickMoveStack(Player player,int slot){return ItemStack.EMPTY;}
 public static NativePropulsionMenu editing(ServerPlayer player,BlockPos pos,int window){return player.getServer().isSameThread()&&player.containerMenu instanceof NativePropulsionMenu menu&&menu.containerId==window&&menu.tile.getBlockPos().equals(pos)&&menu.stillValid(player)?menu:null;}
 public static boolean apply(ServerPlayer player,BlockPos pos,int window,NativePropulsionConfiguration.Draft draft){var menu=editing(player,pos,window);return menu!=null&&(!draft.updateSide()||com.vandorlabs.neoforge.ModernFinishCatalog.validChoice(draft.sideTexture()))&&(!draft.updateShape()||player.isCreative())&&NativePropulsionConfiguration.apply(menu.tile,draft,player);}
}
