package com.vandorlabs.neoforge.ramp;

import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.*;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Real journal authority establishes scope; this fixture accepts prepared-body installation only. */
@EventBusSubscriber(modid="vandorlabs")
public final class NativeRampPartPublisherChecks {
    private static final BlockPos ROOT=new BlockPos(29200,100,29200),SOURCE=ROOT.north();
    private static NativeRampBlock rootBlock;private static NativeRampPartBlock partBlock;private static BlockEntityType<NativeRampEntity> roots;private static BlockEntityType<NativeRampPartEntity> parts;
    private static ServerLevel level;private static NativeRampEntity owner;private static ServerPlayer actor;private static NativeRampJournal.Operation operation;private static NativeRampAssembly assembly;private static Set<BlockPos> target;
    private static int stage,ticks,checks,factories;private static boolean badFactory;private static String mode;private static NativeRampPartPublisher.Prepared expiring;private static Map<BlockPos,BlockEntity> beforeExpiry;
    private static boolean enabled(){return System.getProperty("vandorlabs.transmitterPhase","").startsWith("ramp-publisher-");}
    private static void need(boolean yes,String why){checks++;if(!yes)throw new AssertionError("Ramp publisher: "+why);}
    private static final class Part extends NativeRampPartBlock {
        Part(){super(BlockBehaviour.Properties.of(),()->parts);}
        @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state){factories++;return badFactory?null:super.newBlockEntity(pos,state);}
    }
    @EventBusSubscriber(modid="vandorlabs",bus=EventBusSubscriber.Bus.MOD)
    public static final class Registration {
        @SubscribeEvent public static void register(net.neoforged.neoforge.registries.RegisterEvent event){if(!enabled())return;
            event.register(Registries.BLOCK,h->{rootBlock=new NativeRampBlock(BlockBehaviour.Properties.of().strength(1),()->roots);partBlock=new Part();h.register(ResourceLocation.parse("vandorlabs:ramp_publisher_root"),rootBlock);h.register(ResourceLocation.parse("vandorlabs:ramp_publisher_part"),partBlock);});
            event.register(Registries.BLOCK_ENTITY_TYPE,h->{roots=BlockEntityType.Builder.<NativeRampEntity>of((p,s)->new NativeRampEntity(roots,p,s,false),rootBlock).build(null);parts=BlockEntityType.Builder.<NativeRampPartEntity>of((p,s)->new NativeRampPartEntity(parts,p,s),partBlock).build(null);h.register(ResourceLocation.parse("vandorlabs:ramp_publisher_root"),roots);h.register(ResourceLocation.parse("vandorlabs:ramp_publisher_part"),parts);});
        }
    }
    @SubscribeEvent public static void start(ServerStartedEvent event){if(!enabled())return;level=event.getServer().overworld();mode=System.getProperty("vandorlabs.transmitterPhase");
        for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++){var chunk=level.getChunkAt(ROOT.offset(x*16,0,z*16));level.setChunkForced(chunk.getPos().x,chunk.getPos().z,true);}
        if(mode.endsWith("read")){need(level.getBlockState(SOURCE).is(Blocks.STONE)&&level.getBlockState(SOURCE.north()).is(Blocks.STONE),"restored original sources survive real restart");need(NativeRampJournal.atRoot(level,ROOT)==null,"no ownership journal remains after completed fixture recovery");System.out.println("[vandorlabs][ramp-publisher] read PASS checks="+checks);event.getServer().halt(false);return;}
        for(var p:BlockPos.betweenClosed(ROOT.offset(-4,-5,-4),ROOT.offset(4,4,4)))level.setBlock(p,Blocks.AIR.defaultBlockState(),18);level.setBlock(ROOT,rootBlock.defaultBlockState(),18);level.setBlock(SOURCE,Blocks.STONE.defaultBlockState(),18);level.setBlock(SOURCE.north(),Blocks.STONE.defaultBlockState(),18);owner=(NativeRampEntity)level.getBlockEntity(ROOT);
        actor=new net.neoforged.neoforge.common.util.FakePlayer(level,new com.mojang.authlib.GameProfile(new UUID(0,291108),"RampPublisher"));actor.setPos(ROOT.getX()+.5,ROOT.getY(),ROOT.getZ()+.5);actor.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);need(NativeRampAuthorization.authorize(owner,actor),"actual installed actor authority");
        var capture=NativeRampCapture.select(owner,actor);need(capture.accepted()&&capture.plan().sources().size()==2,"actual two-cell source capture");assembly=NativeRampAssembly.prepare(ROOT,owner.settings(),capture.plan(),Map.of());target=assembly.cells(0,0);need(target.size()==2,"initial physical cells");
    }
    private static NativeRampPartPublisher.Prepared prepared(UUID id,long revision){return NativeRampPartPublisher.prepare(level,partBlock.defaultBlockState(),id,ROOT,assembly,target,new NativeRampPartData.Motion(1,level.getGameTime(),0,0,1,false,0),NativeRampPartData.Belt.NONE,revision);}
    private static Map<BlockPos,BlockEntity> identities(){var result=new LinkedHashMap<BlockPos,BlockEntity>();for(var pos:target)result.put(pos,level.getBlockEntity(pos));return result;}
    private static void same(Map<BlockPos,BlockEntity> previous,String why){for(var entry:previous.entrySet())need(level.getBlockEntity(entry.getKey())==entry.getValue(),why);}
    private static void refused(Runnable action,String why){boolean refused=false;try{action.run();}catch(IllegalArgumentException|IllegalStateException expected){refused=true;}need(refused,why);}
    private static void exercise(){
        var original=identities();badFactory=true;try{refused(()->prepared(operation.id(),0),"factory refusal before any replacement");}finally{badFactory=false;}same(original,"failed factory leaves every installed identity");
        int calls=factories;var workerMotion=new NativeRampPartData.Motion(1,level.getGameTime(),0,0,1,false,0);var workerId=operation.id();var workerProxy=partBlock.defaultBlockState();need(java.util.concurrent.CompletableFuture.supplyAsync(()->{try{NativeRampPartPublisher.prepare(level,workerProxy,workerId,ROOT,assembly,target,workerMotion,NativeRampPartData.Belt.NONE,0);return false;}catch(IllegalStateException expected){return true;}}).join()&&factories==calls,"worker refused before factory/world work");
        var workerInstall=prepared(operation.id(),0);need(java.util.concurrent.CompletableFuture.supplyAsync(()->{try{workerInstall.install();return false;}catch(IllegalStateException expected){return true;}}).join(),"worker install refused before any world access");refused(workerInstall::install,"main thread still requires exact scope after worker refusal");same(original,"worker install never changes installed identities");
        var wrongScope=prepared(operation.id(),0);need(wrongScope.preparedBytes()>0&&wrongScope.preparedBytes()<=64L*1024*1024,"bounded prepared body estimate");refused(wrongScope::install,"missing exact operation scope");same(original,"missing scope never alters installed bodies");
        try(var scope=NativeRampHeldOwners.mutate(operation)){
            for(var pos:target)need(level.setBlock(pos,Blocks.AIR.defaultBlockState(),Block.UPDATE_KNOWN_SHAPE),"private fixture vacates journal-owned placeholder");
            var ordered=new ArrayList<>(target);ordered.sort(Comparator.<BlockPos>comparingInt(p->p.getX()).thenComparingInt(BlockPos::getY).thenComparingInt(BlockPos::getZ));var first=ordered.getFirst();var last=ordered.getLast();
            var foreign=prepared(operation.id(),0);level.setBlock(last,Blocks.GOLD_BLOCK.defaultBlockState(),Block.UPDATE_KNOWN_SHAPE);refused(foreign::install,"foreign later target refuses complete prepared set");need(level.getBlockState(first).isAir()&&level.getBlockState(last).is(Blocks.GOLD_BLOCK),"all targets checked before first mutation");level.setBlock(last,Blocks.AIR.defaultBlockState(),Block.UPDATE_KNOWN_SHAPE);
            var pending=new net.minecraft.nbt.CompoundTag();pending.putString("id","vandorlabs:ramp_publisher_part");pending.putInt("x",first.getX());pending.putInt("y",first.getY());pending.putInt("z",first.getZ());pending.putString("OpaqueFutureBody","retained");var chunk=level.getChunkAt(first);chunk.setBlockEntityNbt(pending);var pendingCandidate=prepared(operation.id(),0);refused(pendingCandidate::install,"orphan pending tile never counts as empty air");need(chunk.getBlockEntityNbt(first).equals(pending)&&chunk.getBlockEntities().get(first)==null&&level.getBlockState(first).isAir(),"pending envelope retained without instantiation");
            try{var field=net.minecraft.world.level.chunk.ChunkAccess.class.getDeclaredField("pendingBlockEntities");field.setAccessible(true);((Map<?,?>)field.get(chunk)).remove(first);}catch(ReflectiveOperationException failure){throw new AssertionError("fixture-only pending cleanup",failure);}
            var wrongId=prepared(new UUID(9,17),0);refused(wrongId::install,"wrong operation cannot borrow live scope");need(level.getBlockState(first).isAir(),"wrong operation retains empty target");
            var valid=prepared(operation.id(),0);valid.install();refused(valid::install,"prepared set is single attempt");for(var pos:target){var part=(NativeRampPartEntity)level.getBlockEntity(pos);need(part!=null&&part.belongs(operation.id(),ROOT)&&part.publication()!=null&&part.publication().revision()==0&&part.physicalBounds()!=null,"complete owned body installed before caller notification");}
            var installed=identities();refused(()->prepared(operation.id(),0).install(),"equal revision cannot replace live bodies");same(installed,"revision refusal preflights whole set");
            var replacement=prepared(operation.id(),1);replacement.install();for(var pos:target){var part=(NativeRampPartEntity)level.getBlockEntity(pos);need(part!=installed.get(pos)&&part.publication().revision()==1&&part.belongs(operation.id(),ROOT),"new complete revision replaces same operation");}
            expiring=prepared(operation.id(),2);beforeExpiry=identities();
        }
    }
    @SubscribeEvent public static void tick(ServerTickEvent.Post event){if(!enabled()||mode.endsWith("read"))return;if(++ticks>500)throw new AssertionError("Ramp publisher stage="+stage+" op="+(operation==null?"none":operation.phase()+"/"+operation.error()));
        if(stage==0){if(NativeRampJournal.load(level)!=NativeRampJournal.Status.READY)return;var start=NativeRampJournal.prepare(owner,actor,assembly.potential(),partBlock.defaultBlockState(),false);need(start.status()!=NativeRampJournal.Status.REFUSED&&start.operation()!=null,"real asynchronous journal preparation status="+start.status()+" error="+start.error()+" rootInCells="+assembly.potential().contains(ROOT)+" inert="+owner.inert()+" removed="+owner.isRemoved()+" installed="+(level.getBlockEntity(ROOT)==owner)+" actor="+actor.position()+" alive="+actor.isAlive()+" edit="+com.vandorlabs.neoforge.NativeConfigurationAccess.canEditDialog(actor,owner)+" cold="+assembly.potential().stream().filter(p->level.getChunkSource().getChunkNow(p.getX()>>4,p.getZ()>>4)==null).toList());operation=start.operation();stage=1;return;}
        if(stage==1){if(NativeRampJournal.poll(level,operation)==NativeRampJournal.Status.PENDING)return;need(NativeRampJournal.arm(level,operation,actor)==NativeRampJournal.Status.PENDING,"durable ARM pending accepted");stage=2;return;}
        if(stage==2){if(NativeRampJournal.poll(level,operation)==NativeRampJournal.Status.PENDING)return;need(NativeRampJournal.apply(level,operation,actor)==NativeRampJournal.Status.READY,"real journal ownership applied before isolated replacement checks");exercise();stage=3;return;}
        if(stage==3){try(var scope=NativeRampHeldOwners.mutate(operation)){refused(expiring::install,"prepared set expires on next actual world tick");same(beforeExpiry,"expired publication leaves every installed identity");var phaseUpdate=prepared(operation.id(),1);phaseUpdate.install();for(var pos:target){var part=(NativeRampPartEntity)level.getBlockEntity(pos);need(part!=beforeExpiry.get(pos)&&part.publication().revision()==1&&part.publication().observationTick()==level.getGameTime(),"new observation advances phase without inventing control revision");}}expiring=null;beforeExpiry=null;stage=4;}
        if(stage==4){var status=NativeRampJournal.recover(level,operation);need(status!=NativeRampJournal.Status.REFUSED,"fixture journal recovery "+operation.error());if(status!=NativeRampJournal.Status.DONE)return;need(level.getBlockState(SOURCE).is(Blocks.STONE)&&level.getBlockState(SOURCE.north()).is(Blocks.STONE),"real journal restores original sources after helper checks");System.out.println("[vandorlabs][ramp-publisher] write PASS checks="+checks+" scoped prepared bodies/factory/worker/foreign/revision/expiry; source publication integration remains separate");event.getServer().halt(false);}
    }
}
