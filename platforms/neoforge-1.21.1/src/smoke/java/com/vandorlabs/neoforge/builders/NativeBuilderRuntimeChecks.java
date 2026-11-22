package com.vandorlabs.neoforge.builders;
import com.vandorlabs.neoforge.transfer.*;import com.vandorlabs.neoforge.rack.*;import com.vandorlabs.redstone.ChannelList;import java.util.*;import net.minecraft.core.*;import net.minecraft.nbt.*;import net.minecraft.server.level.*;import net.minecraft.world.*;import net.minecraft.world.entity.item.ItemEntity;import net.minecraft.world.item.*;import net.minecraft.world.level.*;import net.minecraft.world.level.block.Blocks;import net.minecraft.world.phys.AABB;import net.neoforged.bus.api.SubscribeEvent;import net.neoforged.fml.common.EventBusSubscriber;import net.neoforged.neoforge.event.server.ServerStartedEvent;import net.neoforged.neoforge.event.tick.ServerTickEvent;
/** Real async formation/mining, ownership holds, physical ports, typed failure retention and cold mixed-chunk recovery. */
@EventBusSubscriber(modid="vandorlabs")
public final class NativeBuilderRuntimeChecks {
 private static final BlockPos ROOT=new BlockPos(31,120,31);private static ServerLevel world;private static Actor actor;private static int stage,ticks,checks,caseIndex,recipe,stageAt,desiredX,desiredZ;private static float paused;private static CompoundTag owned;
 private static final class Actor extends net.neoforged.neoforge.common.util.FakePlayer {Actor(ServerLevel w){super(w,new com.mojang.authlib.GameProfile(new UUID(0,211004),"NativeBuilderRuntime"));}}
 private static String phase(){return System.getProperty("vandorlabs.builderRecoveryPhase","");}private static boolean enabled(){return Boolean.getBoolean("vandorlabs.builderRuntimeChecks");}private static NativeBuilderEntity root(){return (NativeBuilderEntity)world.getBlockEntity(ROOT);}private static void need(boolean b,String why){checks++;if(!b)throw new AssertionError("Builder stage="+stage+" tick="+ticks+": "+why);}
 private static java.nio.file.Path expected(){return world.getServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).resolve("builder-smoke-expected.nbt");}
 @SubscribeEvent public static void started(ServerStartedEvent e){if(!enabled())return;try{world=e.getServer().overworld();for(int x=24;x<=40;x+=8)for(int z=24;z<=40;z+=8){world.setChunkForced(x>>4,z>>4,true);world.getChunkAt(new BlockPos(x,120,z));}actor=new Actor(world);actor.setGameMode(GameType.SURVIVAL);actor.setPos(31.5,120,27.5);actor.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.DIAMOND_PICKAXE));if(phase().equals("legacy-drive-save")){legacySetup();return;}if(phase().equals("legacy-drive-read")){actor.setPos(27.5,120,31.5);stage=302;return;}if(phase().equals("drives")){setup(3,Direction.SOUTH,2);need(NativeBuilderTransactions.request(root(),actor,true),"Longburn async formation");stage=200;return;}if(phase().equals("gantry")){setup(2,Direction.EAST,3);need(NativeBuilderTransactions.request(root(),actor,true),"Gantry async formation");stage=100;return;}if(phase().equals("export-read")){stage=95;return;}if(phase().startsWith("read")){recovered();return;}setup(0,Direction.NORTH);negativeData();NativeBuilderQueueChecks.run(world,actor,ROOT.offset(-4,20,-4));if(phase().equals("crash-owned")){world.setBlock(ROOT.south(),Blocks.AIR.defaultBlockState(),3);fillOwnership();saveExpected(false);armCrash();need(!actor.gameMode.destroyBlock(ROOT),"unformed owned core mining defers original removal with incomplete frame");stage=90;return;}if(phase().equals("crash-form")){fillOwnership();saveExpected(false);armCrash();need(NativeBuilderTransactions.request(root(),actor,true),"crash formation queued");stage=90;return;}if(phase().equals("crash-mine")){need(NativeBuilderTransactions.request(root(),actor,true),"mine preparation queues real formation");stage=80;return;}stage=0;}catch(Throwable bad){fail(bad);}}
 private static void setup(int design,Direction face){setup(design,face,0);}
 private static void setup(int design,Direction face,int corner){clear();var block=NativeBuilderBindings.CORES.get(NativeBuilderDesigns.at(design).id());world.setBlock(ROOT,block.defaultBlockState().setValue(NativeBuilderCoreBlock.FACING,face),3);var owner=root();var d=owner.design();desiredX=(corner&1)==0?0:d.width()-1;desiredZ=(corner&2)==0?0:d.depth()-1;var layout=new com.vandorlabs.machines.CoreLayout(d.width(),d.height(),d.depth(),face.get3DDataValue(),desiredX,desiredZ);for(int x=0;x<d.width();x++)for(int y=0;y<d.height();y++)for(int z=0;z<d.depth();z++){var at=ROOT.offset(layout.x(x,z),y,layout.z(x,z));if(!at.equals(ROOT))world.setBlock(at,layout.frame(x,y,z)?NativeBuilderFormation.matter().defaultBlockState():Blocks.AIR.defaultBlockState(),3);}actor.setPos(ROOT.getX()+face.getStepX()*4+.5,120,ROOT.getZ()+face.getStepZ()*4+.5);}
 private static void clear(){for(var p:BlockPos.betweenClosed(ROOT.offset(-6,-1,-6),ROOT.offset(6,6,6)))if(NativeBuilderFormation.loaded(world,p))world.setBlock(p,Blocks.AIR.defaultBlockState(),3);for(var e:world.getEntitiesOfClass(ItemEntity.class,new AABB(ROOT).inflate(12)))e.discard();}
 private static void fillOwnership(){root().configureRedstone(1,ChannelList.EMPTY);root().setItem(0,new ItemStack(NativeBuilderFormation.matter(),3));root().setItem(1,NativeSynthesisRuntime.result(1));var battery=new ItemStack(NativeRackBindings.BATTERY);NativeBatteryModule.transfer(battery,4321,false,true);root().setItem(2,battery);root().setItem(3,com.vandorlabs.neoforge.credentials.NativeCredentialItems.dataChip(0));owned=root().saveWithFullMetadata(world.registryAccess());}
 private static void negativeData(){var saved=root().saveWithoutMetadata(world.registryAccess());for(int kind=0;kind<11;kind++){var bad=saved.copy();var list=new ListTag();var row=new CompoundTag();row.putInt("Slot",0);row.put("Stack",new ItemStack(NativeBuilderFormation.matter()).save(world.registryAccess()));list.add(row);var inv=new CompoundTag();inv.put("Items",list);bad.put("Inventory",inv);switch(kind){case 0->row.remove("Slot");case 1->row.putString("Slot","0");case 2->list.add(row.copy());case 3->{var wrong=new ListTag();wrong.add(StringTag.valueOf("bad"));inv.put("Items",wrong);}case 4->row.getCompound("Stack").putString("id","missing:unknown");case 5->row.getCompound("Stack").putInt("count",0);case 6->row.getCompound("Stack").putInt("count",1000);case 7->row.putInt("Slot",2);case 8->{var components=new CompoundTag();components.put("missing:unknown",new CompoundTag());bad.put("components",components);}case 9->{var attachments=new CompoundTag();attachments.put("missing:unknown",new CompoundTag());bad.put("neoforge:attachments",attachments);}case 10->{var caps=new CompoundTag();caps.putInt("foreign:owned",17);bad.put("ForgeCaps",caps);}}var tile=new NativeBuilderEntity(ROOT,root().getBlockState());tile.setLevel(world);tile.loadWithComponents(bad,world.registryAccess());need(tile.inert(),"typed malformed inventory becomes inert kind="+kind);var retained=tile.saveWithoutMetadata(world.registryAccess());for(var key:bad.getAllKeys())need(Objects.equals(bad.get(key),retained.get(key)),"failed raw original retained "+kind+"/"+key);}}
 @SuppressWarnings("unchecked") private static void journalNegatives(boolean formation)throws Exception{var familyField=NativeBuilderTransactions.class.getDeclaredField("FAMILY");familyField.setAccessible(true);var family=(NativeCellTransactions.Family<?>)familyField.get(null);var queuedField=NativeBuilderTransactions.class.getDeclaredField("QUEUED");queuedField.setAccessible(true);var queued=(Map<ServerLevel,Map<BlockPos,?>>)queuedField.get(null);var pending=queued.get(world).get(ROOT);var getter=pending.getClass().getDeclaredMethod("request");getter.setAccessible(true);var request=(NativeCellTransactions.Request)getter.invoke(pending);var header=new NativeCellTransactions.Header(UUID.randomUUID(),family.id(),ROOT,root().key(),actor.getUUID(),false);var tag=request.recipe();var after=request.after();if(formation)tag.putByte("Form",(byte)2);else{var facing=Direction.from3DDataValue(tag.getInt("Facing")).getClockWise();var d=root().design();var positions=new HashSet<BlockPos>();for(var c:request.before().cells())positions.add(c.pos);boolean found=false;for(int x:new int[]{0,d.width()-1})for(int z:new int[]{0,d.depth()-1}){var layout=new com.vandorlabs.machines.CoreLayout(d.width(),d.height(),d.depth(),facing.get3DDataValue(),x,z);var candidate=new HashSet<BlockPos>();for(int a=0;a<d.width();a++)for(int y=0;y<d.height();y++)for(int b=0;b<d.depth();b++)candidate.add(ROOT.offset(layout.x(a,b),y,layout.z(a,b)));if(!candidate.equals(positions))continue;tag.putInt("Facing",facing.get3DDataValue());tag.putInt("X",x);tag.putInt("Z",z);var changed=new ArrayList<NativeRawCell>();for(var c:request.after().cells()){if(!c.pos.equals(ROOT)){changed.add(c);continue;}var raw=c.tile();raw.putInt("ConstructorCoreX",x);raw.putInt("ConstructorCoreZ",z);changed.add(new NativeRawCell(ROOT,c.state.setValue(NativeBuilderCoreBlock.FACING,facing),raw,65536));}after=new NativeTransferSnapshot(changed,new NativeTransferSnapshot.Limits(128,65536,1024*1024,4*1024*1024));found=true;break;}need(found,"symmetric edited journal reproducer shares exact world footprint");}boolean rejected=false;try{family.readRecipe(world.registryAccess(),header,request.before(),after,Set.copyOf(request.held()),tag);}catch(IllegalArgumentException expected){rejected=true;}need(rejected,formation?"noncanonical Form byte2 refused":"symmetric wrong saved facing/corner journal refused");if(!formation)recipeStorageControls(family,header,request);}
 private static void recipeStorageControls(NativeCellTransactions.Family<?> family,NativeCellTransactions.Header header,NativeCellTransactions.Request request){for(int kind=0;kind<5;kind++){var cells=new ArrayList<NativeRawCell>();boolean mutated=false;for(var cell:request.before().cells()){var raw=cell.tile();if(raw!=null){raw.putBoolean("keepPacked",false);raw.put("NeoForgeData",new CompoundTag());if(!mutated&&cell.state.getBlock() instanceof NativeBuilderPartBlock){mutated=true;if(kind==1)raw.putBoolean("keepPacked",true);else if(kind==2)raw.putInt("keepPacked",0);else if(kind==3)raw.putUUID("BuilderOwner",UUID.randomUUID());else if(kind==4)raw.getCompound("NeoForgeData").putInt("ForeignOwnership",1);}}cells.add(new NativeRawCell(cell.pos,cell.state,raw,65536));}var before=new NativeTransferSnapshot(cells,new NativeTransferSnapshot.Limits(128,65536,1024*1024,4*1024*1024));boolean accepted=true;try{family.readRecipe(world.registryAccess(),header,before,request.after(),Set.copyOf(request.held()),request.recipe());}catch(IllegalArgumentException expected){accepted=false;}need(accepted==(kind==0),"recipe storage metadata projection only harmless false/empty, true/wrongtype/owner/nonempty stay rejected kind="+kind);}}
 private static void storageMetadataControls(){var original=root().saveWithFullMetadata(world.registryAccess());var harmless=original.copy();harmless.putBoolean("keepPacked",false);harmless.put("NeoForgeData",new CompoundTag());need(NativeBuilderData.sameSnapshots(original,harmless),"loaded vanilla false storage hint and empty platform envelope are equivalent");for(int kind=0;kind<5;kind++){var changed=harmless.copy();switch(kind){case 0->changed.putBoolean("keepPacked",true);case 1->changed.putInt("keepPacked",0);case 2->{var invalidItems=new ListTag();invalidItems.add(new CompoundTag());changed.getCompound("Inventory").put("Items",invalidItems);}case 3->changed.getCompound("Synthesis").putInt("Work",1);case 4->changed.putUUID("BuilderKey",UUID.randomUUID());}need(!NativeBuilderData.sameSnapshots(original,changed),"storage normalization never ignores true/wrongtype/private inventory/work/identity kind="+kind);}var part=(NativeBuilderPartEntity)world.getBlockEntity(root().cell(0,1,0));var originalPart=part.saveWithFullMetadata(world.registryAccess());var foreign=originalPart.copy();foreign.putBoolean("keepPacked",false);foreign.putUUID("BuilderOwner",UUID.randomUUID());need(!NativeBuilderData.sameSnapshots(originalPart,foreign),"BuilderOwner remains exact despite harmless storage hint");}
 private static NativeBuilderEntity firstLoadSignals(NativeBuilderEntity owner){
  final int channel=211004;var sourcePos=ROOT.west(4);world.setBlock(sourcePos,NativeRackBindings.BLOCK.defaultBlockState(),3);final var sourceTile=world.getBlockEntity(sourcePos);var source=new com.vandorlabs.neoforge.redstone.NativeChannelMember(){public net.minecraft.world.level.block.entity.BlockEntity channelBlockEntity(){return sourceTile;}public ChannelList getRedstoneChannels(){return ChannelList.EMPTY;}public ChannelList transmittedChannels(){return ChannelList.of(channel);}public int transmissionRange(){return 0;}public int localSignalLevel(int c){return 15;}public void setChannelLevel(int ignored){}};com.vandorlabs.neoforge.redstone.NativeRedstoneChannels.queueRegistration(source);com.vandorlabs.neoforge.redstone.NativeRedstoneChannels.flush(world);
  for(boolean wireless:new boolean[]{false,true}){if(!wireless)world.setBlock(ROOT.west(),Blocks.REDSTONE_BLOCK.defaultBlockState(),3);owner.configureRedstone(2,wireless?ChannelList.of(channel):ChannelList.EMPTY);owner.setItem(0,new ItemStack(NativeBuilderFormation.matter()));var battery=new ItemStack(NativeRackBindings.BATTERY);NativeBatteryModule.transfer(battery,1000,false,true);owner.setItem(2,battery);var saved=owner.saveWithFullMetadata(world.registryAccess());var loaded=new NativeBuilderEntity(ROOT,owner.getBlockState());loaded.setLevel(world);loaded.loadWithComponents(saved,world.registryAccess());world.removeBlockEntity(ROOT);world.setBlockEntity(loaded);loaded.onLoad();NativeBuilderEntity.tick(world,ROOT,loaded.getBlockState(),loaded);need(loaded.getItem(0).getCount()==1&&loaded.batteryEnergy()==1000&&!loaded.processing()&&loaded.buildProgress()==0,"first tick before deferred readiness cannot spend Matter/RF OFF-under-ON wireless="+wireless);com.vandorlabs.neoforge.redstone.NativeRedstoneChannels.flush(world);need(!loaded.enabled(),"deferred physical/channel registration settles before enabling OFF mode wireless="+wireless);NativeBuilderEntity.tick(world,ROOT,loaded.getBlockState(),loaded);need(loaded.getItem(0).getCount()==1&&loaded.batteryEnergy()==1000&&!loaded.processing(),"settled disabled machine remains exact before gameplay wireless="+wireless);loaded.removeItem(0,1);loaded.removeItem(2,1);if(!wireless)world.setBlock(ROOT.west(),Blocks.AIR.defaultBlockState(),3);owner=loaded;}
  com.vandorlabs.neoforge.redstone.NativeRedstoneChannels.unregister(source);world.setBlock(sourcePos,Blocks.AIR.defaultBlockState(),3);owner.configureRedstone(0,ChannelList.EMPTY);return owner;
 }
 private static void compatibilityAndPublic(){var original=root().saveWithoutMetadata(world.registryAccess());original.putString("BuilderUnknown","private unknown normal field");var module=new ItemStack(NativeRackBindings.BATTERY);NativeBatteryModule.transfer(module,5555,false,true);var privateData=new CompoundTag();privateData.putString("Secret","private module/chip payload");module.set(net.minecraft.core.component.DataComponents.CUSTOM_DATA,net.minecraft.world.item.component.CustomData.of(merge(NativeRackData.data(module),privateData)));var privateItems=new ItemStack[]{new ItemStack(NativeBuilderFormation.matter(),3),ItemStack.EMPTY,module,com.vandorlabs.neoforge.credentials.NativeCredentialItems.dataChip(2)};original.put("Inventory",NativeBuilderData.save(privateItems,world.registryAccess()));var detached=new NativeBuilderEntity(ROOT,root().getBlockState());detached.setLevel(world);detached.loadWithComponents(original,world.registryAccess());need(!detached.inert()&&detached.saveWithoutMetadata(world.registryAccess()).getString("BuilderUnknown").equals("private unknown normal field"),"unknown normal fields preserved without losing valid ownership");var publicTag=detached.getUpdateTag(world.registryAccess());need(!publicTag.contains("Inventory")&&!publicTag.contains("Items")&&!publicTag.contains("BuilderUnknown")&&!publicTag.toString().contains("Secret")&&!publicTag.toString().contains("private module"),"public updates contain only visual item fields and no private inventory/custom data");need(publicTag.getInt("BatteryCharge")==5555&&publicTag.getInt("ChipCompany")==2,"public progress/module/chip summaries remain sufficient");
  var partPos=root().cell(0,1,0);var part=(NativeBuilderPartEntity)world.getBlockEntity(partPos);var valid=part.saveWithoutMetadata(world.registryAccess());for(int kind=0;kind<4;kind++){var bad=valid.copy();switch(kind){case 0->bad.remove("ConstructorOwner");case 1->bad.putString("ConstructorOwner","wrong");case 2->bad.putLong("ConstructorOwner",new BlockPos(17,0,0).asLong());case 3->bad.putLong("ConstructorOwner",BlockPos.ZERO.asLong());}var loaded=new NativeBuilderPartEntity(partPos,part.getBlockState());loaded.setLevel(world);loaded.loadWithComponents(bad,world.registryAccess());need(loaded.inert()&&loaded.root()==null,"malformed part reference remains inert kind="+kind);var saved=loaded.saveWithoutMetadata(world.registryAccess());for(var name:bad.getAllKeys())need(Objects.equals(bad.get(name),saved.get(name)),"failed part original retained "+name);}
  var outside=ROOT.east(6);world.setBlock(outside,NativeBuilderBindings.PART.defaultBlockState(),3);var foreign=(NativeBuilderPartEntity)world.getBlockEntity(outside);var reference=new CompoundTag();reference.putLong("ConstructorOwner",ROOT.subtract(outside).asLong());reference.putUUID("BuilderOwner",root().key());foreign.loadWithComponents(reference,world.registryAccess());need(!foreign.inert()&&NativeBuilderBindings.ownerAt(world,outside)==null,"valid bounded offset outside actual footprint cannot bind a foreign core");need(!world.setBlock(outside,Blocks.AIR.defaultBlockState(),3),"foreign reserved cell refuses non-player replacement without inventing a root");var beforeRoot=root().saveWithFullMetadata(world.registryAccess());need(!world.setBlock(ROOT,Blocks.AIR.defaultBlockState(),3),"command/setBlock cannot remove formed owner");need(!world.destroyBlock(ROOT,true,actor),"non-player destroyBlock refuses before native loot");net.minecraft.world.level.block.Block.dropResources(root().getBlockState(),world,ROOT,root(),actor,actor.getMainHandItem());need(world.getEntitiesOfClass(ItemEntity.class,new AABB(ROOT).inflate(8)).isEmpty(),"direct protected Core loot cannot mint a duplicate owner");var explosion=world.explode(null,ROOT.getX()+.5,ROOT.getY()+.5,ROOT.getZ()+.5,2f,Level.ExplosionInteraction.BLOCK);need(!explosion.getToBlow().contains(ROOT)&&world.getBlockState(ROOT).getValue(NativeBuilderCoreBlock.FORMED),"actual explosion filters protected core");need(beforeRoot.equals(root().saveWithFullMetadata(world.registryAccess())),"refused set/destroy/explosion preserve exact one core owner");checkFrame(true);need(world.getEntitiesOfClass(ItemEntity.class,new AABB(ROOT).inflate(8)).isEmpty(),"refused removals create no loose duplicates");
 }
 private static CompoundTag merge(CompoundTag a,CompoundTag b){a.merge(b);return a;}
 private static void checkFrame(boolean formed){var owner=root();var d=owner.design();for(int x=0;x<d.width();x++)for(int y=0;y<d.height();y++)for(int z=0;z<d.depth();z++){var at=owner.cell(x,y,z);if(at.equals(ROOT))continue;if(!formed&&phase().endsWith("owned")&&at.equals(ROOT.south())){need(world.isEmptyBlock(at),"unformed owned recovery preserves already incomplete frame");continue;}if(formed)need(world.getBlockEntity(at) instanceof NativeBuilderPartEntity p&&p.owner().equals(ROOT),"exact owned reservation "+at);else need(owner.layout().frame(x,y,z)?world.getBlockState(at).is(NativeBuilderFormation.matter()):world.isEmptyBlock(at),"restored original frame "+at);}}
 @SubscribeEvent public static void tick(ServerTickEvent.Post e){if(!enabled()||world==null)return;try{if(++ticks>6000)throw new AssertionError("Builder async timeout");var owner=world.getBlockEntity(ROOT) instanceof NativeBuilderEntity r?r:null;if(stage>=300){legacyTick(owner);return;}if(stage>=200){drives(owner);return;}if(stage>=100){gantry(owner);return;}if(stage==95){if(owner==null||!owner.available()||!owner.synthesisReady())return;coldExport(false);return;}if(stage==70){if(!NativeSynthesisExportEdgeChecks.tick(world,actor))return;System.out.println("[vandorlabs][builder-runtime] PASS focused800ticks/16000RF/workingPause/queuedChestExport/coldReload/unloadedTarget/menu/bank checks="+checks);world=null;e.getServer().halt(false);return;}if(stage==90)return;if(stage==80){if(owner==null||!owner.available()||!owner.formed())return;fillOwnership();saveExpected(true);armCrash();need(!actor.gameMode.destroyBlock(ROOT),"real Survival core break defers vanilla removal");need(root().transactionBlocked()&&world.getBlockState(ROOT).getValue(NativeBuilderCoreBlock.FORMED),"queued break retains original source until durable");stage=90;return;}
  if(stage==0&&Boolean.getBoolean("vandorlabs.builderProductionOnly")){need(NativeBuilderTransactions.request(root(),actor,true),"focused production async formation");stage=10;return;}
  if(stage==0){int design=caseIndex/16;Direction face=Direction.from2DDataValue((caseIndex/4)%4);setup(design,face,caseIndex%4);need(NativeBuilderTransactions.request(root(),actor,true),"actual async formation admitted");need(!root().available(),"queued preparation freezes ownership without rejecting request");if(caseIndex==0)journalNegatives(true);stage=1;return;}
  if(stage==1){if(owner==null||!owner.available()||!owner.formed())return;if(caseIndex==0)NativeBuilderQueueChecks.empty();checkFrame(true);need(owner.layout().cornerX==desiredX&&owner.layout().cornerZ==desiredZ,"auto-discovered actual placed bottom corner");var roundtrip=new NativeBuilderEntity(ROOT,owner.getBlockState());roundtrip.setLevel(world);roundtrip.loadWithComponents(owner.saveWithFullMetadata(world.registryAccess()),world.registryAccess());need(!roundtrip.inert()&&roundtrip.pivot().equals(owner.pivot())&&roundtrip.cell(0,0,0).equals(owner.cell(0,0,0)),"saved corner and emitted origin roundtrip each layout");need(NativeBuilderTransactions.request(owner,actor,false),"actual async dismantle admitted");if(caseIndex==0)journalNegatives(false);stage=2;return;}
  if(stage==2){if(owner==null||!owner.available()||owner.formed())return;checkFrame(false);need(actor.gameMode.destroyBlock(ROOT),"empty unformed Core uses actual normal Survival mining");var ordinary=world.getEntitiesOfClass(ItemEntity.class,new AABB(ROOT).inflate(3));need(ordinary.size()==1&&ordinary.getFirst().getItem().is(NativeBuilderBindings.CORES.get(owner.design().id()).asItem())&&ordinary.getFirst().getItem().getCount()==1,"all3 empty unformed Core loot tables return exactly one proper-tool item");if(++caseIndex<48){stage=0;return;}setup(0,Direction.NORTH);need(NativeBuilderTransactions.request(root(),actor,true),"production frame queued");stage=10;return;}
  if(stage==10){if(owner==null||!owner.available()||!owner.formed())return;if(phase().equals("export-save")){coldExport(true);return;}owner=firstLoadSignals(owner);storageMetadataControls();compatibilityAndPublic();NativeBuilderMenuChecks.run(actor,owner);exportChecks(owner);owner=root();need(owner.available()&&!owner.inert(),"export fixture reset leaves an installed production owner");startRecipe(owner);stage=11;return;}
  if(stage==11){for(int i=0;i<32&&owner.buildProgress()<=.4;i++)productionTick(owner);if(owner.processing()&&owner.buildProgress()>.4&&owner.activation()==0){owner.configureRedstone(1,ChannelList.EMPTY);paused=owner.buildProgress();stageAt=ticks;stage=12;return;}return;}
  if(stage==12){need(owner.buildProgress()==paused&&!owner.working(),"off pauses exact progress and lasers");if(ticks-stageAt<5)return;var raw=owner.saveWithFullMetadata(world.registryAccess());var duplicate=new NativeBuilderEntity(ROOT,owner.getBlockState());duplicate.setLevel(world);duplicate.loadWithComponents(raw,world.registryAccess());NativeBuilderEntity.tick(world,ROOT,duplicate.getBlockState(),duplicate);need(raw.equals(duplicate.saveWithFullMetadata(world.registryAccess())),"stale uninstalled owner cannot spend input/RF/work");need(!duplicate.inert()&&duplicate.processing()&&duplicate.buildProgress()==paused,"active saved work reloads exactly");owner.configureRedstone(0,ChannelList.EMPTY);stage=13;return;}
  if(stage==13){for(int i=0;i<32&&owner.processing();i++)productionTick(owner);if(owner.processing()||owner.getItem(1).isEmpty())return;need(ItemStack.matches(owner.getItem(1),NativeSynthesisRuntime.result(recipe))&&owner.batteryEnergy()==4000,"three selected outputs exact16000RF charge cost recipe="+recipe);owner.removeItem(1,1);if(++recipe<3){startRecipe(owner);stage=11;return;}owner.setItem(2,ItemStack.EMPTY);owner.setItem(NativeBuilderEntity.batterySlot(1),ItemStack.EMPTY);owner.configureRedstone(1,ChannelList.EMPTY);var cap=world.getCapability(net.neoforged.neoforge.capabilities.Capabilities.EnergyStorage.BLOCK,owner.powerPort(),owner.facing().getOpposite());owner.setItem(2,new ItemStack(NativeRackBindings.BATTERY));need(cap==null||cap.receiveEnergy(20,false)==20,"valid source can charge paused empty module");owner.configureRedstone(0,ChannelList.EMPTY);var battery=new ItemStack(NativeRackBindings.BATTERY);NativeBatteryModule.transfer(battery,4000,false,true);owner.setItem(2,battery);owner.setItem(0,new ItemStack(NativeBuilderFormation.matter()));sourceTicks=0;stage=20;return;}
  if(stage==20){
   var cap=world.getCapability(net.neoforged.neoforge.capabilities.Capabilities.EnergyStorage.BLOCK,owner.powerPort(),owner.facing().getOpposite());
   need(cap!=null,"formed back power port");
   for(int i=0;i<32;i++){
    if(i!=0)productionTick(owner);
    need(cap.receiveEnergy(20,false)==20,"real cable after machine tick replenishes module");
    sourceTicks++;
    if(!owner.processing())break;
   }
   if(owner.processing())return;
   need(sourceTicks>=com.vandorlabs.machines.SynthesisRecipePolicy.CORE_TICKS&&owner.batteryEnergy()==4000,"20 RF work budget independent of machine/source order");
   owner.removeItem(1,1);if(Boolean.getBoolean("vandorlabs.builderProductionOnly")){stage=70;return;}fillOwnership();need(!actor.gameMode.destroyBlock(owner.itemPort()),"real Survival reserved-cell break deferred");need(owner.transactionBlocked(),"part mining remains queued through async prepare");stage=30;return;}
  if(stage==30){if(owner==null||!owner.available()||owner.formed())return;need(world.isEmptyBlock(owner.itemPort()),"mined original frame member omitted");need(owner.getItem(0).getCount()==3&&owner.getItem(1).is(NativeSynthesisRuntime.result(1).getItem())&&owner.batteryEnergy()==4321&&!owner.getItem(3).isEmpty(),"part teardown preserves all core-owned contents exactly");var drops=world.getEntitiesOfClass(ItemEntity.class,new AABB(ROOT).inflate(8));need(drops.size()==1&&drops.getFirst().getItem().is(NativeBuilderFormation.matter().asItem())&&drops.getFirst().getItem().getCount()==1,"one native matter drop for mined frame cell");for(var drop:drops)drop.discard();world.setBlock(owner.itemPort(),NativeBuilderFormation.matter().defaultBlockState(),3);need(NativeBuilderTransactions.request(owner,actor,true),"repair reassembly reuses retained ownership");stage=31;return;}
  if(stage==31){if(owner==null||!owner.available()||!owner.formed())return;need(!actor.gameMode.destroyBlock(ROOT),"real Survival core break queues durable loot");stage=32;return;}
  if(stage==32||stage==33){if(!world.isEmptyBlock(ROOT)||NativeTransferHolds.active(world))return;checkOwnedDrops();if(stage==32){setup(0,Direction.NORTH);world.setBlock(ROOT.south(),Blocks.AIR.defaultBlockState(),3);fillOwnership();need(!root().formed()&&root().ownsRecovery(),"unformed dismantled core still owns private inventories");need(!world.setBlock(ROOT,Blocks.AIR.defaultBlockState(),3)&&!world.destroyBlock(ROOT,true,actor),"unformed owned core refuses non-player removal");need(!actor.gameMode.destroyBlock(ROOT),"actual Survival unformed owned mining queues durable single-cell receipt despite incomplete frame");stage=33;return;}System.out.println("[vandorlabs][builder-runtime] PASS checks="+checks+" all3/all4Facings/all4BottomCorners/48AsyncFormationDismantle/typedNBT/actualSurvivalMining/escrowDrops/unformedOwnedSingleCell/3Recipes/16000RF/pausedReload/tickOrder/nonplayerRefusal");world=null;e.getServer().halt(false);}

 }catch(Throwable bad){fail(bad);}}
 private static void checkOwnedDrops(){var drops=world.getEntitiesOfClass(ItemEntity.class,new AABB(ROOT).inflate(8));int core=0,matter=0,output=0,battery=0,chip=0;for(var drop:drops){var stack=drop.getItem();if(stack.is(NativeBuilderBindings.CORES.get("synthesis_tower").asItem()))core+=stack.getCount();else if(stack.is(NativeBuilderFormation.matter().asItem()))matter+=stack.getCount();else if(stack.is(NativeSynthesisRuntime.result(1).getItem()))output+=stack.getCount();else if(NativeBatteryModule.valid(stack)){battery+=stack.getCount();need(NativeBatteryModule.energy(stack)==4321,"dropped physical module charge exact");}else if(com.vandorlabs.neoforge.credentials.NativeCredentialItems.accepts(stack,true))chip+=stack.getCount();}need(core==1&&matter==3&&output==1&&battery==1&&chip==1,"mining gives exactly one core and each owned input/output/module/chip");}
 private static void coldExport(boolean save){
  var owner=root();var at=owner.itemPort().relative(owner.facing().getCounterClockWise());
  if(save){owner.configureRedstone(1,ChannelList.EMPTY);owner.setItem(0,new ItemStack(NativeBuilderFormation.matter(),2));owner.setItem(1,NativeSynthesisRuntime.result(0));world.setBlock(at,Blocks.CHEST.defaultBlockState(),3);var chest=(net.minecraft.world.level.block.entity.ChestBlockEntity)world.getBlockEntity(at);for(int i=0;i<27;i++)chest.setItem(i,new ItemStack(Items.COBBLESTONE,64));world.getServer().saveEverything(true,true,true);System.out.println("[vandorlabs][builder-runtime] export-save PASS queuedOutput/fullChest/saved");var server=world.getServer();world=null;server.halt(false);return;}
  var chest=(net.minecraft.world.level.block.entity.ChestBlockEntity)world.getBlockEntity(at);need(!owner.processing()&&owner.getItem(0).getCount()==2&&owner.getItem(1).getCount()==1&&chest.getItem(0).is(Items.COBBLESTONE),"cold saved full chest retains queued owner");chest.setItem(0,new ItemStack(NativeSynthesisRuntime.result(0).getItem(),2));owner.configureRedstone(0,ChannelList.EMPTY);long now=world.getGameTime();for(int i=0;i<10;i++){((net.minecraft.world.level.storage.ServerLevelData)world.getLevelData()).setGameTime(++now);NativeBuilderEntity.tick(world,ROOT,owner.getBlockState(),owner);}need(chest.getItem(0).getCount()==3&&owner.getItem(1).isEmpty()&&owner.processing()&&owner.getItem(0).getCount()==1&&!owner.working(),"cold reload exports once then queues powerless next recipe without lasers");System.out.println("[vandorlabs][builder-runtime] export-read PASS coldQueuedOutput/chestExactly3/inputExactly1/noRepeatedExport");var server=world.getServer();world=null;server.halt(false);
 }
 private static void exportChecks(NativeBuilderEntity owner){
  var at=owner.itemPort().relative(owner.facing().getCounterClockWise());world.setBlock(at,Blocks.CHEST.defaultBlockState(),3);var chest=(net.minecraft.world.level.block.entity.ChestBlockEntity)world.getBlockEntity(at);
  for(int i=0;i<chest.getContainerSize();i++)chest.setItem(i,new ItemStack(Items.COBBLESTONE,64));
  owner.configureRedstone(0,ChannelList.EMPTY);owner.setItem(0,new ItemStack(NativeBuilderFormation.matter(),2));owner.setItem(1,NativeSynthesisRuntime.result(0));
  long time=world.getGameTime();for(int i=0;i<9;i++){((net.minecraft.world.level.storage.ServerLevelData)world.getLevelData()).setGameTime(++time);NativeBuilderEntity.tick(world,ROOT,owner.getBlockState(),owner);}
  need(!owner.processing()&&owner.getItem(0).getCount()==2&&!owner.getItem(1).isEmpty(),"full output inventory preserves queued matter");
  var saved=owner.saveWithFullMetadata(world.registryAccess());var reload=new NativeBuilderEntity(ROOT,owner.getBlockState());reload.setLevel(world);reload.loadWithComponents(saved,world.registryAccess());world.removeBlockEntity(ROOT);world.setBlockEntity(reload);reload.configureRedstone(0,ChannelList.EMPTY);reload.beforeChannelRegistration();chest.setItem(0,ItemStack.EMPTY);
  ((net.minecraft.world.level.storage.ServerLevelData)world.getLevelData()).setGameTime(++time);NativeBuilderEntity.tick(world,ROOT,reload.getBlockState(),reload);
  need(chest.getItem(0).getCount()==1&&chest.getItem(0).is(NativeSynthesisRuntime.result(0).getItem())&&reload.getItem(1).isEmpty()&&reload.processing()&&reload.getItem(0).getCount()==1,"reloaded output exports exactly once and queued next recipe starts");
  reload.configureRedstone(1,ChannelList.EMPTY);var clean=reload.saveWithFullMetadata(world.registryAccess());clean.getCompound("Synthesis").putBoolean("Active",false);clean.getCompound("Synthesis").putInt("Work",0);clean.getCompound("Synthesis").remove("WorkCost");reload.loadWithComponents(clean,world.registryAccess());reload.setItem(0,ItemStack.EMPTY);reload.configureRedstone(0,ChannelList.EMPTY);reload.beforeChannelRegistration();world.setBlock(at,Blocks.AIR.defaultBlockState(),3);
 }
 private static CompoundTag legacyChip;
 private static void legacySetup(){
  clear();stage=300;
  var design=NativeDriveDesign.LONGBURN;
  var block=NativeBuilderBindings.CORES.get(design.id);
  var state=block.defaultBlockState().setValue(NativeBuilderCoreBlock.FACING,Direction.WEST).setValue(NativeBuilderCoreBlock.FORMED,true);
  world.setBlock(ROOT,state,3);actor.setPos(27.5,120,31.5);
  var data=legacyWork("synthesis_tower",0,0);data.putInt("ConstructorCoreX",design.width-1);data.putInt("ConstructorCoreZ",0);
  data.putIntArray("MachineSurfaceTextures",new int[]{-1,-2});data.putBoolean("NavigationRedstone",true);
  data.putBoolean("NavigationAnimated",false);data.putInt("NavigationSpeed",2);
  data.putIntArray("RedstoneChannels",new int[]{17,29});data.putInt("RedstoneChannel",17);
  data.putBoolean("DriveSignalLevels",true);data.putBoolean("DriveInverted",true);
  data.putInt("DriveParticles",2);data.putInt("DriveParticleThreshold",9);data.putInt("DriveLevel",3);data.putInt("MachineActivity",2);
  data.putBoolean("ChipSlot",true);
  legacyChip=new CompoundTag();legacyChip.putString("id","vandorlabs:data_chip");
  legacyChip.putByte("Count",(byte)1);legacyChip.putShort("Damage",(short)3);
  var extra=new CompoundTag();extra.putIntArray("foreign:calibration",new int[]{19,42});legacyChip.put("ForgeCaps",extra);
  var chipTag=new CompoundTag();var display=new CompoundTag();display.putString("Name","Site Access Card");chipTag.put("display",display);
  chipTag.putLong("ExternalSequence",123456789L);legacyChip.put("tag",chipTag);data.put("InsertedToken",legacyChip.copy());
  data.putString("ExternalNote","Imported site controller");

  var registries=world.registryAccess();var owner=root();owner.loadWithComponents(data,registries);
  need(!owner.inert()&&owner.driveSettings().equals(new NativeDriveSettings(true,true,true,2,9,-1,-2,false,2))
      &&owner.getRedstoneChannels().equals(ChannelList.of(17,29)),"Forge drive fields import into canonical settings");
  need(com.vandorlabs.neoforge.credentials.NativeCredentialItems.legacyCredentialData(owner.getItem(3)).equals(legacyChip)
      &&owner.getItem(3).getHoverName().getString().equals("Site Access Card"),"placed Core owns one exact legacy chip including opaque caps and display");
  var canonical=owner.saveWithoutMetadata(registries);
  need(!canonical.contains("InsertedToken")&&!canonical.contains("RedstoneChannels")&&canonical.getString("ExternalNote").equals("Imported site controller"),"canonical inventory is sole chip owner and keeps unknown unrelated fields");
  var reload=new NativeBuilderEntity(ROOT,state);reload.loadWithComponents(canonical,registries);
  need(!reload.inert()&&ItemStack.matches(reload.getItem(3),owner.getItem(3)),"canonical root reload preserves exact migrated chip");
  for(int kind=0;kind<10;kind++){
   var bad=data.copy();
   switch(kind){
    case 0->bad.put("Inventory",canonical.getCompound("Inventory").copy());
    case 1->bad.getCompound("InsertedToken").putInt("Count",1);
    case 2->bad.getCompound("InsertedToken").putString("id","vandorlabs:access_card");
    case 3->bad.getCompound("InsertedToken").putByte("Count",(byte)2);
    case 4->bad.putInt("RedstoneChannel",19);
    case 5->bad.putByte("NavigationRedstone",(byte)2);
    case 6->bad.putIntArray("MachineSurfaceTextures",new int[]{-1,-2,-2});
    case 7->bad.putLong("TokenReturnToLeast",73);
    case 8->bad.putLong("TokenReturnToMost",73);
    case 9->bad.putIntArray("RedstoneChannels",new int[]{17,17,29});
   }
   var corrupt=new NativeBuilderEntity(ROOT,state);
   corrupt.loadWithComponents(bad,registries);
   need(corrupt.inert()&&corrupt.isEmpty()&&corrupt.saveWithoutMetadata(registries).equals(bad),
       "bad legacy drive retains complete original with no exposed chip, case "+kind);
  }
  var computerState=NativeBuilderBindings.CORES.get(NativeDriveDesign.SEYR_COMPUTER.id).defaultBlockState();
  var computerData=legacyWork("",0,0);computerData.putIntArray("MachineSurfaceTextures",new int[]{-1});
  computerData.putInt("MachineActivity",2);computerData.putInt("RedstoneChannel",83);
  var computer=new NativeBuilderEntity(ROOT,computerState);computer.loadWithComponents(computerData,registries);
  need(!computer.inert()&&computer.driveActivity()==2&&computer.driveSettings().display()==-1
      &&computer.getRedstoneChannels().equals(ChannelList.of(83))&&computer.isEmpty(),"Forge chipless computer activity/finish/single channel");
  computerData.put("InsertedToken",legacyChip.copy());computer.loadWithComponents(computerData,registries);
  need(computer.inert()&&computer.isEmpty()&&computer.saveWithoutMetadata(registries).equals(computerData),
      "impossible computer chip remains in the inert original");

  legacyFrame(owner);
  for(int unit=0;unit<LEGACY_PRODUCTION.size();unit++) productionImport(unit);
  for(int unit=0;unit<ACTIVE_PRODUCTION.size();unit++) activeProductionImport(unit);
 }
 private record LegacyProduction(String id,int forgeSelected,int forgeBuilding,int selected,int building) {}
 private static final List<LegacyProduction> LEGACY_PRODUCTION=List.of(
     new LegacyProduction("synthesis_tower",5,10,10,6),
     new LegacyProduction("assembly_press",4,1,0,2),
     new LegacyProduction("enclosed_gantry",7,4,7,4));
 private static BlockPos legacyProductionPos(int unit){return ROOT.above(12+unit*10);}
 private static CompoundTag legacyWork(String machine,int selected,int building){
  var data=new CompoundTag();var work=new CompoundTag();var inv=new CompoundTag();
  inv.putInt("Size",6);inv.put("Items",new ListTag());work.put("Inventory",inv);
  var recipes=com.vandorlabs.machines.BuilderRecipeCatalog.known(machine);
  work.putInt("Selected",selected);work.putString("SelectedRecipe",recipes.isEmpty()?"":recipes.get(selected));
  work.putInt("Building",building);work.putString("BuildingRecipe",recipes.isEmpty()?"":recipes.get(building));
  work.putBoolean("Active",false);work.putBoolean("Connected",false);work.putInt("Work",0);data.put("Synthesis",work);
  var sequences=new CompoundTag();for(int bay=0;bay<4;bay++)sequences.putLong("Bay"+bay,0);
  data.put("SynthesisBatterySequences",sequences);data.putInt("SynthesisActivation",0);
  return data;
 }
 private static CompoundTag legacyItem(String id,int count){
  var raw=new CompoundTag();raw.putString("id","vandorlabs:"+id);
  raw.putByte("Count",(byte)count);raw.putShort("Damage",(short)0);return raw;
 }
 private static ListTag legacyInventory(CompoundTag data){return data.getCompound("Synthesis").getCompound("Inventory").getList("Items",Tag.TAG_COMPOUND);}
 private static void legacySlot(ListTag inventory,int slot,CompoundTag raw){
  var entry=raw.copy();entry.putInt("Slot",slot);inventory.add(entry);
 }
 private static void fillImportedProduction(CompoundTag data,int unit){
  var inv=legacyInventory(data);
  legacySlot(inv,0,legacyItem(unit==1?"programmable_matter_ingot":"programmable_matter_block",3+unit));
  legacySlot(inv,1,unit==1?legacyChip:legacyItem(unit==0?"synthesis_tower_core":"landing_plate",1));
  for(int bay=0;bay<4;bay++){
   var battery=legacyItem("battery_module",1);var tag=new CompoundTag();tag.putInt("BatteryEnergy",700+bay*1100);
   tag.putString("Calibration","Bay "+bay);battery.put("tag",tag);
   legacySlot(inv,2+bay,battery);
  }
  data.put("InsertedToken",legacyChip.copy());
 }
 private static void productionImport(int unit){
  var expected=LEGACY_PRODUCTION.get(unit);var pos=legacyProductionPos(unit);
  var state=NativeBuilderBindings.CORES.get(expected.id).defaultBlockState()
      .setValue(NativeBuilderCoreBlock.FACING,Direction.NORTH).setValue(NativeBuilderCoreBlock.FORMED,true);
  world.setBlock(pos,state,3);var owner=(NativeBuilderEntity)world.getBlockEntity(pos);
  var data=legacyWork(expected.id,expected.forgeSelected,expected.forgeBuilding);
  data.putIntArray("MachineSurfaceTextures",new int[0]);data.putInt("MachineActivity",1);
  data.putIntArray("RedstoneChannels",new int[]{11,21});data.putInt("RedstoneChannel",11);
  data.putInt("SynthesisActivation",2);data.getCompound("SynthesisBatterySequences").putLong("Bay2",23);
  var note=new CompoundTag();note.putString("Site","andromeda");data.put("ExternalOpaque",note);
  if(unit==0){
   var digitize=data.copy();digitize.getCompound("Synthesis").putInt("Mode",1);
   var imported=new NativeBuilderEntity(pos,state);imported.loadWithComponents(digitize,world.registryAccess());
   need(!imported.inert()&&imported.digitizing()&&imported.isEmpty(),"empty Forge Digitize mode stays available");
  }
  fillImportedProduction(data,unit);
  owner.loadWithComponents(data,world.registryAccess());
  verifyProduction(owner,expected,unit);
  for(int kind=0;kind<13;kind++){
   var bad=data.copy();var work=bad.getCompound("Synthesis");var rows=legacyInventory(bad);
   switch(kind){
    case 0->work.putBoolean("Active",true);
    case 1->work.putInt("Work",1);
    case 2->rows.getCompound(0).putByte("Slot",(byte)0);
    case 3->work.getCompound("Inventory").putInt("Size",7);
    case 4->bad.getCompound("InsertedToken").putByte("Count",(byte)2);
    case 5->work.putString("SelectedRecipe","unknown");
    case 6->bad.put("Inventory",new CompoundTag());
    case 7->work.putByteArray("DigitizeChipHash",new byte[32]);
    case 8->rows.getCompound(1).putInt("Slot",0);
    case 9->rows.getCompound(2).getCompound("tag").put("Items",new ListTag());
    case 10->rows.getCompound(0).put("ForgeCaps",legacyChip.getCompound("ForgeCaps").copy());
    case 11->rows.getCompound(3).getCompound("tag").putInt("BatteryEnergy",10001);
    case 12->rows.getCompound(2).putString("id","othermod:foreign_battery");
   }
   var refused=new NativeBuilderEntity(pos,state);refused.loadWithComponents(bad,world.registryAccess());
   need(refused.inert()&&refused.isEmpty()&&refused.saveWithoutMetadata(world.registryAccess()).equals(bad),
       "unsupported production snapshot retains all raw fields "+expected.id+" case "+kind);
  }
  var unformed=new NativeBuilderEntity(pos,state.setValue(NativeBuilderCoreBlock.FORMED,false));
  unformed.loadWithComponents(data,world.registryAccess());
  need(unformed.inert()&&unformed.saveWithoutMetadata(world.registryAccess()).equals(data),"bounded formed-only import");
  legacyFrame(owner);
 }
 private record ForgeActive(String design,int building,int cost,int credit,boolean explicitCost,boolean refund) {}
 private static final List<ForgeActive> ACTIVE_PRODUCTION=List.of(
     new ForgeActive("synthesis_tower",5,16000,15987,true,false),
     new ForgeActive("assembly_press",0,8000,7973,false,false),
     new ForgeActive("enclosed_gantry",7,8000,125,false,true),
     new ForgeActive("synthesis_tower",0,8000,7987,false,false));
 private static BlockPos forgeActivePos(int unit){return ROOT.above(52+unit*10);}
 private static void activeProductionImport(int unit){
  var fixture=ACTIVE_PRODUCTION.get(unit);var pos=forgeActivePos(unit);
  var state=NativeBuilderBindings.CORES.get(fixture.design).defaultBlockState()
      .setValue(NativeBuilderCoreBlock.FACING,Direction.NORTH).setValue(NativeBuilderCoreBlock.FORMED,true);
  world.setBlock(pos,state,3);var owner=(NativeBuilderEntity)world.getBlockEntity(pos);
  var data=legacyWork(fixture.design,0,fixture.building);data.putInt("SynthesisActivation",1);
  var work=data.getCompound("Synthesis");work.putBoolean("Active",true);work.putInt("Work",fixture.credit);
  if(fixture.explicitCost)work.putInt("WorkCost",fixture.cost);
  var matter=fixture.design.equals("assembly_press")?"programmable_matter_ingot":"programmable_matter_block";
  legacySlot(legacyInventory(data),0,legacyItem(matter,3));
  owner.loadWithComponents(data,world.registryAccess());
  verifyActive(owner,fixture);
  for(int kind=0;kind<7;kind++){
   var bad=data.copy();var badWork=bad.getCompound("Synthesis");
   switch(kind){
    case 0->badWork.putInt("Work",fixture.cost);
    case 1->badWork.putInt("WorkCost",12345);
    case 2->badWork.putInt("Mode",1);
    case 3->badWork.put("ProductionTemplate",legacyItem(matter,1));
    case 4->badWork.remove("Work");
    case 5->legacySlot(legacyInventory(bad),1,legacyItem(fixture.design.equals("assembly_press")?"data_chip":
        fixture.design.equals("enclosed_gantry")?"landing_plate":"synthesis_tower_core",1));
    case 6->{badWork.putInt("Building",999);badWork.remove("BuildingRecipe");}
   }
   var rejected=new NativeBuilderEntity(pos,state);rejected.loadWithComponents(bad,world.registryAccess());
   need(rejected.inert()&&rejected.isEmpty()&&rejected.saveWithoutMetadata(world.registryAccess()).equals(bad),
       "ambiguous Forge active work keeps raw snapshot "+fixture.design+" case "+kind);
  }
  legacyFrame(owner);
 }
 private static void verifyActive(NativeBuilderEntity owner,ForgeActive fixture){
  var work=owner.saveWithoutMetadata(world.registryAccess()).getCompound("Synthesis");
  need(!owner.inert()&&owner.formed()&&owner.processing()&&owner.activation()==1
      &&owner.getItem(0).getCount()==3&&owner.getItem(1).isEmpty()&&owner.batteryEnergy()==0
      &&work.getInt("Work")==fixture.credit&&work.contains("WorkCost")==fixture.explicitCost
      &&(!fixture.explicitCost||work.getInt("WorkCost")==fixture.cost),"exact saved Forge job credit and remaining inventory "+fixture.design);
 }
 private static void verifyProduction(NativeBuilderEntity owner,LegacyProduction expected,int unit){
  var saved=owner.saveWithoutMetadata(world.registryAccess());var work=saved.getCompound("Synthesis");
  need(!owner.inert()&&owner.formed()&&owner.design().id().equals(expected.id)
      &&!owner.processing()&&owner.selectedDesign()==expected.selected&&work.getInt("Building")==expected.building
      &&owner.activation()==2&&owner.channels().equals(ChannelList.of(11,21))&&owner.batterySequence(2)==23
      &&!owner.digitizing()&&!work.contains("Inventory")
      &&saved.getCompound("ExternalOpaque").getString("Site").equals("andromeda"),
      "Forge idle production fields map by stable recipe and preserve external raw data "+expected.id);
  need(owner.getItem(0).getCount()==3+unit&&owner.getItem(1).getCount()==1
      &&owner.getItem(3).getCount()==1,"Forge physical input, output and inserted chip each keep their separate slot");
  need(com.vandorlabs.neoforge.credentials.NativeCredentialItems.legacyCredentialData(owner.getItem(3)).equals(legacyChip),
      "production chip retains exact legacy envelope");
  if(unit==1)need(com.vandorlabs.neoforge.credentials.NativeCredentialItems.legacyCredentialData(owner.getItem(1)).equals(legacyChip),
      "two separately owned matching chips retain output and mount independently");
  for(int bay=0;bay<4;bay++){
   var stack=owner.batteryStack(bay);
   var data=NativeRackData.data(stack);var original=data.getCompound(NativeRackData.LEGACY_ITEM);
   need(stack.getCount()==1&&NativeBatteryModule.energy(stack)==700+bay*1100
       &&original.getCompound("tag").getString("Calibration").equals("Bay "+bay),"Forge battery gains exact native bay and charge "+bay);
  }
 }
 private static void legacyFrame(NativeBuilderEntity owner){
  var design=owner.design();var root=owner.getBlockPos();var layout=owner.layout();var registries=world.registryAccess();
  for(int x=0;x<design.width();x++)for(int y=0;y<design.height();y++)for(int z=0;z<design.depth();z++){
   var at=owner.cell(x,y,z);if(at.equals(root))continue;
   world.setBlock(at,NativeBuilderBindings.PART.defaultBlockState(),3);
   var part=(NativeBuilderPartEntity)world.getBlockEntity(at);var offset=root.subtract(at);
   var raw=new CompoundTag();
   raw.putLong("ConstructorOwner",((long)offset.getX()&0x3ffffffL)<<38
       |((long)offset.getY()&0xfffL)<<26|((long)offset.getZ()&0x3ffffffL));
   raw.putBoolean("ConstructorMatter",layout.frame(x,y,z));
   part.loadWithComponents(raw,registries);part.setChanged();
   need(part.root()==owner&&part.saveWithoutMetadata(registries).equals(raw),"Forge packed reservation and frame bit "+at);
   var wrong=raw.copy();wrong.putBoolean("ConstructorMatter",!layout.frame(x,y,z));
   part.loadWithComponents(wrong,registries);need(part.root()==null,"contradictory Forge frame bit cannot resolve the owner");
   part.loadWithComponents(raw,registries);
  }
  owner.setChanged();
 }
 private static void legacyTick(NativeBuilderEntity owner){
  if(stage>=305){legacyActiveTick();return;}
  if(stage>=303){legacyProductionTick();return;}
  if(stage==300){
   if(owner==null||!owner.available())return;
   System.out.println("[vandorlabs][builder-runtime] PASS legacyDriveComputerProductionImport/exactChip/inertOriginal/legacyFrame checks="+checks);
   world.getServer().halt(false);world=null;return;
  }
  if(stage==302){
   if(owner==null||!owner.available())return;
   var registries=world.registryAccess();var design=owner.drive();
   need(design==NativeDriveDesign.LONGBURN&&owner.formed()&&owner.getRedstoneChannels().equals(ChannelList.of(17,29))
       &&owner.driveSettings().equals(new NativeDriveSettings(true,true,true,2,9,-1,-2,false,2)),"cold saved owner preserves imported settings");
   legacyChip=com.vandorlabs.neoforge.credentials.NativeCredentialItems.legacyCredentialData(owner.getItem(3));
   need(legacyChip.getCompound("ForgeCaps").getIntArray("foreign:calibration").length==2
       &&legacyChip.getCompound("tag").getLong("ExternalSequence")==123456789L
       &&legacyChip.getShort("Damage")==3&&owner.getItem(3).getCount()==1,"cold saved owner contains one complete legacy chip");
   for(int x=0;x<design.width;x++)for(int y=0;y<design.height;y++)for(int z=0;z<design.depth;z++){
    var at=owner.cell(x,y,z);if(at.equals(ROOT))continue;
    need(world.getBlockEntity(at) instanceof NativeBuilderPartEntity part&&part.root()==owner
        &&part.saveWithoutMetadata(registries).getBoolean("ConstructorMatter")==owner.layout().frame(x,y,z),
        "cold legacy reservation classification "+at);
   }
   need(!actor.gameMode.destroyBlock(ROOT)&&owner.transactionBlocked(),"Survival mining queues an asynchronous ownership journal");
   need(!world.isEmptyBlock(ROOT)&&!owner.getItem(3).isEmpty(),"original drive owns chip until durable commit");
   stage=301;return;
  }
  if(!world.isEmptyBlock(ROOT)||NativeTransferHolds.active(world))return;
  var drops=world.getEntitiesOfClass(ItemEntity.class,new AABB(ROOT).inflate(8));
  var chipDrops=drops.stream().map(ItemEntity::getItem)
      .filter(it->it.is(com.vandorlabs.neoforge.credentials.NativeCredentialItems.DATA_CHIP.get())).toList();
  need(chipDrops.size()==1&&chipDrops.getFirst().getCount()==1
      &&legacyChip.equals(com.vandorlabs.neoforge.credentials.NativeCredentialItems.legacyCredentialData(chipDrops.getFirst())),
      "asynchronous imported-Core break drops the exact chip once");
  need(drops.stream().map(ItemEntity::getItem).filter(it->it.is(NativeBuilderBindings.CORES.get(NativeDriveDesign.LONGBURN.id).asItem()))
      .mapToInt(ItemStack::getCount).sum()==1,"asynchronous imported-Core break drops one Core");
  stage=303;caseIndex=0;
 }
 private static List<net.minecraft.world.item.ItemStack> expectedProductionDrops;
 private static List<BlockPos> legacyMatter,legacyAir;
 private static void legacyProductionTick(){
  if(caseIndex==LEGACY_PRODUCTION.size()){stage=305;caseIndex=0;return;}
  var pos=legacyProductionPos(caseIndex);
  if(stage==303){
   if(!(world.getBlockEntity(pos) instanceof NativeBuilderEntity owner)||!owner.available())return;
   verifyProduction(owner,LEGACY_PRODUCTION.get(caseIndex),caseIndex);
   actor.setPos(pos.getX()+.5,pos.getY(),pos.getZ()-3.5);
   expectedProductionDrops=new ArrayList<>();expectedProductionDrops.add(new ItemStack(owner.getBlockState().getBlock().asItem()));
   for(int slot=0;slot<7;slot++) expectedProductionDrops.add(owner.getItem(slot).copy());
   legacyMatter=new ArrayList<>();legacyAir=new ArrayList<>();
   var layout=owner.layout();var design=owner.design();
   for(int x=0;x<design.width();x++)for(int y=0;y<design.height();y++)for(int z=0;z<design.depth();z++){
    var at=owner.cell(x,y,z);if(!at.equals(pos))(layout.frame(x,y,z)?legacyMatter:legacyAir).add(at);
   }
   need(!actor.gameMode.destroyBlock(pos)&&owner.transactionBlocked()&&owner.getItem(0).getCount()==3+caseIndex,
       "legacy production survival mining queues journal and retains input pending durability");
   stage=304;return;
  }
  if(!world.isEmptyBlock(pos)||NativeTransferHolds.active(world))return;
  for(var at:legacyMatter)need(world.getBlockState(at)==NativeBuilderFormation.matter().defaultBlockState(),"legacy frame Matter "+at);
  for(var at:legacyAir)need(world.isEmptyBlock(at),"legacy frame air "+at);
  var drops=world.getEntitiesOfClass(ItemEntity.class,new AABB(pos).inflate(7));
  var actual=new ArrayList<ItemStack>();
  for(var drop:drops)if(drop.getY()>=pos.getY()-2&&drop.getY()<pos.getY()+8)actual.add(drop.getItem());
  for(var expected:expectedProductionDrops){
   int expectedCount=expectedProductionDrops.stream().filter(it->ItemStack.isSameItemSameComponents(it,expected))
       .mapToInt(ItemStack::getCount).sum();
   int actualCount=actual.stream().filter(it->ItemStack.isSameItemSameComponents(it,expected)).mapToInt(ItemStack::getCount).sum();
   need(actualCount==expectedCount,"migrated production item dropped exact total "+expected);
  }
  need(actual.stream().allMatch(it->expectedProductionDrops.stream().anyMatch(expected->ItemStack.isSameItemSameComponents(it,expected))),
      "no extra migrated production item");
  stage=303;caseIndex++;
 }
 private static void legacyActiveTick(){
  if(caseIndex==ACTIVE_PRODUCTION.size()){
   System.out.println("[vandorlabs][builder-runtime] PASS legacyDriveProductionColdReload/legacyFrame/durableMine/exactChipDrop/exactActiveCredit/refund checks="+checks);
   world.getServer().halt(false);world=null;return;
  }
  var fixture=ACTIVE_PRODUCTION.get(caseIndex);var pos=forgeActivePos(caseIndex);
  if(stage==306){
   if(!world.isEmptyBlock(pos)||NativeTransferHolds.active(world))return;
   var drops=world.getEntitiesOfClass(ItemEntity.class,new AABB(pos).inflate(7));
   need(drops.stream().map(ItemEntity::getItem).filter(it->it.is(NativeBuilderFormation.matter().asItem()))
       .mapToInt(ItemStack::getCount).sum()==4,"cold active break returns three queued Matter and exactly one committed Matter");
   need(drops.stream().map(ItemEntity::getItem).filter(it->it.is(NativeBuilderBindings.CORES.get(fixture.design).asItem()))
       .mapToInt(ItemStack::getCount).sum()==1,"cold active break returns one Core");
   need(drops.stream().map(ItemEntity::getItem).mapToInt(ItemStack::getCount).sum()==5,"cold active refund creates no product");
   stage=305;caseIndex++;return;
  }
  if(!(world.getBlockEntity(pos) instanceof NativeBuilderEntity owner)||!owner.available()||!owner.synthesisReady())return;
  verifyActive(owner,fixture);
  if(fixture.refund){
   actor.setPos(pos.getX()+.5,pos.getY(),pos.getZ()-3.5);
   need(!actor.gameMode.destroyBlock(pos)&&owner.transactionBlocked(),"cold active refund queues real Survival Core break");
   stage=306;return;
  }
  var power=world.getCapability(net.neoforged.neoforge.capabilities.Capabilities.EnergyStorage.BLOCK,
      owner.powerPort(),owner.facing().getOpposite());
  need(power!=null&&power.getEnergyStored()==fixture.credit&&power.getMaxEnergyStored()==fixture.cost,
      "cold active job exposes exact prior RF and historical cost");
  owner.configureRedstone(0,ChannelList.EMPTY);
  int remaining=fixture.cost-fixture.credit;
  need(power.receiveEnergy(remaining-1,false)==remaining-1&&owner.processing()&&owner.getItem(1).isEmpty(),
      "cold active job remains uncommitted at one RF short");
  need(power.receiveEnergy(1,false)==1&&!owner.processing()&&owner.getItem(1).getCount()==1
      &&owner.getItem(0).getCount()==3,"exact final RF completes once without consuming another Matter");
  var expected=caseIndex==0?NativeBuilderProducts.result(NativeBuilderProducts.VECTOR_FOLD):
      caseIndex==1?NativeBuilderProducts.partResult(fixture.design,1):NativeBuilderProducts.result(0);
  need(ItemStack.matches(owner.getItem(1),expected),"cold job outputs building recipe rather than current selection");
  caseIndex++;
 }
 private static void drives(NativeBuilderEntity owner){
  if(stage>=202){driveRecipes(owner);return;}
  if(stage==201){
   if(!world.isEmptyBlock(ROOT)||NativeTransferHolds.active(world))return;
   var design=NativeDriveDesign.SUPPORTED.get(caseIndex);
   var drops=world.getEntitiesOfClass(ItemEntity.class,new AABB(ROOT).inflate(8));
   var core=NativeBuilderBindings.CORES.get(design.id).asItem();
   var expected=com.vandorlabs.neoforge.credentials.NativeCredentialItems.DATA_CHIP.get();
   need(drops.stream().filter(drop->drop.getItem().is(core)).mapToInt(drop->drop.getItem().getCount()).sum()==1
         &&drops.stream().filter(drop->drop.getItem().is(expected)).mapToInt(drop->drop.getItem().getCount()).sum()==(design.hasChip()?1:0),
        "formed machine mining returns one Core and only its owned chip when supported");
   if(++caseIndex==NativeDriveDesign.SUPPORTED.size()){
    setup(0,Direction.NORTH);need(NativeBuilderTransactions.request(root(),actor,true),"Tower async formation for drive core production");
    stage=202;return;
   }
   setup(3+caseIndex,caseIndex==1?Direction.WEST:Direction.EAST,caseIndex==1?1:3);need(NativeBuilderTransactions.request(root(),actor,true),"next drive async formation");stage=200;return;
  }
  if(owner==null||!owner.available()||!owner.formed()||!owner.synthesisReady())return;
  checkFrame(true);
  var drive=NativeDriveDesign.SUPPORTED.get(caseIndex);
  need(owner.drive()==drive&&owner.driveLevel()==(owner.maxwell()?0:15)&&!owner.production()&&owner.layout().cornerZ==desiredZ,
        "standalone drive activates after its deferred inputs and forms in selected orientation");
  if(drive.computer()){
   need(!NativeBuilderProducts.output("synthesis_tower","vandorlabs:"+drive.block),"computer cores keep Creative-only supply");
  }else{
   var output=NativeBuilderProducts.result(NativeBuilderProducts.LONGBURN+caseIndex);
   need(output.is(NativeBuilderBindings.CORES.get(drive.id).asItem()),"Tower uses the exact drive core item");
  }
  if(owner.maxwell()){
   need(owner.maxwellFuel()==0&&owner.maxwellThrustLevel(true)==0&&!drive.hasChip(), "empty Maxwell supplies no thrust or chip mount");
   need(!actor.gameMode.destroyBlock(ROOT)&&owner.transactionBlocked(), "Maxwell Core mining waits for durable publication");
   stage=201;
   return;
  }
  if(drive.manualActivity()){
   int first=drive.defaultActivity();
   need(owner.driveActivity()==first&&owner.cycleActivity()&&owner.driveActivity()==(first+1)%3
        &&owner.cycleActivity()&&owner.driveActivity()==(first+2)%3,"manual activity cycle starts with the Source default");
  }
  var menu=new NativeDriveMenu(NativeDriveBindings.MENU,77,owner,ROOT);
  actor.containerMenu=menu;
  var config=new NativeDriveSettings(true,false,drive.engine(),drive.exhaust?2:0,8,-1,-2,false,2);
  ChannelList bank=ChannelList.of(4197);
  var edit=new NativeDriveBindings.Edit(77,ROOT,config,bank);
  var bytes=new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(),world.registryAccess());
  try{
   NativeDriveBindings.Edit.CODEC.encode(bytes,edit);
   need(bytes.readableBytes()==29&&NativeDriveBindings.Edit.CODEC.decode(bytes).equals(edit),"bounded drive settings exact wire roundtrip");
  }finally{bytes.release();}
  need(!NativeDriveBindings.apply(actor,new NativeDriveBindings.Edit(76,ROOT,config,bank))
        &&!NativeDriveBindings.apply(actor,new NativeDriveBindings.Edit(77,ROOT.above(),config,bank)),
        "drive packet refuses stale window and wrong world owner");
  need(NativeDriveBindings.apply(actor,edit)&&owner.driveLevel()==0,"real guarded edit applies configured Redstone gate");
  if(drive.computer()){
   var keyboard=new NativeDriveSettings(true,false,false,0,8,-2,-1,false,2);
   need(!NativeDriveBindings.apply(actor,new NativeDriveBindings.Edit(77,ROOT,keyboard,bank)),"navigation computer rejects a nonexistent keyboard");
  }
  var front=owner.cell(0,0,0).relative(owner.facing());
  world.setBlock(front,Blocks.REDSTONE_BLOCK.defaultBlockState(),3);
  need(owner.driveLevel()==15,"loaded direct neighbor signal turns the standalone drive on");
  var inverted=new NativeDriveSettings(true,true,true,config.particles(),8,-1,-2,false,2);
  boolean applied=NativeDriveBindings.apply(actor,new NativeDriveBindings.Edit(77,ROOT,inverted,bank));
  need(drive.engine()?applied&&owner.driveLevel()==0:!applied&&owner.driveLevel()==15,
        "Engine inversion uses the actual high input and the Vector Fold refuses engine controls");
  world.setBlock(front,Blocks.AIR.defaultBlockState(),3);
  need(owner.driveLevel()==(drive.engine()?15:0),"removing physical input restores the configured no-signal state");
  var sourcePos=ROOT.below(3);
  world.setBlock(sourcePos,NativeRackBindings.BLOCK.defaultBlockState(),3);
  var tile=world.getBlockEntity(sourcePos);
  var source=new com.vandorlabs.neoforge.redstone.NativeChannelMember(){
   public net.minecraft.world.level.block.entity.BlockEntity channelBlockEntity(){return tile;}
   public ChannelList getRedstoneChannels(){return ChannelList.EMPTY;}
   public ChannelList transmittedChannels(){return bank;}
   public int transmissionRange(){return 0;}
   public int localSignalLevel(int channel){return 7;}
   public void setChannelLevel(int value){}
  };
  com.vandorlabs.neoforge.redstone.NativeRedstoneChannels.queueRegistration(source);
  com.vandorlabs.neoforge.redstone.NativeRedstoneChannels.flush(world);
  int expectedLevel=drive.engine()?8:15;
  var expectedConfig=drive.engine()?inverted:config;
  need(owner.driveLevel()==expectedLevel,"channel network uses inverted analogue or Array boolean activation");
  var chip=com.vandorlabs.neoforge.credentials.NativeCredentialItems.dataChip(2);
  need(owner.canPlaceItem(3,chip)==drive.hasChip(),"physical token storage only exists on the authored drive mount");
  owner.setItem(3,chip);
  need(owner.getItem(3).isEmpty()!=drive.hasChip(),"private inventory cannot install a chip in a computer");
  var saved=owner.saveWithoutMetadata(world.registryAccess());
  var copy=new NativeBuilderEntity(ROOT,owner.getBlockState());
  copy.loadWithComponents(saved,world.registryAccess());
  need(!copy.inert()&&copy.driveSettings().equals(expectedConfig)&&copy.channels().equals(bank)
       &&copy.driveActivity()==owner.driveActivity()&&ItemStack.matches(owner.getItem(3),copy.getItem(3))&&NativeBuilderData.sameSnapshots(saved,copy.saveWithoutMetadata(world.registryAccess())),
       "exact drive settings, channels and owned chip survive reload");
  var publicCopy=new NativeBuilderEntity(ROOT,owner.getBlockState());
  publicCopy.handleUpdateTag(owner.getUpdateTag(world.registryAccess()),world.registryAccess());
  need(!publicCopy.inert()&&publicCopy.driveLevel()==expectedLevel&&publicCopy.driveSettings().equals(expectedConfig),"client receives exact runtime appearance and settings");
  var bad=saved.copy();bad.getCompound("Drive").putInt("Threshold",16);
  copy.loadWithComponents(bad,world.registryAccess());
  need(copy.inert()&&bad.equals(copy.saveWithoutMetadata(world.registryAccess())),"malformed drive preserves exact original recovery data");
  com.vandorlabs.neoforge.redstone.NativeRedstoneChannels.unregister(source);
  world.setBlock(sourcePos,Blocks.AIR.defaultBlockState(),3);
  actor.containerMenu=actor.inventoryMenu;
  need(!actor.gameMode.destroyBlock(ROOT)&&owner.transactionBlocked(),"active drive mining waits for durable publication");
  stage=201;
 }
 private static int driveRecipeRf;
 private static void driveRecipes(NativeBuilderEntity owner){
  if(owner==null||!owner.available()||!owner.formed()||!owner.synthesisReady())return;
  if(stage==202){
   owner.selectDesign(NativeBuilderProducts.LONGBURN);
   need(owner.selectedDesign()==0,"drive choice refuses a missing catalog");
   owner.setItem(3,com.vandorlabs.neoforge.credentials.NativeChipTypes.variant("catalog"));
   recipe=NativeBuilderProducts.LONGBURN;
   beginDriveCore(owner);
   world.getServer().tickRateManager().setTickRate(100);
   stage=203;return;
  }
  if(owner.getItem(1).isEmpty()){
   var power=world.getCapability(net.neoforged.neoforge.capabilities.Capabilities.EnergyStorage.BLOCK,
           owner.powerPort(),owner.facing().getOpposite());
   need(power!=null&&power.receiveEnergy(1024,false)==20,"Tower retains 20 RF per tick for drive cores");
   driveRecipeRf+=20;
   if(driveRecipeRf==8000){
    var saved=owner.saveWithoutMetadata(world.registryAccess());
    var detached=new NativeBuilderEntity(ROOT,owner.getBlockState());
    detached.loadWithComponents(saved,world.registryAccess());
    need(saved.getCompound("Synthesis").getInt("WorkCost")==16000&&detached.processing()&&!detached.inert()
         &&detached.buildProgress()==.5F&&detached.buildDesign()==recipe,"drive core active job reload retains 16,000 RF cost and exact index");
   }
   return;
  }
  var expected=NativeBuilderProducts.result(recipe);
  need(driveRecipeRf==16000&&owner.getItem(0).isEmpty()&&ItemStack.matches(owner.getItem(1),expected),
       "one Matter Block and 16,000 RF yields the exact drive core");
  var publicCopy=new NativeBuilderEntity(ROOT,owner.getBlockState());
  publicCopy.handleUpdateTag(owner.getUpdateTag(world.registryAccess()),world.registryAccess());
  need(!publicCopy.inert()&&ItemStack.matches(publicCopy.getItem(1),expected),"drive core public representation");
  owner.removeItem(1,1);
  if(recipe<NativeBuilderProducts.MAX_TOWER_PRODUCT){recipe++;beginDriveCore(owner);return;}
  System.out.println("[vandorlabs][builder-runtime] PASS MaxwellMeridianOculusVectorFoldComputers asyncOwnership/physicalAndAnalogRedstone/staleMenu/exactConfig/opaqueFailure/oneChip/TowerCatalog16000RF checks="+checks);
  var server=world.getServer();world=null;server.halt(false);
 }
 private static void beginDriveCore(NativeBuilderEntity owner){
  driveRecipeRf=0;
  owner.selectDesign(recipe);
  need(owner.selectedDesign()==recipe,"Master activates the exact drive blueprint");
  owner.setItem(0,new ItemStack(NativeBuilderFormation.matter()));
 }
 private static int gantryEnergy;
 private static void gantry(NativeBuilderEntity owner){
  if(stage==102){
   if(!world.isEmptyBlock(ROOT)||NativeTransferHolds.active(world))return;
   var matter=NativeBuilderFormation.matter().asItem();
   var core=NativeBuilderBindings.CORES.get("enclosed_gantry").asItem();
   var drops=world.getEntitiesOfClass(ItemEntity.class,new AABB(ROOT).inflate(8));
   need(drops.stream().filter(drop->drop.getItem().is(matter)).mapToInt(drop->drop.getItem().getCount()).sum()==1
         &&drops.stream().filter(drop->drop.getItem().is(core)).mapToInt(drop->drop.getItem().getCount()).sum()==1,
         "active Gantry mining refunds its one escrowed Matter Block with its Core");
   System.out.println("[vandorlabs][builder-runtime] PASS Gantry tenProducts/8000RF/40RFPerTick/realPorts/partialReload/asyncMining checks="+checks);
   var server=world.getServer();world=null;server.halt(false);return;
  }
  if(owner==null||!owner.available()||!owner.formed()||!owner.synthesisReady())return;
  if(stage==100){
   checkFrame(true);
   need(owner.gantry()&&owner.layout().cornerX==desiredX&&owner.layout().cornerZ==desiredZ,"real rotated Gantry with opposite-corner core");
   if(!com.vandorlabs.neoforge.gear.NativeGearBindings.enabled()){
    var power=net.neoforged.neoforge.capabilities.Capabilities.EnergyStorage.BLOCK;
    need(!owner.production()&&world.getCapability(power,owner.powerPort(),Direction.DOWN)==null,
          "Gantry reserves its structure without exposing production while gear is disabled");
    for(int i=0;i<NativeBuilderProducts.PRESS.size();i++)need(NativeBuilderProducts.pressPartAvailable(i)==(i<3||i==5),
          "disabled rack hides only its two Press products: "+i);
    System.out.println("[vandorlabs][builder-runtime] PASS disabled parts retains Gantry structure and filters Press modules");
    var server=world.getServer();world=null;server.halt(false);return;
   }
   world.getServer().tickRateManager().setTickRate(100);
   need(!owner.setMode(1),"Digitize remains Tower-only");
   need(NativeBuilderProducts.GANTRY.equals(com.vandorlabs.machines.BuilderRecipeCatalog.known("enclosed_gantry")),
         "Gantry production matches all ten default blueprints");
   var edit=new NativeBuilderEdit(3,ROOT,0,NativeBuilderProducts.GANTRY.size()-1,ChannelList.EMPTY);
   var bytes=new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(),world.registryAccess());
   try{
    NativeBuilderPayload.CODEC.encode(bytes,new NativeBuilderPayload(edit));
    need(bytes.readableBytes()==15&&NativeBuilderPayload.CODEC.decode(bytes).edit().equals(edit),
          "last Gantry recipe retains the 15-byte selection packet");
   }finally{bytes.release();}
   var overflow=net.neoforged.neoforge.capabilities.Capabilities.EnergyStorage.BLOCK;
   need(owner.powerPort().equals(owner.cell(4,0,2))&&world.getCapability(overflow,owner.powerPort(),Direction.DOWN)!=null
         &&world.getCapability(overflow,owner.cell(2,0,2),Direction.DOWN)==null,"FE is exposed on both faces of the rear corner only");
   beginGantry(owner);stage=101;return;
  }
  if(owner.getItem(1).isEmpty()){
   var energy=world.getCapability(net.neoforged.neoforge.capabilities.Capabilities.EnergyStorage.BLOCK,
           owner.powerPort(),owner.facing().getOpposite());
   need(energy!=null&&energy.receiveEnergy(1024,false)==40&&energy.receiveEnergy(40,false)==0,
         "rear corner enforces shared 40 RF per tick across repeated supply calls");
   gantryEnergy+=40;
   if(gantryEnergy==4000){
    var saved=owner.saveWithoutMetadata(world.registryAccess());
    var loaded=new NativeBuilderEntity(ROOT,owner.getBlockState());
    loaded.loadWithComponents(saved,world.registryAccess());
    need(!loaded.inert()&&loaded.processing()&&loaded.buildProgress()==.5F&&loaded.buildDesign()==recipe
          &&NativeBuilderData.sameSnapshots(saved,loaded.saveWithoutMetadata(world.registryAccess())),
          "Gantry partial job retains its recipe and paid work "+recipe);
   }
   return;
  }
  var expected=NativeBuilderProducts.partResult("enclosed_gantry",recipe);
  need(!expected.isEmpty()&&!owner.processing()&&owner.getItem(0).isEmpty()&&gantryEnergy==8000
        &&ItemStack.matches(owner.getItem(1),expected),"exact Gantry item after one Matter Block and 8000 RF "+recipe);
  var loaded=new NativeBuilderEntity(ROOT,owner.getBlockState());
  var saved=owner.saveWithoutMetadata(world.registryAccess());
  loaded.loadWithComponents(saved,world.registryAccess());
  need(!loaded.inert()&&ItemStack.matches(loaded.getItem(1),expected),"exact output survives reload, including recipe index 5");
  var viewer=new NativeBuilderEntity(ROOT,owner.getBlockState());
  viewer.handleUpdateTag(owner.getUpdateTag(world.registryAccess()),world.registryAccess());
  need(!viewer.inert()&&ItemStack.matches(viewer.getItem(1),expected),"fixed Gantry public preview "+recipe);
  var output=world.getCapability(net.neoforged.neoforge.capabilities.Capabilities.ItemHandler.BLOCK,
          owner.itemPort(),owner.facing().getCounterClockWise());
  var extracted=output==null?ItemStack.EMPTY:output.extractItem(0,1,false);
  need(ItemStack.matches(extracted,expected),"outward port returns one exact product");
  if(extracted.is(com.vandorlabs.neoforge.coupler.NativeCouplerBindings.SQUARE.asItem())
     ||extracted.is(com.vandorlabs.neoforge.coupler.NativeCouplerBindings.SLAB.asItem()))
   com.vandorlabs.neoforge.coupler.NativeCouplerChecks.exercise(world,actor,ROOT.offset(8,8,-4),extracted,NativeBuilderRuntimeChecks::need);
  if(++recipe<NativeBuilderProducts.GANTRY.size()){beginGantry(owner);return;}
  recipe=0;beginGantry(owner);
  var energy=world.getCapability(net.neoforged.neoforge.capabilities.Capabilities.EnergyStorage.BLOCK,
          owner.powerPort(),Direction.DOWN);
  need(energy!=null&&energy.receiveEnergy(40,false)==40&&owner.processing()&&owner.getItem(0).isEmpty(),
        "lower feed starts the next job and takes ownership of its Matter Block");
  need(!actor.gameMode.destroyBlock(ROOT)&&owner.transactionBlocked(),"active Gantry mining queues one durable transfer");
  stage=102;
 }
 private static void beginGantry(NativeBuilderEntity owner){
  gantryEnergy=0;
  owner.selectDesign(recipe);
  var input=world.getCapability(net.neoforged.neoforge.capabilities.Capabilities.ItemHandler.BLOCK,
          owner.itemPort(),owner.facing().getOpposite());
  need(owner.selectedDesign()==recipe&&owner.getItem(3).isEmpty()&&input!=null
        &&input.insertItem(0,new ItemStack(NativeBuilderFormation.matter()),false).isEmpty(),
        "Gantry built-in recipe accepts Matter Block from rear feed without a chip");
 }
 private static int sourceTicks;
 private static void productionTick(NativeBuilderEntity owner){
  ((net.minecraft.world.level.storage.ServerLevelData)world.getLevelData()).setGameTime(world.getGameTime()+1);
  NativeBuilderEntity.tick(world,ROOT,owner.getBlockState(),owner);
 }
 private static void startRecipe(NativeBuilderEntity owner){owner.selectDesign(recipe);owner.configureRedstone(0,ChannelList.EMPTY);var b=new ItemStack(NativeRackBindings.BATTERY);NativeBatteryModule.transfer(b,10000,false,true);owner.setItem(2,b);owner.setItem(NativeBuilderEntity.batterySlot(1),b.copy());owner.setItem(0,new ItemStack(NativeBuilderFormation.matter()));}
 private static void saveExpected(boolean formed)throws java.io.IOException{var tag=new CompoundTag();tag.putBoolean("Formed",formed);tag.put("Core",root().saveWithFullMetadata(world.registryAccess()));NbtIo.writeCompressed(tag,expected());world.getServer().saveEverything(true,true,true);}
 private static void armCrash(){NativeBuilderJournalFault.set((name,header,index)->{if(!header.family().getPath().equals("builder_formation")||!name.equals("after-cell")||index!=(phase().equals("crash-owned")?0:1))return;need(NativeCellTransactions.reservedBy(world,header,ROOT),"actual async durable hold owns root before partial save");boolean ownedPhase=phase().equals("crash-owned");var other=ownedPhase?ROOT:ROOT.south();var chunk=new ChunkPos(other);need(ownedPhase||!chunk.equals(new ChunkPos(ROOT)),"partial save excludes original root chunk or precedes drop actor publication");world.noSave=true;var loaded=world.getChunkSource().getChunkNow(chunk.x,chunk.z);world.getChunkSource().chunkMap.write(chunk,net.minecraft.world.level.chunk.storage.ChunkSerializer.write(world,loaded)).thenCompose(v->((com.vandorlabs.neoforge.mixin.NativeRampChunkStorageAccess)(Object)world.getChunkSource().chunkMap).vandorlabs$rampWorker().synchronize(true)).whenComplete((ignored,error)->{if(error!=null){error.printStackTrace();Runtime.getRuntime().halt(2);}System.out.println("[vandorlabs][builder-runtime] "+phase()+" PASS actualAsyncARMED/partialTerrainSaveBeforeActors/abruptHalt");System.out.flush();Runtime.getRuntime().halt(0);});throw new IllegalStateException("Awaiting deliberate abrupt builder checkpoint");});}
 private static void recovered()throws java.io.IOException{var expected=NbtIo.readCompressed(expected(),NbtAccounter.unlimitedHeap());need(root()!=null&&!root().inert()&&root().formed()==expected.getBoolean("Formed"),"cold recovery restores original core state");need(NativeBuilderData.sameSnapshots(root().saveWithFullMetadata(world.registryAccess()),expected.getCompound("Core")),"cold recovery restores exact private input/output/battery/chip snapshot");checkFrame(root().formed());need(!NativeTransferHolds.active(world)&&world.getEntitiesOfClass(ItemEntity.class,new AABB(ROOT).inflate(8)).isEmpty(),"cold rollback has one terrain owner and zero loose duplicates");System.out.println("[vandorlabs][builder-runtime] "+phase()+" PASS exactBeforeImage/oneOwner/zeroDuplicateDrops/durableRetirement");var server=world.getServer();world=null;server.halt(false);}
 private static void fail(Throwable bad){bad.printStackTrace();System.out.println("[vandorlabs][builder-runtime] FAIL "+bad);NativeBuilderJournalFault.set(null);var server=world==null?net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer():world.getServer();world=null;if(server!=null)server.halt(false);}
 private NativeBuilderRuntimeChecks(){}
}
