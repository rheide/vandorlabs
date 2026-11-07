package com.vandorlabs.neoforge.screen;

import com.vandorlabs.neoforge.NativeConfigurationAccess;
import com.vandorlabs.neoforge.VandorLabsBootstrap;
import com.vandorlabs.neoforge.credentials.*;
import com.vandorlabs.screen.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** Chip-only callback, called with the outer interaction's authoritative current-angle local ray. */
public final class NativeScreenChipInteractions {
 private NativeScreenChipInteractions(){}
 private static boolean finite(Vec3 vector){return vector!=null&&Double.isFinite(vector.x)&&Double.isFinite(vector.y)&&Double.isFinite(vector.z);}
 private static NativeScreenEntity screen(Level level,BlockPos pos){var chunk=level.getChunkSource().getChunkNow(pos.getX()>>4,pos.getZ()>>4);if(chunk==null)return null;var raw=chunk.getBlockEntities().get(pos);if(!(raw instanceof NativeScreenEntity tile)||tile.inert()||tile.isRemoved()||tile.getLevel()!=level||chunk.getBlockState(pos)!=tile.getBlockState()||!(tile.getBlockState().getBlock() instanceof NativeScreenBlock block)||block.family()!=tile.family())return null;return tile;}
 private static NativeScreenChipView reader(Level level,BlockPos pos){if(pos==null)return null;var chunk=level.getChunkSource().getChunkNow(pos.getX()>>4,pos.getZ()>>4);if(chunk==null)return null;var raw=chunk.getBlockEntities().get(pos);if(!(raw instanceof NativeCredentialReaderEntity tile)||tile.inert()||tile.isRemoved()||tile.getLevel()!=level||!tile.chip()||tile.getBlockState()!=chunk.getBlockState(pos)||!(tile.getBlockState().getBlock() instanceof NativeCredentialReaderBlock block)||!block.chip())return null;return tile.chipView();}
 /** Source picker eligibility uses the current authored group; an empty valid reader is sufficient. */
 public static boolean available(NativeScreenEntity owner,int slot){if(owner==null||owner.getLevel()==null)return false;var level=owner.getLevel();if(level instanceof ServerLevel server&&!server.getServer().isSameThread())throw new IllegalStateException("Screen chip availability requires server thread");if(slot!=(owner.family()==ScreenFamily.HALF_CONSOLE?1:0)||screen(level,owner.getBlockPos())!=owner)return false;if(ScreenChipMount.integrated(owner.family()))return true;if(owner.family()!=ScreenFamily.SCREEN)return false;var group=NativeScreenChipGroups.group(owner);return group!=null&&reader(level,group.reader())!=null;}
 public static boolean activate(NativeScreenEntity owner,Player player,InteractionHand hand,Vec3 localEye,Vec3 localEnd){
  if(owner==null||player==null||hand!=InteractionHand.MAIN_HAND||!finite(localEye)||!finite(localEnd))return false;
  var level=owner.getLevel();if(level==null)return false;if(level instanceof ServerLevel server&&!server.getServer().isSameThread())throw new IllegalStateException("Screen chip interaction requires server thread");
  if(player.level()!=level||!player.isAlive()||player.isSpectator()||player.isShiftKeyDown()||player.getMainHandItem().is(VandorLabsBootstrap.CONFIGURIZER.get())||screen(level,owner.getBlockPos())!=owner)return false;
  ServerPlayer actor=player instanceof ServerPlayer server?server:null;if(!level.isClientSide&&(actor==null||!NativeConfigurationAccess.canEditDialog(actor,owner)))return false;
  var family=owner.family();int slot=family==ScreenFamily.HALF_CONSOLE?1:0;if(!owner.chips().selected(slot))return false;
  var block=(NativeScreenBlock)owner.getBlockState().getBlock();var state=block.screenState(owner.getBlockState());var settings=owner.screenSettings();
  if(ScreenChipMount.integrated(family)){
   var frame=ScreenChipMount.frame(family,state.facing,family==ScreenFamily.DIAGONAL?state.inverted:state.upper,settings.ceilingMounted(),settings.ceilingPosition()<0?1:settings.ceilingPosition());
   // A matched tray refuses immediately on invalid contents; it never falls through to a page click.
   if(frame.hit(localEye.x,localEye.y,localEye.z,localEnd.x,localEnd.y,localEnd.z))return level.isClientSide||owner.chips().interact(actor,hand);
  }
  var data=settings.scalar();boolean deck=family==ScreenFamily.HALF_CONSOLE;
  var projection=ScreenControlLayout.of(family,state,slot,data.smallInput,data.wallPosition,settings.ceilingMounted(),settings.ceilingPosition(),deck);
  var sample=projection.sample(localEye.x,localEye.y,localEye.z,localEnd.x,localEnd.y,localEnd.z);double column=sample.column;
  if(sample.row(12)<0||column<0||column>128)return false;
  NativeScreenEntity display=owner;NativeScreenChipView contents;
  if(ScreenChipMount.integrated(family))contents=owner.chips().view();
  else{
   if(family!=ScreenFamily.SCREEN)return false;var group=NativeScreenChipGroups.group(owner);if(group==null)return false;display=screen(level,group.owner());if(display==null)return false;contents=reader(level,group.reader());column=group.column(owner.getBlockPos(),column);
  }
  contents=display.chipDisplay(contents); // Source materializes even an empty/master display before refusing a page click.
  if(contents==null||contents.journal==null)return false;
  return level.isClientSide||display.chips().navigate(actor,owner,contents,column<64?-1:1);
 }
}
