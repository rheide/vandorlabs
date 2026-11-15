package com.vandorlabs.neoforge.ports;

import com.vandorlabs.neoforge.NativeConfigurationAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;

/** Configuration-only menu; resource buffers never travel through an editor packet. */
public final class NativePortMenu extends AbstractContainerMenu {
    public final NativePortOwner tile;
    public NativePortMenu(MenuType<?> type,int id,NativePortOwner tile){super(type,id);this.tile=java.util.Objects.requireNonNull(tile);}
    public NativePortMenu(MenuType<?> type,int id,Inventory inventory,BlockPos pos){this(type,id,require(inventory.player,pos));}
    private static NativePortOwner require(Player player,BlockPos pos){if(!player.level().hasChunkAt(pos)||!(player.level().getBlockEntity(pos) instanceof NativePortOwner tile))throw new IllegalArgumentException("Missing resource port");return tile;}
    @Override public boolean stillValid(Player player){return !tile.portBlockEntity().isRemoved()&&!tile.inert()&&NativeConfigurationAccess.canEditDialog(player,tile.portBlockEntity());}
    @Override public ItemStack quickMoveStack(Player player,int index){return ItemStack.EMPTY;}
    public boolean applyBody(ServerPlayer player,int window,BlockPos pos,int casing,int layout,boolean block){return player.getServer().isSameThread()&&player.containerMenu==this&&containerId==window&&tile.getBlockPos().equals(pos)&&stillValid(player)&&layout==1&&tile.portBlockEntity() instanceof com.vandorlabs.neoforge.reservoir.NativeBatteryEntity battery&&battery.configure(player,casing,layout,block);}
    public boolean apply(ServerPlayer player,int window,BlockPos pos,NativePortData.Configuration config){return player.getServer().isSameThread()&&player.containerMenu==this&&containerId==window&&tile.getBlockPos().equals(pos)&&stillValid(player)&&tile.configure(config);}
}
