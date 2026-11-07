package com.vandorlabs.neoforge.copifier;
import com.vandorlabs.neoforge.*;
import com.vandorlabs.neoforge.storage.*;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/** Canonical independent inventories plus a private tool/menu; actual client interactions drive edits. */
@EventBusSubscriber(modid="vandorlabs")
public final class CopifierStorageClientFixture {
 static final BlockPos SOURCE=new BlockPos(0,96,0),TARGET=SOURCE.east(2),PEER=TARGET.east(),ACK=SOURCE.east(5);private static NativeStorageEntity first,second;private static java.util.UUID actor;private static int stage;
 static boolean enabled(){return "copifier-storage-client".equals(System.getProperty("vandorlabs.transmitterPhase"));}
 private static void need(boolean yes,String why){if(!yes)throw new IllegalStateException("Copifier Storage server stage="+stage+": "+why);}
 @EventBusSubscriber(modid="vandorlabs",bus=EventBusSubscriber.Bus.MOD)public static final class Registration {
  @SubscribeEvent public static void packets(net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent event){if(!enabled())return;NativeCopifierPayload.register(event);NativeCopifierUndoPayload.register(event);event.registrar("1").playToServer(Mode.TYPE,new StreamCodec<RegistryFriendlyByteBuf,Mode>(){public Mode decode(RegistryFriendlyByteBuf b){if(b.readableBytes()!=1)throw new IllegalArgumentException();int n=b.readUnsignedByte();if(n>2)throw new IllegalArgumentException();return new Mode(n);}public void encode(RegistryFriendlyByteBuf b,Mode p){b.writeByte(p.operation);}},(p,c)->c.enqueueWork(()->{if(c.player() instanceof ServerPlayer player)apply(player,p.operation);}));}
 }
 public record Mode(int operation) implements CustomPacketPayload {static final Type<Mode> TYPE=new Type<>(ResourceLocation.parse("vandorlabs:copifier_storage_fixture_mode"));public Type<? extends CustomPacketPayload> type(){return TYPE;}}
 @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent event){if(!enabled()||!(event.getEntity() instanceof ServerPlayer player))return;actor=player.getUUID();stage=0;var level=player.serverLevel();for(int x=-4;x<=10;x++)for(int z=-4;z<=4;z++){level.setBlock(new BlockPos(x,95,z),Blocks.STONE.defaultBlockState(),3);for(int y=96;y<=101;y++)level.setBlock(new BlockPos(x,y,z),Blocks.AIR.defaultBlockState(),3);}for(var pos:new BlockPos[]{SOURCE,TARGET,PEER})level.setBlock(pos,NativeStorageRegistrations.BLOCK.get().defaultBlockState(),3);
 var source=(NativeStorageEntity)level.getBlockEntity(SOURCE);var raw=source.saveWithoutMetadata(level.registryAccess());raw.putInt("housingTexture",7);raw.putInt("HousingTextureVersion",1);raw.putInt("MainTextureLayout",2);raw.putBoolean("SlabTileSides",false);raw.putBoolean("FaceTexturesEnabled",true);raw.putIntArray("FaceTextures",new int[]{1,2,3,4,5,6});source.loadWithComponents(raw,level.registryAccess());source.setItem(0,new ItemStack(net.minecraft.world.item.Items.IRON_INGOT,12));first=(NativeStorageEntity)level.getBlockEntity(TARGET);second=(NativeStorageEntity)level.getBlockEntity(PEER);first.setItem(26,new ItemStack(net.minecraft.world.item.Items.DIAMOND,7));second.setItem(0,new ItemStack(net.minecraft.world.item.Items.IRON_INGOT,5));player.setGameMode(GameType.SURVIVAL);player.getInventory().clearContent();player.getInventory().selected=0;var tool=new ItemStack(NativeCopifierItemChecks.ITEM);player.setItemInHand(InteractionHand.MAIN_HAND,tool);player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetCarriedItemPacket(0));player.connection.send(new net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket(-2,0,0,tool.copy()));player.inventoryMenu.broadcastChanges();player.connection.teleport(.5,96,-3,0,0);System.out.println("[vandorlabs][copifier-storage-client] setup PASS Survival/tool/source/twoTargets");}
 private static void apply(ServerPlayer player,int operation){need(player.getUUID().equals(actor)&&operation==stage,"actor/order");need(player.serverLevel().getBlockEntity(TARGET)==first&&player.serverLevel().getBlockEntity(PEER)==second,"same physical owners");var source=(NativeStorageEntity)player.serverLevel().getBlockEntity(SOURCE);need(source.getItem(0).getCount()==12&&second.getItem(0).getCount()==5,"source and peer retain independent inventories");var clipboard=NativeCopifierClipboard.settings(NativeCopifierItem.data(player.getMainHandItem()));need(clipboard.getInt("wall_texture")==7&&clipboard.getCompound("face_textures").getBoolean("enabled")&&!clipboard.contains("Items")&&!clipboard.contains("NativeStorageSchema"),"bounded inventory-free source clipboard");
  if(operation==0){for(var owner:new NativeStorageEntity[]{first,second})need(owner.settings().housing().mainTexture==7&&owner.settings().housing().layout()==2&&owner.settings().housing().facesEnabled&&owner.settings().housing().faces()[5]==6,"real connected housing/per-face appearance");need(first.getItem(26).getCount()==7,"target inventory retained");first.setItem(26,new ItemStack(net.minecraft.world.item.Items.DIAMOND,11));System.out.println("[vandorlabs][copifier-storage-client] applied PASS realSourceCapture/connectedUse/independentInventories");}
  else if(operation==1){for(var owner:new NativeStorageEntity[]{first,second})need(owner.settings().housing().mainTexture==NativeStorageSettings.fresh().housing().mainTexture&&!owner.settings().housing().facesEnabled,"real undo restores appearance");need(first.getItem(26).getCount()==11,"undo preserves later inventory change");System.out.println("[vandorlabs][copifier-storage-client] undo PASS realEditorButton/laterInventoryEdit/clipboard");}
  else{need(player.containerMenu==player.inventoryMenu,"menu closed");System.out.println("[vandorlabs][copifier-storage-client] completed PASS");}player.serverLevel().setBlock(ACK.east(operation),Blocks.STONE.defaultBlockState(),3);stage++;
 }
 @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event){if(enabled()&&event.getEntity().getUUID().equals(actor)){need(stage==3,"all stages complete");System.out.println("[vandorlabs][copifier-storage-client] logout PASS");}}
}
