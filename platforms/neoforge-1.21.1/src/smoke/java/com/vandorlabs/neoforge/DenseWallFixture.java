package com.vandorlabs.neoforge;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

/** Canonical placed owners, deliberately outside the camera's visible height. */
@EventBusSubscriber(modid="vandorlabs")
public final class DenseWallFixture {
 static final String[] IDS={"programmable_diagonal_glass","programmable_diagonal_porthole","programmable_glass"};
 static final BlockPos BASE=new BlockPos(0,180,0);
 static boolean enabled(){return "dense-wall-client".equals(System.getProperty("vandorlabs.transmitterPhase",""));}
 static BlockPos pos(int role,int index){return BASE.offset(role*30+(index&7)*3,((index>>6)&7)*3,((index>>3)&7)*3);}
 @SubscribeEvent public static void start(net.neoforged.neoforge.event.server.ServerStartedEvent event){
  if(!enabled())return;var level=event.getServer().overworld();
  level.getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_DAYLIGHT).set(false,event.getServer());level.setDayTime(6000);level.setWeatherParameters(6000,0,false,false);
  for(int role=0;role<IDS.length;role++){
   var id=ResourceLocation.fromNamespaceAndPath("vandorlabs",IDS[role]);
   if(!BuiltInRegistries.BLOCK.containsKey(id))throw new AssertionError("Missing canonical "+id);
   var block=BuiltInRegistries.BLOCK.get(id);
   for(int i=0;i<512;i++){var pos=pos(role,i);level.getChunkAt(pos);level.setBlock(pos,block.defaultBlockState(),3);if(role<2?!(level.getBlockEntity(pos) instanceof com.vandorlabs.neoforge.shape.NativeWallEntity):!(level.getBlockEntity(pos) instanceof com.vandorlabs.neoforge.glass.NativeGlassEntity))throw new AssertionError("Missing canonical wall factory "+pos);}
  }
  System.out.println("[vandorlabs][dense-wall] actual server PID="+ProcessHandle.current().pid());
  System.out.println("[vandorlabs][dense-wall] setup PASS 1536canonicalOwners");
 }
 @SubscribeEvent public static void login(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent event){if(enabled()&&event.getEntity() instanceof ServerPlayer player){player.setGameMode(GameType.CREATIVE);player.setNoGravity(true);player.teleportTo(40,110,12);player.connection.teleport(40,110,12,0,0);}}
 @SubscribeEvent public static void logout(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent event){if(enabled()&&event.getEntity() instanceof ServerPlayer player){System.out.println("[vandorlabs][dense-wall] logout PASS");player.getServer().halt(false);}}
 private DenseWallFixture(){}
}
