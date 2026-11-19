package com.vandorlabs.neoforge.copier;

import com.mojang.authlib.GameProfile;
import com.vandorlabs.copier.core.CopySettings;
import com.vandorlabs.machines.CoreLayout;
import com.vandorlabs.neoforge.navigation.observer.*;
import com.vandorlabs.neoforge.surface.NativeSurfaceBindings;
import com.vandorlabs.redstone.ChannelList;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

@EventBusSubscriber(modid = "vandorlabs")
public final class NativeCopyObserverChecks {
    private static final String MARKER = "[vandorlabs][copy-observers]";
    private static ServerLevel world;
    private static ServerPlayer actor;
    private static NativeCopyControllerEntity copy;
    private static NativeObserverDesigns.Design design;
    private static NativeObserverEntity source;
    private static BlockPos root, input, output;
    private static ChestBlockEntity supply;
    private static int stage, profile, ticks, matter, writes;
    private static boolean inverted;

    @SubscribeEvent
    public static void start(ServerStartedEvent event) {
        if (!Boolean.getBoolean("vandorlabs.copyObserversCheck")) return;
        world = event.getServer().overworld();
        try {
            for (int x=0; x<=1; x++) for (int z=0; z<=2; z++) {
                world.setChunkForced(x,z,true);
                world.getChunk(x,z);
            }
            actor = new FakePlayer(world, new GameProfile(new UUID(0,0x436f6273), "ObserverCopy"));
            actor.setGameMode(GameType.SURVIVAL);
            setup();
        } catch (Throwable error) {
            fail(error);
        }
    }

    private static void setup() {
        var template=NativeObserverDesigns.ALL.get(profile);
        design=NativeObserverDesigns.scaled(template.id(),0);
        inverted=profile==1;
        root=new BlockPos(15,100,8+profile*24);
        input=root.east().above(inverted?design.height():1);
        output=root.west(design.width()).above(inverted?design.height():1);
        actor.setPos(root.getX()+.5,root.getY(),root.getZ()-1.5);
        world.setBlockAndUpdate(root,NativeCopyBindings.BLOCK.defaultBlockState());
        world.setBlockAndUpdate(root.south(),Blocks.CHEST.defaultBlockState());
        copy=(NativeCopyControllerEntity)world.getBlockEntity(root);
        supply=(ChestBlockEntity)world.getBlockEntity(root.south());
        for(int x=0;x<design.width();x++)for(int z=0;z<design.depth();z++){
            world.setBlockAndUpdate(root.east(x+1).south(z),NativeSurfaceBindings.BLOCK.defaultBlockState());
            world.setBlockAndUpdate(root.west(x+1).south(z),NativeSurfaceBindings.BLOCK.defaultBlockState());
        }
        var core=NativeObserverBindings.CORES.get(design.id());
        world.setBlockAndUpdate(input,core.defaultBlockState().setValue(NativeObserverCoreBlock.FACING,Direction.NORTH));
        source=(NativeObserverEntity)world.getBlockEntity(input);
        var layout=new CoreLayout(design.width(),design.height(),design.depth(),Direction.NORTH.get3DDataValue(),0,0);
        matter=0;
        for(int x=0;x<design.width();x++)for(int y=0;y<design.height();y++)for(int z=0;z<design.depth();z++){
            var pos=input.offset(layout.x(x,z),inverted?-y:y,layout.z(x,z));
            if(!pos.equals(input)&&layout.frame(x,y,z)){
                world.setBlockAndUpdate(pos,NativeObserverFormation.matter().defaultBlockState());
                matter++;
            }
        }
        source.configure(ChannelList.of(51),64);
        source.setRadar(!inverted);
        supply.setItem(0,new ItemStack(core,2));
        require(matter+1<=64,"base design cost fits fixture slot");
        supply.setItem(1,new ItemStack(NativeObserverFormation.matter(),matter+1));
        require(NativeObserverTransactions.request(source,actor,true),"ordinary native formation was queued");
        writes=0;
        ticks=0;
        stage=0;
    }

