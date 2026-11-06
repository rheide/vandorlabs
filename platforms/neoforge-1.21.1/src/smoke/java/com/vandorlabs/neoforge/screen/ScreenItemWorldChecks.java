package com.vandorlabs.neoforge.screen;

import com.google.gson.*;
import com.vandorlabs.screen.*;
import java.nio.file.*;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.*;
import net.minecraft.world.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.phys.*;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

/** Private real item-use/harvest ownership, Source mounting comparisons and saved rollback recovery. */
@EventBusSubscriber(modid="vandorlabs")
public final class ScreenItemWorldChecks {
 private static final NativeScreenBlock[] BLOCKS=new NativeScreenBlock[7];private static final FaultItem[] ITEMS=new FaultItem[7];private static BlockEntityType<NativeScreenEntity> TYPE;
 private static final BlockPos BASE=new BlockPos(9800,90,9800),SAVE=BASE.east(8);private static int checks,fault,factoryCalls;private static BlockPos dropPos;private static Item droppedItem;private static int dropCount;private static ItemStack dropped=ItemStack.EMPTY;
 private static String phase(){return System.getProperty("vandorlabs.transmitterPhase","");}private static boolean enabled(){return phase().startsWith("screen-item-world-");}private static void need(boolean value,String why){checks++;if(!value)throw new AssertionError("Screen item world: "+why);}
 private static ResourceLocation id(int family){return ResourceLocation.fromNamespaceAndPath("vandorlabs","screen_item_world_"+family);}
 private static final class FaultItem extends NativeScreenItem {FaultItem(NativeScreenBlock block){super(block,new Item.Properties());}@Override protected boolean stage(Level world,BlockPos pos,BlockState state){if(fault==1)return false;boolean changed=super.stage(world,pos,state);if(fault==3)throw new IllegalStateException("fixture Screen staged throw");return fault==2||fault==4||fault==6?false:changed;}@Override protected boolean restore(Level world,BlockPos pos,BlockState state){return fault==4||fault==6?false:super.restore(world,pos,state);}}
 @EventBusSubscriber(modid="vandorlabs",bus=EventBusSubscriber.Bus.MOD)public static final class Registration {
  @SubscribeEvent public static void register(net.neoforged.neoforge.registries.RegisterEvent event){if(!enabled())return;event.register(Registries.BLOCK,h->{for(var family:ScreenFamily.values()){BLOCKS[family.ordinal()]=NativeScreenBlock.create(family,BlockBehaviour.Properties.of().strength(5,6).requiresCorrectToolForDrops(),()->TYPE);h.register(id(family.ordinal()),BLOCKS[family.ordinal()]);}});event.register(Registries.ITEM,h->{for(int i=0;i<7;i++){ITEMS[i]=new FaultItem(BLOCKS[i]);h.register(id(i),ITEMS[i]);}});event.register(Registries.BLOCK_ENTITY_TYPE,h->{TYPE=BlockEntityType.Builder.<NativeScreenEntity>of((pos,state)->{factoryCalls++;return (fault==5||fault==6)&&factoryCalls>1?null:new NativeScreenEntity(TYPE,pos,state,((NativeScreenBlock)state.getBlock()).family(),NativeScreenCatalog.INSTANCE);},BLOCKS).build(null);h.register(ResourceLocation.parse("vandorlabs:screen_item_world"),TYPE);});}
 }
 @SubscribeEvent public static void drop(net.neoforged.neoforge.event.entity.EntityJoinLevelEvent event){if(enabled()&&dropPos!=null&&event.getEntity() instanceof ItemEntity item&&item.getItem().is(droppedItem)&&item.position().distanceToSqr(Vec3.atCenterOf(dropPos))<16){dropCount+=item.getItem().getCount();dropped=item.getItem().copy();item.discard();}}
 private static NativeScreenEntity owner(ServerLevel world,BlockPos pos){return world.getBlockEntity(pos) instanceof NativeScreenEntity tile?tile:null;}
 private static ItemStack configured(int family,CompoundTag choices){var stack=new ItemStack(ITEMS[family],2);var tag=new CompoundTag();tag.put(NativeScreenItemSettings.CONFIGURATION,choices.copy());stack.set(DataComponents.CUSTOM_DATA,CustomData.of(tag));return stack;}
 private static void clear(ServerLevel world,BlockPos pos){fault=0;world.getChunkAt(pos);world.setBlock(pos,Blocks.AIR.defaultBlockState(),18);for(var direction:Direction.values())world.setBlock(pos.relative(direction),Blocks.AIR.defaultBlockState(),18);}
 private static InteractionResult use(ServerLevel world,ServerPlayer actor,BlockPos pos,ItemStack stack,Direction face,float hit,int support){
  var supportPos=pos.relative(face.getOpposite());var supporting=support==0?Blocks.STONE.defaultBlockState():Blocks.STONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE,support==1?SlabType.BOTTOM:SlabType.TOP);world.setBlock(supportPos,supporting,18);actor.setPos(pos.getX()+.5,pos.getY()+1,pos.getZ()-2);actor.setItemInHand(InteractionHand.MAIN_HAND,stack);factoryCalls=0;
  return stack.useOn(new UseOnContext(actor,InteractionHand.MAIN_HAND,new BlockHitResult(Vec3.atLowerCornerOf(supportPos).add(hit,hit,1F-hit),face,supportPos,false)));
 }
 private static void replay(ServerLevel world,ServerPlayer actor)throws Exception{
  JsonArray rows;try(var reader=Files.newBufferedReader(Path.of(System.getProperty("vandorlabs.screenConfiguredPlacementReference","../../build/test-artifacts/screen-configured-placement-reference.json")))){rows=JsonParser.parseReader(reader).getAsJsonArray();}int cases=0;
  for(var entry:rows){var row=entry.getAsJsonObject();if(row.get("yaw").getAsInt()!=0||row.get("sneak").getAsBoolean())continue;var family=ScreenFamily.fromId(row.get("id").getAsString());int index=family.ordinal();clear(world,BASE);actor.setGameMode(GameType.SURVIVAL);actor.setYRot(0);actor.setXRot(0);actor.setShiftKeyDown(false);var raw=TagParser.parseTag(row.get("input").getAsString());var stack=configured(index,raw);float hit=.1F+.4F*row.get("fraction").getAsInt();var result=use(world,actor,BASE,stack,Direction.from3DDataValue(row.get("face").getAsInt()),hit,row.get("support").getAsInt());
   need(result.consumesAction()&&stack.getCount()==1,"actual Survival item use row="+cases+" role="+family+" result="+result);var tile=owner(world,BASE);need(tile!=null&&!tile.inert(),"actual configured owner");need(family.encode(BLOCKS[index].screenState(world.getBlockState(BASE)))==row.get("meta").getAsInt(),"actual Source state metadata");
   var saved=NativeScreenItemSettings.choices(tile,world.registryAccess());var expected=NativeScreenArtworkMigration.normalize(TagParser.parseTag(row.get("pick").getAsString()),NativeScreenCatalog.INSTANCE);for(String key:ScreenItemSettingsChecks.OWNED)need(Objects.equals(saved.get(key),expected.get(key)),"actual Source configured mount row="+cases+" role="+family+" key="+key+" expected="+expected.get(key)+" actual="+saved.get(key));
   var picked=BLOCKS[index].getCloneItemStack(world.getBlockState(BASE),new BlockHitResult(Vec3.atCenterOf(BASE),Direction.NORTH,BASE,false),world,BASE,actor);need(picked.is(ITEMS[index])&&NativeScreenItemSettings.configuration(picked).equals(saved),"actual configured pick");
   if(cases%270==0){dropPos=BASE;droppedItem=ITEMS[index];dropCount=0;actor.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.STONE_PICKAXE));need(world.getBlockState(BASE).canHarvestBlock(world,BASE,actor),"actual stone harvest tier");need(actor.gameMode.destroyBlock(BASE)&&dropCount==1,"actual configured harvest exactly once");need(NativeScreenItemSettings.configuration(dropped).equals(saved),"captured removed tile harvest configuration");dropPos=null;}
   cases++;
  }
  need(cases==1890,"Source six faces/three bands/three supports/five configured tags/all7 families");System.out.println("[vandorlabs][screen-item-world] source uses PASS cases="+cases);
 }
 private static CompoundTag choices(){var tag=new CompoundTag();tag.putInt("housingTexture",14);tag.putInt("HousingTextureVersion",1);tag.putIntArray("RedstoneChannels",new int[]{17,19});tag.putString("FutureChoice","retain");return tag;}
 private static void faults(ServerLevel world,ServerPlayer actor){actor.setGameMode(GameType.SURVIVAL);actor.setYRot(0);actor.setShiftKeyDown(false);for(int mode=1;mode<=6;mode++){clear(world,BASE);fault=mode;var stack=configured(0,choices());boolean threw=false;try{use(world,actor,BASE,stack,Direction.UP,.5F,0);}catch(RuntimeException expected){threw=true;}finally{fault=0;}if(mode==4||mode==6){var tile=owner(world,BASE);need(threw&&tile!=null&&tile.inert()&&stack.getCount()==1,"refused rollback retains one consumed inert owner mode="+mode);need(tile.saveWithoutMetadata(world.registryAccess()).getBoolean(NativeScreenItemSettings.PLACEMENT_RECOVERY),"durable recovery marker");}else need(world.isEmptyBlock(BASE)&&stack.getCount()==2,"stage/factory failure restores old cell and stack mode="+mode);}
  clear(world,BASE);var bad=choices();bad.putInt("ScreenChipReader",42);var stack=configured(0,bad);need(!use(world,actor,BASE,stack,Direction.UP,.5F,0).consumesAction()&&stack.getCount()==2&&world.isEmptyBlock(BASE),"unsupported ownership refused before mutation");
 }
 private static void recoveryMining(ServerLevel world,ServerPlayer actor){
  for(int family=0;family<7;family++)for(boolean creative:new boolean[]{false,true})for(boolean proper:new boolean[]{false,true}){
   clear(world,BASE);world.setBlock(BASE,BLOCKS[family].defaultBlockState(),18);var tile=owner(world,BASE);var original=choices();original.putInt("ScreenChipReader",42);tile.loadWithComponents(original.copy(),world.registryAccess());need(tile.inert(),"inert full snapshot fixture");actor.setGameMode(creative?GameType.CREATIVE:GameType.SURVIVAL);actor.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(proper?Items.STONE_PICKAXE:Items.WOODEN_PICKAXE));
   if(!creative)need(world.getBlockState(BASE).canHarvestBlock(world,BASE,actor)==proper,"actual recovery harvest tier");dropPos=BASE;droppedItem=ITEMS[family];dropCount=0;need(actor.gameMode.destroyBlock(BASE)&&dropCount==1,"one inert recovery owner family="+family+" creative="+creative+" proper="+proper);var recovered=dropped;dropPos=null;need(NativeScreenItemSettings.configuration(recovered).getCompound(NativeScreenItemSettings.RECOVERY).equals(original),"failed owner full private snapshot in item");
   clear(world,BASE);actor.setGameMode(GameType.SURVIVAL);int count=recovered.getCount();need(!use(world,actor,BASE,recovered,Direction.UP,.5F,0).consumesAction()&&recovered.getCount()==count&&world.isEmptyBlock(BASE),"actual opaque recovery placement refuses without consumption");
  }
  // A cancelled break mark must not manufacture an old recovery item after a successful repair.
  clear(world,BASE);world.setBlock(BASE,BLOCKS[0].defaultBlockState(),18);var tile=owner(world,BASE);var bad=choices();bad.putInt("ScreenChipReader",42);tile.loadWithComponents(bad,world.registryAccess());actor.setGameMode(GameType.CREATIVE);BLOCKS[0].playerWillDestroy(world,BASE,world.getBlockState(BASE),actor);tile.loadWithComponents(choices(),world.registryAccess());need(!tile.inert(),"repaired owner");dropPos=BASE;droppedItem=ITEMS[0];dropCount=0;world.setBlock(BASE,Blocks.AIR.defaultBlockState(),18);dropPos=null;need(dropCount==0,"transient failed-break mark cannot graft stale recovery onto repaired owner");
 }
 private static void write(ServerLevel world)throws Exception{var actor=new net.neoforged.neoforge.common.util.FakePlayer(world,new com.mojang.authlib.GameProfile(new UUID(0,809),"ScreenItem"));actor.getInventory().selected=0;replay(world,actor);faults(world,actor);recoveryMining(world,actor);
  actor.setGameMode(GameType.SURVIVAL);clear(world,SAVE);var stack=configured(1,choices());need(use(world,actor,SAVE,stack,Direction.UP,.5F,0).consumesAction(),"saved valid owner");clear(world,SAVE.south(4));fault=4;stack=configured(0,choices());try{use(world,actor,SAVE.south(4),stack,Direction.UP,.5F,0);}catch(RuntimeException expected){}finally{fault=0;}need(stack.getCount()==1&&owner(world,SAVE.south(4)).inert(),"saved rollback owner");world.setChunkForced(SAVE.getX()>>4,SAVE.getZ()>>4,true);
 }
 private static void read(ServerLevel world){world.getChunkAt(SAVE);var valid=owner(world,SAVE);need(valid!=null&&!valid.inert()&&valid.family()==ScreenFamily.CONSOLE&&valid.screenSettings().housing().mainTexture==14,"valid configured owner survives process restart");var failed=owner(world,SAVE.south(4));need(failed!=null&&failed.inert()&&failed.saveWithoutMetadata(world.registryAccess()).getBoolean(NativeScreenItemSettings.PLACEMENT_RECOVERY),"failed rollback stays inert after restart");var recovered=NativeScreenItemSettings.pick(ITEMS[0],failed,world.registryAccess());need(NativeScreenItemSettings.configuration(recovered).getCompound(NativeScreenItemSettings.RECOVERY).getString("FutureChoice").equals("retain"),"recovery item retains original private choice");}
 @SubscribeEvent public static void started(ServerStartedEvent event){if(!enabled())return;try{var world=event.getServer().overworld();if(phase().endsWith("write"))write(world);else read(world);event.getServer().saveEverything(true,true,true);System.out.println("[vandorlabs][screen-item-world] "+(phase().endsWith("write")?"write":"read")+" PASS checks="+checks+" private bindings; public Screen/render acceptance remains separate");}catch(Throwable failure){failure.printStackTrace();System.out.println("[vandorlabs][screen-item-world] FAIL "+failure);}finally{event.getServer().halt(false);}}
}
