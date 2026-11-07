package com.vandorlabs.neoforge;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
/** Actual current-window housing payload, before/during held stationary Ramp ownership. */
public final class NativeRampHeldEditorChecks {
 public static void run(ServerPlayer player,BlockPos pos,boolean allowed){var level=player.serverLevel();var tile=(ProgrammableBlockEntity)level.getBlockEntity(pos);var before=tile.settings();var menu=player.containerMenu;var hand=player.getMainHandItem().copy();var point=player.position();try{player.setPos(pos.getX()+.5,pos.getY(),pos.getZ()-1);player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(VandorLabsBootstrap.CONFIGURIZER.get()));player.containerMenu=new ProgrammableMenu(731,player.getInventory(),pos);if(NativeConfigurationAccess.canEditDialog(player,level,pos)!=allowed)throw new AssertionError("Ramp held editor admission");var edit=new FinishPayload(pos,before.mainTexture==0?1:0,before.layout,before.facesEnabled,before.faces());if(FinishPayload.apply(player,edit)!=allowed)throw new AssertionError("Ramp held current-window payload result");if(!allowed&&tile.settings()!=before)throw new AssertionError("Held housing payload mutated source");if(allowed){if(tile.settings().mainTexture==before.mainTexture)throw new AssertionError("Unheld current-window edit did not apply");tile.apply(before);}}finally{player.containerMenu=menu;player.setItemInHand(InteractionHand.MAIN_HAND,hand);player.setPos(point);}}
 private NativeRampHeldEditorChecks(){}
}
