package com.vandorlabs.neoforge.gear;

import com.vandorlabs.neoforge.VandorLabsBootstrap;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/** One actual construction/editor journey; no alternate assembly or ownership implementation. */
@EventBusSubscriber(modid="vandorlabs")
public final class NativeGearMenuClientFixture {
    public static final BlockPos PRIMARY=new BlockPos(-3,99,0),SELECTED=new BlockPos(0,99,0),ACK=new BlockPos(3,96,0);
    private static java.util.UUID actorId,selectedId;private static int stage,window;
    public static boolean enabled(){return Boolean.getBoolean("vandorlabs.gearMenuClientCheck");}
    private static void need(boolean ok,String why){if(!ok)throw new AssertionError("Native gear menu server stage="+stage+": "+why);}
    public record Mode(int operation) implements CustomPacketPayload {public static final Type<Mode> TYPE=new Type<>(ResourceLocation.parse("vandorlabs:gear_menu_client_fixture"));public Type<? extends CustomPacketPayload> type(){return TYPE;}}
    @EventBusSubscriber(modid="vandorlabs",bus=EventBusSubscriber.Bus.MOD) public static final class Packets {@SubscribeEvent public static void register(net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent event){if(enabled())event.registrar("1").playToServer(Mode.TYPE,new StreamCodec<RegistryFriendlyByteBuf,Mode>(){public Mode decode(RegistryFriendlyByteBuf b){if(b.readableBytes()!=1)throw new IllegalArgumentException();return new Mode(b.readUnsignedByte());}public void encode(RegistryFriendlyByteBuf b,Mode p){b.writeByte(p.operation);}},(p,context)->context.enqueueWork(()->{if(context.player() instanceof ServerPlayer actor)apply(actor,p.operation);}));}}
    private static void held(ServerPlayer actor,ItemStack stack){actor.getInventory().selected=0;actor.setItemInHand(InteractionHand.MAIN_HAND,stack);actor.setItemInHand(InteractionHand.OFF_HAND,ItemStack.EMPTY);actor.connection.send(new net.minecraft.network.protocol.game.ClientboundSetCarriedItemPacket(0));actor.connection.send(new net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket(-2,0,0,stack.copy()));actor.inventoryMenu.broadcastChanges();}
    private static ItemStack sections(boolean join){var choices=NativeGearSettings.defaults().choices();choices.putBoolean("JoinArms",join);var tag=new CompoundTag();tag.put(NativeGearItem.SETTINGS,choices);var result=new ItemStack(NativeGearBindings.ITEMS.get(NativeGearDefinition.EXTENDER.id),2);result.set(DataComponents.CUSTOM_DATA,CustomData.of(tag));return result;}
    @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent event){if(!enabled()||!(event.getEntity() instanceof ServerPlayer actor))return;actorId=actor.getUUID();var world=actor.serverLevel();for(int x=-6;x<=6;x++)for(int z=-5;z<=4;z++){world.setBlock(new BlockPos(x,95,z),Blocks.STONE.defaultBlockState(),3);for(int y=96;y<=103;y++)world.setBlock(new BlockPos(x,y,z),Blocks.AIR.defaultBlockState(),3);}world.setBlock(PRIMARY.above(),Blocks.STONE.defaultBlockState(),3);world.setBlock(SELECTED.above(),Blocks.STONE.defaultBlockState(),3);actor.setGameMode(GameType.SURVIVAL);actor.getInventory().clearContent();held(actor,sections(true));actor.connection.teleport(.5,96,-3,34,-12);System.out.println("[vandorlabs][gear-menu-client] setup PASS actualSurvival/slot/held/physicalSupports");}
    private static NativeGearEntity root(ServerPlayer actor,BlockPos pos){var member=NativeGearFormation.at(actor.level(),pos);need(member!=null&&!member.inert(),"installed healthy member "+pos);return member;}
    private static void apply(ServerPlayer actor,int operation){need(actor.getUUID().equals(actorId)&&operation==stage,"actor/order");var first=root(actor,PRIMARY);switch(operation){
        case 0->{need(first.controller()&&first.assembly().columnHeight()==2&&actor.getMainHandItem().isEmpty(),"real primary two-section placement consumed exactly two");held(actor,sections(false));}
        case 1->{var second=root(actor,SELECTED);need(second.controller()&&second.assembly().columnHeight()==2&&actor.getMainHandItem().isEmpty(),"real secondary placement remains independent");held(actor,new ItemStack(Blocks.DIAMOND_BLOCK,4));}
        case 2->{for(int x=-3;x<=0;x++)need(actor.level().getBlockState(new BlockPos(x,97,0)).is(Blocks.DIAMOND_BLOCK),"real connected platform placement");need(actor.getMainHandItem().isEmpty(),"four platform items consumed");held(actor,new ItemStack(VandorLabsBootstrap.CONFIGURIZER.get()));}
        case 3->{need(actor.containerMenu instanceof NativeGearMenu,"dedicated shared route");var menu=(NativeGearMenu)actor.containerMenu;need(menu.selectedPosition().equals(SELECTED)&&menu.tile.getBlockPos().equals(PRIMARY)&&menu.stillValid(actor),"same selected member rebound to different primary");need(menu.tile.settings().join()&&menu.tile.settings().joinRange()==8&&menu.tile.settings().carryLoad()&&menu.tile.settings().loadHeight()==2,"real subsequent C2S edits reach new controller");window=menu.containerId;selectedId=menu.selectedIdentity();need(selectedId.equals(root(actor,SELECTED).identity()),"immutable member UUID retained");System.out.println("[vandorlabs][gear-menu-client] rebound PASS sameWindow/selectedUUID/newPrimary/range8/loadHeight2");}
        case 4->{need(actor.containerMenu instanceof NativeGearMenu&&actor.containerMenu.containerId==window,"editor remained open");var menu=(NativeGearMenu)actor.containerMenu;var old=root(actor,SELECTED);var replacement=NativeGearBindings.ENTITY.create(SELECTED,old.getBlockState());need(replacement!=null,"native replacement factory");actor.level().removeBlockEntity(SELECTED);actor.level().setBlockEntity(replacement);need(!selectedId.equals(replacement.identity()),"replacement has distinct identity");need(!menu.apply(actor,window,SELECTED,menu.tile.settings(),true),"same-position replacement rejects old session before automatic close");actor.closeContainer();System.out.println("[vandorlabs][gear-menu-client] replaced PASS exactMemberIdentity/closedWindow");}
        case 5->{need(actor.containerMenu==actor.inventoryMenu,"editor closed");need(first.settings().joinRange()==8&&first.settings().loadHeight()==2,"real stale C2S did not mutate retained controller");System.out.println("[vandorlabs][gear-menu-client] completed PASS");}
        default->throw new IllegalArgumentException("Gear menu fixture operation");}
        actor.serverLevel().setBlock(ACK.east(operation),Blocks.STONE.defaultBlockState(),3);stage++;
    }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event){if(enabled()&&event.getEntity().getUUID().equals(actorId)){need(stage==6,"complete editor journey");System.out.println("[vandorlabs][gear-menu-client] logout PASS");}}
}
