package com.vandorlabs.neoforge.gear;

import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;
import net.minecraft.network.*;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/** A real joined ordinary payload, captured by the normal asynchronous arm journal. */
@EventBusSubscriber(modid="vandorlabs")
public final class NativeGearCullingFixture {
 public static final BlockPos ROOT=new BlockPos(8,112,8), ACK=new BlockPos(4,108,4);
 private static ServerPlayer actor;private static int stage,ticks;
 public static boolean enabled(){return Boolean.getBoolean("vandorlabs.gearCullingCheck");}
 private static void need(boolean ok,String why){if(!ok)throw new AssertionError("Native culling server stage="+stage+" tick="+ticks+": "+why);}
 public record Mode(int value) implements CustomPacketPayload {public static final Type<Mode> TYPE=new Type<>(ResourceLocation.parse("vandorlabs:gear_culling_fixture"));public Type<? extends CustomPacketPayload> type(){return TYPE;}}
 @EventBusSubscriber(modid="vandorlabs",bus=EventBusSubscriber.Bus.MOD) public static final class Packets {@SubscribeEvent public static void register(net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent e){if(enabled())e.registrar("1").playToServer(Mode.TYPE,new StreamCodec<RegistryFriendlyByteBuf,Mode>(){public Mode decode(RegistryFriendlyByteBuf b){if(b.readableBytes()!=1)throw new IllegalArgumentException();return new Mode(b.readUnsignedByte());}public void encode(RegistryFriendlyByteBuf b,Mode p){b.writeByte(p.value);}},(p,c)->c.enqueueWork(()->{need(c.player()==actor,"exact actor");if(p.value==0){camera(110);actor.serverLevel().setBlock(ACK,Blocks.STONE.defaultBlockState(),3);}else if(p.value==1){need(stage==2,"captured scene remains live");System.out.println("[vandorlabs][gear-culling] completed PASS realJournal/nineOrdinaryCells/physicalLoadedOwner");}else throw new IllegalArgumentException();}));}}
 private static void camera(double y){actor.setGameMode(GameType.SURVIVAL);actor.getAbilities().mayfly=true;actor.getAbilities().flying=true;actor.onUpdateAbilities();actor.getInventory().selected=0;actor.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);actor.setItemInHand(InteractionHand.OFF_HAND,ItemStack.EMPTY);actor.connection.send(new net.minecraft.network.protocol.game.ClientboundSetCarriedItemPacket(0));actor.connection.send(new net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket(-2,0,0,ItemStack.EMPTY));actor.connection.teleport(9.5,y,9.5,0,90);}
 @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent e){if(!enabled()||!(e.getEntity() instanceof ServerPlayer p))return;actor=p;var world=p.serverLevel();for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++){world.setChunkForced(x,z,true);world.getChunk(x,z);}p.setGameMode(GameType.SURVIVAL);p.getInventory().clearContent();p.setPos(8.5,110,6);var choices=NativeGearSettings.defaults().choices();choices.putBoolean("JoinArms",true);choices.putInt("JoinRange",3);var data=new CompoundTag();data.put(NativeGearItem.SETTINGS,choices);for(int x:new int[]{0,2})for(int z:new int[]{0,2}){var root=ROOT.offset(x,0,z);world.setBlock(root.above(),Blocks.STONE.defaultBlockState(),3);var stack=new ItemStack(NativeGearBindings.ITEMS.get(NativeGearDefinition.EXTENDER.id),8);stack.set(DataComponents.CUSTOM_DATA,CustomData.of(data));p.setItemInHand(InteractionHand.MAIN_HAND,stack);for(int h=0;h<8;h++){var support=root.above(1-h);need(p.gameMode.useItemOn(p,world,stack,InteractionHand.MAIN_HAND,new BlockHitResult(Vec3.atCenterOf(support).add(0,-.5,0),Direction.DOWN,support,false)).consumesAction(),"real section placement "+h);}need(stack.isEmpty(),"eight consumed sections");}System.out.println("[vandorlabs][gear-culling] setup PASS fourColumns/eightSections/realSurvivalPlacement");}
 @SubscribeEvent public static void tick(net.neoforged.neoforge.event.tick.ServerTickEvent.Post e){if(!enabled()||actor==null||stage==2)return;need(++ticks<500,"bounded setup root="+NativeGearFormation.at(actor.level(),ROOT));var root=NativeGearFormation.at(actor.level(),ROOT);if(stage==0){if(root==null||!root.controller()||root.columns().size()!=4||root.members().size()!=32)return;for(int x=0;x<3;x++)for(int z=0;z<3;z++)actor.serverLevel().setBlock(ROOT.offset(x,-8,z),Blocks.DIAMOND_BLOCK.defaultBlockState(),3);need(NativeGearJoined.selected(root,java.util.function.Function.identity()).size()==9,"nine selected ordinary cells");actor.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);need(actor.gameMode.useItemOn(actor,actor.serverLevel(),ItemStack.EMPTY,InteractionHand.MAIN_HAND,new BlockHitResult(Vec3.atCenterOf(ROOT).add(0,0,-.5),Direction.NORTH,ROOT,false)).consumesAction(),"ordinary actual activation");stage=1;}
 else if(root.payload()!=null&&!root.payload().materialized()&&!NativeGearJournal.busy(root)&&root.progress()<.7&&root.progress()>.4){need(root.payload().size()==9,"whole platform captured");for(int x=0;x<3;x++)for(int z=0;z<3;z++)need(actor.level().getBlockState(ROOT.offset(x,-8,z)).isAir(),"single captured ownership");e.getServer().tickRateManager().setFrozen(true);camera(115);stage=2;System.out.println("[vandorlabs][gear-culling] capture PASS phase="+root.progress()+" boxes="+root.payload().boxes(root,root.progress()));}}
 @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent e){if(enabled()&&e.getEntity()==actor){e.getEntity().getServer().tickRateManager().setFrozen(false);System.out.println("[vandorlabs][gear-culling] logout PASS");}}
}
