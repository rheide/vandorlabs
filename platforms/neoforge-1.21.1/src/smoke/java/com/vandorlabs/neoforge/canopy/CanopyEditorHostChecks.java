package com.vandorlabs.neoforge.canopy;

import com.mojang.authlib.GameProfile;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.*;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

/** Installed ServerLevel session/authorization/blocked transaction checks; no real-client claim. */
public final class CanopyEditorHostChecks {
 private static MenuType<NativeCanopyMenu> type;private static int checks;
 private static final BlockPos POS=new BlockPos(104,200,104);
 @EventBusSubscriber(modid="vandorlabs",bus=EventBusSubscriber.Bus.MOD)public static final class Registration {
  @SubscribeEvent public static void register(net.neoforged.neoforge.registries.RegisterEvent event){if(System.getProperty("vandorlabs.transmitterPhase","").startsWith("canopy-host-editor"))event.register(Registries.MENU,h->{type=new MenuType<>((id,inventory)->{throw new UnsupportedOperationException("Server session fixture");},net.minecraft.world.flag.FeatureFlags.VANILLA_SET);h.register(ResourceLocation.parse("vandorlabs:canopy_editor_host_fixture"),type);});}
 }
 private static void need(boolean value,String text){checks++;if(!value)throw new AssertionError("Canopy editor host "+text);}
 private static NativeCanopySettings change(NativeCanopyEntity tile,String field,int value){var tag=new CompoundTag();tile.settings().write(tag);tag.putInt(field,value);return NativeCanopySettings.read(tag);}
 private static final class Player extends net.neoforged.neoforge.common.util.FakePlayer {
  BlockPos denied;int opens;Player(ServerLevel level){super(level,new GameProfile(UUID.fromString("4ce8dd61-d821-4844-bf98-6c9dbb7b9834"),"CanopyEditor"));}
  @Override public boolean mayUseItemAt(BlockPos pos,Direction face,net.minecraft.world.item.ItemStack stack){return !pos.equals(denied)&&super.mayUseItemAt(pos,face,stack);}
  @Override public java.util.OptionalInt openMenu(MenuProvider provider,java.util.function.Consumer<net.minecraft.network.RegistryFriendlyByteBuf> writer){containerMenu=provider.createMenu(37,getInventory(),this);opens++;var buffer=new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(),serverLevel().registryAccess());try{writer.accept(buffer);need(buffer.readBlockPos().equals(((NativeCanopyMenu)containerMenu).tile.getBlockPos())&&!buffer.isReadable(),"owner-only opening buffer");}finally{buffer.release();}return java.util.OptionalInt.of(37);}
 }
 static void run(ServerLevel world){
  for(int x=5;x<=7;x++)for(int z=5;z<=7;z++)world.getChunk(x,z);
  var player=new Player(world);player.setGameMode(GameType.CREATIVE);player.setPos(POS.getX()+.5,POS.getY(),POS.getZ()+.5);
  for(String kind:new String[]{"canopy","long_canopy","front_canopy_glass","large_front_canopy_glass","regular_canopy_glass","angled_front_1x1","angled_front_1x2","space_visor_2x1","space_visor_3x1","space_visor_5x1","space_visor_5x1_double_height"}){
   for(var cell:BlockPos.betweenClosed(POS.offset(-8,-8,-8),POS.offset(8,8,8)))world.setBlock(cell,Blocks.AIR.defaultBlockState(),18);
   var saved=new CompoundTag();saved.putString("PrivateCanopyOwner","kept");saved.putLong("Anchor",com.vandorlabs.persistence.LegacyBlockPosition.pack(POS.getX(),POS.getY(),POS.getZ()));var tile=CanopyHostChecks.put(world,POS,kind,Direction.NORTH,saved);var cells=NativeCanopyFootprint.cells(kind,POS,tile.facing(),tile.settings(),tile.links());tile.configurationFootprint(cells);for(var cell:cells)if(!cell.equals(POS)){var helper=CanopyHostChecks.put(world,cell,kind,tile.facing(),new CompoundTag());helper.helperOf(tile);}NativeCanopyLifecycle.prepare(tile);
   need(NativeCanopyMenus.open(player,world,cells.getLast(),type),"shared opener from member "+kind);need(player.containerMenu instanceof NativeCanopyMenu,"dedicated menu type");var menu=(NativeCanopyMenu)player.containerMenu;need(menu.tile==tile&&menu.stillValid(player),"exact installed root session "+kind);
   player.denied=POS;need(!menu.stillValid(player)&&!NativeCanopyMenus.open(player,world,POS,type),"root edit denial");player.denied=null;
   var edit=new NativeCanopyEdit(POS,37,1,change(tile,"CanopyShade",2));var result=CanopySettingsPayload.apply(player,edit);need(result!=null&&result.applied()&&tile.settings().data().shade==2,"authorized settings dispatch "+kind);need(tile.saveWithoutMetadata(world.registryAccess()).getString("PrivateCanopyOwner").equals("kept"),"private fields excluded/preserved");
   need(CanopySettingsPayload.apply(player,new NativeCanopyEdit(POS,38,2,edit.settings()))==null,"wrong window");need(CanopySettingsPayload.apply(player,new NativeCanopyEdit(POS.above(),37,2,edit.settings()))==null,"wrong owner position");need(java.util.concurrent.CompletableFuture.supplyAsync(()->CanopySettingsPayload.apply(player,edit)).join()==null,"worker refusal");
   player.setGameMode(GameType.SPECTATOR);need(!menu.stillValid(player)&&CanopySettingsPayload.apply(player,edit)==null,"spectator refusal");player.setGameMode(GameType.SURVIVAL);need(menu.stillValid(player)&&CanopySettingsPayload.apply(player,edit)==null,"survival session requires held Configurizer for edits");player.getInventory().setItem(0,new net.minecraft.world.item.ItemStack(com.vandorlabs.neoforge.VandorLabsBootstrap.CONFIGURIZER.get()));need(CanopySettingsPayload.apply(player,edit)!=null,"survival held tool authorization");player.getInventory().setItem(0,net.minecraft.world.item.ItemStack.EMPTY);player.setGameMode(GameType.CREATIVE);
   need(menu.stillValid(player),"post-edit pending local bounds prepared without closing session");var bounds=tile.publication().geometry().bounds();player.setPos(bounds.maxX+7.9,bounds.minY-player.getEyeHeight()+.1,(bounds.minZ+bounds.maxZ)/2);need(menu.stillValid(player),"eye to large bounds range");player.setPos(bounds.maxX+8.1,bounds.minY-player.getEyeHeight()+.1,(bounds.minZ+bounds.maxZ)/2);need(!menu.stillValid(player),"beyond bounds range");player.setPos(POS.getX()+.5,POS.getY(),POS.getZ()+.5);
   if(kind.equals("front_canopy_glass")){var next=change(tile,"CanopyDepth",80);var desired=NativeCanopyFootprint.shell(kind,POS,tile.facing(),next,tile.links().centered());var obstructed=desired.stream().filter(p->!cells.contains(p)).findFirst().orElseThrow();world.setBlock(obstructed,Blocks.STONE.defaultBlockState(),18);var denied=CanopySettingsPayload.apply(player,new NativeCanopyEdit(POS,37,7,next));need(denied!=null&&!denied.applied()&&denied.authoritative().request()==7&&denied.authoritative().settings()==tile.settings(),"blocked reshape authoritative correlated rejection");need(tile.settings().data().depth==16&&world.getBlockState(obstructed).is(Blocks.STONE),"blocked transaction retains owner and obstruction");}
   world.removeBlockEntity(POS);need(!menu.stillValid(player)&&CanopySettingsPayload.apply(player,edit)==null,"stale installed owner refused");
  }
  System.out.println("[vandorlabs][canopy-editor-host] PASS roles=11 checks="+checks+" installedSessions authorization blockedAck worker bounds privateFields");
 }
}
