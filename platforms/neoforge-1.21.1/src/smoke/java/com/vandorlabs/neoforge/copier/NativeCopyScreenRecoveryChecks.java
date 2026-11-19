package com.vandorlabs.neoforge.copier;

import com.mojang.authlib.GameProfile;
import com.vandorlabs.copier.core.CopySettings;
import com.vandorlabs.neoforge.VandorLabsBootstrap;
import com.vandorlabs.neoforge.mixin.NativeRampChunkStorageAccess;
import com.vandorlabs.neoforge.redstone.*;
import com.vandorlabs.neoforge.screen.*;
import com.vandorlabs.neoforge.surface.NativeSurfaceBindings;
import com.vandorlabs.neoforge.transfer.*;
import com.vandorlabs.redstone.ChannelList;
import com.vandorlabs.screen.ScreenFamily;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.*;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.chunk.storage.ChunkSerializer;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

@EventBusSubscriber(modid="vandorlabs")
public final class NativeCopyScreenRecoveryChecks {
    private static final String LABEL="[vandorlabs][copy-screen-recovery]";
    private static final BlockPos ROOT=new BlockPos(16,120,8),SUPPLY=ROOT.south(),SOURCE=ROOT.east().above(),
        OUTPUT=ROOT.west(2).above(),UNRELATED=ROOT.west(6);
    private static final ChannelList SOURCE_BANK=ChannelList.of(301,303),OUTPUT_BANK=ChannelList.of(338,340);
    private static ServerLevel world;
    private static ServerPlayer actor;
    private static NativeCopyControllerEntity copy;
    private static boolean started,crashing;
    private static int ticks;

    private static String phase(){return System.getProperty("vandorlabs.copyScreenRecovery","");}

    @SubscribeEvent public static void start(ServerStartedEvent event){
        if(phase().isEmpty())return;
        world=event.getServer().overworld();
        try{
            for(int x=0;x<=1;x++){
                world.setChunkForced(x,0,true);
                world.getChunk(x,0);
            }
            actor=new FakePlayer(world,new GameProfile(new UUID(0,329288),"CopyScreenRecovery"));
            actor.setGameMode(GameType.SURVIVAL);
            actor.setPos(16.5,120,6.5);
            if(phase().equals("read")){
                copy=(NativeCopyControllerEntity)world.getBlockEntity(ROOT);
                require(copy!=null&&!copy.inert()&&!copy.busy()&&!NativeTransferHolds.active(world),
                    "cold startup retires its recovery lease before gameplay");
                require(emptyOutput()&&supplyCount()==3,"partial output-only save rolls back without double refund");
                require(world.getBlockState(UNRELATED).is(Blocks.GOLD_BLOCK),"unrelated newer block in saved output chunk survives");
                NativeRedstoneChannels.flush(world);
                require(receives(SOURCE,SOURCE_BANK,15),"saved source group and original wireless subscription resume on startup");
                return;
            }
            world.setBlockAndUpdate(ROOT,NativeCopyBindings.BLOCK.defaultBlockState());
            world.setBlockAndUpdate(SUPPLY,Blocks.CHEST.defaultBlockState());
            copy=(NativeCopyControllerEntity)world.getBlockEntity(ROOT);
            for(int x=1;x<=2;x++)for(var pos:List.of(ROOT.east(x),ROOT.west(x)))
                world.setBlockAndUpdate(pos,NativeSurfaceBindings.BLOCK.defaultBlockState());
            for(var pos:List.of(SOURCE,SOURCE.east())){
                var block=NativeScreenRegistrations.BLOCKS.get(ScreenFamily.SCREEN).get();
                world.setBlockAndUpdate(pos,block.defaultBlockState().setValue(NativeScreenBlock.FACING,Direction.NORTH));
                var screen=screen(pos);
                var raw=screen.saveWithFullMetadata(world.registryAccess());
                raw.putIntArray("RedstoneChannels",SOURCE_BANK.toArray());
                screen.loadWithComponents(raw,world.registryAccess());
            }
            ((ChestBlockEntity)world.getBlockEntity(SUPPLY)).setItem(0,
                new ItemStack(NativeScreenRegistrations.ITEMS.get(ScreenFamily.SCREEN).get(),3));
            toggle(SOURCE_BANK);
            toggle(OUTPUT_BANK);
            NativeRedstoneChannels.flush(world);
            require(receives(SOURCE,SOURCE_BANK,15),"live persistent source transmitter powers both input members");
            require(copy.configure(actor,new CopySettings(1,37,0,ChannelList.EMPTY,false)),"paired receiver Copy choices");
            world.getServer().saveEverything(true,true,true);
            NativeCopyService.fault=NativeCopyScreenRecoveryChecks::partial;
        }catch(Throwable error){fail(error);}
    }

