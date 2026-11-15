package com.vandorlabs.neoforge.legacylanding;
import com.vandorlabs.neoforge.NativeConfigurationAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
final class NativeLegacyLandingConfiguration {
 static boolean open(Player actor,Level world,BlockPos pos){if(!NativeLegacyLandingBindings.enabled()||!NativeLegacyLandingBlock.loaded(world,pos))return false;var selected=NativeLegacyLandingBlock.owner(world,pos);if(selected==null)return false;var root=selected.controller();if(root==null||!NativeConfigurationAccess.canEdit(actor,selected)||!NativeConfigurationAccess.canEditDialog(actor,root))return false;if(actor instanceof ServerPlayer player){if(!player.getServer().isSameThread())return false;player.openMenu(new SimpleMenuProvider((window,inventory,ignored)->new NativeLegacyLandingMenu(window,selected.getBlockPos(),selected.identity(),inventory.player),root.getBlockState().getBlock().getName()),buffer->{buffer.writeBlockPos(selected.getBlockPos());buffer.writeUUID(selected.identity());});}return true;}
 private NativeLegacyLandingConfiguration(){}
}
