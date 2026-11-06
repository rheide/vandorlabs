package com.vandorlabs.neoforge.light;

import com.vandorlabs.light.ProgrammableLightShape;
import com.vandorlabs.neoforge.NativeVoxelShapes;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.*;

/** Fixed, checked cache of every released single-box Light pose. */
public final class NativeLightGeometry {
    private static final AABB[] BOXES=new AABB[72];
    private static final VoxelShape[] SHAPES=new VoxelShape[72];
    private static final int RETAINED;
    static {int cost=0;for(int family=0;family<3;family++)for(Direction facing:Direction.values())for(boolean upper:new boolean[]{false,true})for(boolean small:new boolean[]{false,true}){int index=index(family,facing,upper,small);double[] b=ProgrammableLightShape.bounds(family,facing.get3DDataValue(),upper,small);BOXES[index]=new AABB(b[0],b[1],b[2],b[3],b[4],b[5]);SHAPES[index]=Shapes.create(BOXES[index]);cost+=NativeVoxelShapes.retainedCost(SHAPES[index],1)+80;}if(cost>2*1024*1024)throw new IllegalStateException("Light shapes exceed fixed retained budget");RETAINED=cost;}
    private NativeLightGeometry(){}
    private static int index(int family,Direction facing,boolean upper,boolean small){if(family<0||family>2)throw new IllegalArgumentException("Light family");return ((family*6+facing.get3DDataValue())*2+(upper?1:0))*2+(small?1:0);}
    public static AABB bounds(int family,Direction facing,boolean upper,boolean small){return BOXES[index(family,facing,upper,small)];}
    public static VoxelShape shape(int family,Direction facing,boolean upper,boolean small){return SHAPES[index(family,facing,upper,small)];}
    public static int retainedBytes(){return RETAINED;}
}
