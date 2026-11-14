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
 public static int apply(ServerLevel level,BlockPos clicked,ServerPlayer actor,Direction face,CompoundTag values,boolean ignore){return apply(level,clicked,actor,face,values,ignore,false);}
 public static int apply(ServerLevel level,BlockPos clicked,ServerPlayer actor,Direction face,CompoundTag values,boolean ignore,boolean respectJoins){
  var origin=NativeCopifierHousingWorld.target(level,clicked,actor,face,true);if(origin==null||values==null||values.isEmpty())return 0;
  final CompoundTag original;try{original=origin.configuration(ignore);}catch(IllegalArgumentException unsupported){return 0;}
  if(respectJoins)original.remove("texture_join_policy");
  final var policy=origin.tile() instanceof com.vandorlabs.surface.TextureJoinOwner joinOwner?joinOwner.textureJoin():com.vandorlabs.surface.TextureJoinSettings.DEFAULT;
  var state=origin.tile().getBlockState();
  var root=origin.tile().getBlockPos().immutable();
  var selection=ConnectedCopySelection.discover(root,root,new ConnectedCopySelection.Access<BlockPos>(){
   public BlockPos matchingOwner(BlockPos cell){
    var candidate=NativeCopifierHousingWorld.target(level,cell,actor,face,false);if(candidate==null||!matchingState(state,candidate.tile().getBlockState(),ignore))return null;
    try{var configuration=candidate.configuration(ignore);if(respectJoins)configuration.remove("texture_join_policy");return original.equals(configuration)?candidate.tile().getBlockPos().immutable():null;}catch(IllegalArgumentException unsupported){return null;}
   }
   public BlockPos offset(BlockPos cell,int x,int y,int z){return cell.offset(x,y,z);}
   public boolean connects(BlockPos from,BlockPos next){if(!respectJoins)return true;if(!level.hasChunkAt(next))return false;var a=NativeCopifierHousingWorld.target(level,from,actor,face,false);var b=NativeCopifierHousingWorld.target(level,next,actor,face,false);return a!=null&&b!=null&&(a.tile().getBlockPos().equals(b.tile().getBlockPos())||com.vandorlabs.neoforge.NativeTextureJoins.joined(level,from,next,policy));}
  });
  if(selection.exceeded)return -1;
  // Linked configuration callbacks may change later selected owners in place.
  for(var pos:selection.owners)NativeCopifierUndo.before(actor,level,pos);
  int applied=0;
  for(var pos:selection.owners){var target=NativeCopifierHousingWorld.target(level,pos,actor,face,false);if(target!=null&&target.tile().getBlockState().getBlock()==state.getBlock()&&NativeCopifierHousingWorld.apply(level,pos,target,values,actor))applied++;}
  return applied;
 }
 private NativeCopifierHousingConnected(){}
}
