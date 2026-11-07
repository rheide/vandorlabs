package com.vandorlabs.neoforge;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;

/** Position-dependent emission without lighting-worker block-entity or neighbour access. */
public final class NativeAuxiliaryLight {
 private NativeAuxiliaryLight(){}
 /** Blocks using this lookup must advertise hasDynamicLightEmission. */
 public static int read(BlockGetter level,BlockPos pos){var manager=level.getAuxLightManager(pos);return manager==null?0:manager.getLightAt(pos);}
 /** Family services retain their installed-owner/lifecycle guard; client callers use their game thread. */
 public static boolean publish(Level level,BlockPos pos,int emission){
  if(level instanceof ServerLevel server&&!server.getServer().isSameThread())throw new IllegalStateException("Auxiliary light publication requires owning server thread");
  int bounded=Math.max(0,Math.min(15,emission));var manager=level.getAuxLightManager(pos);if(manager==null||manager.getLightAt(pos)==bounded)return false;manager.setLightAt(pos,bounded);return true;
 }
}
