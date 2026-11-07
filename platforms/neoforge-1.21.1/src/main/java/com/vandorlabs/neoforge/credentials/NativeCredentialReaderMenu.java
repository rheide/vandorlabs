package com.vandorlabs.neoforge.credentials;

import com.vandorlabs.neoforge.NativeConfigurationAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;

/** Configuration-only session; credentials shown by the picker never become inventory slots. */
public final class NativeCredentialReaderMenu extends AbstractContainerMenu {
    public final NativeCredentialReaderEntity tile;
    private final NativeCredentialReaderBlock block;
    public NativeCredentialReaderMenu(MenuType<?> type,int window,NativeCredentialReaderEntity tile){super(type,window);this.tile=java.util.Objects.requireNonNull(tile);if(!(tile.getBlockState().getBlock() instanceof NativeCredentialReaderBlock reader))throw new IllegalArgumentException("Missing reader block");block=reader;}
    public NativeCredentialReaderMenu(MenuType<?> type,int window,Inventory inventory,BlockPos pos){this(type,window,require(inventory.player,pos));}
    private static NativeCredentialReaderEntity require(Player player,BlockPos pos){if(!player.level().hasChunkAt(pos)||!(player.level().getBlockEntity(pos) instanceof NativeCredentialReaderEntity tile))throw new IllegalArgumentException("Missing loaded reader");return tile;}
    @Override public boolean stillValid(Player player){return !tile.isRemoved()&&tile.getBlockState().getBlock()==block&&(player instanceof ServerPlayer server?tile.usable(server):player.level().isClientSide&&NativeConfigurationAccess.canEditDialog(player,tile));}
    public static NativeCredentialReaderMenu editing(ServerPlayer player,int window){return player.getServer().isSameThread()&&player.containerMenu instanceof NativeCredentialReaderMenu menu&&menu.containerId==window&&menu.stillValid(player)?menu:null;}
    @Override public ItemStack quickMoveStack(Player player,int index){return ItemStack.EMPTY;}
    @Override public void clicked(int slot,int button,ClickType type,Player player){} // Samples are neither sources nor destinations.
}
