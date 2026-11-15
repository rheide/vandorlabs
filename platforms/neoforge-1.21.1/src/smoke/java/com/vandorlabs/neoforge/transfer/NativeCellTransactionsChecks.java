package com.vandorlabs.neoforge.transfer;

import com.vandorlabs.persistence.JournalWorker;
import java.util.*;
import java.util.concurrent.*;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.*;
import net.minecraft.world.Container;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.*;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.*;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** One real async move/rollback/retry; no production family or unlisted block is enabled by this fixture. */
@EventBusSubscriber(modid="vandorlabs")
public final class NativeCellTransactionsChecks {
    private static final BlockPos ROOT=new BlockPos(14,110,8),SOURCE=ROOT.east(),TARGET=ROOT.east(2);
    private static final UUID OWNER=new UUID(0,0x7189),ACTOR=new UUID(0,0x7190);
    private static final NativeTransferSnapshot.Limits LIMITS=new NativeTransferSnapshot.Limits(2,256*1024,1024*1024,8*1024*1024);
    private static final ResourceLocation FAMILY_ID=ResourceLocation.parse("vandorlabs:cell_core_fixture");
    private static final CountDownLatch RELEASE=new CountDownLatch(1),GATE_STARTED=new CountDownLatch(1);
    private static ServerLevel world;private static Actor actor;private static NativeCellTransactions.Operation<Receipt> operation;
    private static int ticks,checks,stage,pendingTicks,preparedTick=-1,published,prepared,armed;private static boolean gateSubmitted,caught;
    private static CompoundTag chest;private static boolean abrupt;
    private static final class Actor extends net.neoforged.neoforge.common.util.FakePlayer {Actor(ServerLevel level){super(level,new com.mojang.authlib.GameProfile(ACTOR,"CellJournalFixture"));}}
    private record Receipt(int schema){}
    private static void need(boolean value,String why){checks++;if(!value)throw new AssertionError("Cell journal stage="+stage+" tick="+ticks+": "+why);}
    private static final NativeCellTransactions.Family<Receipt> FAMILY=new NativeCellTransactions.Family<>(){
        public ResourceLocation id(){return FAMILY_ID;}public NativeTransferSnapshot.Limits limits(){return LIMITS;}
        public Receipt readRecipe(HolderLookup.Provider registries,NativeCellTransactions.Header header,NativeTransferSnapshot before,NativeTransferSnapshot after,Set<BlockPos> held,CompoundTag raw){
            if(!raw.getAllKeys().equals(Set.of("Schema"))||!raw.contains("Schema",Tag.TAG_INT)||raw.getInt("Schema")!=1||!header.root().equals(ROOT)||!header.owner().equals(OWNER)||!header.actor().equals(ACTOR)||header.creative()||!held.equals(Set.of(ROOT,SOURCE,TARGET))||before.cells().size()!=2||after.cells().size()!=2||!before.cells().get(0).pos.equals(SOURCE)||!before.cells().get(1).pos.equals(TARGET)||!before.cells().get(0).state.is(Blocks.CHEST)||!before.cells().get(1).state.isAir()||!after.cells().get(0).state.isAir()||after.cells().get(1).state!=before.cells().get(0).state)throw new IllegalArgumentException("Cell fixture recipe identity");
            var expected=before.cells().get(0).tile();expected.putInt("x",TARGET.getX());expected.putInt("y",TARGET.getY());expected.putInt("z",TARGET.getZ());if(!expected.equals(after.cells().get(1).tile()))throw new IllegalArgumentException("Cell fixture changed chest contents");return new Receipt(1);
        }
        public void authorize(ServerLevel level,NativeCellTransactions.Header header,ServerPlayer player,Receipt recipe){
            if(!player.getUUID().equals(ACTOR)||player.serverLevel()!=level||!player.isAlive()||player.isSpectator()||player.isCreative()||!level.getBlockState(ROOT).is(Blocks.IRON_BLOCK))throw new IllegalArgumentException("Cell fixture actor/marker");
            if(!gateSubmitted){gateSubmitted=true;JournalWorker.submit(()->{GATE_STARTED.countDown();if(!RELEASE.await(10,TimeUnit.SECONDS))throw new IllegalStateException("Fixture disk gate timeout");return null;});}
        }
        public BlockEntity stage(ServerLevel level,NativeCellTransactions.Header header,NativeRawCell cell,Receipt recipe){return cell.stage(level,tile->tile.getClass()==ChestBlockEntity.class,CompoundTag::equals);}
        public boolean same(CompoundTag expected,CompoundTag actual){return Objects.equals(expected,actual);}
        public void publish(ServerLevel level,NativeCellTransactions.Header header,NativeRawCell before,NativeRawCell after,BlockEntity staged,NativeTransferHolds.Lease lease,Receipt recipe){
            if(level.getBlockEntity(before.pos) instanceof Container inventory)inventory.clearContent();
            try(var scope=lease.allowOnce(after.pos)){
                if(level.getBlockState(after.pos)!=after.state&&!level.setBlock(after.pos,after.state,Block.UPDATE_KNOWN_SHAPE))throw new IllegalStateException("Core cell publication refused");
                need(!level.setBlock(after.pos,Blocks.EMERALD_BLOCK.defaultBlockState(),Block.UPDATE_ALL),"consumed one-use scope rejects a reentrant block write");
            }
            if(after.hasTile()){level.removeBlockEntity(after.pos);level.setBlockEntity(staged);staged.setChanged();}else if(level.getBlockEntity(after.pos)!=null)level.removeBlockEntity(after.pos);level.getChunkAt(after.pos).setUnsaved(true);published++;
        }
    };
    private static String phase(){return System.getProperty("vandorlabs.cellJournalPhase","");}
    private static boolean enabled(){return Boolean.getBoolean("vandorlabs.cellJournalChecks");}
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void register(ServerStartingEvent event){if(enabled()){NativeCellTransactions.register(FAMILY);NativeCellMaintenanceChecks.register();}}
    @SubscribeEvent public static void started(ServerStartedEvent event){if(!enabled())return;try{
        world=event.getServer().overworld();if(phase().equals("read")){coldRead(event);return;}if(phase().equals("crash"))gateSubmitted=true;for(int x=ROOT.getX()-1;x<=TARGET.getX()+1;x++){world.setChunkForced(x>>4,ROOT.getZ()>>4,true);world.getChunkAt(new BlockPos(x,110,ROOT.getZ()));}world.setBlock(ROOT,Blocks.IRON_BLOCK.defaultBlockState(),3);world.setBlock(SOURCE,Blocks.CHEST.defaultBlockState(),3);world.setBlock(TARGET,Blocks.AIR.defaultBlockState(),3);var source=(ChestBlockEntity)world.getBlockEntity(SOURCE);var gold=new ItemStack(Items.GOLD_INGOT,37);gold.set(DataComponents.CUSTOM_NAME,net.minecraft.network.chat.Component.literal("Receipt gold"));var data=new CompoundTag();data.putString("Receipt","exact-components");gold.set(DataComponents.CUSTOM_DATA,CustomData.of(data));source.setItem(0,gold);source.setItem(1,new ItemStack(Items.DIAMOND,2));source.setChanged();chest=source.saveWithFullMetadata(world.registryAccess());actor=new Actor(world);actor.setGameMode(GameType.SURVIVAL);actor.setPos(ROOT.getX()+.5,111,ROOT.getZ()-2);if(phase().equals("crash"))world.getServer().saveEverything(true,true,true);
        NativeCellTransactions.fault=(point,header,cell)->{
            if(point.equals("prepared-durable")){prepared++;need(NativeTransferHolds.blocked(world,SOURCE),"durable preparation reserves the exact source before ARMED I/O");need(world.getBlockState(SOURCE).is(Blocks.CHEST)&&world.getBlockState(TARGET).isAir(),"reservation does not transfer ownership");need(!world.setBlock(SOURCE,Blocks.STONE.defaultBlockState(),3),"reservation cannot authorize a block write");var inventory=world.getCapability(net.neoforged.neoforge.capabilities.Capabilities.ItemHandler.BLOCK,SOURCE,Direction.NORTH);need(inventory!=null&&inventory.extractItem(0,1,false).isEmpty(),"prepared reservation freezes actual source capability extraction");if(preparedTick<0&&!phase().equals("crash")){preparedTick=ticks;world.noSave=true;}}
            if(point.equals("armed-durable")){armed++;need(operation.mutationArmed()&&NativeTransferHolds.blocked(world,SOURCE),"mutation authority follows durable ARMED completion");}
            if(point.equals("after-cell")&&cell==0&&!caught){caught=true;if(phase().equals("crash")){need(operation.mutationArmed()&&world.getBlockState(SOURCE).isAir()&&world.getBlockState(TARGET).isAir(),"actual ARMED removal reached source-only fault point");need(!new net.minecraft.world.level.ChunkPos(SOURCE).equals(new net.minecraft.world.level.ChunkPos(TARGET)),"fault save genuinely excludes destination chunk");need(world.getEntitiesOfClass(ItemEntity.class,new AABB(ROOT).inflate(8)).isEmpty(),"source-only fault has zero loose resource copies");abrupt=true;world.noSave=true;var cp=new net.minecraft.world.level.ChunkPos(SOURCE);var chunk=world.getChunkSource().getChunkNow(cp.x,cp.z);world.getChunkSource().chunkMap.write(cp,net.minecraft.world.level.chunk.storage.ChunkSerializer.write(world,chunk)).thenCompose(ignored->((com.vandorlabs.neoforge.mixin.NativeRampChunkStorageAccess)(Object)world.getChunkSource().chunkMap).vandorlabs$rampWorker().synchronize(true)).whenComplete((ignored,failure)->{if(failure!=null){failure.printStackTrace();System.out.flush();Runtime.getRuntime().halt(2);}System.out.println("[vandorlabs][cell-journal] crash PASS checks="+checks+" realAsyncARMED/afterSourceCell/sourceOnlyCrossChunkSave/zeroLooseItems/abruptHalt");System.out.flush();Runtime.getRuntime().halt(0);});throw new IllegalStateException("Awaiting deliberate abrupt source-only checkpoint");}throw new IllegalStateException("Expected caught partial-install fault");}
        };
    }catch(Throwable failure){fail(failure);}}
    private static NativeCellTransactions.Request request(){var before=new NativeTransferSnapshot(List.of(new NativeRawCell(SOURCE,Blocks.CHEST.defaultBlockState(),chest,256*1024),new NativeRawCell(TARGET,Blocks.AIR.defaultBlockState(),null,256*1024)),LIMITS);var moved=chest.copy();moved.putInt("x",TARGET.getX());var after=new NativeTransferSnapshot(List.of(new NativeRawCell(SOURCE,Blocks.AIR.defaultBlockState(),null,256*1024),new NativeRawCell(TARGET,Blocks.CHEST.defaultBlockState(),moved,256*1024)),LIMITS);var recipe=new CompoundTag();recipe.putInt("Schema",1);return new NativeCellTransactions.Request(ROOT,OWNER,before,after,List.of(ROOT,SOURCE,TARGET),recipe);}
    @SubscribeEvent public static void tick(ServerTickEvent.Post event){if(!enabled()||world==null)return;try{ticks++;need(ticks<400,"bounded async ownership journey status="+(operation==null?"not-admitted":operation.status()+" "+operation.error()));
        if(preparedTick>=0&&world.noSave){need(operation.status()==NativeCellTransactions.Status.PENDING&&world.getBlockState(SOURCE).is(Blocks.CHEST),"saving disabled suspends the reserved operation without rejecting or removing its source");if(ticks-preparedTick>=3)world.noSave=false;}
        if(stage==4){if(!NativeCellMaintenanceChecks.tick())return;System.out.println("[vandorlabs][cell-journal] PASS checks="+checks+" maintenanceChecks="+NativeCellMaintenanceChecks.checks()+" queuedIO/reservation/NoSave/oneUse/caughtRollback/exactRetry/maintenanceAuthority/recordReload");NativeCellTransactions.fault=null;world=null;event.getServer().halt(false);return;}
        if(stage==0){operation=NativeCellTransactions.submit(world,FAMILY,actor,request());if(operation!=null)stage=phase().equals("crash")?2:1;return;}
        if(stage==1){if(GATE_STARTED.getCount()!=0)return;pendingTicks++;need(operation.status()==NativeCellTransactions.Status.PENDING&&!NativeTransferHolds.active(world),"pending preparation is non-owning and stays queued");need(((ChestBlockEntity)world.getBlockEntity(SOURCE)).getItem(0).getCount()==37&&world.getBlockState(TARGET).isAir(),"pending disk leaves source resources and target unchanged");if(pendingTicks==1){var inventory=world.getCapability(net.neoforged.neoforge.capabilities.Capabilities.ItemHandler.BLOCK,SOURCE,Direction.NORTH);need(inventory.insertItem(26,new ItemStack(Items.ARROW),false).isEmpty()&&inventory.extractItem(26,1,false).is(Items.ARROW),"preparing is not an inventory reservation");}if(pendingTicks>=5){RELEASE.countDown();stage=2;}return;}
        if(abrupt){need(world.noSave&&world.getBlockState(TARGET).isAir(),"suspended fault cannot advance publication while source save is pending");return;}
        if(operation.status()==NativeCellTransactions.Status.FAILED)throw new AssertionError(operation.error());
        if(stage==2&&operation.status()==NativeCellTransactions.Status.DONE){need(caught&&world.getBlockState(TARGET).isAir(),"caught partial installation restores target absence");need(((ChestBlockEntity)world.getBlockEntity(SOURCE)).saveWithFullMetadata(world.registryAccess()).equals(chest),"rollback restores exact37/2 items and components");need(!NativeTransferHolds.active(world),"rollback releases only after durable checkpoint and retirement");operation=NativeCellTransactions.submit(world,FAMILY,actor,request());need(operation!=null,"same real retry accepted");stage=3;return;}
        if(stage==3&&operation.status()==NativeCellTransactions.Status.DONE){need(world.getBlockState(SOURCE).isAir()&&world.getBlockEntity(SOURCE)==null,"successful move leaves no source owner");var saved=((ChestBlockEntity)world.getBlockEntity(TARGET)).saveWithFullMetadata(world.registryAccess());var expected=chest.copy();expected.putInt("x",TARGET.getX());need(saved.equals(expected),"one target chest retains exact count/type/components");need(world.getEntitiesOfClass(ItemEntity.class,new AABB(ROOT).inflate(8)).isEmpty(),"no loose resource duplicates");need(!NativeTransferHolds.active(world)&&prepared==2&&armed==2&&published==4,"both transactions completed exact durable stages");NativeCellMaintenanceChecks.begin(world,actor);stage=4;}
    }catch(Throwable failure){fail(failure);}}
    private static void coldRead(ServerStartedEvent event){
        need(world.getBlockState(SOURCE).is(Blocks.CHEST)&&world.getBlockEntity(SOURCE) instanceof ChestBlockEntity,"cold recovery restores one physical source chest before gameplay");need(world.getBlockState(TARGET).isAir()&&world.getBlockEntity(TARGET)==null,"unpublished destination remains empty after partial source save");var inventory=(ChestBlockEntity)world.getBlockEntity(SOURCE);need(inventory.getItem(0).is(Items.GOLD_INGOT)&&inventory.getItem(0).getCount()==37&&inventory.getItem(0).getHoverName().getString().equals("Receipt gold")&&inventory.getItem(0).getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag().getString("Receipt").equals("exact-components"),"cold owner retains exact named37 gold and item components");need(inventory.getItem(1).is(Items.DIAMOND)&&inventory.getItem(1).getCount()==2,"cold owner retains second item type/count");int total=0;for(int slot=0;slot<inventory.getContainerSize();slot++)total+=inventory.getItem(slot).getCount();need(total==39,"no extra or missing inventory resources");need(!NativeTransferHolds.active(world)&&!NativeCellTransactions.busy(world,ROOT,OWNER),"cold rollback checkpoint and retirement finish before gameplay");need(world.getEntitiesOfClass(ItemEntity.class,new AABB(ROOT).inflate(8)).isEmpty(),"cold recovery has zero loose resource duplicates");System.out.println("[vandorlabs][cell-journal] read PASS checks="+checks+" abruptPartialSourceSave/oneUsableOwner/exactComponents/zeroLooseItems/asyncRecoveryRetired");world=null;event.getServer().halt(false);
    }
    private static void fail(Throwable failure){RELEASE.countDown();failure.printStackTrace();System.out.println("[vandorlabs][cell-journal] FAIL "+failure);var server=world==null?net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer():world.getServer();if(world!=null)world.noSave=false;world=null;NativeCellTransactions.fault=null;if(server!=null)server.halt(false);}
    private NativeCellTransactionsChecks(){}
}
