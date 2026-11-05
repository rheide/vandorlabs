package com.vandorlabs.copychecks;
import com.vandorlabs.neoforge.*;
import com.vandorlabs.persistence.ControlMountSettings;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
public final class ControlHoverServer {
 public static BlockPos position(int sample){return new BlockPos(-2+sample%4,80+2*(sample/4),0);}
 @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent event){if(!(event.getEntity() instanceof ServerPlayer player))return;var level=player.serverLevel();
  for(int x=-5;x<=6;x++)for(int z=-5;z<=2;z++)level.setBlock(new BlockPos(x,79,z),Blocks.SMOOTH_STONE.defaultBlockState(),3);
  for(int sample=0;sample<8;sample++){var pos=position(sample);level.setBlock(pos.south(),Blocks.IRON_BLOCK.defaultBlockState(),3);
   var block=switch(sample){case 0,1,2,3->VandorLabsBootstrap.INDUSTRIAL_POWER_LEVER.get();case 4->VandorLabsBootstrap.PUSH_BUTTON.get();case 5->VandorLabsBootstrap.ROCKER_SWITCH.get();case 6->VandorLabsBootstrap.WALL_SLIDER.get();default->VandorLabsBootstrap.THRUSTER_LEVER.get();};
   level.setBlock(pos,block.defaultBlockState(),3);var tile=(NativeBinaryControlBlockEntity)level.getBlockEntity(pos);tile.configureMount(sample<2?ControlMountSettings.of(0,0,0):ControlMountSettings.of(3,3,3));if(sample<4)tile.setPowerLeverSize(sample%2);
  }
  player.setGameMode(GameType.CREATIVE);player.getInventory().clearContent();player.getInventory().selected=0;player.teleportTo(.5,80,-3.5);player.setYRot(0);player.setXRot(10);player.inventoryMenu.broadcastChanges();System.out.println("[vandorlabs][control-hover] server ready 8 actual mounted controls");
 }
}
