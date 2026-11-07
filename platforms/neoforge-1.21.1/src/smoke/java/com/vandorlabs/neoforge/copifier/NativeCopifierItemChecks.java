package com.vandorlabs.neoforge.copifier;

import com.google.gson.*;
import com.vandorlabs.neoforge.ProgrammableBlockEntity;
import com.vandorlabs.items.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.GZIPInputStream;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.*;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.*;
import net.minecraft.world.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.*;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

/** Actual server GameMode held-item dispatch, using a private item and canonical housing targets. */
@EventBusSubscriber(modid="vandorlabs")
public final class NativeCopifierItemChecks {
 static NativeCopifierItem ITEM;static MenuType<NativeCopifierMenu> MENU;private static int checks;
 private static boolean enabled(){return System.getProperty("vandorlabs.transmitterPhase","").equals("copifier-item");}
 private static void need(boolean value,String why){checks++;if(!value)throw new AssertionError("Copifier item: "+why);}
 @EventBusSubscriber(modid="vandorlabs",bus=EventBusSubscriber.Bus.MOD)public static final class Registration {
  @SubscribeEvent public static void register(net.neoforged.neoforge.registries.RegisterEvent event){if(!enabled()&&!CopifierEditorClientFixture.enabled()&&!NativeCopifierUndoChecks.enabled()&&!NativeCopifierVanillaChecks.enabled()&&!CopifierGlassClientFixture.enabled()&&!CopifierControlClientFixture.enabled()&&!CopifierStorageClientFixture.enabled()&&!CopifierLightClientFixture.enabled()&&!CopifierDoorClientFixture.enabled()&&!CopifierTriggerClientFixture.enabled()&&!CopifierPropulsionClientFixture.enabled()&&!CopifierSegmentedClientFixture.enabled()&&!CopifierReaderClientFixture.enabled()&&!CopifierTrapdoorClientFixture.enabled())return;var id=ResourceLocation.parse("vandorlabs:copifier_item_fixture");event.register(Registries.ITEM,h->{ITEM=new NativeCopifierItem(new Item.Properties(),()->MENU);h.register(id,ITEM);});event.register(Registries.MENU,h->{MENU=new MenuType<>((window,inventory)->new NativeCopifierMenu(MENU,window,inventory),net.minecraft.world.flag.FeatureFlags.VANILLA_SET);h.register(id,MENU);});}
 }
 private static final class Actor extends net.neoforged.neoforge.common.util.FakePlayer {
  Actor(ServerLevel level){super(level,new com.mojang.authlib.GameProfile(new UUID(0,1873),"CopifierItem"));}
  @Override public OptionalInt openMenu(MenuProvider provider){containerMenu=provider.createMenu(73,getInventory(),this);return OptionalInt.of(73);}
 }
 private static void wire(ServerLevel level,long[] words){
  var undo=new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(),level.registryAccess());try{NativeCopifierUndoPayload.CODEC.encode(undo,new NativeCopifierUndoPayload(73));need(undo.readableBytes()==4&&NativeCopifierUndoPayload.CODEC.decode(undo).window()==73&&!undo.isReadable(),"fixed4byte undo roundtrip");for(int size:new int[]{0,3,5}){undo.clear();undo.writeZero(size);boolean refused=false;try{NativeCopifierUndoPayload.CODEC.decode(undo);}catch(IllegalArgumentException expected){refused=true;}need(refused,"undo length rejected "+size);}}finally{undo.release();}
  for(int bit=-1;bit<DuplifierOptionCatalog.size();bit++)for(int flags=0;flags<4;flags++){
   var values=words.clone();if(bit>=0)values[bit/64]^=1L<<(bit%64);var packet=new NativeCopifierPayload(73,values,(flags&1)!=0,(flags&2)!=0);values[0]^=1;need(!java.util.Arrays.equals(values,packet.words()),"packet defensively copies draft");
   var buffer=new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(),level.registryAccess());try{NativeCopifierPayload.CODEC.encode(buffer,packet);need(buffer.readableBytes()==22,"fixed bounded wire bytes");byte[] valid=new byte[buffer.readableBytes()];buffer.getBytes(0,valid);var decoded=NativeCopifierPayload.CODEC.decode(buffer);need(decoded.window()==73&&java.util.Arrays.equals(decoded.words(),packet.words())&&decoded.connected()==packet.connected()&&decoded.ignoreDirectionality()==packet.ignoreDirectionality()&&!buffer.isReadable(),"wire roundtrip");
    if(bit==-1)for(int bad=0;bad<4;bad++){buffer.clear();buffer.writeBytes(valid);if(bad==0)buffer.writeByte(0);if(bad==1)buffer.writerIndex(valid.length-1);if(bad==2)buffer.setByte(valid.length-1,2);if(bad==3)buffer.setLong(12,Long.MIN_VALUE);boolean refused=false;try{NativeCopifierPayload.CODEC.decode(buffer);}catch(IllegalArgumentException expected){refused=true;}need(refused,"malformed wire rejected "+bad);}
   }finally{buffer.release();}
  }
 }
 private static void run(ServerLevel level)throws Exception{
  var actor=new Actor(level);var pos=new BlockPos(14720,100,14640);level.getChunkAt(pos);actor.setPos(pos.getX()+.5,pos.getY()+1,pos.getZ()-2);var hit=new BlockHitResult(Vec3.atCenterOf(pos).add(0,0,-.5),Direction.NORTH,pos,false);
  JsonArray rows;try(var in=new GZIPInputStream(Files.newInputStream(Path.of(System.getProperty("vandorlabs.copifierHousingReference"))))){rows=JsonParser.parseString(new String(in.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8)).getAsJsonArray();}
  int cases=0;
  for(var mode:new GameType[]{GameType.SURVIVAL,GameType.CREATIVE})for(var element:rows){var row=element.getAsJsonObject();if(row.get("meta").getAsInt()!=0||row.get("profile").getAsInt()!=7)continue;actor.setGameMode(mode);actor.containerMenu=actor.inventoryMenu;var block=BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath("vandorlabs",row.get("block").getAsString()));level.removeBlock(pos,false);level.setBlock(pos,block.defaultBlockState(),18);var owner=(ProgrammableBlockEntity)level.getBlockEntity(pos);owner.loadWithComponents(TagParser.parseTag(row.get("input").getAsString()),level.registryAccess());
   var tool=new ItemStack(ITEM);var initial=new CompoundTag();initial.putString("ForeignToolMarker","preserve");tool.set(DataComponents.CUSTOM_DATA,CustomData.of(initial));actor.setItemInHand(InteractionHand.MAIN_HAND,tool);actor.setShiftKeyDown(true);
   var captured=actor.gameMode.useItemOn(actor,level,tool,InteractionHand.MAIN_HAND,hit);need(captured.consumesAction(),"GameMode captures "+mode+" "+row.get("block"));need(actor.containerMenu==actor.inventoryMenu,"creative sneaking tool owns interaction");var clipboard=NativeCopifierItem.data(tool);need(NativeCopifierClipboard.settings(clipboard).equals(TagParser.parseTag(row.get("capture").getAsString())),"Source capture");need(clipboard.getString("ForeignToolMarker").equals("preserve")&&tool.getCount()==1,"capture preserves item");
   level.removeBlock(pos,false);level.setBlock(pos,block.defaultBlockState().rotate(net.minecraft.world.level.block.Rotation.CLOCKWISE_90),18);owner=(ProgrammableBlockEntity)level.getBlockEntity(pos);var state=level.getBlockState(pos);actor.setShiftKeyDown(false);need(actor.gameMode.useItemOn(actor,level,tool,InteractionHand.MAIN_HAND,hit).consumesAction(),"GameMode applies");need(level.getBlockEntity(pos)==owner&&level.getBlockState(pos)==state,"same owner/orientation");need(TagParser.parseTag(row.get("recaptured").getAsString()).equals(NativeCopifierHousingWorld.capture(level,pos,actor,Direction.NORTH)),"Source applied result");need(clipboard.equals(NativeCopifierItem.data(tool)),"clipboard unchanged");cases++;
  }
  var unchanged=NativeCopifierItem.data(actor.getMainHandItem());var target=(ProgrammableBlockEntity)level.getBlockEntity(pos);var targetSaved=target.saveWithoutMetadata(level.registryAccess());actor.setGameMode(GameType.SURVIVAL);actor.setShiftKeyDown(false);
  actor.getMainHandItem().set(DataComponents.CUSTOM_DATA,CustomData.of(NativeCopifierOptions.withConnected(unchanged,true)));need(actor.gameMode.useItemOn(actor,level,actor.getMainHandItem(),InteractionHand.MAIN_HAND,hit).consumesAction()&&targetSaved.equals(target.saveWithoutMetadata(level.registryAccess())),"actual connected item dispatch preserves already matching installed settings");actor.getMainHandItem().set(DataComponents.CUSTOM_DATA,CustomData.of(unchanged));
  actor.setGameMode(GameType.SPECTATOR);need(!ITEM.useOn(new net.minecraft.world.item.context.UseOnContext(actor,InteractionHand.MAIN_HAND,hit)).consumesAction()&&targetSaved.equals(target.saveWithoutMetadata(level.registryAccess())),"spectator item refusal");actor.setGameMode(GameType.SURVIVAL);actor.setPos(pos.getX()+30,pos.getY(),pos.getZ());need(!ITEM.useOn(new net.minecraft.world.item.context.UseOnContext(actor,InteractionHand.MAIN_HAND,hit)).consumesAction()&&targetSaved.equals(target.saveWithoutMetadata(level.registryAccess())),"out of reach item refusal");actor.setPos(pos.getX()+.5,pos.getY()+1,pos.getZ()-2);
  actor.setItemInHand(InteractionHand.OFF_HAND,new ItemStack(ITEM));need(ITEM.useOn(new net.minecraft.world.item.context.UseOnContext(actor,InteractionHand.OFF_HAND,hit))==InteractionResult.PASS&&targetSaved.equals(target.saveWithoutMetadata(level.registryAccess())),"offhand passes without mutation");
  var tool=actor.getMainHandItem();actor.setShiftKeyDown(false);need(actor.gameMode.useItem(actor,level,tool,InteractionHand.MAIN_HAND).consumesAction()&&actor.containerMenu instanceof NativeCopifierMenu,"actual air use opens options provider");var menu=(NativeCopifierMenu)actor.containerMenu;var words=NativeCopifierOptions.maskWords(NativeCopifierItem.data(tool));need(NativeCopifierPayload.apply(actor,new NativeCopifierPayload(73,words,true,true)),"authenticated settings");need(!NativeCopifierPayload.apply(actor,new NativeCopifierPayload(74,words,false,false)),"wrong window rejected");need(!NativeCopifierUndoPayload.apply(actor,new NativeCopifierUndoPayload(74)),"wrong undo window rejected");wire(level,words);need(NativeCopifierOptions.connected(NativeCopifierItem.data(tool))&&NativeCopifierOptions.ignoreDirectionality(NativeCopifierItem.data(tool)),"options stored");var before=NativeCopifierItem.data(tool);menu.clicked(0,0,ClickType.PICKUP,actor);need(menu.quickMoveStack(actor,0).isEmpty()&&before.equals(NativeCopifierItem.data(tool))&&menu.getCarried().isEmpty(),"preview cannot transfer");
  var invalid=words.clone();invalid[invalid.length-1]=Long.MIN_VALUE;need(!menu.configure(actor,invalid,false,false)&&before.equals(NativeCopifierItem.data(tool)),"invalid mask rejected");actor.getInventory().selected=1;need(!menu.configure(actor,words,false,false),"selected slot invalidates");actor.getInventory().selected=0;actor.setItemInHand(InteractionHand.MAIN_HAND,tool.copy());need(!menu.configure(actor,words,false,false),"replacement object invalidates");actor.setItemInHand(InteractionHand.MAIN_HAND,tool);actor.containerMenu=actor.inventoryMenu;need(!menu.configure(actor,words,false,false),"stale window rejected");
  actor.setShiftKeyDown(true);need(actor.gameMode.useItem(actor,level,tool,InteractionHand.MAIN_HAND).consumesAction(),"actual air clear");var cleared=NativeCopifierItem.data(tool);need(!NativeCopifierClipboard.hasCopy(cleared)&&NativeCopifierOptions.connected(cleared)&&NativeCopifierOptions.ignoreDirectionality(cleared)&&cleared.getString("ForeignToolMarker").equals("preserve"),"clear only clipboard");
  System.out.println("[vandorlabs][copifier-item] PASS cases="+cases+" checks="+checks+" actual GameMode item dispatch; menu provider adapter; private registration only");
 }
 @SubscribeEvent public static void started(ServerStartedEvent event){if(!enabled())return;try{run(event.getServer().overworld());}catch(Throwable failure){failure.printStackTrace();System.out.println("[vandorlabs][copifier-item] FAIL "+failure);}finally{event.getServer().halt(false);}}
}