    @SubscribeEvent
    public static void tick(ServerTickEvent.Post event) {
        if(world==null)return;
        try {
            require(++ticks<400,"bounded asynchronous copy; status "+copy.status()+" "+copy.error());
            if(stage==0&&world.getBlockEntity(input) instanceof NativeObserverEntity current)source=current;
            if(stage==0 && source.formed() && source.available()){
                require(source.inverted()==inverted,"native formation used the intended orientation");
                require(copy.configure(actor,new CopySettings(1,37,0,ChannelList.EMPTY,false)),"configure partial scan");
                try {
                    copy.request(actor);
                    throw new AssertionError("Partial scan was accepted");
                }catch(IllegalArgumentException expected){
                    require(expected.getMessage().contains("observer"),"partial frame diagnostic: "+expected.getMessage());
                }
                require(supply.getItem(1).getCount()==matter+1&&!copy.busy(),"partial scan pays nothing");
                require(copy.configure(actor,new CopySettings(design.height(),37,0,ChannelList.EMPTY,false)),"configure full scan");
                NativeCopyService.fault=(header,cell)->{
                    if(writes!=0 || !(world.getBlockEntity(output) instanceof NativeObserverEntity))return;
                    if(!(world.getBlockState(output.east()).getBlock() instanceof NativeObserverPartBlock))return;
                    writes++;
                    throw new IllegalStateException("Expected staged observer copy failure");
                };
                copy.request(actor);
                stage=1;
            }else if(stage==1&&!copy.busy()){
                require(writes==1&&copy.status().equals("Copy rolled back")&&world.getBlockState(output).isAir()
                    &&world.getBlockState(output.east()).isAir(),"caught rollback removed formed core and protected parts");
                require(supply.getItem(0).getCount()==2&&supply.getItem(1).getCount()==matter+1,"full exact material refund");
                require(world.getEntitiesOfClass(ItemEntity.class,new AABB(root).inflate(12)).isEmpty(),"no physical frame drops on rollback");
                copy.request(actor);
                stage=2;
            }else if(stage==2&&!copy.busy()){
                require(copy.status().equals("Copy complete"),"retry completed");
                var placed=(NativeObserverEntity)world.getBlockEntity(output);
                require(placed!=null&&placed.formed()&&placed.available()&&placed.design().equals(design)
                    &&placed.inverted()==inverted&&placed.facing()==source.facing()&&placed.radar()==source.radar()
                    &&placed.range()==64&&placed.channels().equals(ChannelList.of(88))&&!placed.key().equals(source.key()),
                    "output has its own identity and same layout/settings, with configured channel increment");
                require(source.available()&&source.channels().equals(ChannelList.of(51)),"source retains its physical identity and channel");
                for(int x=0;x<design.width();x++)for(int y=0;y<design.height();y++)for(int z=0;z<design.depth();z++){
                    require(NativeObserverAccess.member(source,source.cell(x,y,z))&&NativeObserverAccess.member(placed,placed.cell(x,y,z)),
                        "both formed closures resolve all relative member references");
                }
                require(supply.getItem(0).getCount()==1&&supply.getItem(1).getCount()==1,"pay one Core and only physical frame Matter");
                require(NativeNavigationSources.matching(world,root,ChannelList.of(88)).stream().anyMatch(a->a.stationary()==placed),
                    "copied observer is discoverable by actual navigation");
                require(world.getEntitiesOfClass(ItemEntity.class,new AABB(root).inflate(12)).isEmpty(),"no duplicated components after retry");
                System.out.println(MARKER+" profile PASS "+design.id()+" inverted="+inverted+" paidMatter="+matter);
                NativeCopyService.fault=null;
                if(++profile<NativeObserverDesigns.ALL.size())setup();
                else {
                    System.out.println(MARKER+" PASS nativeFormation/partialRefusal/caughtRollback/retry/freshIdentity/exactCosts");
                    world=null;
                    event.getServer().halt(false);
                }
            }
        }catch(Throwable error){fail(error);}
    }

    private static void require(boolean valid,String why){
        if(!valid)throw new AssertionError(MARKER+" profile="+profile+" stage="+stage+" "+why);
    }

    private static void fail(Throwable error){
        error.printStackTrace();
        var server=world.getServer();
        world=null;
        NativeCopyService.fault=null;
        server.halt(false);
    }

    private NativeCopyObserverChecks(){}
}
