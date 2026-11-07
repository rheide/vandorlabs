package com.vandorlabs.neoforge.copifier;

import com.vandorlabs.neoforge.ProgrammableBlockEntity;
import java.util.UUID;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.*;
import net.minecraft.world.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.*;
import net.minecraft.world.phys.*;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

/** Real item operations create history; undo compares all targets before changing any of them. */
@EventBusSubscriber(modid="vandorlabs")
public final class NativeCopifierUndoChecks {
 private static final BlockPos BASE=new BlockPos(14912,100,14640);private static int checks;
 static boolean enabled(){return "copifier-undo".equals(System.getProperty("vandorlabs.transmitterPhase"));}
 private static void need(boolean yes,String why){checks++;if(!yes)throw new AssertionError("Copifier undo: "+why);}
 private static ProgrammableBlockEntity put(ServerLevel level,BlockPos pos,String role){level.getChunkAt(pos);level.setBlock(pos,Blocks.AIR.defaultBlockState(),18);level.setBlock(pos,BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath("vandorlabs",role)).defaultBlockState(),18);return (ProgrammableBlockEntity)level.getBlockEntity(pos);}
 private static ItemStack tool(ServerPlayer actor,boolean connected){var root=new CompoundTag();var copy=new CompoundTag();copy.putInt("wall_texture",7);copy.putInt("texture_layout",2);var faces=new CompoundTag();faces.putBoolean("enabled",true);faces.putIntArray("choices",new int[]{7,7,7,7,7,7});copy.put("face_textures",faces);root=NativeCopifierClipboard.copied(root,copy,"Source housing");root=NativeCopifierOptions.withConnected(root,connected);var tool=new ItemStack(NativeCopifierItemChecks.ITEM);tool.set(DataComponents.CUSTOM_DATA,CustomData.of(root));actor.setItemInHand(InteractionHand.MAIN_HAND,tool);return tool;}
 private static boolean use(ServerPlayer actor,BlockPos pos){return actor.gameMode.useItemOn(actor,actor.serverLevel(),actor.getMainHandItem(),InteractionHand.MAIN_HAND,new BlockHitResult(Vec3.atCenterOf(pos).add(0,0,-.5),Direction.NORTH,pos,false)).consumesAction();}
 private static void run(ServerLevel level){var actor=new net.neoforged.neoforge.common.util.FakePlayer(level,new com.mojang.authlib.GameProfile(new UUID(0,1875),"CopifierUndo"));actor.setGameMode(GameType.SURVIVAL);actor.setPos(BASE.getX()+.5,BASE.getY()+1,BASE.getZ()-2);
  for(var family:NativeCopifierHousingCapture.Family.values()){
   var owner=put(level,BASE,family.id);var initial=owner.saveWithoutMetadata(level.registryAccess());initial.putBoolean("FaceTexturesEnabled",false);initial.putIntArray("FaceTextures",new int[]{1,2,3,4,5,6});initial.putString("UnrelatedUndoMarker","retained");owner.loadWithComponents(initial,level.registryAccess());var state=level.getBlockState(BASE);var before=NativeCopifierHousingCapture.undo(family,owner.saveWithoutMetadata(level.registryAccess()));var tool=tool(actor,false);var clipboard=NativeCopifierItem.data(tool);
   need(use(actor,BASE),"real item apply "+family);need(NativeCopifierUndo.undo(actor),"single undo "+family);need(before.equals(NativeCopifierHousingCapture.undo(family,owner.saveWithoutMetadata(level.registryAccess()))),"Source before projection including dormant choices "+family);need(level.getBlockEntity(BASE)==owner&&level.getBlockState(BASE)==state&&owner.saveWithoutMetadata(level.registryAccess()).getString("UnrelatedUndoMarker").equals("retained"),"identity/orientation/unrelated data");need(clipboard.equals(NativeCopifierItem.data(tool))&&tool.getCount()==1,"undo leaves tool/clipboard");need(!NativeCopifierUndo.undo(actor),"one operation only");
   need(use(actor,BASE),"second actual apply");var applied=NativeCopifierHousingCapture.undo(family,owner.saveWithoutMetadata(level.registryAccess()));owner.apply(owner.settings().choose(8));var edit=owner.saveWithoutMetadata(level.registryAccess());need(!NativeCopifierUndo.undo(actor)&&edit.equals(owner.saveWithoutMetadata(level.registryAccess())),"later settings refuse without mutation");need(NativeCopifierHousingWorld.applySingle(level,BASE,actor,Direction.NORTH,applied),"restore later edit using validated setter");need(NativeCopifierUndo.undo(actor),"refused history remains retryable");
   need(use(actor,BASE),"spectator setup");actor.setGameMode(GameType.SPECTATOR);need(!NativeCopifierUndo.undo(actor),"spectator denied");actor.setGameMode(GameType.SURVIVAL);need(NativeCopifierUndo.undo(actor),"authorized retry");level.removeBlock(BASE,false);
  }
  for(int i=0;i<12;i++)put(level,BASE.east(i),"programmable_block");tool(actor,true);need(use(actor,BASE),"real connected item apply");var last=(ProgrammableBlockEntity)level.getBlockEntity(BASE.east(11));last.apply(last.settings().choose(8));need(!NativeCopifierUndo.undo(actor)&&((ProgrammableBlockEntity)level.getBlockEntity(BASE)).settings().mainTexture==7,"whole operation preflight prevents partial undo");last.apply(last.settings().choose(7));need(NativeCopifierUndo.undo(actor),"undo includes distant connected cells");for(int i=0;i<12;i++){need(((ProgrammableBlockEntity)level.getBlockEntity(BASE.east(i))).settings().mainTexture==0,"connected before restored");level.removeBlock(BASE.east(i),false);}
  // A normal maximum-size copy can exceed the separate retained-history memory budget.
  for(int x=0;x<16;x++)for(int y=0;y<16;y++)for(int z=0;z<16;z++)put(level,BASE.offset(x,y,z),"programmable_block");tool(actor,true);need(use(actor,BASE),"large bounded copy still applies");need(!NativeCopifierUndo.undo(actor),"over-budget history is unavailable as a whole");for(int x=0;x<16;x++)for(int y=0;y<16;y++)for(int z=0;z<16;z++){need(((ProgrammableBlockEntity)level.getBlockEntity(BASE.offset(x,y,z))).settings().mainTexture==7,"over-budget copy has no partial undo");level.removeBlock(BASE.offset(x,y,z),false);}
  var owner=put(level,BASE,"programmable_block");tool(actor,false);need(use(actor,BASE),"logout setup");NativeCopifierUndo.logout(new net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent(actor));need(!NativeCopifierUndo.undo(actor)&&owner.settings().mainTexture==7,"logout retires history");
  System.out.println("[vandorlabs][copifier-undo] PASS checks="+checks+" ninefamilies/dormantchoices/lateredit/wholepreflight/distant/boundedmemory/logout; inventory target adapters remain excluded");
 }
 @SubscribeEvent public static void started(ServerStartedEvent event){if(!enabled())return;try{run(event.getServer().overworld());}catch(Throwable failure){failure.printStackTrace();System.out.println("[vandorlabs][copifier-undo] FAIL "+failure);}finally{event.getServer().halt(false);}}
}
