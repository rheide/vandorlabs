package com.vandorlabs.container;

import com.vandorlabs.redstone.RedstoneChannelMember;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.tileentity.TileEntity;

public class ContainerRedstoneChannel extends Container {
    public final RedstoneChannelMember member;

    public ContainerRedstoneChannel(RedstoneChannelMember member) { this.member = member; }

    @Override public boolean canInteractWith(EntityPlayer player) {
        TileEntity tile = member.channelTile();
        return tile.getWorld() != null && tile.getWorld().getTileEntity(tile.getPos()) == tile
                && player.getDistanceSq(tile.getPos()) <= 64
                && player.canPlayerEdit(tile.getPos(), net.minecraft.util.EnumFacing.UP,
                        player.getHeldItemMainhand())
                && tile.getWorld().isBlockModifiable(player, tile.getPos());
    }
}
