package com.vandorlabs.neoforge.screen;

import com.vandorlabs.neoforge.credentials.NativeCredentialItems;
import com.vandorlabs.screen.ScreenFamily;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.*;
import net.minecraft.world.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

/** Actual canonical IDs, item placement, mining and complete process restart; no private registrations. */
@EventBusSubscriber(modid="vandorlabs")
public final class ScreenCanonicalItemChecks {
 private static final BlockPos BASE=new BlockPos(10640,100,10640);
 private static final NativeScreenBlock[] BLOCKS=new NativeScreenBlock[7];private static final NativeScreenItem[] ITEMS=new NativeScreenItem[7];
 private static int checks;private static BlockPos capture;private static final List<ItemStack> DROPS=new ArrayList<>();
 private static String phase(){return System.getProperty("vandorlabs.transmitterPhase","");}private static boolean enabled(){return phase().startsWith("screen-canonical-items-");}
 private static void need(boolean yes,String why){checks++;if(!yes)throw new AssertionError("Canonical Screen items: "+why);}
 @SubscribeEvent public static void drop(net.neoforged.neoforge.event.entity.EntityJoinLevelEvent event){if(enabled()&&capture!=null&&event.getEntity() instanceof ItemEntity item&&item.position().distanceToSqr(Vec3.atCenterOf(capture))<16){DROPS.add(item.getItem().copy());item.discard();}}
 private static CompoundTag choices(ScreenFamily family){var tag=NativeScreenFactory.choices(family,NativeScreenCatalog.INSTANCE,NativeScreenCatalog.INSTANCE.defaultHousing(),NativeScreenCatalog.INSTANCE.defaultFrame());tag.putInt("housingTexture",14);tag.putIntArray("RedstoneChannels",new int[]{17,19});tag.putString("CanonicalPrivateMarker","owned-not-public");return tag;}
 private static int chipCount(CompoundTag tag,ServerLevel level){var stack=NativeCredentialItems.readStack(tag.getCompound("ScreenChipReader").getCompound("InsertedToken"),level.registryAccess());return stack.is(NativeCredentialItems.DATA_CHIP.get())?stack.getCount():0;}
 private static void occupy(NativeScreenEntity tile,ServerLevel level){var tag=tile.saveWithoutMetadata(level.registryAccess());var reader=new CompoundTag();var chip=NativeCredentialItems.dataChip(2);var data=new CompoundTag();data.putString("CanonicalChipSecret","private-payload");chip.set(DataComponents.CUSTOM_DATA,CustomData.of(data));reader.put("InsertedToken",NativeCredentialItems.writeStack(chip,level.registryAccess()));tag.put("ScreenChipReader",reader);tile.loadWithComponents(tag,level.registryAccess());need(!tile.inert()&&chipCount(tile.saveWithoutMetadata(level.registryAccess()),level)==1,"occupied one physical owner "+tile.family());}
 private static void mine(ServerLevel level,ServerPlayer actor,BlockPos pos,int family){capture=pos;DROPS.clear();actor.setGameMode(GameType.SURVIVAL);actor.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.STONE_PICKAXE));need(level.getBlockState(pos).canHarvestBlock(level,pos,actor),"canonical stone tool requirement");need(actor.gameMode.destroyBlock(pos),"actual canonical mining");capture=null;need(DROPS.size()==1&&DROPS.getFirst().is(ITEMS[family])&&DROPS.getFirst().getCount()==1,"one configured Screen drop, no separately ejected chip "+family);need(chipCount(NativeScreenItemSettings.configuration(DROPS.getFirst()),level)==1,"sole chip retained by configured drop "+family);need(level.getBlockEntity(pos)==null,"removed world owner "+family);}
 private static void run(ServerLevel level,boolean read)throws Exception{
  for(var family:ScreenFamily.values()){var id=ResourceLocation.fromNamespaceAndPath("vandorlabs",family.id);need(BuiltInRegistries.BLOCK.get(id) instanceof NativeScreenBlock,"canonical block "+id);BLOCKS[family.ordinal()]=(NativeScreenBlock)BuiltInRegistries.BLOCK.get(id);need(BuiltInRegistries.ITEM.get(id) instanceof NativeScreenItem,"canonical specialized item "+id);ITEMS[family.ordinal()]=(NativeScreenItem)BuiltInRegistries.ITEM.get(id);}
  var actor=new net.neoforged.neoforge.common.util.FakePlayer(level,new com.mojang.authlib.GameProfile(new UUID(0,810),"CanonicalScreenItem"));actor.getInventory().selected=0;
  if(!read)ScreenItemWorldChecks.canonicalReplay(level,actor,BLOCKS,ITEMS);
  for(var family:ScreenFamily.values()){int index=family.ordinal();var pos=BASE.east(index*3);level.getChunkAt(pos);if(!read){level.setBlock(pos,BLOCKS[index].defaultBlockState(),18);var tile=(NativeScreenEntity)level.getBlockEntity(pos);need(BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(tile.getType()).getPath().equals("programmable_viewscreen"),"shared canonical entity identity");tile.loadWithComponents(choices(family),level.registryAccess());occupy(tile,level);var update=tile.getUpdateTag(level.registryAccess());need(!update.contains("ScreenChipReader")&&!update.toString().contains("CanonicalChipSecret")&&!update.toString().contains("private-payload"),"public update excludes physical chip/private stack "+family);actor.setGameMode(GameType.CREATIVE);var pick=BLOCKS[index].getCloneItemStack(tile.getBlockState(),new BlockHitResult(Vec3.atCenterOf(pos),Direction.NORTH,pos,false),level,pos,actor);need(pick.is(ITEMS[index])&&chipCount(NativeScreenItemSettings.configuration(pick),level)==1,"authorized configured Creative pick preserves Source chip data "+family);need(chipCount(tile.saveWithoutMetadata(level.registryAccess()),level)==1,"pick leaves original owner intact "+family);mine(level,actor,pos,index);var placed=DROPS.getFirst().copy();level.setBlock(pos.below(),Blocks.STONE.defaultBlockState(),18);actor.setPos(pos.getX()+.5,pos.getY()+1,pos.getZ()-2);actor.setItemInHand(InteractionHand.MAIN_HAND,placed);var used=placed.useOn(new net.minecraft.world.item.context.UseOnContext(actor,InteractionHand.MAIN_HAND,new BlockHitResult(Vec3.atCenterOf(pos.below()),Direction.UP,pos.below(),false)));need(used.consumesAction()&&placed.isEmpty(),"configured embedded owner placement consumes sole item "+family);tile=(NativeScreenEntity)level.getBlockEntity(pos);need(tile!=null&&!tile.inert()&&chipCount(tile.saveWithoutMetadata(level.registryAccess()),level)==1,"replaced configured owner retains one chip "+family);level.setChunkForced(pos.getX()>>4,pos.getZ()>>4,true);}else{var tile=(NativeScreenEntity)level.getBlockEntity(pos);need(tile!=null&&!tile.inert()&&tile.family()==family&&chipCount(tile.saveWithoutMetadata(level.registryAccess()),level)==1,"one saved embedded owner after full process restart "+family);mine(level,actor,pos,index);}}
  actor.getInventory().clearContent();System.out.println("[vandorlabs][screen-canonical-items] "+(read?"read":"write")+" PASS checks="+checks+" canonical seven-role placement/mining/pick/private updates/embedded ownership");
 }
 @SubscribeEvent public static void started(ServerStartedEvent event){if(!enabled())return;try{run(event.getServer().overworld(),phase().endsWith("read"));event.getServer().saveEverything(true,true,true);}catch(Throwable failure){failure.printStackTrace();System.out.println("[vandorlabs][screen-canonical-items] FAIL "+failure);}finally{event.getServer().halt(false);}}
}