    @SubscribeEvent public static void tick(ServerTickEvent.Post event){
        if(world==null||crashing)return;
        try{
            require(++ticks<350,"bounded asynchronous Copy "+copy.status()+" "+copy.error());
            if(!started){
                copy.request(actor);
                started=true;
                return;
            }
            if(!phase().equals("read")||copy.busy()||!receives(OUTPUT,OUTPUT_BANK,15))return;
            require(receives(SOURCE,SOURCE_BANK,15)&&supplyCount()==1,"cold retry charges just two items; source bank remains exact");
            require(world.getEntitiesOfClass(ItemEntity.class,new AABB(ROOT).inflate(10)).isEmpty(),"zero duplicate items after cold retry");
            toggle(OUTPUT_BANK);
            require(receives(OUTPUT,OUTPUT_BANK,0)&&receives(SOURCE,SOURCE_BANK,15),
                "removing the shifted transmitter clears only the copied group");
            System.out.println(LABEL+" read PASS restoredOneSupply/retiredBeforeGameplay/nativeRetry/shiftedPoweredJoin");
            world=null;
            event.getServer().halt(false);
        }catch(Throwable error){fail(error);}
    }

    private static void partial(NativeCellTransactions.Header header,NativeRawCell target){
        if(!target.pos.equals(OUTPUT))return;
        require(screen(OUTPUT)!=null&&screen(OUTPUT.east())==null&&supplyCount()==1,
            "one output physically installed in memory after full survival withdrawal");
        NativeRedstoneChannels.flush(world);
        require(NativeCellTransactions.reservedBy(world,header,OUTPUT)&&screen(OUTPUT).getRedstoneChannels().equals(OUTPUT_BANK)
            &&screen(OUTPUT).screenSettings().channelLevel()==0,"exact armed output bank withheld from receiver network");
        require(new ChunkPos(SUPPLY).equals(new ChunkPos(SOURCE))&&!new ChunkPos(SUPPLY).equals(new ChunkPos(OUTPUT)),
            "only the output chunk will be saved");
        world.setBlockAndUpdate(UNRELATED,Blocks.GOLD_BLOCK.defaultBlockState());
        world.noSave=true;
        crashing=true;
        var chunk=new ChunkPos(OUTPUT);
        var loaded=world.getChunkSource().getChunkNow(chunk.x,chunk.z);
        world.getChunkSource().chunkMap.write(chunk,ChunkSerializer.write(world,loaded))
            .thenCompose(ignored->((NativeRampChunkStorageAccess)world.getChunkSource().chunkMap).vandorlabs$rampWorker().synchronize(true))
            .whenComplete((ignored,error)->{
                if(error!=null){
                    error.printStackTrace();
                    System.out.flush();
                    Runtime.getRuntime().halt(2);
                }
                System.out.println(LABEL+" crash PASS actualARMED/oneOutputOnlyChunkSave/withheldReceiver/abruptHalt");
                System.out.flush();
                Runtime.getRuntime().halt(0);
            });
        throw new IllegalStateException("Awaiting the deliberately abrupt Screen checkpoint");
    }

    private static void toggle(ChannelList bank){
        var item=NativeTransmitterItem.variant(VandorLabsBootstrap.REDSTONE_TRANSMITTER.get(),3);
        NativeTransmitterItem.configure(item,bank);
        require(NativeRedstoneChannels.manualToggle(actor,item),"real saved transmitter toggle");
    }
    private static NativeScreenEntity screen(BlockPos pos){
        return world.getBlockEntity(pos) instanceof NativeScreenEntity screen?screen:null;
    }
    private static boolean emptyOutput(){return world.getBlockState(OUTPUT).isAir()&&world.getBlockState(OUTPUT.east()).isAir();}
    private static int supplyCount(){return ((ChestBlockEntity)world.getBlockEntity(SUPPLY)).getItem(0).getCount();}
    private static boolean receives(BlockPos pos,ChannelList bank,int value){
        var first=screen(pos);
        var second=screen(pos.east());
        return first!=null&&second!=null&&!first.inert()&&!second.inert()&&first.getRedstoneChannels().equals(bank)
            &&second.getRedstoneChannels().equals(bank)&&first.screenSettings().channelLevel()==value
            &&second.screenSettings().channelLevel()==value&&NativeScreenGroups.editMembers(first).size()==2;
    }
    private static void require(boolean valid,String why){if(!valid)throw new AssertionError(LABEL+" "+phase()+" "+why);}
    private static void fail(Throwable error){
        error.printStackTrace();
        NativeCopyService.fault=null;
        var server=world.getServer();
        world=null;
        server.halt(false);
    }
    private NativeCopyScreenRecoveryChecks(){}
}
