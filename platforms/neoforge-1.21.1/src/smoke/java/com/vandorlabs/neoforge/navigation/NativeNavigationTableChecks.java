package com.vandorlabs.neoforge.navigation;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
/** Executed on the server thread by the native navigation runtime fixture. */
public final class NativeNavigationTableChecks {
 private static ItemStack pendingDrop=ItemStack.EMPTY;private static net.minecraft.world.phys.AABB pendingBounds;private static long pendingBefore;private static int dropTicks,matchedTicks;
 /** Spawned items become queryable only after entity-section visibility settles. */
 public static boolean completeDrops(ServerLevel world){if(pendingDrop.isEmpty())return true;long count=world.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,pendingBounds).stream().filter(e->ItemStack.isSameItemSameComponents(e.getItem(),pendingDrop)).mapToInt(e->e.getItem().getCount()).sum();need(count-pendingBefore<=1,"cassette ejection never duplicates after publication before="+pendingBefore+" now="+count);if(count-pendingBefore==1){if(++matchedTicks>=2){pendingDrop=ItemStack.EMPTY;System.out.println("[vandorlabs][navigation] cassette publication PASS exactly-one/preserved-components/second-real-tick");return true;}}else matchedTicks=0;need(++dropTicks<=40,"cassette item publication timeout before="+pendingBefore+" now="+count);return false;}

 public static void run(ServerLevel world,net.minecraft.server.level.ServerPlayer actor,BlockPos base,ItemStack module,ItemStack chip){
  for(int x=0;x<4;x++)for(int z=0;z<3;z++)world.setBlock(base.offset(x,0,z),Blocks.AIR.defaultBlockState(),3);
  world.setBlock(base,NativeNavigationTableBindings.FLAT.defaultBlockState(),3);var first=table(world,base);NativeNavigationTableAssemblies.rebuild(first,new HashSet<>());need(first.formed()&&first.hasMount()&&!first.hasModuleBay(),"singleton chip/no cassette bay");need(!first.installModule(module),"singleton rejects module");
  world.setBlock(base.east(),NativeNavigationTableBindings.FLAT.defaultBlockState(),3);NativeNavigationTableAssemblies.rebuild(first,new HashSet<>());var second=table(world,base.east());need(first.formed()&&second.formed()&&first.assemblyWidth()==2,"joined two");var bay=first.hasModuleBay()?first:second;need(first.hasMount()!=second.hasMount()&&first.hasModuleBay()!=second.hasModuleBay(),"one reader and one cassette bay");need(bay.installModule(module),"module insertion");var storage=bay.moduleStorage();need(!storage.storedModule().isEmpty(),"physical inventory anchored");var raw=storage.saveWithoutMetadata(world.registryAccess());var loaded=new NativeNavigationTableEntity(storage.getBlockPos(),storage.getBlockState());loaded.loadWithComponents(raw,world.registryAccess());need(ItemStack.isSameItemSameComponents(loaded.storedModule(),module),"full module save round trip");
  for(int fault=0;fault<3;fault++){var broken=raw.copy();if(fault==0)broken.putInt("NavigationFutureSchema",77);if(fault==1)broken.putLong("NavigationModuleStorage",new BlockPos(500,0,0).asLong());if(fault==2)broken.getCompound("NavigationModule").putInt("count",2);var failed=new NativeNavigationTableEntity(storage.getBlockPos(),storage.getBlockState());failed.loadWithComponents(broken,world.registryAccess());need(failed.inert()&&!failed.formed()&&failed.storedModule().isEmpty(),"bad ownership remains inert "+fault);need(failed.saveWithoutMetadata(world.registryAccess()).equals(broken),"failed ownership NBT retained exactly "+fault);need(!failed.getUpdateTag(world.registryAccess()).contains("NavigationModule"),"failed private inventory not projected "+fault);}

  first.setChip(chip);need(ItemStack.isSameItemSameComponents(second.chipStack(),chip),"shared chip owner");world.setBlock(base.south(),NativeNavigationTableBindings.FLAT.defaultBlockState(),3);world.setBlock(base.east().south(),NativeNavigationTableBindings.FLAT.defaultBlockState(),3);NativeNavigationTableAssemblies.rebuild(first,new HashSet<>());need(bay.moduleStorage()==storage&&ItemStack.isSameItemSameComponents(bay.moduleStack(),module),"resize retains original physical storage");
  first.setTexture(-1);first.setRxChannels(com.vandorlabs.redstone.ChannelList.of(71));var oldOwner=first.reader();var view=NativeNavigationRuntime.view(oldOwner);view.x=4321;view.z=-1234;view.zoom=2;world.setBlock(base.east(2),NativeNavigationTableBindings.FLAT.defaultBlockState(),3);world.setBlock(base.east(2).south(),NativeNavigationTableBindings.FLAT.defaultBlockState(),3);NativeNavigationTableAssemblies.rebuild(first,new HashSet<>());var newOwner=first.reader();need(newOwner!=oldOwner&&newOwner.texture()==-1&&newOwner.rxChannels().equals(com.vandorlabs.redstone.ChannelList.of(71)),"owner change preserves existing explicit artwork and channels");var retainedView=NativeNavigationRuntime.view(newOwner);need(retainedView.x==4321&&retainedView.z==-1234&&retainedView.zoom==2,"owner change preserves panned view and zoom");need(first.moduleStorage()==storage,"owner change leaves inventory physically anchored");inertChipStorage(world,actor,base,newOwner,chip);
  var missingPos=base.east(2).south();var missingState=world.getBlockState(missingPos);var missingTag=world.getBlockEntity(missingPos).saveWithFullMetadata(world.registryAccess());world.removeBlockEntity(missingPos);NativeNavigationTableAssemblies.rebuild(first,new HashSet<>());need(!storage.storedModule().isEmpty()&&first.moduleStorage()==storage,"matching table missing tile factory retains anchored inventory and roles");var restored=new NativeNavigationTableEntity(missingPos,missingState);restored.loadWithComponents(missingTag,world.registryAccess());world.setBlockEntity(restored);NativeNavigationTableAssemblies.rebuild(first,new HashSet<>());need(first.formed()&&!storage.storedModule().isEmpty(),"matching tile restoration recovers complete table without ejection");
  var tag=storage.saveWithoutMetadata(world.registryAccess());var copy=tag.copy();NativeNavigationTableEntity.rotateTag(copy,new com.vandorlabs.neoforge.gear.NativeGearPayload.Transform(0,0,-1,0,1,0,1,0,0,0,0,0));need(tag.getCompound("NavigationModule").equals(copy.getCompound("NavigationModule")),"rotation preserves saved geography");
  var bounds=new net.minecraft.world.phys.AABB(base).inflate(5).expandTowards(0,-128,0);long before=world.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,bounds).stream().filter(e->ItemStack.isSameItemSameComponents(e.getItem(),module)).mapToInt(e->e.getItem().getCount()).sum();
  world.setBlock(base.south(),Blocks.AIR.defaultBlockState(),3);NativeNavigationTableAssemblies.rebuild(first,new HashSet<>());need(!first.formed()&&storage.storedModule().isEmpty(),"complete invalid L ejects saved cassette");NativeNavigationTableAssemblies.rebuild(first,new HashSet<>());need(storage.storedModule().isEmpty(),"second invalid rebuild cannot retain or eject another cassette");pendingDrop=com.vandorlabs.neoforge.credentials.NativeCredentialItems.ownedCopy(module);pendingBounds=bounds;pendingBefore=before;dropTicks=matchedTicks=0;
  for(int x=0;x<3;x++)for(int z=0;z<2;z++)world.setBlock(base.offset(x,0,z),Blocks.AIR.defaultBlockState(),3);
  System.out.println("[vandorlabs][navigation] table PASS singleton/join/one-reader/one-bay/anchored-storage/full-save/rotation-geography/ejection-storage-cleared");
 }
 /** Exercise the ordinary Survival use path while the actual arm journal reserves its chip. */
 public static void heldChipInteraction(net.minecraft.server.level.ServerPlayer actor,NativeNavigationTableEntity member){
  var reader=member.reader();need(reader!=null&&reader.hasMount()&&!reader.chipStack().isEmpty(),"reserved table has a physical chip");
  var chip=reader.chipStack().copy();refusedInteraction(actor,reader,ItemStack.EMPTY,true);
  need(ItemStack.matches(chip,reader.chipStack()),"refused removal leaves the reserved chip in its original owner");
 }
 private static void inertChipStorage(ServerLevel world,net.minecraft.server.level.ServerPlayer actor,BlockPos base,NativeNavigationTableEntity reader,ItemStack chip){
  NativeNavigationTableEntity storage=null;for(int x=0;x<3;x++)for(int z=0;z<2;z++){var candidate=table(world,base.offset(x,0,z));if(candidate!=null&&!candidate.storedChip().isEmpty())storage=candidate;}
  need(storage!=null&&storage!=reader,"resized table retains a separate physical chip anchor");var saved=storage.saveWithFullMetadata(world.registryAccess());var retained=saved.copy();retained.putInt("NavigationFutureOwner",77);
  storage.loadWithComponents(retained,world.registryAccess());try{need(storage.inert()&&reader.chipStack().isEmpty(),"inert chip anchor is unavailable");refusedInteraction(actor,reader,chip.copy(),false);need(retained.equals(storage.saveWithFullMetadata(world.registryAccess())),"failed insertion retains the inert owner and its original private inventory");}
  finally{storage.loadWithComponents(saved,world.registryAccess());need(saved.equals(storage.saveWithFullMetadata(world.registryAccess())),"restored anchor retains its exact snapshot");}
  need(ItemStack.matches(reader.chipStack(),chip),"restored chip anchor still has exactly its original chip");
 }
 private static void refusedInteraction(net.minecraft.server.level.ServerPlayer actor,NativeNavigationTableEntity reader,ItemStack held,boolean reserved){
  var previousPos=actor.position();var previousHeld=actor.getMainHandItem();boolean previousShift=actor.isShiftKeyDown();var pos=reader.getBlockPos();var face=reader.getBlockState().getValue(NativeNavigationTableBlock.FACING);
  try{actor.setShiftKeyDown(false);actor.setPos(pos.getX()+.5+face.getStepX()*2,pos.getY(),pos.getZ()+.5+face.getStepZ()*2);actor.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,held);
   need(reader.hasMount()&&actor.isAlive()&&!actor.isCreative()&&!actor.isSpectator()&&actor.level()==reader.getLevel()
         &&actor.distanceToSqr(pos.getCenter())<=64&&actor.level().mayInteract(actor,pos)&&actor.mayUseItemAt(pos,face,held),
         "ordinary Survival click has valid actor, range and world permission");
   need(com.vandorlabs.neoforge.NativeConfigurationAccess.canEditDialog(actor,reader.getLevel(),pos,face)!=reserved,
         "only the held mount itself is refused by the dialog permission guard");
   var before=new ArrayList<ItemStack>();for(int slot=0;slot<actor.getInventory().getContainerSize();slot++)before.add(actor.getInventory().getItem(slot).copy());
   double y=reader.kind()==com.vandorlabs.navigation.NavigationLayout.Kind.FLAT?.65:.3;var hit=new net.minecraft.world.phys.BlockHitResult(new net.minecraft.world.phys.Vec3(pos.getX()+.5+face.getStepX()*.5,pos.getY()+y,pos.getZ()+.5+face.getStepZ()*.5),face,pos,false);
   var result=actor.gameMode.useItemOn(actor,actor.serverLevel(),held,net.minecraft.world.InteractionHand.MAIN_HAND,hit);
   // Native game mode falls through from a block FAIL to the chip item's non-consuming PASS.
   need(!result.consumesAction(),"refused chip transfer does not report a completed ordinary interaction");
   for(int slot=0;slot<before.size();slot++)need(ItemStack.matches(before.get(slot),actor.getInventory().getItem(slot)),"refused chip transfer leaves player inventory unchanged slot="+slot);
  }finally{actor.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,previousHeld);actor.setShiftKeyDown(previousShift);actor.setPos(previousPos.x,previousPos.y,previousPos.z);}
 }
 private static NativeNavigationTableEntity table(ServerLevel world,BlockPos pos){return (NativeNavigationTableEntity)world.getBlockEntity(pos);}private static void need(boolean value,String message){if(!value)throw new AssertionError(message);}
 private NativeNavigationTableChecks(){}
}
