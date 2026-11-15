package com.vandorlabs.neoforge;

import com.vandorlabs.placement.PlayerViewFacing;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;

/** Thin native adapter for the same player-view placement used by Source lights and racks. */
public final class NativeDirectionalPlacement {
    private NativeDirectionalPlacement() { }
    public static Direction towardView(BlockPlaceContext context) {
        var player = context.getPlayer();
        return Direction.from3DDataValue(PlayerViewFacing.towardView(player == null ? 0 : player.getXRot(),
                context.getHorizontalDirection().get3DDataValue()));
    }
}
