package com.vandorlabs.neoforge.light;

import com.vandorlabs.neoforge.screen.NativeScreenCatalog;
import com.vandorlabs.persistence.LightData;
import com.vandorlabs.redstone.ChannelList;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/** Private real hosts and authoritative assertions; no production Light IDs, item or menu registration. */
@EventBusSubscriber(modid="vandorlabs")
public final class LightRenderWorldFixture {
 public static final BlockPos BASE=new BlockPos(0,80,0),FRAME=BASE.east(5),SAMPLE=BASE.east(12);
 public static final NativeLightBlock[] BLOCKS=new NativeLightBlock[3];public static BlockEntityType<NativeLightEntity> TYPE;private static boolean completed;
 public static boolean enabled(){return "light-render-world-client".equals(System.getProperty("vandorlabs.transmitterPhase"));}
 public static boolean canonical(){return Boolean.getBoolean("vandorlabs.lightCanonical");}
 public static void resolveCanonical(){BLOCKS[0]=NativeLightRegistrations.BLOCK.get();BLOCKS[1]=NativeLightRegistrations.FRAME.get();BLOCKS[2]=NativeLightRegistrations.SLAB.get();TYPE=NativeLightRegistrations.ENTITY.get();}
 public static ResourceLocation id(int kind){return ResourceLocation.fromNamespaceAndPath("vandorlabs","light_render_world_"+kind);}
 private static void need(boolean yes,String why){if(!yes)throw new IllegalStateException("Light world server: "+why);}
 @EventBusSubscriber(modid="vandorlabs",bus=EventBusSubscriber.Bus.MOD) public static final class Registration {
  @SubscribeEvent public static void register(net.neoforged.neoforge.registries.RegisterEvent event){if(!enabled()||canonical())return;event.register(Registries.BLOCK,h->{for(var kind:NativeLightBlock.Kind.values()){int i=kind.ordinal();BLOCKS[i]=NativeLightBlock.create(kind,BlockBehaviour.Properties.of().strength(5,10),()->TYPE);h.register(id(i),BLOCKS[i]);}});event.register(Registries.BLOCK_ENTITY_TYPE,h->{TYPE=BlockEntityType.Builder.of((pos,state)->new NativeLightEntity(TYPE,pos,state,NativeScreenCatalog.INSTANCE),BLOCKS).build(null);h.register(ResourceLocation.fromNamespaceAndPath("vandorlabs","light_render_world"),TYPE);});}
  @SubscribeEvent public static void payload(net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent event){if(!enabled())return;event.registrar("1").playToServer(Mode.TYPE,new StreamCodec<RegistryFriendlyByteBuf,Mode>(){public Mode decode(RegistryFriendlyByteBuf b){if(b.readableBytes()!=1)throw new IllegalArgumentException("Light fixture mode length");return new Mode(b.readUnsignedByte());}public void encode(RegistryFriendlyByteBuf b,Mode value){b.writeByte(value.operation());}},(p,c)->c.enqueueWork(()->{if(c.player() instanceof ServerPlayer player)mode(player,p.operation());}));}
 }
 public record Mode(int operation) implements CustomPacketPayload {public static final Type<Mode> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath("vandorlabs","light_render_world_mode"));public Type<? extends CustomPacketPayload> type(){return TYPE;}}
 private static NativeLightEntity at(ServerLevel world,BlockPos pos){return (NativeLightEntity)world.getBlockEntity(pos);}
 private static void pose(ServerPlayer p,boolean wide){p.setGameMode(wide?GameType.CREATIVE:GameType.SURVIVAL);p.getInventory().clearContent();p.getInventory().selected=0;p.setItemInHand(InteractionHand.OFF_HAND,ItemStack.EMPTY);p.getAbilities().flying=wide;p.onUpdateAbilities();double x=wide?6:1.5,y=80,z=wide?-12:-3;Vec3 target=wide?new Vec3(6,80.8,.8):new Vec3(1.5,80.5,0);double dx=target.x-x,dz=target.z-z,dy=target.y-y-p.getEyeHeight();p.connection.teleport(x,y,z,(float)-Math.toDegrees(Math.atan2(dx,dz)),(float)-Math.toDegrees(Math.atan2(dy,Math.sqrt(dx*dx+dz*dz))));p.inventoryMenu.broadcastChanges();}
 private static NativeLightEntity put(ServerLevel world,BlockPos pos,int family,boolean upper,boolean small,boolean join,boolean on,int face){var state=BLOCKS[family].defaultBlockState().setValue(NativeLightBlock.FACING,Direction.NORTH);if(family==2)state=state.setValue(NativeLightBlock.HALF,upper?Half.TOP:Half.BOTTOM);world.setBlock(pos,state,18);var owner=at(world,pos);var tag=new CompoundTag();tag.putInt("housingTexture",9);tag.putInt("HousingTextureVersion",1);tag.putInt("MainTextureLayout",1);tag.putInt("LightFaceTexture",face);tag.putInt("LightTrigger",0);tag.putInt("LightLevel",11);tag.putBoolean("LightJoin",join);tag.putBoolean("LightOn",on);tag.putBoolean("smallInput",small);tag.putString("PrivateLightMarker","server-only");owner.loadWithComponents(tag,world.registryAccess());owner.onLoad();NativeLightService.refresh(owner);return owner;}
 @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent event){if(!enabled()||!(event.getEntity() instanceof ServerPlayer p))return;if(canonical())resolveCanonical();var w=p.serverLevel();w.getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_DOMOBSPAWNING).set(false,w.getServer());w.getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_DAYLIGHT).set(false,w.getServer());w.getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_WEATHER_CYCLE).set(false,w.getServer());w.setDayTime(6000);w.setWeatherParameters(0,6000,false,false);for(int x=-4;x<=17;x++)for(int z=-15;z<=5;z++){w.setBlock(new BlockPos(x,79,z),Blocks.STONE.defaultBlockState(),18);for(int y=80;y<=84;y++)w.setBlock(new BlockPos(x,y,z),Blocks.AIR.defaultBlockState(),18);}
  for(int y=0;y<2;y++)for(int x=0;x<3;x++)put(w,BASE.offset(x,y,0),0,false,false,true,true,382);put(w,FRAME,1,false,false,true,true,64);put(w,FRAME.east(),1,false,false,true,true,64);put(w,FRAME.above(),1,false,false,true,true,64);put(w,FRAME.east(2),1,false,true,true,true,64);put(w,BASE.east(9),2,false,false,true,true,80);put(w,BASE.east(10),2,true,false,true,true,80);put(w,SAMPLE,1,false,false,false,false,64);w.setBlock(SAMPLE.above(),Blocks.GLOWSTONE.defaultBlockState(),18);NativeLightRenderService.flush(w);pose(p,true);System.out.println("[vandorlabs][light-render-world] setup PASS "+(canonical()?"canonical":"private")+"13 real full/frame/tiny/slab hosts rectangle/L/half mismatch and isolated lighting");}
 private static void mode(ServerPlayer p,int op){var w=p.serverLevel();switch(op){
  case 0->pose(p,false);
  case 1->{for(int y=0;y<2;y++)for(int x=0;x<3;x++)need(!at(w,BASE.offset(x,y,0)).visual().on(),"actual survival use toggles entire six-member rectangle");pose(p,true);System.out.println("[vandorlabs][light-render-world] toggle PASS actual survival vanilla use packet");}
  case 2->{w.setBlock(SAMPLE.above(),Blocks.AIR.defaultBlockState(),3);System.out.println("[vandorlabs][light-render-world] light-only PASS removed physical glowstone");}
  case 3->{pose(p,false);p.setGameMode(GameType.CREATIVE);var owner=at(w,BASE.east());var old=owner.settings().light();need(owner.configure(p,new NativeLightSettings(new LightData(old.texture,64,old.offFaceTexture,old.configuredLevel,old.signalBrightness,old.offset,old.joinedLevel,old.manualOn,old.join,old.trigger,old.joinedPower)),ChannelList.EMPTY),"owning-thread material edit");NativeLightRenderService.flush(w);var projection=owner.renderProjection();need(projection.left()==0&&projection.top()==0&&projection.right()==16&&projection.bottom()==16,"different material splits selected root into own rectangle");pose(p,true);System.out.println("[vandorlabs][light-render-world] material PASS source membership and UV split");}
  case 4->{completed=true;System.out.println("[vandorlabs][light-render-world] complete PASS");}
  default->throw new IllegalArgumentException("Light private fixture operation");}}
 @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event){if(enabled())System.out.println("[vandorlabs][light-render-world] logout "+(completed?"PASS":"INCOMPLETE"));}
}
