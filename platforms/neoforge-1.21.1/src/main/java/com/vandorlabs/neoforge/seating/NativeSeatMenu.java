package com.vandorlabs.neoforge.seating;
import com.vandorlabs.neoforge.NativeConfigurationAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
/** Exact lower-cell editor; ordinary and Configurizer use share this owner/window. */
public final class NativeSeatMenu extends AbstractContainerMenu {
    public final NativeChairEntity tile;
    public NativeSeatMenu(MenuType<?> type,int window,NativeChairEntity tile){super(type,window);this.tile=tile;}
    public NativeSeatMenu(MenuType<?> type,int window,Inventory inventory,BlockPos pos){this(type,window,require(inventory.player,pos));}
    private static NativeChairEntity require(Player player,BlockPos pos){var owner=NativeSeatBlock.owner(player.level(),pos);if(owner==null)throw new IllegalArgumentException("Missing loaded seat owner");return owner;}
    @Override public boolean stillValid(Player player){return !tile.inert()&&!tile.getBlockState().getValue(NativeSeatBlock.UPPER)&&NativeConfigurationAccess.canEditDialog(player,tile);}
    public static NativeSeatMenu editing(ServerPlayer actor,BlockPos pos,int window){return actor.getServer().isSameThread()&&actor.containerMenu instanceof NativeSeatMenu menu&&menu.containerId==window&&menu.tile.getBlockPos().equals(pos)&&menu.stillValid(actor)?menu:null;}
    @Override public ItemStack quickMoveStack(Player player,int slot){return ItemStack.EMPTY;}
}
