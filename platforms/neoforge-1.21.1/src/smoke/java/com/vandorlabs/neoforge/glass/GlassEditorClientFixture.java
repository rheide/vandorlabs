package com.vandorlabs.neoforge.glass;

import com.vandorlabs.neoforge.VandorLabsBootstrap;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

/** Private Survival Configurizer and creative sneak-click through real menus and C2S settings. */
@EventBusSubscriber(modid="vandorlabs")
public final class GlassEditorClientFixture {
 public static final BlockPos ROOT=new BlockPos(0,80,0),PEER=ROOT.east(),ACK=ROOT.east(4);
 public static NativeGlassBlock BLOCK;public static BlockEntityType<NativeGlassEntity> TYPE;public static MenuType<NativeGlassMenu> MENU;private static int stage,ordinaryOpens;private static java.util.UUID player;
 public static boolean canonical(){return "true".equals(System.getenv("VANDOR_FLAT_GLASS_CANONICAL_CHECK"));}
 public static void bind(){if(canonical()){BLOCK=NativeGlassRegistrations.BLOCK.get();TYPE=NativeGlassRegistrations.ENTITY.get();MENU=NativeGlassRegistrations.MENU.get();}}
 public static boolean enabled(){return "flat-glass-editor-client".equals(System.getProperty("vandorlabs.transmitterPhase",""));}
 private static void need(boolean yes,String text){if(!yes)throw new IllegalStateException("Glass editor server stage="+stage+" "+text);}
 private static final class Housing extends NativeGlassBlock {Housing(){super(Block.Properties.of().strength(.5F,1.2F).sound(SoundType.GLASS),()->TYPE);}@Override protected MenuType<NativeGlassMenu> menuType(){return MENU;}
 @Override protected net.minecraft.world.InteractionResult useWithoutItem(net.minecraft.world.level.block.state.BlockState state,Level world,BlockPos pos,net.minecraft.world.entity.player.Player player,net.minecraft.world.phys.BlockHitResult hit){var result=super.useWithoutItem(state,world,pos,player,hit);if(!world.isClientSide&&result.consumesAction()){need(player.isCreative()&&player.isShiftKeyDown()&&player.getMainHandItem().isEmpty(),"actual ordinary callback creative/sneak/empty hand");ordinaryOpens++;}return result;}}
 @EventBusSubscriber(modid="vandorlabs",bus=EventBusSubscriber.Bus.MOD)public static final class Registration {
  @SubscribeEvent public static void register(net.neoforged.neoforge.registries.RegisterEvent e){if(!enabled()||canonical())return;var key=ResourceLocation.parse("vandorlabs:flat_glass_editor_fixture");e.register(Registries.BLOCK,h->{BLOCK=new Housing();h.register(key,BLOCK);});e.register(Registries.BLOCK_ENTITY_TYPE,h->{TYPE=BlockEntityType.Builder.of((p,s)->new NativeGlassEntity(TYPE,p,s),BLOCK).build(null);h.register(key,TYPE);});e.register(Registries.MENU,h->{MENU=net.neoforged.neoforge.common.extensions.IMenuTypeExtension.create((id,inventory,b)->new NativeGlassMenu(MENU,id,inventory,b.readBlockPos()));h.register(key,MENU);});}
  @SubscribeEvent public static void packets(net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent e){if(!enabled())return;if(!canonical())GlassSettingsPayload.register(e);e.registrar("1").playToServer(Mode.TYPE,new StreamCodec<RegistryFriendlyByteBuf,Mode>(){public Mode decode(RegistryFriendlyByteBuf b){if(b.readableBytes()!=1)throw new IllegalArgumentException("Glass fixture length");return new Mode(b.readUnsignedByte());}public void encode(RegistryFriendlyByteBuf b,Mode v){b.writeByte(v.operation);}},(v,c)->c.enqueueWork(()->{if(c.player() instanceof ServerPlayer actor)mode(actor,v.operation);}));}
 }
 public record Mode(int operation) implements CustomPacketPayload {public static final Type<Mode> TYPE=new Type<>(ResourceLocation.parse("vandorlabs:glass_editor_fixture_mode"));public Mode{if(operation<0||operation>5)throw new IllegalArgumentException("Glass fixture mode");}@Override public Type<? extends CustomPacketPayload> type(){return TYPE;}}
 private static NativeGlassEntity owner(ServerPlayer actor,BlockPos pos){return (NativeGlassEntity)actor.serverLevel().getBlockEntity(pos);}
 private static void held(ServerPlayer actor,boolean tool){var stack=tool?new ItemStack(VandorLabsBootstrap.CONFIGURIZER.get()):ItemStack.EMPTY;actor.getInventory().selected=0;actor.setItemInHand(InteractionHand.MAIN_HAND,stack);actor.setItemInHand(InteractionHand.OFF_HAND,ItemStack.EMPTY);actor.connection.send(new net.minecraft.network.protocol.game.ClientboundSetCarriedItemPacket(0));actor.connection.send(new net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket(-2,0,0,stack.copy()));actor.inventoryMenu.broadcastChanges();}
 @SubscribeEvent public static void started(net.neoforged.neoforge.event.server.ServerStartedEvent e){if(enabled())System.out.println("[vandorlabs][glass-editor] actual server PID="+ProcessHandle.current().pid());}
 @SubscribeEvent public static void login(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent e){if(!enabled()||!(e.getEntity() instanceof ServerPlayer actor))return;bind();player=actor.getUUID();stage=0;ordinaryOpens=0;var w=actor.serverLevel();System.out.println("[vandorlabs][glass-editor] actual server PID="+ProcessHandle.current().pid());for(int x=-4;x<=7;x++)for(int z=-4;z<=4;z++){w.setBlock(new BlockPos(x,79,z),Blocks.STONE.defaultBlockState(),18);for(int y=80;y<=84;y++)w.setBlock(new BlockPos(x,y,z),Blocks.AIR.defaultBlockState(),18);}w.setBlock(ROOT,BLOCK.defaultBlockState(),3);w.setBlock(PEER,BLOCK.defaultBlockState(),3);actor.setGameMode(GameType.SURVIVAL);actor.getInventory().clearContent();held(actor,true);actor.connection.teleport(.5,80,-3,0,0);System.out.println("[vandorlabs][glass-editor] setup PASS Survival/current-slot/held-Configurizer");}
 private static void unchanged(ServerPlayer actor){var t=owner(actor,ROOT);var g=t.settings().glass();need(g.material==64&&g.tint==2&&!g.join&&g.layout==0&&g.size==0&&t.settings().frames().tile,"complete authoritative draft");need(owner(actor,PEER).settings().glass().material==-1,"peer not edited");}
 private static void mode(ServerPlayer actor,int op){need(actor.getUUID().equals(player)&&op==stage,"current actor and exact operation");var w=actor.serverLevel();switch(op){
  case 0->{unchanged(actor);need(actor.containerMenu instanceof NativeGlassMenu,"dedicated menu installed");held(actor,false);w.setBlock(ACK,Blocks.STONE.defaultBlockState(),3);System.out.println("[vandorlabs][glass-editor] roundtrip PASS real widgets/menu/C2S/S2C");}
  case 1->{unchanged(actor);need(actor.getMainHandItem().isEmpty(),"tool removed before rejection");w.setBlock(ACK.east(),Blocks.STONE.defaultBlockState(),3);System.out.println("[vandorlabs][glass-editor] permission PASS removed tool rejected");}
  case 2->{unchanged(actor);actor.closeContainer();actor.setGameMode(GameType.CREATIVE);actor.setShiftKeyDown(true);actor.connection.teleport(.5,80,-3,0,0);w.setBlock(ACK.east(2),Blocks.STONE.defaultBlockState(),3);}
  case 3->{unchanged(actor);need(actor.containerMenu instanceof NativeGlassMenu&&actor.isCreative()&&(canonical()||ordinaryOpens==1),"ordinary creative sneak click same dedicated menu at callback; opens="+ordinaryOpens+" menu="+actor.containerMenu);actor.closeContainer();w.setBlock(ACK.east(3),Blocks.STONE.defaultBlockState(),3);System.out.println("[vandorlabs][glass-editor] ordinary PASS creative sneak dedicated dialog");}
  case 4->{unchanged(actor);w.setBlock(ACK.east(4),Blocks.STONE.defaultBlockState(),3);System.out.println("[vandorlabs][glass-editor] closed-window PASS stale edit rejected");}
  case 5->{unchanged(actor);System.out.println("[vandorlabs][glass-editor] complete PASS");}
 }stage++;}
 @SubscribeEvent public static void logout(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent e){if(enabled()&&e.getEntity() instanceof ServerPlayer p){System.out.println("[vandorlabs][glass-editor] logout "+(stage==6?"PASS":"INCOMPLETE"));p.getServer().halt(false);}}
 private GlassEditorClientFixture(){}
}
