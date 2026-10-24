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
    public final int slot;
    public ScreenMenu(int id,Inventory inventory,BlockPos pos){this(id,inventory,pos,false);}
    public ScreenMenu(int id,Inventory inventory,BlockPos pos,boolean redstoneEditor){this(id,inventory,pos,redstoneEditor,0);}
    public ScreenMenu(int id,Inventory inventory,BlockPos pos,boolean redstoneEditor,int slot){super(ScreenRegistration.MENU.get(),id);this.redstoneEditor=redstoneEditor;this.slot=slot;this.pos=pos.immutable();if(!(inventory.player.level().getBlockEntity(pos) instanceof ProgrammableScreenEntity screen))throw new IllegalArgumentException("Missing programmable screen");tile=screen;if(!tile.supportsSlot(slot))throw new IllegalArgumentException("Unsupported screen slot "+slot);}
    @Override public boolean stillValid(Player player){return com.vandorlabs.neoforge.NativeConfigurationAccess.canEdit(player,tile)&&ScreenRegistration.isScreen(player.level().getBlockState(pos).getBlock())&&tile.supportsSlot(slot);}
    @Override public ItemStack quickMoveStack(Player player,int slot){return ItemStack.EMPTY;}
}
