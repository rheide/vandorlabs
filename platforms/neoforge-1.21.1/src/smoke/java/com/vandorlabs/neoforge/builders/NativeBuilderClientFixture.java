package com.vandorlabs.neoforge.builders;
import com.vandorlabs.machines.CoreLayout;
import com.vandorlabs.neoforge.rack.*;
import com.vandorlabs.redstone.ChannelList;
import net.minecraft.core.*;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.*;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
/** One real player places a core, forms its authored frame and uses actual menu slots. */
@EventBusSubscriber(modid="vandorlabs")
public final class NativeBuilderClientFixture{
 public static final BlockPos ROOT=new BlockPos(0,96,0),ACK=new BlockPos(7,96,0);private static java.util.UUID actor;private static int stage;
 public static boolean enabled(){return Boolean.getBoolean("vandorlabs.builderClientCheck");}
 private static void need(boolean v,String why){if(!v)throw new AssertionError("Builder player server stage="+stage+": "+why);}
 public record Mode(int operation)implements CustomPacketPayload{public static final Type<Mode> TYPE=new Type<>(ResourceLocation.parse("vandorlabs:builder_client_fixture"));public Type<? extends CustomPacketPayload> type(){return TYPE;}}
 @EventBusSubscriber(modid="vandorlabs",bus=EventBusSubscriber.Bus.MOD)public static final class Packets{@SubscribeEvent public static void register(net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent e){if(enabled())e.registrar("1").playToServer(Mode.TYPE,new StreamCodec<RegistryFriendlyByteBuf,Mode>(){public Mode decode(RegistryFriendlyByteBuf in){if(in.readableBytes()!=1)throw new IllegalArgumentException("Builder fixture bound");return new Mode(in.readUnsignedByte());}public void encode(RegistryFriendlyByteBuf out,Mode p){out.writeByte(p.operation);}},(p,c)->c.enqueueWork(()->{if(c.player() instanceof ServerPlayer player)try{apply(player,p.operation);}catch(Throwable error){error.printStackTrace();System.out.println("[vandorlabs][builder-client] FAIL "+error);player.getServer().halt(false);}}));}}
 @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent event){if(!enabled()||!(event.getEntity() instanceof ServerPlayer p))return;actor=p.getUUID();var world=p.serverLevel();for(int x=-3;x<=14;x++)for(int z=-4;z<=6;z++){world.setBlock(new BlockPos(x,95,z),Blocks.STONE.defaultBlockState(),3);for(int y=96;y<=102;y++)world.setBlock(new BlockPos(x,y,z),Blocks.AIR.defaultBlockState(),3);}var shape=new CoreLayout(3,5,3,2,0,0);for(int x=0;x<3;x++)for(int y=0;y<5;y++)for(int z=0;z<3;z++)if(shape.frame(x,y,z)&&!(x==0&&y==0&&z==0))world.setBlock(ROOT.offset(x,y,z),NativeBuilderFormation.matter().defaultBlockState(),3);
  p.setGameMode(GameType.SURVIVAL);p.getInventory().clearContent();p.getInventory().selected=0;p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(NativeBuilderBindings.CORES.get("synthesis_tower")));p.setItemInHand(InteractionHand.OFF_HAND,ItemStack.EMPTY);p.getInventory().setItem(9,new ItemStack(NativeBuilderFormation.matter(),1));var battery=new ItemStack(NativeRackBindings.BATTERY);NativeBatteryModule.transfer(battery,10000,false,true);p.getInventory().setItem(10,battery);p.connection.send(new net.minecraft.network.protocol.game.ClientboundSetCarriedItemPacket(0));p.inventoryMenu.broadcastChanges();p.connection.teleport(.5,96,-3.5,0,(float)Math.toDegrees(Math.atan2(p.getEyeHeight(),4)));System.out.println("[vandorlabs][builder-client] setup PASS actualSurvival/canonicalFrame/heldCore/chargedBattery");
 }
 private static void held(ServerPlayer p,ItemStack stack,double x,double y,double z){p.getInventory().selected=0;p.setItemInHand(InteractionHand.MAIN_HAND,stack);p.setItemInHand(InteractionHand.OFF_HAND,ItemStack.EMPTY);p.connection.send(new net.minecraft.network.protocol.game.ClientboundSetCarriedItemPacket(0));p.connection.send(new net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket(-2,0,0,stack.copy()));p.inventoryMenu.broadcastChanges();float pitch=(float)Math.toDegrees(Math.atan2(96+p.getEyeHeight()-y,z+2.5));p.connection.teleport(x,96,-2.5,0,pitch);}
 private static NativeBuilderEntity tile(ServerPlayer p){need(p.level().getBlockEntity(ROOT) instanceof NativeBuilderEntity,"physical builder owner");return (NativeBuilderEntity)p.level().getBlockEntity(ROOT);}
 private static void apply(ServerPlayer p,int operation){need(p.getUUID().equals(actor)&&stage==operation,"actor/ordered fixture");var t=tile(p);switch(operation){
  case 0->need(!t.formed()&&p.getMainHandItem().isEmpty(),"ordinary core placement consumes one");
  case 1->{need(t.formed()&&t.available(),"asynchronous physical frame formation completed");for(int x=0;x<3;x++)for(int y=0;y<5;y++)for(int z=0;z<3;z++){var at=t.cell(x,y,z);need(p.level().getBlockState(at).getBlock()!=NativeBuilderFormation.matter(),"server consumed frame cell "+at);if(!at.equals(ROOT))need(p.level().getBlockEntity(at) instanceof NativeBuilderPartEntity member&&member.root()==t,"server exact part owner "+at);}NativeBuilderMenuChecks.run(p,t);t.configureRedstone(1,ChannelList.EMPTY);var battery=new ItemStack(NativeRackBindings.BATTERY);NativeBatteryModule.transfer(battery,10000,false,true);p.getInventory().setItem(10,ItemStack.EMPTY);held(p,battery,.67,96.56,0);}
  case 2->{need(t.batteryEnergy()==10000&&p.getMainHandItem().isEmpty(),"held battery inserted at physical rack mount once");held(p,com.vandorlabs.neoforge.credentials.NativeCredentialItems.dataChip(3),.68,97.5,.35);}
  case 3->{need(com.vandorlabs.neoforge.credentials.NativeCredentialItems.company(t.getItem(3))==3&&p.getMainHandItem().isEmpty(),"held chip inserted at physical mount once");held(p,ItemStack.EMPTY,.1,96.95,0);}
  case 4->need(p.containerMenu instanceof NativeBuilderMenu&&t.selectedDesign()==1&&t.activation()==0&&t.channels().equals(ChannelList.of(71,72)),"real GUI select/previous activation/channels");
  case 5->{need(p.containerMenu==p.inventoryMenu,"ordinary right-click dialog closed");held(p,new ItemStack(com.vandorlabs.neoforge.VandorLabsBootstrap.CONFIGURIZER.get()),.1,96.95,0);}
  case 6->need(p.containerMenu instanceof NativeBuilderMenu menu&&menu.tile==t&&t.selectedDesign()==1&&t.channels().equals(ChannelList.of(71,72)),"Configurizer opens same physical owner and dedicated menu");
  case 7->{need(p.containerMenu==p.inventoryMenu&&t.processing()&&t.buildProgress()>0&&t.buildProgress()<1,"partial build remains owned after closing GUI");t.configureRedstone(1,ChannelList.EMPTY);double dx=7,dz=9;float yaw=(float)-Math.toDegrees(Math.atan2(dx,dz)),pitch=(float)-Math.toDegrees(Math.atan2(98.5-(96+p.getEyeHeight()),Math.sqrt(dx*dx+dz*dz)));p.connection.teleport(-5.5,96,-7.5,yaw,pitch);}
  case 8->{need(t.processing()&&!t.enabled(),"world overview retained paused partial build");t.configureRedstone(0,ChannelList.of(71,72));held(p,p.getMainHandItem(),.1,96.95,0);}
  case 9->{need(p.containerMenu==p.inventoryMenu&&t.getItem(0).isEmpty()&&t.getItem(1).isEmpty(),"one matter consumed and ordinary menu output collected");need(p.getInventory().items.stream().filter(v->v.is(NativeBuilderBindings.CORES.get("assembly_press").asItem())).mapToInt(ItemStack::getCount).sum()==1,"one built Core taken by output slot");p.getInventory().setItem(2,p.getMainHandItem().copy());held(p,ItemStack.EMPTY,.67,96.56,0);}
  case 10->{need(t.getItem(2).isEmpty()&&NativeBatteryModule.energy(p.getMainHandItem())==2000,"physical extraction retains exactly unspent2000RF");p.getInventory().setItem(1,p.getMainHandItem().copy());held(p,ItemStack.EMPTY,.68,97.5,.35);}
  case 11->{need(t.getItem(3).isEmpty()&&p.getInventory().items.stream().filter(v->com.vandorlabs.neoforge.credentials.NativeCredentialItems.accepts(v,true)&&com.vandorlabs.neoforge.credentials.NativeCredentialItems.company(v)==3).mapToInt(ItemStack::getCount).sum()==1,"physical chip extraction retains one owner");need(p.getInventory().items.stream().filter(v->NativeBatteryModule.energy(v)==2000).count()==1,"one extracted battery owner");need(!NativeBuilderMenu.apply(p,new NativeBuilderEdit(37,ROOT,0,2,ChannelList.EMPTY)),"stale closed menu refused");System.out.println("[vandorlabs][builder-client] ownership PASS physicalFrame/heldBatteryChip/ordinaryConfigurizer/selectPrevious/channels/production/output/physicalExtraction/stale");}
  default->throw new IllegalArgumentException("Builder fixture operation");}p.serverLevel().setBlock(ACK.east(operation),Blocks.STONE.defaultBlockState(),3);stage++;
 }
 @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent e){if(enabled()&&e.getEntity().getUUID().equals(actor)){need(stage==12,"complete player journey");System.out.println("[vandorlabs][builder-client] logout PASS");}}
}
