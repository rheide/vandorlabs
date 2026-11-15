package com.vandorlabs.neoforge.surface;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;

public final class NativeSurfaceMenu extends AbstractContainerMenu {
    public final NativeSurfaceEntity tile;
    public NativeSurfaceMenu(MenuType<?> type,int id,NativeSurfaceEntity tile){super(type,id);this.tile=java.util.Objects.requireNonNull(tile);}
    public NativeSurfaceMenu(MenuType<?> type,int id,Inventory inventory,BlockPos pos){this(type,id,require(inventory.player,pos));}
    private static NativeSurfaceEntity require(Player player,BlockPos pos){if(!player.level().hasChunkAt(pos)||!(player.level().getBlockEntity(pos) instanceof NativeSurfaceEntity tile))throw new IllegalArgumentException("Surface unavailable");return tile;}
    @Override public boolean stillValid(Player actor){return !tile.inert()&&!tile.isRemoved()&&com.vandorlabs.neoforge.NativeConfigurationAccess.canEditDialog(actor,tile);}
    @Override public ItemStack quickMoveStack(Player actor,int slot){return ItemStack.EMPTY;}
    public boolean configure(ServerPlayer actor,int window,BlockPos pos,String on,String off,int mode){return actor.getServer().isSameThread()&&actor.containerMenu==this&&containerId==window&&tile.getBlockPos().equals(pos)&&stillValid(actor)&&tile.configure(actor,on,off,mode);}
}
