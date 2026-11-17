package com.vandorlabs.neoforge.navigation.observer;
import com.vandorlabs.neoforge.NativeConfigurationAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
/** An exact loaded owner/window session; replacement or lost authorization invalidates it. */
public final class NativeObserverMenu extends AbstractContainerMenu {
    public final NativeObserverEntity tile;public final BlockPos target;
    public NativeObserverMenu(MenuType<?> type,int window,NativeObserverEntity tile){this(type,window,tile,tile.getBlockPos());}
    public NativeObserverMenu(MenuType<?> type,int window,NativeObserverEntity tile,BlockPos target){super(type,window);this.tile=tile;this.target=target.immutable();}
    public NativeObserverMenu(MenuType<?> type,int window,Inventory inventory,BlockPos pos,BlockPos target){this(type,window,require(inventory.player,pos),target);}
    private static NativeObserverEntity require(Player player,BlockPos pos){if(!player.level().hasChunkAt(pos)||!(player.level().getBlockEntity(pos) instanceof NativeObserverEntity tile))throw new IllegalArgumentException("Missing loaded observer");return tile;}
    @Override public boolean stillValid(Player player){return NativeObserverAccess.canUse(player,tile,target);}
    public static NativeObserverMenu editing(ServerPlayer player,int window){return player.getServer().isSameThread()&&player.containerMenu instanceof NativeObserverMenu menu&&menu.containerId==window&&menu.stillValid(player)?menu:null;}
    @Override public ItemStack quickMoveStack(Player player,int slot){return ItemStack.EMPTY;}
}
