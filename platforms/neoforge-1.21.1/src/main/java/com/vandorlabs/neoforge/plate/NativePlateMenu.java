package com.vandorlabs.neoforge.plate;
import com.vandorlabs.neoforge.NativeConfigurationAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
/** An exact loaded owner/window session; replacement or lost authorization invalidates it. */
public final class NativePlateMenu extends AbstractContainerMenu {
    public final NativePlateEntity tile;
    public NativePlateMenu(MenuType<?> type,int window,NativePlateEntity tile){super(type,window);this.tile=tile;}
    public NativePlateMenu(MenuType<?> type,int window,Inventory inventory,BlockPos pos){this(type,window,require(inventory.player,pos));}
    private static NativePlateEntity require(Player player,BlockPos pos){if(!player.level().hasChunkAt(pos)||!(player.level().getBlockEntity(pos) instanceof NativePlateEntity tile))throw new IllegalArgumentException("Missing loaded pressure plate");return tile;}
    @Override public boolean stillValid(Player player){return !tile.inert()&&NativeConfigurationAccess.canEditDialog(player,tile);}
    public static NativePlateMenu editing(ServerPlayer player,int window){return player.getServer().isSameThread()&&player.containerMenu instanceof NativePlateMenu menu&&menu.containerId==window&&menu.stillValid(player)?menu:null;}
    @Override public ItemStack quickMoveStack(Player player,int slot){return ItemStack.EMPTY;}
}
