package com.vandorlabs.neoforge.legacylanding;
import com.vandorlabs.neoforge.NativeConfigurationAccess;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
public final class NativeLegacyLandingMenu extends AbstractContainerMenu {
 public final BlockPos selected;public final UUID identity;private final Player actor;
 NativeLegacyLandingMenu(int window,BlockPos selected,UUID identity,Player actor){super(NativeLegacyLandingBindings.MENU,window);this.selected=selected.immutable();this.identity=identity;this.actor=actor;}
 public NativeLegacyLandingEntity root(){var physical=NativeLegacyLandingBlock.owner(actor.level(),selected);if(physical==null||!identity.equals(physical.identity())||!physical.getBlockPos().equals(selected))return null;if(!actor.level().isClientSide)NativeLegacyColumns.refresh(physical);var root=physical.controller();return root!=null&&!root.isRemoved()&&!root.inert()?root:null;}
 @Override public boolean stillValid(Player player){var root=root();return player==actor&&root!=null&&NativeConfigurationAccess.canEditDialog(player,root)&&NativeConfigurationAccess.canEditDialog(player,actor.level(),selected);}
 static NativeLegacyLandingMenu editing(ServerPlayer actor,BlockPos selected,UUID identity,int window){return actor.getServer().isSameThread()&&actor.containerMenu instanceof NativeLegacyLandingMenu menu&&menu.containerId==window&&menu.selected.equals(selected)&&menu.identity.equals(identity)&&menu.stillValid(actor)?menu:null;}
 @Override public ItemStack quickMoveStack(Player player,int slot){return ItemStack.EMPTY;}
}
