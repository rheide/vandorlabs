package com.vandorlabs.neoforge.glass;

import com.vandorlabs.neoforge.NativeConfigurationAccess;
import com.vandorlabs.neoforge.shape.PanelPlayback;
import com.vandorlabs.persistence.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;

/** Dedicated glass editor; every transaction rechecks the installed loaded owner and current permission. */
public final class NativeGlassMenu extends AbstractContainerMenu {
 public final NativeGlassEntity tile;private final NativeGlassBlock block;
 public NativeGlassMenu(MenuType<?> type,int id,NativeGlassEntity tile){super(type,id);this.tile=java.util.Objects.requireNonNull(tile);block=(NativeGlassBlock)tile.getBlockState().getBlock();}
 public NativeGlassMenu(MenuType<?> type,int id,Inventory inventory,BlockPos pos){this(type,id,require(inventory.player,pos));}
 private static NativeGlassEntity require(Player player,BlockPos pos){if(!player.level().hasChunkAt(pos)||!(player.level().getBlockEntity(pos) instanceof NativeGlassEntity tile))throw new IllegalArgumentException("Missing loaded Glass");return tile;}
 @Override public boolean stillValid(Player player){return tile.available()&&tile.getBlockState().getBlock()==block&&NativeConfigurationAccess.canEditDialog(player,tile);}
 @Override public ItemStack quickMoveStack(Player player,int slot){return ItemStack.EMPTY;}
 public static NativeGlassMenu editing(ServerPlayer player,BlockPos pos,int window){return player.getServer().isSameThread()&&NativeConfigurationAccess.canConfigure(player)&&player.containerMenu instanceof NativeGlassMenu menu&&menu.containerId==window&&menu.tile.getBlockPos().equals(pos)&&menu.stillValid(player)?menu:null;}
 public static boolean apply(ServerPlayer player,GlassSettingsPayload edit){var menu=editing(player,edit.pos(),edit.window());if(menu==null||!edit.valid())return false;var old=menu.tile.settings();var before=old.glass();var glass=new ProgrammableGlassSettings(edit.shade(),edit.material(),edit.layout(),edit.layout()!=1,edit.tint(),edit.framed(),Math.min(1,edit.size()),edit.join(),before.panelFacing,false);return menu.tile.configure(new NativeGlassSettings(glass,old.frames(),new PanelPlayback(edit.mode(),edit.speed())),edit.size());}
 public static boolean frames(ServerPlayer player,GlassFramePayload edit){var menu=editing(player,edit.pos(),edit.window());if(menu==null||!GlassFramePayload.valid(edit.frames()))return false;var old=menu.tile.settings();return menu.tile.configure(new NativeGlassSettings(old.glass(),edit.frames(),old.playback()),menu.tile.getBlockState().getValue(NativeGlassBlock.SIZE));}
}
