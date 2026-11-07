package com.vandorlabs.neoforge.glass;

import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.*;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

/** Private installed-world render fixture; canonical registration remains independent. */
@EventBusSubscriber(modid="vandorlabs")
public final class GlassWorldFixture {
 public static NativeGlassBlock BLOCK;public static BlockEntityType<NativeGlassEntity> TYPE;
 public static final BlockPos BASE=new BlockPos(0,80,0),L=BASE.offset(-4,0,3),T=BASE.offset(-7,0,3);
 public static boolean canonical(){return "true".equals(System.getenv("VANDOR_FLAT_GLASS_CANONICAL_CHECK"));}
 public static void bind(){if(canonical()){BLOCK=NativeGlassRegistrations.BLOCK.get();TYPE=NativeGlassRegistrations.ENTITY.get();}}
 public static boolean enabled(){return "flat-glass-world-client".equals(System.getProperty("vandorlabs.transmitterPhase",""));}
 @EventBusSubscriber(modid="vandorlabs",bus=EventBusSubscriber.Bus.MOD)public static final class Registration {
  @SubscribeEvent public static void register(net.neoforged.neoforge.registries.RegisterEvent e){if(!enabled()||canonical())return;
   e.register(Registries.BLOCK,h->{BLOCK=new NativeGlassBlock(Block.Properties.of().strength(.5F,1.2F).sound(SoundType.GLASS),()->TYPE);h.register(ResourceLocation.parse("vandorlabs:flat_glass_world_fixture"),BLOCK);});
   e.register(Registries.BLOCK_ENTITY_TYPE,h->{TYPE=BlockEntityType.Builder.of((p,s)->new NativeGlassEntity(TYPE,p,s),BLOCK).build(null);h.register(ResourceLocation.parse("vandorlabs:flat_glass_world_fixture"),TYPE);});
  }
 }
 private static void put(ServerLevel w,BlockPos p,int material,int tint,boolean framed,int interior){
  putPose(w,p,material,tint,framed,interior,false,Direction.NORTH);
 }
 private static void putPose(ServerLevel w,BlockPos p,int material,int tint,boolean framed,int interior,boolean rotated,Direction facing){
  w.setBlock(p,BLOCK.defaultBlockState().setValue(NativeGlassBlock.ROTATED,rotated),18);var t=new CompoundTag();t.putInt("GlassSize",1);t.putInt("GlassPanelFacing",facing.get2DDataValue());t.putInt("GlassMaterial",material);t.putInt("GlassTint",tint);t.putBoolean("GlassFramed",framed);t.putBoolean("GlassJoin",true);if(interior>=0)t.putInt("FrameInterior",interior);
  ((NativeGlassEntity)w.getBlockEntity(p)).loadWithComponents(t,w.registryAccess());
 }
 @SubscribeEvent public static void start(net.neoforged.neoforge.event.server.ServerStartedEvent e){if(!enabled())return;bind();var w=e.getServer().overworld();System.out.println("[vandorlabs][glass-world] actual server PID="+ProcessHandle.current().pid());
  w.getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_DAYLIGHT).set(false,w.getServer());w.setDayTime(6000);w.setWeatherParameters(6000,0,false,false);
  for(int x=-10;x<12;x++)for(int z=-8;z<8;z++){w.setBlock(new BlockPos(x,79,z),Blocks.STONE.defaultBlockState(),18);for(int y=80;y<85;y++)w.setBlock(new BlockPos(x,y,z),Blocks.AIR.defaultBlockState(),18);}
  for(int x=0;x<3;x++)for(int y=0;y<2;y++)put(w,BASE.offset(x,y,0),-1,1,true,-1);
  put(w,BASE.east(4),0,1,true,0);put(w,BASE.east(5),0,3,true,0);put(w,BASE.east(6),-1,1,false,-1);
  for(int x=0;x<7;x++)w.setBlock(BASE.offset(x,0,2),Blocks.RED_CONCRETE.defaultBlockState(),18);
  putPose(w,L,64,1,true,-1,false,Direction.NORTH);putPose(w,L.north(),64,1,true,-1,true,Direction.EAST);
  putPose(w,T,64,1,true,-1,false,Direction.SOUTH);putPose(w,T.east(),64,1,true,-1,false,Direction.SOUTH);putPose(w,T.north(),64,1,true,-1,true,Direction.EAST);putPose(w,T.south(),64,1,true,-1,true,Direction.EAST);
  System.out.println("[vandorlabs][glass-world] setup PASS actual15hosts/defaultJoined/selected/opaque/bare/L/T/oppositeFacing");
 }
 @SubscribeEvent public static void login(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent e){if(!enabled()||!(e.getEntity() instanceof ServerPlayer p))return;p.setGameMode(GameType.SURVIVAL);p.setNoGravity(true);p.teleportTo(3.5,80,-7);p.connection.teleport(3.5,80,-7,0,5);}
 @SubscribeEvent public static void logout(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent e){if(enabled()&&e.getEntity() instanceof ServerPlayer p){System.out.println("[vandorlabs][glass-world] logout PASS");p.getServer().halt(false);}}
 private GlassWorldFixture(){}
}
