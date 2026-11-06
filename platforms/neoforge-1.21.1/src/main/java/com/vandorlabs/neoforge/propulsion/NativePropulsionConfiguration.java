package com.vandorlabs.neoforge.propulsion;

import com.vandorlabs.persistence.RedstoneLightData;
import com.vandorlabs.redstone.ChannelList;
import java.util.List;
import java.util.function.UnaryOperator;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;

/** Released editor transaction order, distinct from plain-click mode cycling. */
public final class NativePropulsionConfiguration {
 private NativePropulsionConfiguration(){}
 public static boolean open(Player player,net.minecraft.world.level.Level world,net.minecraft.core.BlockPos pos,net.minecraft.world.inventory.MenuType<NativePropulsionMenu> type){if(type==null||!NativePropulsionService.loaded(world,pos)||!(world.getBlockEntity(pos) instanceof NativePropulsionEntity owner)||!owner.available()||!com.vandorlabs.neoforge.NativeConfigurationAccess.canEdit(player,owner))return false;if(world.isClientSide)return true;if(!(player instanceof net.minecraft.server.level.ServerPlayer server))return false;server.openMenu(new net.minecraft.world.SimpleMenuProvider((id,inventory,actor)->new NativePropulsionMenu(type,id,owner),owner.getBlockState().getBlock().getName()),pos);return true;}
 public record Draft(ChannelList channels,boolean updateParticles,int particleLevel,boolean updateJoin,boolean join,boolean updateSide,int sideTexture,boolean updateShape,int shape,boolean inverted,boolean analog,int threshold){
  public Draft{if(channels==null||particleLevel<0||particleLevel>3||sideTexture<0||shape<0||shape>2||threshold<0||threshold>15)throw new IllegalArgumentException("Invalid engine edit");}
 }
 /** Called only after the dedicated menu has checked owner identity, interaction and creative shape access. */
 static boolean apply(NativePropulsionEntity selected,Draft draft,Player player){
  if(!(selected.getLevel() instanceof ServerLevel)||!selected.available()||!NativePropulsionService.installed(selected))return false;
  var definition=((NativePropulsionBlock)selected.getBlockState().getBlock()).definition();
  if(draft.updateShape&&draft.shape!=definition.shape)return NativePropulsionShape.configure(selected,draft.shape,player);
  if(definition.maxConnected>0){
   // Each released phase discovers its own current assembly. Inversion or analog state can split it.
   edit(group(selected),d->copy(d,d.channel,d.particleLevel,draft.inverted,d.analog,d.particleThreshold,d.join,d.sideTexture),null);
   edit(group(selected),d->copy(d,d.channel,d.particleLevel,d.inverted,draft.analog,draft.threshold,d.join,d.sideTexture),null);
   var peers=group(selected);
   edit(peers,d->copy(d,draft.channels.first(),d.particleLevel,d.inverted,d.analog,d.particleThreshold,d.join,d.sideTexture),draft.channels);
   if(draft.updateParticles)edit(peers,d->copy(d,d.channel,draft.particleLevel,d.inverted,d.analog,d.particleThreshold,d.join,d.sideTexture),null);
   if(draft.updateJoin)edit(peers,d->copy(d,d.channel,d.particleLevel,d.inverted,d.analog,d.particleThreshold,draft.join,d.sideTexture),null);
   if(draft.updateSide)edit(peers,d->copy(d,d.channel,d.particleLevel,d.inverted,d.analog,d.particleThreshold,d.join,draft.sideTexture),null);
  }else{
   edit(List.of(selected),d->copy(d,draft.channels.first(),draft.updateParticles?draft.particleLevel:d.particleLevel,d.inverted,d.analog,d.particleThreshold,d.join,draft.updateSide?draft.sideTexture:d.sideTexture),draft.channels);
  }
  edit(List.of(selected),d->copy(d,d.channel,d.particleLevel,draft.inverted,draft.analog,draft.threshold,d.join,d.sideTexture),null);
  if(draft.updateShape&&!definition.family.isEmpty()){
   if(!NativePropulsionShape.configure(selected,draft.shape,player))return false;
   if(draft.updateJoin&&draft.shape!=2)edit(List.of(selected),d->copy(d,d.channel,d.particleLevel,d.inverted,d.analog,d.particleThreshold,draft.join,d.sideTexture),null);
  }
  NativePropulsionService.flush(selected.getLevel());return true;
 }
 private static List<NativePropulsionEntity> group(NativePropulsionEntity selected){NativePropulsionService.refresh(selected);return NativePropulsionService.members(selected);}
 private static void edit(List<NativePropulsionEntity> peers,UnaryOperator<RedstoneLightData> change,ChannelList channels){for(var peer:peers){var before=peer.settings();peer.installSettings(new NativeRedstoneLightSettings(change.apply(before.data()),channels==null?before.channels():channels));}if(!peers.isEmpty())NativePropulsionService.flush(peers.getFirst().getLevel());}
 private static RedstoneLightData copy(RedstoneLightData d,int channel,int particles,boolean inverted,boolean analog,int threshold,boolean join,int side){return new RedstoneLightData(channel,d.channelSignal,d.channelLevel,d.manualOn,particles,d.initialized,inverted,analog,threshold,join,side);}
}
