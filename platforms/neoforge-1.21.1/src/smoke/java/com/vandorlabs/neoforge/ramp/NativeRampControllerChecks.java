package com.vandorlabs.neoforge.ramp;

import java.util.*;
import java.util.concurrent.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.*;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** The actual controller API and scheduled block ticks over real asynchronous ownership I/O. */
@EventBusSubscriber(modid="vandorlabs")
public final class NativeRampControllerChecks {
    private static final BlockPos ROOT=new BlockPos(23760,100,23760),A=ROOT.north(),B=ROOT.north(2);
    private static NativeRampBlock rootBlock;private static NativeRampPartBlock partBlock;
    private static BlockEntityType<NativeRampEntity> roots;private static BlockEntityType<NativeRampPartEntity> parts;
    private static ServerLevel world;private static ServerPlayer actor;private static NativeRampEntity owner;
    private static final Map<BlockPos,CompoundTag> originals=new LinkedHashMap<>();
    private static int stage,ticks,checks;private static boolean reversed;private static BlockPos obstruction;
    private static final CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);
    private static boolean enabled(){return System.getProperty("vandorlabs.transmitterPhase","").equals("ramp-controller-write");}
    private static void need(boolean yes,String message){checks++;if(!yes)throw new AssertionError(message+" stage="+stage+" ticks="+ticks+" runtime="+(owner==null?"none":owner.controller().phase()+"/"+owner.controller().status()));}
    @EventBusSubscriber(modid="vandorlabs",bus=EventBusSubscriber.Bus.MOD)
    public static final class Registration {
        @SubscribeEvent public static void register(net.neoforged.neoforge.registries.RegisterEvent event){
            if(!enabled())return;
            event.register(Registries.BLOCK,h->{rootBlock=new NativeRampBlock(BlockBehaviour.Properties.of(),()->roots,()->partBlock.defaultBlockState());partBlock=new NativeRampPartBlock(BlockBehaviour.Properties.of(),()->parts);h.register(ResourceLocation.parse("vandorlabs:ramp_controller_check"),rootBlock);h.register(ResourceLocation.parse("vandorlabs:ramp_controller_check_part"),partBlock);});
            event.register(Registries.BLOCK_ENTITY_TYPE,h->{roots=BlockEntityType.Builder.<NativeRampEntity>of((p,s)->new NativeRampEntity(roots,p,s,false),rootBlock).build(null);parts=BlockEntityType.Builder.<NativeRampPartEntity>of((p,s)->new NativeRampPartEntity(parts,p,s),partBlock).build(null);h.register(ResourceLocation.parse("vandorlabs:ramp_controller_check"),roots);h.register(ResourceLocation.parse("vandorlabs:ramp_controller_check_part"),parts);});
        }
    }
    @SubscribeEvent public static void start(ServerStartedEvent event){
        if(!enabled())return;world=event.getServer().overworld();for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++){var p=ROOT.offset(x*16,0,z*16);world.getChunkAt(p);world.setChunkForced(p.getX()>>4,p.getZ()>>4,true);}
        for(int x=-3;x<=3;x++)for(int y=-7;y<=3;y++)for(int z=-4;z<=2;z++)world.setBlock(ROOT.offset(x,y,z),Blocks.AIR.defaultBlockState(),18);
        world.setBlock(ROOT.below(),Blocks.STONE.defaultBlockState(),18);world.setBlock(ROOT,rootBlock.defaultBlockState(),18);owner=(NativeRampEntity)world.getBlockEntity(ROOT);
        actor=new net.neoforged.neoforge.common.util.FakePlayer(world,new com.mojang.authlib.GameProfile(new UUID(3,7721),"RampController"));actor.setPos(ROOT.getX()+.5,ROOT.getY(),ROOT.getZ()+.5);actor.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
        for(var pos:List.of(A,B)){world.setBlock(pos,com.vandorlabs.neoforge.VandorLabsBootstrap.PROGRAMMABLE_SLAB.get().defaultBlockState(),18);var source=world.getBlockEntity(pos);source.getPersistentData().putString("RampControllerOriginal","preserve");originals.put(pos,source.saveWithFullMetadata(world.registryAccess()));}
        NativeRampJournal.testFault=point->{if(point.equals("after-file-sync")&&entered.getCount()>0){entered.countDown();try{if(!release.await(20,TimeUnit.SECONDS))throw new IllegalStateException("Controller fixture disk hold timeout");}catch(InterruptedException interrupted){Thread.currentThread().interrupt();throw new IllegalStateException(interrupted);}}};
    }
    @SubscribeEvent public static void tick(ServerTickEvent.Post event){
        if(!enabled()||world==null)return;
        try{ticks++;need(ticks<1600,"controller timeout");var runtime=owner.controller();
            if(stage==0&&ticks>2){var c=NativeRampSettings.defaults();var next=new NativeRampSettings(0,-4,8,false,false,false,0,true,0,0,false,false,0,false,0,c.channels());var result=runtime.configure(actor,next);need(result.admitted()&&result.choicesCommitted()&&result.actionPending()&&!result.activationRefused(),"configuration starts pending ownership, not refusal");stage=1;return;}
            if(stage==1){if(entered.getCount()!=0)return;need(runtime.phase()==NativeRampController.Phase.PREPARING,"real disk worker still preparing");for(var pos:List.of(A,B))need(world.getBlockEntity(pos)!=null&&world.getBlockEntity(pos).saveWithFullMetadata(world.registryAccess()).equals(originals.get(pos)),"pending disk operation leaves exact native source live");release.countDown();NativeRampJournal.testFault=null;stage=2;return;}
            if(stage==2){need(!runtime.status().error(),"capture/movement refused");if(runtime.committedPose()>.2&&!reversed){need(runtime.requestPose(0).admitted(),"actual controller reversal admitted");reversed=true;stage=3;}return;}
            if(stage==3){need(!runtime.status().error(),"reverse refused");if(runtime.phase()!=NativeRampController.Phase.IDLE)return;for(var pos:List.of(A,B))need(world.getBlockEntity(pos)!=null&&NativeRampJournal.sameSnapshot(originals.get(pos),world.getBlockEntity(pos).saveWithFullMetadata(world.registryAccess())),"reversal restores exact native source");need(NativeRampJournal.atRoot(world,ROOT)==null,"reversed operation fully retired");need(runtime.requestPose(1).admitted(),"retry admitted");stage=4;return;}
            if(stage==4){need(!runtime.status().error(),"retry refused");if(runtime.committedPose()!=1||runtime.status().moving())return;need(runtime.attached()&&NativeRampJournal.atRoot(world,ROOT)!=null,"deployed generated layout owns durable original snapshots");need(runtime.requestPose(0).admitted(),"native return admitted");stage=5;return;}
            if(stage==5){need(!runtime.status().error(),"native return refused");if(runtime.phase()!=NativeRampController.Phase.IDLE)return;for(var pos:List.of(A,B))need(world.getBlockEntity(pos)!=null&&NativeRampJournal.sameSnapshot(originals.get(pos),world.getBlockEntity(pos).saveWithFullMetadata(world.registryAccess())),"complete stroke restores exact native source");need(NativeRampJournal.atRoot(world,ROOT)==null,"complete operation fully retired");stage=6;return;}
            if(stage==6){need(runtime.requestPose(1).admitted(),"obstruction profile capture admitted");stage=7;return;}
            if(stage==7){need(!runtime.status().error(),"obstruction setup stroke");if(runtime.committedPose()<=.2)return;var operation=NativeRampJournal.atRoot(world,ROOT);need(operation!=null&&runtime.status().moving(),"bounded future-cell obstruction during unfinished stroke");for(var candidate:operation.assembly().cells(1,1))if(world.getBlockState(candidate).isAir()&&world.getBlockEntity(candidate)==null){obstruction=candidate.immutable();break;}need(obstruction!=null,"profile has an unoccupied future endpoint cell");need(world.setBlock(obstruction,Blocks.STONE.defaultBlockState(),18),"ordinary foreign obstacle publication");stage=8;return;}
            if(stage==8){if(!runtime.status().error())return;need(runtime.phase()==NativeRampController.Phase.ACTIVE&&!runtime.status().moving()&&runtime.committedPose()<1,"obstruction retains last valid physical frame");var operation=NativeRampJournal.atRoot(world,ROOT);need(operation!=null,"obstruction retains durable source ownership");for(var cell:operation.assembly().cells(runtime.committedPose(),runtime.committedPose())){need(world.getBlockEntity(cell) instanceof NativeRampPartEntity,"stopped body retained");var part=(NativeRampPartEntity)world.getBlockEntity(cell);need(part.belongs(operation.id(),ROOT)&&part.publication()!=null&&!part.data().motion().moving()&&part.publication().pose()==runtime.committedPose(),"stopped projection agrees with last valid geometry");}need(world.getBlockState(obstruction).is(Blocks.STONE)&&world.setBlock(obstruction,Blocks.AIR.defaultBlockState(),18),"ordinary obstacle removal");need(runtime.requestPose(1).admitted(),"clear-path retry admitted");stage=9;return;}
            if(stage==9){need(!runtime.status().error(),"clear-path retry remains valid");if(runtime.committedPose()!=1||runtime.status().moving())return;need(runtime.requestPose(0).admitted(),"post-obstruction native return admitted");stage=10;return;}
            if(stage==10){need(!runtime.status().error(),"post-obstruction return");if(runtime.phase()!=NativeRampController.Phase.IDLE)return;for(var pos:List.of(A,B))need(world.getBlockEntity(pos)!=null&&NativeRampJournal.sameSnapshot(originals.get(pos),world.getBlockEntity(pos).saveWithFullMetadata(world.registryAccess())),"obstruction/retry returns exact native source NBT");need(NativeRampJournal.atRoot(world,ROOT)==null,"obstruction/retry journal retired");stage=11;System.out.println("[vandorlabs][ramp-controller] write PASS checks="+checks+" pending/reversal/stopped-projection/obstruction/retry/native-return");world.getServer().halt(false);}
        }catch(Throwable failure){release.countDown();NativeRampJournal.testFault=null;System.err.println("[vandorlabs][ramp-controller] FAIL "+failure);failure.printStackTrace();world.getServer().halt(false);throw failure;}
    }
    private NativeRampControllerChecks(){}
}
