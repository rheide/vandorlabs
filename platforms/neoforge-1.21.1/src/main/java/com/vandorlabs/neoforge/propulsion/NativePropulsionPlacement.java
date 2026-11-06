package com.vandorlabs.neoforge.propulsion;

import com.vandorlabs.propulsion.*;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.phys.Vec3;

/** Inactive world-free callback adapter; placed shape changes intentionally retain the Source vanilla merge rule. */
public final class NativePropulsionPlacement {
    private static final NativeRedstoneLightSettings FRESH=NativeRedstoneLightSettings.read(new CompoundTag());
    private static final String[] PICK={"RedstoneChannel","RedstoneChannels","ManualOn","ParticleLevel","ParticleStream","SignalBrightness","ParticleThreshold","RedstoneInverted","PropulsionJoin","PropulsionSideTexture"};
    private NativePropulsionPlacement(){}
    public static PropulsionDefinition select(String item,CompoundTag raw,Direction side,Vec3 hit){return PropulsionPlacement.select(PropulsionDefinition.of(item),raw==null?0:raw.getInt("PropulsionShape"),side.get3DDataValue(),(float)hit.x,(float)hit.y,(float)hit.z);}
    /** ItemBlock merges the fresh save only on the server and only when selected block == item's block. */
    public static NativeRedstoneLightSettings settings(String item,String selected,boolean remote,CompoundTag raw){if(remote||!item.equals(selected))return FRESH;var merged=new CompoundTag();FRESH.write(merged);if(raw!=null)merged.merge(raw.copy());return NativeRedstoneLightSettings.read(merged);}
    public static String item(PropulsionDefinition block){return block.family.isEmpty()?block.id:block.family;}
    public static CompoundTag drop(PropulsionDefinition block){var result=new CompoundTag();if(block.shape>0)result.putInt("PropulsionShape",block.shape);return result;}
    public static CompoundTag pick(PropulsionDefinition block,NativeRedstoneLightSettings settings){var result=drop(block);var values=new CompoundTag();settings.write(values);for(String key:PICK)if(values.contains(key))result.put(key,values.get(key).copy());return result;}
}
