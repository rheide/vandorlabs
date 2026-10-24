package com.vandorlabs.neoforge.screen;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

/** A server-owned editing session; payloads must match this exact position and menu. */
public final class ScreenMenu extends AbstractContainerMenu {
    public final BlockPos pos;
    public final ProgrammableScreenEntity tile;
    public final boolean redstoneEditor;
    public ScreenMenu(int id,Inventory inventory,BlockPos pos){this(id,inventory,pos,false);}
    public ScreenMenu(int id,Inventory inventory,BlockPos pos,boolean redstoneEditor){super(ScreenRegistration.MENU.get(),id);this.redstoneEditor=redstoneEditor;this.pos=pos.immutable();if(!(inventory.player.level().getBlockEntity(pos) instanceof ProgrammableScreenEntity screen))throw new IllegalArgumentException("Missing programmable screen");tile=screen;}
    @Override public boolean stillValid(Player player){return player.isAlive()&&player.mayBuild()&&com.vandorlabs.neoforge.ProgrammableMenu.canConfigure(player)&&!player.isSpectator()&&player.level()==tile.getLevel()&&player.level().hasChunkAt(pos)&&player.level().getBlockEntity(pos)==tile&&player.level().getBlockState(pos).is(ScreenRegistration.BLOCK.get())&&player.distanceToSqr(pos.getCenter())<=64&&player.level().mayInteract(player,pos);}
    @Override public ItemStack quickMoveStack(Player player,int slot){return ItemStack.EMPTY;}
}
