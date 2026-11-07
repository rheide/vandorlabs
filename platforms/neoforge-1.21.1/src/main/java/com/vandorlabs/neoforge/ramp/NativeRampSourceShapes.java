package com.vandorlabs.neoforge.ramp;
import java.util.*;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.properties.StairsShape;
import net.minecraft.world.phys.AABB;
/** Released vanilla stair collision decomposition, rather than a differently partitioned voxel union. */
public final class NativeRampSourceShapes {
 private static final List<List<AABB>> VANILLA;
 static{var values=new ArrayList<List<AABB>>(Collections.nCopies(40,null));for(var face:new Direction[]{Direction.NORTH,Direction.SOUTH,Direction.WEST,Direction.EAST})for(boolean upper:new boolean[]{false,true})for(var shape:StairsShape.values())values.set(index(face,upper,shape),build(face,upper,shape));VANILLA=List.copyOf(values);}
 private static int index(Direction face,boolean upper,StairsShape shape){if(!face.getAxis().isHorizontal())throw new IllegalArgumentException("Horizontal stair source face");return face.get2DDataValue()*10+(upper?5:0)+shape.ordinal();}
 public static List<AABB> vanillaStairs(Direction face,boolean upper,StairsShape shape){return VANILLA.get(index(face,upper,shape));}
 private static AABB half(Direction face,double low,double high){return new AABB(face==Direction.EAST?.5:0,low,face==Direction.SOUTH?.5:0,face==Direction.WEST?.5:1,high,face==Direction.NORTH?.5:1);}
 private static List<AABB> build(Direction face,boolean upper,StairsShape shape){var result=new ArrayList<AABB>();result.add(new AABB(0,upper?.5:0,0,1,upper?1:.5,1));double low=upper?0:.5,high=upper?.5:1;boolean inner=shape==StairsShape.INNER_LEFT||shape==StairsShape.INNER_RIGHT;if(shape==StairsShape.STRAIGHT||inner)result.add(half(face,low,high));if(shape!=StairsShape.STRAIGHT){var side=shape==StairsShape.INNER_LEFT||shape==StairsShape.OUTER_LEFT?face.getCounterClockWise():face.getClockWise();var a=half(inner?face.getOpposite():face,low,high);var b=half(side,low,high);result.add(new AABB(Math.max(a.minX,b.minX),low,Math.max(a.minZ,b.minZ),Math.min(a.maxX,b.maxX),high,Math.min(a.maxZ,b.maxZ)));}return List.copyOf(result);}
 private NativeRampSourceShapes(){}
}
