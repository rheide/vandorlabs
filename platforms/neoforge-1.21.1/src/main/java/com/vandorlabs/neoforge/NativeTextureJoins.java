package com.vandorlabs.neoforge;

import com.vandorlabs.surface.*;
import net.minecraft.core.BlockPos;

/** Directed artwork edges; neighbor pair settings never override the selected owner. */
public final class NativeTextureJoins {
 public static boolean edge(TextureJoinOwner owner,BlockPos from,BlockPos next){return edge(owner.textureJoin(),from,next);}
 public static boolean edge(TextureJoinSettings policy,BlockPos from,BlockPos next){return policy.connects(from.getX(),from.getY(),from.getZ(),next.getX(),next.getY(),next.getZ());}
 public static boolean shareable(TextureJoinOwner root,java.util.Collection<? extends TextureJoinOwner> members){var policy=root.textureJoin();if(policy.direction==0||policy.directional()&&policy.parity!=0)return false;for(var member:members)if(member.textureJoin().direction!=policy.direction||policy.directional()&&member.textureJoin().parity!=0)return false;return true;}
 public static boolean ordinary(TextureJoinOwner owner){return owner.textureJoin().direction==TextureJoinSettings.ALL;}
 public static boolean joined(net.minecraft.world.level.Level level,BlockPos from,BlockPos next){return level.hasChunkAt(from)&&level.getBlockEntity(from) instanceof TextureJoinOwner owner&&joined(level,from,next,owner.textureJoin());}
 public static boolean joined(net.minecraft.world.level.Level level,BlockPos from,BlockPos next,TextureJoinSettings rootPolicy){
  if(!level.hasChunkAt(from)||!level.hasChunkAt(next)||!(level.getBlockEntity(from) instanceof TextureJoinOwner owner)||!edge(rootPolicy,from,next))return false;
  var a=level.getBlockEntity(from);var b=level.getBlockEntity(next);if(a==null||b==null||a.isRemoved()||b.isRemoved()||a.getBlockState().getBlock()!=b.getBlockState().getBlock()||b instanceof TextureJoinOwner candidate&&candidate.textureJoin().direction==0)return false;
  if(a instanceof com.vandorlabs.neoforge.shape.NativeWallEntity wall)return com.vandorlabs.neoforge.shape.NativeWallLayouts.joined(wall,next);
  if(a instanceof com.vandorlabs.neoforge.glass.NativeGlassEntity glass&&b instanceof com.vandorlabs.neoforge.glass.NativeGlassEntity other){var x=glass.settings().glass();var y=other.settings().glass();return glass.available()&&other.available()&&x.join&&y.join&&(x.material<0)==(y.material<0)&&ModernFinishCatalog.storedChoice(x.material)==ModernFinishCatalog.storedChoice(y.material)&&(x.material>=0||x.shade==y.shade)&&x.tint==y.tint&&x.textureLayout()==y.textureLayout()&&(x.material<0||!ModernFinishCatalog.option(x.material).category().equals("Screens")||glass.settings().playback().equals(other.settings().playback()))&&com.vandorlabs.neoforge.glass.NativeGlassService.connects(level,from,a.getBlockState(),next);}
  if(Math.abs(from.getX()-next.getX())+Math.abs(from.getY()-next.getY())+Math.abs(from.getZ()-next.getZ())!=1)return false;
  if(a.getBlockState().is(VandorLabsBootstrap.PROGRAMMABLE_BLOCK.get())&&a instanceof ProgrammableBlockEntity cube&&b instanceof ProgrammableBlockEntity other)return cube.settings().layout!=1&&cube.settings().layout==other.settings().layout&&cube.settings().mainTexture==other.settings().mainTexture&&a.getBlockState().getValue(ProgrammableBlock.FACING)==b.getBlockState().getValue(ProgrammableBlock.FACING);
  return false;
 }
 private NativeTextureJoins(){}
}
