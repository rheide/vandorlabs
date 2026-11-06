package com.vandorlabs.neoforge.door;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/** Configurizer and ordinary block configuration enter the same dedicated, authenticated dialog. */
public final class DoorConfiguration {
    private DoorConfiguration(){}
    public static boolean open(Player player,Level level,BlockPos pos){return NativeDoorLifecycle.loaded(level,pos)&&level.getBlockState(pos).getBlock() instanceof NativeProgrammableDoorBlock block&&block.openConfiguration(player,level,pos);}
}
