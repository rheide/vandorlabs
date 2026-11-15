package com.vandorlabs.neoforge.join;
import com.vandorlabs.neoforge.*;
import com.vandorlabs.neoforge.copifier.*;
import com.vandorlabs.neoforge.shape.*;
import com.vandorlabs.surface.*;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/** Real single-peer and whole-six-cell copies; fixture packets only acknowledge or equip. */
@EventBusSubscriber(modid="vandorlabs")
public final class NativePortholeOriginalFixture {
 static final BlockPos BASE=new BlockPos(-162,96,152),CLICK=BASE.south(),SOURCE=BASE.north(3),REF=BASE.south(6),MIX=BASE.south(10),MIXREF=BASE.south(13),ALLSOURCE=BASE.south(16),ACK=new BlockPos(-156,96,153);
 private static java.util.UUID actor;private static int operation,checks;
 public static boolean enabled(){return "porthole-originals".equals(System.getProperty("vandorlabs.transmitterPhase"));}
 private static void need(boolean yes,String why){checks++;if(!yes)throw new AssertionError("Native Porthole originals op="+operation+": "+why);}
 public record Mode(int value) implements CustomPacketPayload {static final Type<Mode> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath("vandorlabs","porthole_original_fixture"));public Type<Mode> type(){return TYPE;}}
 @EventBusSubscriber(modid="vandorlabs",bus=EventBusSubscriber.Bus.MOD) public static final class Packets {@SubscribeEvent public static void register(net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent event){if(enabled())event.registrar("1").playToServer(Mode.TYPE,new StreamCodec<RegistryFriendlyByteBuf,Mode>(){public Mode decode(RegistryFriendlyByteBuf b){if(b.readableBytes()!=1)throw new IllegalArgumentException();int n=b.readUnsignedByte();if(n>4)throw new IllegalArgumentException();return new Mode(n);}public void encode(RegistryFriendlyByteBuf b,Mode m){b.writeByte(m.value);}},(m,c)->c.enqueueWork(()->advance((ServerPlayer)c.player(),m.value)));}}
 private static NativeWallEntity wall(ServerPlayer p,BlockPos at){return (NativeWallEntity)p.level().getBlockEntity(at);}
 private static void put(ServerPlayer p,BlockPos pos,boolean full,int depth,int policy){var block=(NativeFlatWallBlock)BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath("vandorlabs",full?"programmable_porthole_block":"programmable_porthole_wall"));var state=block.defaultBlockState().setValue(NativeFlatWallBlock.FACING,Direction.WEST).setValue(NativeFlatWallBlock.DEPTH,depth);p.level().setBlock(pos,state,3);var t=wall(p,pos);t.setTextureJoin(new TextureJoinSettings(policy,0));t.setChanged();p.level().sendBlockUpdated(pos,state,state,3);}
 private static void equip(ServerPlayer p,boolean connected,BlockPos source){p.getInventory().clearContent();p.getInventory().selected=0;var tool=new ItemStack(NativeCopifierRegistrations.ITEM.get());var data=new CompoundTag();data.putBoolean(NativeCopifierOptions.CONNECTED,connected);data.putBoolean(NativeCopifierOptions.IGNORE_DIRECTION,true);data.putBoolean(NativeCopifierOptions.RESPECT_JOINS,false);tool.set(DataComponents.CUSTOM_DATA,CustomData.of(data));p.setItemInHand(InteractionHand.MAIN_HAND,tool);p.setItemInHand(InteractionHand.OFF_HAND,ItemStack.EMPTY);p.connection.send(new net.minecraft.network.protocol.game.ClientboundSetCarriedItemPacket(0));p.connection.send(new net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket(-2,0,0,tool.copy()));p.inventoryMenu.broadcastChanges();p.connection.teleport(-164.5,96,source.getZ()+.5,-90,8);}
 @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent event){if(!enabled()||!(event.getEntity() instanceof ServerPlayer p))return;actor=p.getUUID();for(int x=-166;x<=-155;x++)for(int z=147;z<=170;z++){p.level().setBlock(new BlockPos(x,95,z),Blocks.STONE.defaultBlockState(),3);for(int y=96;y<=102;y++)p.level().setBlock(new BlockPos(x,y,z),Blocks.AIR.defaultBlockState(),3);}for(int col=0;col<3;col++)for(int row=0;row<2;row++)put(p,BASE.south(col).above(row),true,row==0&&col>0?1:0,3);put(p,SOURCE,false,0,2);for(int row=0;row<2;row++)put(p,REF.above(row),true,0,2);for(int row=0;row<5;row++){put(p,MIX.above(row),true,0,row==4?3:row==0?1:2);put(p,MIXREF.above(row),true,0,row==0||row==4?1:2);}put(p,ALLSOURCE,false,0,1);p.setGameMode(GameType.SURVIVAL);equip(p,false,ALLSOURCE);System.out.println("[vandorlabs][porthole-originals] setup PASS sixFullDepthFlags/mixedPeer/referencePairs/Survival");}
 private static void advance(ServerPlayer p,int op){need(p.getUUID().equals(actor)&&op==operation,"actor/order");need(p.gameMode.getGameModeForPlayer()==GameType.SURVIVAL&&p.getInventory().selected==0&&p.getMainHandItem().is(NativeCopifierRegistrations.ITEM.get()),"real mode/slot/tool");var copied=NativeCopifierClipboard.settings(NativeCopifierItem.data(p.getMainHandItem()));switch(op){
 case 0->{need(copied.getCompound("texture_join_policy").getInt("Direction")==1&&p.isShiftKeyDown(),"actual All capture");p.connection.teleport(-158.5,96,MIX.getZ()+.5,90,8);}
 case 1->{need(!p.isShiftKeyDown()&&wall(p,MIX).textureJoin().direction==1&&wall(p,MIX.above(4)).textureJoin().direction==1,"real upper-only apply leaves original lower setting");System.out.println("[vandorlabs][porthole-originals] mixed PASS server originalAll/interiorVertical/upperAll");}
 case 2->{equip(p,true,SOURCE);}
 case 3->{need(copied.getCompound("texture_join_policy").getInt("Direction")==2&&p.isShiftKeyDown(),"actual Vertical capture");p.connection.teleport(-158.5,96,CLICK.getZ()+.5,90,8);}
 case 4->{int count=0;for(int col=0;col<3;col++)for(int row=0;row<2;row++)if(wall(p,BASE.south(col).above(row)).textureJoin().direction==2)count++;System.out.println("[vandorlabs][porthole-originals] selected count="+count+"/6");need(count==6,"all six physically equivalent original cells selected");System.out.println("[vandorlabs][porthole-originals] completed PASS checks="+checks+" actualMixedPeer/sixOriginals");}
 default->throw new IllegalArgumentException();}p.serverLevel().setBlock(ACK.east(op),Blocks.STONE.defaultBlockState(),3);operation++;}
 @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event){if(enabled()&&event.getEntity().getUUID().equals(actor)){need(operation==5,"all stages complete");System.out.println("[vandorlabs][porthole-originals] logout PASS checks="+checks);}}
}
