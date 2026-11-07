package com.vandorlabs.items;

import java.util.function.Consumer;

/** The released ignore-directionality projection, shared by connected-selection adapters. */
public final class CopiedDirectionality {
 private static final String[] SETTINGS={"switch_rotation","ramp_direction","stair_roll","trapdoor_cover_facing","trapdoor_rotation_flipped","placement_ceiling","placement_ceiling_position","placement_wall_position"};
 public static void strip(Consumer<String> remove){for(String key:SETTINGS)remove.accept(key);}
 public static boolean ignoreProperty(String name,boolean facingType,boolean axisType){return facingType||axisType||name.equals("facing")||name.equals("rotation")||name.equals("rotated")||name.equals("hinge")||name.equals("inverted")||name.equals("half");}
 private CopiedDirectionality(){}
}
