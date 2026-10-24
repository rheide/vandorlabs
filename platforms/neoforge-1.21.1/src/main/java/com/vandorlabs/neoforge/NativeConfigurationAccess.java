package com.vandorlabs.neoforge;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

/** Native adapter for the legacy configurizer and held-item adventure permissions. */
public final class NativeConfigurationAccess {
    public static boolean canConfigure(Player player) {
        return player.isCreative() || player.getMainHandItem().is(VandorLabsModern.CONFIGURIZER.get());
    }
    public static boolean canEdit(Player player, Level level, BlockPos pos) {
        return canConfigure(player) && canEditDialog(player,level,pos);
    }
    public static boolean canEditDialog(Player player, Level level, BlockPos pos) {
        return player.isAlive() && !player.isSpectator()
            && player.level()==level && level.hasChunkAt(pos)
            && player.distanceToSqr(pos.getCenter())<=64 && level.mayInteract(player,pos)
            && player.mayUseItemAt(pos,Direction.UP,player.getMainHandItem());
    }
    public static boolean canEdit(Player player, BlockEntity tile) {
        Level level=tile.getLevel();
        return level!=null && canEdit(player,level,tile.getBlockPos())
            && level.getBlockEntity(tile.getBlockPos())==tile;
    }
    public static boolean canEditDialog(Player player, BlockEntity tile) {
        Level level=tile.getLevel();
        return level!=null && canEditDialog(player,level,tile.getBlockPos())
            && level.getBlockEntity(tile.getBlockPos())==tile;
    }
    private NativeConfigurationAccess() {}
}
