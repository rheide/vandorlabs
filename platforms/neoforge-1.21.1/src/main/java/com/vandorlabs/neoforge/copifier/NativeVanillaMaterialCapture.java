package com.vandorlabs.neoforge.copifier;
import com.vandorlabs.neoforge.NativeConfigurationAccess;
import com.vandorlabs.neoforge.NativeMaterialRegistry;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.*;

/** The released vanilla fallback copies four artwork choices, never a vanilla tile's owned data. */
public final class NativeVanillaMaterialCapture {
 public static CompoundTag capture(ServerLevel level,BlockPos pos,ServerPlayer actor,Direction face){
  if(!level.getServer().isSameThread()||actor==null||face==null||!NativeConfigurationAccess.canEditDialog(actor,level,pos,face))return null;
  var state=level.getBlockState(pos);if(state.isAir()||state.liquid()||!BuiltInRegistries.BLOCK.getKey(state.getBlock()).getNamespace().equals("minecraft"))return null;
  var choice=NativeMaterialRegistry.snapshot().stateSamples().get(state);if(choice==null)return null;
  var settings=new CompoundTag();for(String key:new String[]{"wall_texture","side_texture","glass_material","door_face_texture"})settings.putInt(key,choice);return settings;
 }
 private NativeVanillaMaterialCapture(){}
}
