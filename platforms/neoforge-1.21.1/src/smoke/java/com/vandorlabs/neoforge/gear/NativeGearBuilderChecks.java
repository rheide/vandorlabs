package com.vandorlabs.neoforge.gear;

import com.mojang.authlib.GameProfile;
import com.vandorlabs.machines.CoreLayout;
import com.vandorlabs.neoforge.builders.*;
import com.vandorlabs.neoforge.credentials.NativeCredentialItems;
import com.vandorlabs.redstone.ChannelList;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.server.level.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

@EventBusSubscriber(modid="vandorlabs")
public final class NativeGearBuilderChecks {
    private static final String LABEL="[vandorlabs][gear-builder]";
    private static final BlockPos ROOT=new BlockPos(15,100,8), SOURCE=ROOT.east(2), TARGET=ROOT.east();
    private static final NativeBuilderDesigns.Design DESIGN=NativeBuilderDesigns.find("synthesis_tower");
    private static ServerLevel world;
    private static ServerPlayer actor;
    private static final UUID BUILDER_KEY=new UUID(0,255019);
    private static int stage,ticks;
    private static boolean movingChecked,faulted;
    private static boolean read(){return "read".equals(System.getProperty("vandorlabs.gearBuilderPhase"));}
    private static NativeGearEntity arm(){return NativeGearFormation.at(world,ROOT);}

    @SubscribeEvent public static void start(ServerStartedEvent event){
        if(System.getProperty("vandorlabs.gearBuilderPhase","").isEmpty())return;
        world=event.getServer().overworld();
        try{
            for(int x=0;x<=1;x++){
                world.setChunkForced(x,0,true);
                world.getChunk(x,0);
            }
            actor=new FakePlayer(world,new GameProfile(new UUID(0,255018),"BuilderExtender"));
            actor.setGameMode(GameType.SURVIVAL);
            actor.setPos(15.5,101,5);
            if(read()){
                var arm=arm();
                require(arm!=null&&arm.progress()==0&&arm.payload()!=null&&arm.payload().materialized(),"cold moved endpoint");
                require(unique(TARGET),"cold usable moved builder");
                click();
                stage=6;
                return;
            }
            world.setBlockAndUpdate(ROOT.west(),Blocks.STONE.defaultBlockState());
            var arms=new ItemStack(NativeGearBindings.ITEMS.get(NativeGearDefinition.EXTENDER.id),2);
            actor.setItemInHand(InteractionHand.MAIN_HAND,arms);
            for(var at:List.of(ROOT,ROOT.east())){
                var support=at.west();
                require(actor.gameMode.useItemOn(actor,world,arms,InteractionHand.MAIN_HAND,
                    new BlockHitResult(Vec3.atCenterOf(support).add(.5,0,0),Direction.EAST,support,false)).consumesAction(),
                    "ordinary supported Survival Extender placement");
            }
            require(arms.isEmpty(),"both arm sections consumed");
            var core=NativeBuilderBindings.CORES.get(DESIGN.id());
            world.setBlockAndUpdate(SOURCE,core.defaultBlockState());
            for(int x=0;x<DESIGN.width();x++)for(int y=0;y<DESIGN.height();y++)for(int z=0;z<DESIGN.depth();z++){
                var at=cell(SOURCE,x,y,z);
                if(!at.equals(SOURCE)&&layout().frame(x,y,z))
                    world.setBlockAndUpdate(at,NativeBuilderFormation.matter().defaultBlockState());
            }
            var builder=(NativeBuilderEntity)world.getBlockEntity(SOURCE);
            var savedBuilder=builder.saveWithFullMetadata(world.registryAccess());
            savedBuilder.putUUID("BuilderKey",BUILDER_KEY);
            builder.loadWithComponents(savedBuilder,world.registryAccess());
            require(NativeBuilderTransactions.request(builder,actor,true),"native Tower formation");
        }catch(Throwable error){fail(error);}
    }

