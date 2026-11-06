package com.vandorlabs.neoforge.trapdoor;

import com.vandorlabs.render.DiagonalPanelContact;
import net.minecraft.core.*;
import net.minecraft.nbt.*;

/** Released item/block placement scalars; callers supply a loaded clicked-support snapshot. */
public final class NativeTrapdoorPlacement {
 private NativeTrapdoorPlacement(){}
 public record Support(int mode,Direction facing,boolean inverted){public Support{if(mode<0||mode>2||facing==null||facing.getAxis().isVertical())throw new IllegalArgumentException("Trapdoor clicked plane");}}
 public record State(Direction facing,boolean inverted,boolean open){}
 /** Legacy callbacks receive hit coordinates relative to the clicked cell, before its offset. */
 public static net.minecraft.world.phys.Vec3 hit(net.minecraft.world.item.context.BlockPlaceContext context){return com.vandorlabs.neoforge.placement.NativePlacementHit.hit(context);}
 public static int contextMode(net.minecraft.world.item.context.BlockPlaceContext context,CompoundTag raw){var level=context.getLevel();var support=context.getClickedPos().relative(context.getClickedFace().getOpposite());int inherited=0;if(NativeTrapdoorEntity.loaded(level,support)){var previous=level.getBlockState(support);if(previous.getBlock() instanceof NativeTrapdoorBlock block&&block.diagonal()){var tile=NativeTrapdoorEntity.at(level,support);inherited=tile==null?0:tile.geometryPosition();}else if(level.getBlockEntity(support) instanceof com.vandorlabs.neoforge.shape.NativeWallEntity)inherited=com.vandorlabs.neoforge.shape.NativeDiagonalWallTopology.mode(level,support);}return diagonalMode(raw,inherited);}
 public static net.minecraft.world.level.block.state.BlockState state(NativeTrapdoorBlock block,net.minecraft.world.item.context.BlockPlaceContext context){var pos=context.getClickedPos();var level=context.getLevel();var hit=hit(context);State result;
  if(!block.diagonal())result=regular(context.getClickedFace(),context.getHorizontalDirection(),(float)hit.y,com.vandorlabs.neoforge.redstone.NativeLoadedRedstonePower.level(level,pos)>0);
  else{var support=pos.relative(context.getClickedFace().getOpposite());Support clicked=null;if(NativeTrapdoorEntity.loaded(level,support)){var previous=level.getBlockState(support);if(previous.getBlock() instanceof NativeTrapdoorBlock other&&other==block){var tile=NativeTrapdoorEntity.at(level,support);clicked=new Support(tile==null?0:tile.geometryPosition(),previous.getValue(NativeTrapdoorBlock.FACING),previous.getValue(NativeTrapdoorBlock.HALF)==net.minecraft.world.level.block.state.properties.Half.TOP);}else if(previous.getBlock() instanceof com.vandorlabs.neoforge.shape.NativeDiagonalWallBlock)clicked=new Support(com.vandorlabs.neoforge.shape.NativeDiagonalWallTopology.mode(level,support),previous.getValue(com.vandorlabs.neoforge.shape.NativeDiagonalWallBlock.FACING),previous.getValue(com.vandorlabs.neoforge.shape.NativeDiagonalWallBlock.INVERTED));}result=diagonal(contextMode(context,NativeTrapdoorItem.configuration(context.getItemInHand())),context.getClickedFace(),context.getHorizontalDirection(),(float)hit.x,(float)hit.y,(float)hit.z,clicked,pos.subtract(support));}
  return block.defaultBlockState().setValue(NativeTrapdoorBlock.FACING,result.facing).setValue(NativeTrapdoorBlock.HALF,result.inverted?net.minecraft.world.level.block.state.properties.Half.TOP:net.minecraft.world.level.block.state.properties.Half.BOTTOM).setValue(NativeTrapdoorBlock.OPEN,result.open);
 }
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
