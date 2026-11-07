package com.vandorlabs.neoforge.copifier;

import com.vandorlabs.items.ConnectedCopySelection;
import com.vandorlabs.items.CopiedDirectionality;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.*;
import net.minecraft.world.level.block.state.BlockState;

/** Source-compatible bounded physical-region discovery before any housing settings are applied. */
public final class NativeCopifierHousingConnected {
 private static boolean matchingState(BlockState original,BlockState candidate,boolean ignore){
  if(original.getBlock()!=candidate.getBlock())return false;
  for(var property:original.getProperties()){
   if(ignore&&CopiedDirectionality.ignoreProperty(property.getName(),property.getValueClass()==Direction.class,property.getValueClass()==Direction.Axis.class))continue;
   if(!original.getValue(property).equals(candidate.getValue(property)))return false;
  }
  return true;
 }
 public static int apply(ServerLevel level,BlockPos clicked,ServerPlayer actor,Direction face,CompoundTag values,boolean ignore){
  var origin=NativeCopifierHousingWorld.target(level,clicked,actor,face,true);if(origin==null||values==null||values.isEmpty())return 0;
  final CompoundTag original;try{original=origin.configuration(ignore);}catch(IllegalArgumentException unsupported){return 0;}
  var state=origin.tile().getBlockState();
  var root=origin.tile().getBlockPos().immutable();
  var selection=ConnectedCopySelection.discover(root,root,new ConnectedCopySelection.Access<BlockPos>(){
   public BlockPos matchingOwner(BlockPos cell){
    var candidate=NativeCopifierHousingWorld.target(level,cell,actor,face,false);if(candidate==null||!matchingState(state,candidate.tile().getBlockState(),ignore))return null;
    try{return original.equals(candidate.configuration(ignore))?candidate.tile().getBlockPos().immutable():null;}catch(IllegalArgumentException unsupported){return null;}
   }
   public BlockPos offset(BlockPos cell,int x,int y,int z){return cell.offset(x,y,z);}
  });
  if(selection.exceeded)return -1;int applied=0;
  for(var pos:selection.owners){var target=NativeCopifierHousingWorld.target(level,pos,actor,face,false);if(target!=null&&target.tile().getBlockState().getBlock()==state.getBlock()&&NativeCopifierHousingWorld.apply(level,pos,target,values,actor))applied++;}
  return applied;
 }
 private NativeCopifierHousingConnected(){}
}