    @SubscribeEvent public static void tick(ServerTickEvent.Post event){
        if(world==null)return;
        try{
            var arm=arm();
            require(++ticks<600,"bounded stroke "+(arm==null?"missing":arm.progress()+" "+arm.error()));
            if(stage==0){
                if(arm==null||!arm.controller()||arm.assembly().columnHeight()!=2||!unique(SOURCE))return;
                var builder=(NativeBuilderEntity)world.getBlockEntity(SOURCE);
                builder.configureRedstone(1,ChannelList.of(24));
                builder.setItem(3,NativeCredentialItems.dataChip(2));
                click();
                stage=1;
            }else if(stage==1&&arm.blocked()){
                var builder=(NativeBuilderEntity)world.getBlockEntity(SOURCE);
                require(arm.progress()==1&&arm.payload()==null&&arm.error().contains("Empty and disconnect")
                    &&ItemStack.matches(builder.getItem(3),NativeCredentialItems.dataChip(2)),"owned chip refusal leaves source usable");
                builder.removeItem(3,1);
                NativeGearJournal.testFault=point->{
                    if(point.equals("after-cell")&&!faulted){
                        faulted=true;
                        throw new IllegalStateException("Injected builder attachment publication failure");
                    }
                };
                if(!arm.target())click();
                click();
                stage=2;
            }else if(stage==2&&arm.blocked()&&!NativeGearJournal.busy(arm)){
                require(faulted&&arm.progress()==1&&arm.payload()==null&&unique(SOURCE),"caught rollback retained exact native frame");
                NativeGearJournal.testFault=null;
                click();
                stage=3;
            }else if(stage==3){
                require(!arm.blocked(),"retry accepted "+arm.error());
                if(arm.progress()>0&&arm.progress()<1&&arm.payload()!=null&&!arm.payload().materialized()){
                    require(!(world.getBlockEntity(SOURCE) instanceof NativeBuilderEntity),"no native original during stroke");
                    require(arm.payload().size()==DESIGN.width()*DESIGN.height()*DESIGN.depth(),"complete captured frame and interior");
                    require(arm.payloadView().cells().stream().anyMatch(c->c.state().getBlock() instanceof NativeBuilderCoreBlock
                        &&c.projection().getAllKeys().equals(Set.of("ConstructorCoreX","ConstructorCoreZ")))
                        &&arm.payloadView().cells().stream().noneMatch(c->c.projection().contains("BuilderKey")),
                        "public render metadata excludes ownership");
                    movingChecked=true;
                }
                if(arm.progress()==0&&arm.payload()!=null&&arm.payload().materialized()&&!NativeGearJournal.busy(arm)){
                    require(movingChecked&&unique(TARGET),"one complete moved endpoint");
                    finish("write");
                }
            }else if(stage==6){
                require(!arm.blocked(),"cold reversal accepted "+arm.error());
                if(arm.progress()==1&&arm.payload()!=null&&arm.payload().materialized()&&!NativeGearJournal.busy(arm)){
                    require(unique(SOURCE),"cold reversal returns the same usable native owner");
                    finish("read");
                }
            }
        }catch(Throwable error){fail(error);}
    }

    private static CoreLayout layout(){return new CoreLayout(DESIGN.width(),DESIGN.height(),DESIGN.depth(),Direction.NORTH.get3DDataValue(),0,0);}
    private static BlockPos cell(BlockPos root,int x,int y,int z){var layout=layout();return root.offset(layout.x(x,z),y,layout.z(x,z));}

    private static boolean unique(BlockPos anchor){
        if(!(world.getBlockEntity(anchor) instanceof NativeBuilderEntity builder)||!builder.available()||!builder.formed())return false;
        if(world.getBlockEntity(anchor.equals(TARGET)?SOURCE:TARGET) instanceof NativeBuilderEntity)return false;
        if(!builder.key().equals(BUILDER_KEY)||!builder.design().equals(DESIGN)||builder.processing())return false;
        for(int x=0;x<DESIGN.width();x++)for(int y=0;y<DESIGN.height();y++)for(int z=0;z<DESIGN.depth();z++)
            if(NativeBuilderBindings.ownerAt(world,cell(anchor,x,y,z))!=builder)return false;
        return stage==0||builder.activation()==1&&builder.channels().equals(ChannelList.of(24));
    }

    private static void click(){
        arm().authorize(actor);
        actor.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);
        require(actor.gameMode.useItemOn(actor,world,ItemStack.EMPTY,InteractionHand.MAIN_HAND,
            new BlockHitResult(Vec3.atCenterOf(ROOT).add(0,0,-.5),Direction.NORTH,ROOT,false)).consumesAction(),
            "ordinary player activation");
    }

    private static void finish(String phase){
        System.out.println(LABEL+" "+phase+" PASS completeFrame/oneOwner/realExtenderPlacement");
        var server=world.getServer();
        world=null;
        server.saveEverything(true,true,true);
        server.halt(false);
    }

    private static void require(boolean valid,String why){if(!valid)throw new AssertionError(LABEL+" "+stage+" "+why);}
    private static void fail(Throwable error){
        error.printStackTrace();
        NativeGearJournal.testFault=null;
        var server=world.getServer();
        world=null;
        server.halt(false);
    }

    private NativeGearBuilderChecks(){}
}
