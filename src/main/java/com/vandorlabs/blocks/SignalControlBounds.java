package com.vandorlabs.blocks;

import net.minecraft.util.math.AxisAlignedBB;

/** Union of every detent, including the rotated lever grip, in the supplied geometry. */
final class SignalControlBounds {
    private static final java.util.Map<String,AxisAlignedBB> bounds=new java.util.HashMap<>();
    static {
        bounds.put("32/thruster_lever/north",new AxisAlignedBB(0.18750000,0.11330098,0.30394670,0.81250000,0.87565048,1.00000000));
        bounds.put("32/thruster_lever/south",new AxisAlignedBB(0.18750000,0.11330098,0.00000000,0.81250000,0.87565048,0.69605330));
        bounds.put("32/thruster_lever/east",new AxisAlignedBB(0.00000000,0.11330098,0.18750000,0.69605330,0.87565048,0.81250000));
        bounds.put("32/thruster_lever/west",new AxisAlignedBB(0.30394670,0.11330098,0.18750000,1.00000000,0.87565048,0.81250000));
        bounds.put("32/thruster_lever/floor_x",new AxisAlignedBB(0.12434952,0.00000000,0.18750000,0.88669902,0.69605330,0.81250000));
        bounds.put("32/thruster_lever/floor_z",new AxisAlignedBB(0.18750000,0.00000000,0.11330098,0.81250000,0.69605330,0.87565048));
        bounds.put("32/thruster_lever/ceiling_x",new AxisAlignedBB(0.11330098,0.30394670,0.18750000,0.87565048,1.00000000,0.81250000));
        bounds.put("32/thruster_lever/ceiling_z",new AxisAlignedBB(0.18750000,0.30394670,0.12434952,0.81250000,1.00000000,0.88669902));
        bounds.put("32/wall_slider/north",new AxisAlignedBB(0.18750000,0.06250000,0.85937500,0.81250000,0.93750000,1.00000000));
        bounds.put("32/wall_slider/south",new AxisAlignedBB(0.18750000,0.06250000,0.00000000,0.81250000,0.93750000,0.14062500));
        bounds.put("32/wall_slider/east",new AxisAlignedBB(0.00000000,0.06250000,0.18750000,0.14062500,0.93750000,0.81250000));
        bounds.put("32/wall_slider/west",new AxisAlignedBB(0.85937500,0.06250000,0.18750000,1.00000000,0.93750000,0.81250000));
        bounds.put("32/wall_slider/floor_x",new AxisAlignedBB(0.06250000,0.00000000,0.18750000,0.93750000,0.14062500,0.81250000));
        bounds.put("32/wall_slider/floor_z",new AxisAlignedBB(0.18750000,0.00000000,0.06250000,0.81250000,0.14062500,0.93750000));
        bounds.put("32/wall_slider/ceiling_x",new AxisAlignedBB(0.06250000,0.85937500,0.18750000,0.93750000,1.00000000,0.81250000));
        bounds.put("32/wall_slider/ceiling_z",new AxisAlignedBB(0.18750000,0.85937500,0.06250000,0.81250000,1.00000000,0.93750000));
        bounds.put("32/airliner_throttle/north",new AxisAlignedBB(0.06250000,0.06250000,0.25000000,0.93750000,0.93750000,1.00000000));
        bounds.put("32/airliner_throttle/south",new AxisAlignedBB(0.06250000,0.06250000,0.00000000,0.93750000,0.93750000,0.75000000));
        bounds.put("32/airliner_throttle/east",new AxisAlignedBB(0.00000000,0.06250000,0.06250000,0.75000000,0.93750000,0.93750000));
        bounds.put("32/airliner_throttle/west",new AxisAlignedBB(0.25000000,0.06250000,0.06250000,1.00000000,0.93750000,0.93750000));
        bounds.put("32/airliner_throttle/floor_z",new AxisAlignedBB(0.06250000,0.00000000,0.06250000,0.93750000,0.75000000,0.93750000));
        bounds.put("32/airliner_throttle/floor_x",new AxisAlignedBB(0.06250000,0.00000000,0.06250000,0.93750000,0.75000000,0.93750000));
        bounds.put("32/airliner_throttle/ceiling_z",new AxisAlignedBB(0.06250000,0.25000000,0.06250000,0.93750000,1.00000000,0.93750000));
        bounds.put("32/airliner_throttle/ceiling_x",new AxisAlignedBB(0.06250000,0.25000000,0.06250000,0.93750000,1.00000000,0.93750000));
        bounds.put("32/fighter_throttle/north",new AxisAlignedBB(0.09375000,0.06250000,0.31250000,0.90625000,0.93750000,1.00000000));
        bounds.put("32/fighter_throttle/south",new AxisAlignedBB(0.09375000,0.06250000,0.00000000,0.90625000,0.93750000,0.68750000));
        bounds.put("32/fighter_throttle/east",new AxisAlignedBB(0.00000000,0.06250000,0.09375000,0.68750000,0.93750000,0.90625000));
        bounds.put("32/fighter_throttle/west",new AxisAlignedBB(0.31250000,0.06250000,0.09375000,1.00000000,0.93750000,0.90625000));
        bounds.put("32/fighter_throttle/floor_z",new AxisAlignedBB(0.09375000,0.00000000,0.06250000,0.90625000,0.68750000,0.93750000));
        bounds.put("32/fighter_throttle/floor_x",new AxisAlignedBB(0.06250000,0.00000000,0.09375000,0.93750000,0.68750000,0.90625000));
        bounds.put("32/fighter_throttle/ceiling_z",new AxisAlignedBB(0.09375000,0.31250000,0.06250000,0.90625000,1.00000000,0.93750000));
        bounds.put("32/fighter_throttle/ceiling_x",new AxisAlignedBB(0.06250000,0.31250000,0.09375000,0.93750000,1.00000000,0.90625000));
    }
    static AxisAlignedBB get(int detail,String kind,String mount){return bounds.getOrDefault(detail+"/"+kind+"/"+mount,net.minecraft.block.Block.FULL_BLOCK_AABB);}
    private SignalControlBounds(){}
}
