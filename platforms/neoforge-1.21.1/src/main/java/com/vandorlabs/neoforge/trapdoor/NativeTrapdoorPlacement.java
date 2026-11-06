package com.vandorlabs.neoforge.trapdoor;

import com.vandorlabs.render.DiagonalPanelContact;
import net.minecraft.core.*;
import net.minecraft.nbt.*;

/** Released item/block placement scalars; callers supply a loaded clicked-support snapshot. */
public final class NativeTrapdoorPlacement {
 private NativeTrapdoorPlacement(){}
 public record Support(int mode,Direction facing,boolean inverted){public Support{if(mode<0||mode>2||facing==null||facing.getAxis().isVertical())throw new IllegalArgumentException("Trapdoor clicked plane");}}
 public record State(Direction facing,boolean inverted,boolean open){}
 public static int regularPosition(Direction side,float y){return side==Direction.UP?0:side==Direction.DOWN?2:y<1/3F?0:y>2/3F?2:1;}
 public static int diagonalMode(CompoundTag configuration,int inherited){return configuration==null?inherited:configuration.contains("TrapdoorPosition",Tag.TAG_INT)?Math.max(0,Math.min(2,configuration.getInt("TrapdoorPosition"))):configuration.getBoolean("DiagonalHalfHeight")?2:configuration.getBoolean("DiagonalFullWidth")?1:0;}
 public static State regular(Direction side,Direction playerFacing,float y,boolean powered){return new State(side.getAxis().isHorizontal()?side.getOpposite():playerFacing.getOpposite(),side.getAxis().isHorizontal()?y>.5F:side==Direction.DOWN,powered);}
 public static State diagonal(int mode,Direction side,Direction playerFacing,float x,float y,float z,Support clicked,BlockPos delta){
  if(clicked!=null&&clicked.mode==mode){Direction old=clicked.facing;double normal=delta.getX()*old.getOpposite().getStepX()+delta.getZ()*old.getOpposite().getStepZ();for(Direction facing:new Direction[]{old,old.getOpposite()})for(boolean inverted:new boolean[]{false,true})if(DiagonalPanelContact.samePlane(mode,clicked.inverted,inverted,facing!=old,delta.getY(),normal))return new State(facing,inverted,false);if(side.getAxis()==Direction.Axis.Y)return new State(old,clicked.inverted,false);}
  Direction facing=playerFacing.getOpposite();boolean inverted=side==Direction.DOWN||side.getAxis().isHorizontal()&&y>.5F;
  if(mode!=2){float normal=facing.getAxis()==Direction.Axis.X?x:z;if(normal<1F/3F)facing=facing.getAxis()==Direction.Axis.X?Direction.WEST:Direction.NORTH;else if(normal>2F/3F)facing=facing.getAxis()==Direction.Axis.X?Direction.EAST:Direction.SOUTH;}
  return new State(facing,inverted,false);
 }
}
