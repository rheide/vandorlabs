package com.vandorlabs.neoforge.door;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/** Configurizer and ordinary block configuration enter the same dedicated, authenticated dialog. */
public final class DoorConfiguration {
    private DoorConfiguration(){}
    public static NativeProgrammableDoorEntity root(Level level,BlockPos pos){if(!NativeDoorLifecycle.loaded(level,pos))return null;return level.getBlockState(pos).getBlock() instanceof NativeLargeDoorBlock?NativeLargeDoorLifecycle.root(level,pos):NativeDoorLifecycle.root(level,pos);}
    public static boolean open(Player player,Level level,BlockPos pos){if(!NativeDoorLifecycle.loaded(level,pos))return false;var block=level.getBlockState(pos).getBlock();return block instanceof NativeProgrammableDoorBlock ordinary?ordinary.openConfiguration(player,level,pos):block instanceof NativeLargeDoorBlock large&&large.openConfiguration(player,level,pos);}
}
