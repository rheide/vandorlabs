package com.vandorlabs.neoforge.trapdoor;

import com.vandorlabs.neoforge.VandorLabsBootstrap;
import net.minecraft.core.*;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.*;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/** Public registry, real player inventory and authoritative assertions; never invokes placement/configuration directly. */
@EventBusSubscriber(modid="vandorlabs")
public final class TrapdoorWorldClientFixture {
 public static final BlockPos POS=new BlockPos(0,80,0);private static int drops;private static boolean completed;
 public static boolean enabled(){return System.getProperty("vandorlabs.transmitterPhase","").startsWith("trapdoor-world-client");}
 public static boolean diagonal(){return System.getProperty("vandorlabs.transmitterPhase","").endsWith("-diagonal");}
 public static Item item(){return diagonal()?NativeTrapdoorRegistrations.DIAGONAL_ITEM.get():NativeTrapdoorRegistrations.ITEM.get();}
 private static void need(boolean yes,String why){if(!yes)throw new IllegalStateException("Canonical Trapdoor server: "+why);}
 private static NativeTrapdoorEntity host(ServerPlayer p){return (NativeTrapdoorEntity)p.serverLevel().getBlockEntity(POS);}
 private static void pose(ServerPlayer p,Vec3 target){double x=.5,z=-2.8,y=80,dx=target.x-x,dy=target.y-y-p.getEyeHeight(),dz=target.z-z;p.getInventory().selected=0;p.setItemInHand(InteractionHand.OFF_HAND,ItemStack.EMPTY);p.connection.teleport(x,y,z,(float)-Math.toDegrees(Math.atan2(dx,dz)),(float)-Math.toDegrees(Math.atan2(dy,Math.sqrt(dx*dx+dz*dz))));p.getInventory().setChanged();p.inventoryMenu.broadcastChanges();}
 private static void bodyPose(ServerPlayer p){var b=host(p).geometry().bounds();pose(p,new Vec3(POS.getX()+(b.minX+b.maxX)/2,POS.getY()+(b.minY+b.maxY)/2,POS.getZ()+(b.minZ+b.maxZ)/2));}
 @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent event){if(!enabled()||!(event.getEntity() instanceof ServerPlayer p))return;var w=p.serverLevel();for(int x=-5;x<=5;x++)for(int z=-5;z<=5;z++){w.setBlock(new BlockPos(x,79,z),Blocks.STONE.defaultBlockState(),3);for(int y=80;y<85;y++)w.setBlock(new BlockPos(x,y,z),Blocks.AIR.defaultBlockState(),3);}w.getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_DOMOBSPAWNING).set(false,w.getServer());w.getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_DAYLIGHT).set(false,w.getServer());w.getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_WEATHER_CYCLE).set(false,w.getServer());w.setDayTime(6000);w.setWeatherParameters(0,6000,false,false);p.getInventory().clearContent();p.setGameMode(GameType.SURVIVAL);p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(item(),2));pose(p,new Vec3(.5,80,.5));System.out.println("[vandorlabs][trapdoor-world-client] fixture READY");}
 @EventBusSubscriber(modid="vandorlabs",bus=EventBusSubscriber.Bus.MOD) public static final class Registration {
  @SubscribeEvent public static void payload(net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent event){if(!enabled())return;event.registrar("1").playToServer(Mode.TYPE,new StreamCodec<RegistryFriendlyByteBuf,Mode>(){public Mode decode(RegistryFriendlyByteBuf b){if(b.readableBytes()!=1)throw new IllegalArgumentException("Fixture mode length");return new Mode(b.readUnsignedByte());}public void encode(RegistryFriendlyByteBuf b,Mode m){b.writeByte(m.operation());}},(p,c)->c.enqueueWork(()->{if(c.player() instanceof ServerPlayer player)mode(player,p.operation());}));}
 }
 public record Mode(int operation) implements CustomPacketPayload {public static final Type<Mode> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath("vandorlabs","trapdoor_world_client_mode"));public Type<? extends CustomPacketPayload> type(){return TYPE;}}
 private static void mode(ServerPlayer p,int op){var tile=host(p);switch(op){
  case 0->{need(tile!=null&&tile.getType()==(diagonal()?NativeTrapdoorRegistrations.DIAGONAL_ENTITY.get():NativeTrapdoorRegistrations.ENTITY.get())&&p.getMainHandItem().is(item())&&p.getMainHandItem().getCount()==1,"public item survival placement and factory/consumption");p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(VandorLabsBootstrap.CONFIGURIZER.get()));bodyPose(p);System.out.println("[vandorlabs][trapdoor-world-client] placement PASS");}
  case 1->{need(tile.settings().choices().layout()==2&&tile.trigger()==0&&tile.settings().channels().toString().contains("77731"),"actual dedicated dialog payload and channels");p.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);bodyPose(p);System.out.println("[vandorlabs][trapdoor-world-client] configuration PASS");}
  case 2->{need(tile.open(),"actual nearest moved-leaf use toggles authoritative state");bodyPose(p);System.out.println("[vandorlabs][trapdoor-world-client] toggle PASS");}
  case 3->{need(tile.open(),"open endpoint retained during GPU negative");p.setGameMode(GameType.CREATIVE);p.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);bodyPose(p);}
  case 4->{var tag=NativeTrapdoorItem.configuration(p.getMainHandItem());need(p.getMainHandItem().is(item())&&tag!=null&&tag.getInt("TrapdoorTextureLayout")==2&&!tag.contains("NativeTrapdoorLinks")&&!tag.contains("NativeOpeningFluids"),"configured creative pick choices without owner/fluid state");p.setGameMode(GameType.SURVIVAL);p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.WOODEN_PICKAXE));bodyPose(p);System.out.println("[vandorlabs][trapdoor-world-client] pick PASS");}
  case 5->{need(p.serverLevel().getBlockState(POS).isAir()&&drops==1,"actual survival wooden pickaxe drops exactly one configured canonical item");completed=true;System.out.println("[vandorlabs][trapdoor-world-client] harvest PASS");}
  default->throw new IllegalArgumentException("Fixture operation");}}
 @SubscribeEvent public static void joined(net.neoforged.neoforge.event.entity.EntityJoinLevelEvent event){if(enabled()&&!event.getLevel().isClientSide()&&!event.isCanceled()&&event.getEntity() instanceof ItemEntity e&&e.getItem().is(item())&&e.position().distanceToSqr(Vec3.atCenterOf(POS))<16){var tag=NativeTrapdoorItem.configuration(e.getItem());need(tag!=null&&tag.getInt("TrapdoorTextureLayout")==2,"configured harvested item");drops+=e.getItem().getCount();}}
 @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event){if(enabled())System.out.println("[vandorlabs][trapdoor-world-client] logout "+(completed?"PASS":"INCOMPLETE"));}
}
