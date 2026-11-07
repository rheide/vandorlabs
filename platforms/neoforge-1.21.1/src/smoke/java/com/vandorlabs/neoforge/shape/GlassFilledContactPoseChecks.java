package com.vandorlabs.neoforge.shape;

import com.google.gson.JsonObject;
import com.vandorlabs.neoforge.glass.NativeGlassBlock;
import com.vandorlabs.persistence.WallSettings;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;

/** Production loaded-access projection for actual Source filled-cap emission cases. */
public final class GlassFilledContactPoseChecks {
 public static NativeDiagonalWallGeometry.Geometry geometry(JsonObject row,NativeDiagonalWallBlock wall,NativeGlassBlock glass){
  var face=Direction.byName(row.get("facing").getAsString());boolean inverted=row.get("inverted").getAsBoolean();int mode=row.get("mode").getAsInt(),fill=row.get("fill").getAsInt(),profile=row.get("profile").getAsInt();var root=wall.defaultBlockState().setValue(NativePanelBlock.FACING,face).setValue(NativeDiagonalWallBlock.INVERTED,inverted);Map<BlockPos,BlockState> states=new HashMap<>();states.put(BlockPos.ZERO,root);boolean rotated=face.getAxis()==Direction.Axis.X,reverse=face!=(rotated?Direction.EAST:Direction.NORTH);
  for(boolean upper:new boolean[]{false,true})if(profile==3||profile==(upper?2:1))for(int offset=-1;offset<=1;offset++){int depth=upper?2:1;if(reverse)depth=3-depth;states.put((upper?BlockPos.ZERO.above():BlockPos.ZERO.below()).relative(face.getOpposite(),offset),glass.defaultBlockState().setValue(NativeGlassBlock.ROTATED,rotated).setValue(NativeGlassBlock.DEPTH,depth));}
  var choice=new WallSettings(mode!=0,mode==2,fill,0,true,0);var access=new NativeDiagonalWallTopology.Access(){public boolean loaded(BlockPos pos){return true;}public BlockState state(BlockPos pos){return states.getOrDefault(pos,Blocks.AIR.defaultBlockState());}public WallSettings settings(BlockPos pos){return choice;}public boolean fullBlock(BlockPos pos,BlockState state){return false;}public boolean transparent(BlockPos pos,BlockState state){return state.getBlock() instanceof NativeGlassBlock;}};
  var pose=NativeDiagonalWallTopology.capture(access,BlockPos.ZERO,root,NativePanelLayout.UNIT);double lower=!row.has("lower")||row.get("lower").isJsonNull()?Double.NaN:row.get("lower").getAsDouble(),upper=!row.has("upper")||row.get("upper").isJsonNull()?Double.NaN:row.get("upper").getAsDouble();if(Double.doubleToLongBits(pose.lowerEnd())!=Double.doubleToLongBits(lower)||Double.doubleToLongBits(pose.upperEnd())!=Double.doubleToLongBits(upper)||pose.hideLower()||pose.hideUpper())throw new AssertionError("Actual Source filled end alignment/cap visibility "+row);
  return NativeDiagonalWallGeometry.build(pose);
 }
 private GlassFilledContactPoseChecks(){}
}
