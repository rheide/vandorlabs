package com.vandorlabs.neoforge.copier;

import com.mojang.authlib.GameProfile;
import com.vandorlabs.copier.core.CopySettings;
import com.vandorlabs.machines.CoreLayout;
import com.vandorlabs.neoforge.builders.*;
import com.vandorlabs.neoforge.credentials.NativeCredentialItems;
import com.vandorlabs.neoforge.screen.NativeChipJournal;
import com.vandorlabs.neoforge.surface.NativeSurfaceBindings;
import com.vandorlabs.redstone.ChannelList;
import java.util.List;
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

@EventBusSubscriber(modid="vandorlabs")
public final class NativeCopyBuilderChecks {
    private static final String MARKER="[vandorlabs][copy-builders]";
    private static final List<String> PROFILES=List.of("synthesis_tower","assembly_press","enclosed_gantry");
    private static ServerLevel world;
    private static ServerPlayer actor;
    private static NativeCopyControllerEntity copy;
    private static NativeBuilderDesigns.Design design;
    private static BlockPos root,input,output;
    private static ChestBlockEntity supply;
    private static int stage,profile,ticks,matter,writes;

    @SubscribeEvent
    public static void start(ServerStartedEvent event){
        if(!Boolean.getBoolean("vandorlabs.copyBuildersCheck"))return;
        world=event.getServer().overworld();
        try{
            for(int x=0;x<=1;x++)for(int z=0;z<=4;z++){
                world.setChunkForced(x,z,true);
                world.getChunk(x,z);
            }
            actor=new FakePlayer(world,new GameProfile(new UUID(0,0x436f7042),"BuilderCopy"));
            actor.setGameMode(GameType.SURVIVAL);
            setup();
        }catch(Throwable error){fail(error);}
    }

    private static void setup(){
        design=NativeBuilderDesigns.find(PROFILES.get(profile));
        root=new BlockPos(15,100,8+profile*24);
        input=root.east().above();
        output=root.west(design.width()).above();
        actor.setPos(root.getX()+.5,root.getY(),root.getZ()-1.5);
        world.setBlockAndUpdate(root,NativeCopyBindings.BLOCK.defaultBlockState());
        world.setBlockAndUpdate(root.south(),Blocks.CHEST.defaultBlockState());
        copy=(NativeCopyControllerEntity)world.getBlockEntity(root);
        supply=(ChestBlockEntity)world.getBlockEntity(root.south());
        for(int x=0;x<design.width();x++)for(int z=0;z<design.depth();z++){
            world.setBlockAndUpdate(root.east(x+1).south(z),NativeSurfaceBindings.BLOCK.defaultBlockState());
            world.setBlockAndUpdate(root.west(x+1).south(z),NativeSurfaceBindings.BLOCK.defaultBlockState());
        }
        var core=NativeBuilderBindings.CORES.get(design.id());
        world.setBlockAndUpdate(input,core.defaultBlockState().setValue(NativeBuilderCoreBlock.FACING,Direction.NORTH));
        var source=(NativeBuilderEntity)world.getBlockEntity(input);
        var layout=new CoreLayout(design.width(),design.height(),design.depth(),Direction.NORTH.get3DDataValue(),0,0);
        matter=0;
        for(int x=0;x<design.width();x++)for(int y=0;y<design.height();y++)for(int z=0;z<design.depth();z++){
            var pos=input.offset(layout.x(x,z),y,layout.z(x,z));
            if(!pos.equals(input)&&layout.frame(x,y,z)){
                world.setBlockAndUpdate(pos,NativeBuilderFormation.matter().defaultBlockState());
                matter++;
            }
        }
        supply.setItem(0,new ItemStack(core,2));
        supply.setItem(1,new ItemStack(NativeBuilderFormation.matter(),matter+1));
        require(NativeBuilderTransactions.request(source,actor,true),"native formation queued");
        ticks=stage=writes=0;
    }

    @SubscribeEvent
    public static void tick(ServerTickEvent.Post event){
        if(world==null)return;
        try{
            require(++ticks<450,"bounded asynchronous copy "+copy.status()+" "+copy.error());
            var source=(NativeBuilderEntity)world.getBlockEntity(input);
            if(stage==0&&source!=null&&source.formed()&&source.available()){
                source.configureRedstone(1,ChannelList.of(51));
                if(source.choices()>1)source.selectDesign(1);
                var chip=NativeCredentialItems.dataChip(2);
                NativeChipJournal.write(chip,true,"Private title",new String[]{"Physical builder source chip"});
                source.setItem(3,chip);
                require(ItemStack.matches(source.getItem(3),chip),"fixture physical Builder chip installed");
                require(copy.configure(actor,new CopySettings(design.height(),37,0,ChannelList.EMPTY,false)),"configure full scan");
                refuse("Empty and disconnect");
                require(ItemStack.matches(source.getItem(3),chip),"refusal left physical chip exact");
                source.removeItem(3,1);
                require(copy.configure(actor,new CopySettings(1,37,0,ChannelList.EMPTY,false)),"configure partial scan");
                refuse("entire builder frame");
                require(copy.configure(actor,new CopySettings(design.height(),37,0,ChannelList.EMPTY,false)),"configure complete scan");
                NativeCopyService.fault=(header,cell)->{
                    if(writes==0&&world.getBlockEntity(output) instanceof NativeBuilderEntity
                        &&world.getBlockEntity(output.east()) instanceof NativeBuilderPartEntity){
                        writes++;
                        throw new IllegalStateException("Expected staged builder frame failure");
                    }
                };
                copy.request(actor);
                stage=1;
            }else if(stage==1&&!copy.busy()){
                require(writes==1&&copy.status().equals("Copy rolled back")&&world.getBlockState(output).isAir()
                    &&world.getBlockState(output.east()).isAir(),"protected root and part removed after caught rollback");
                require(supply.getItem(0).getCount()==2&&supply.getItem(1).getCount()==matter+1,"full exact refund");
                require(world.getEntitiesOfClass(ItemEntity.class,new AABB(root).inflate(12)).isEmpty(),"no duplicate loose items");
                copy.request(actor);
                stage=2;
            }else if(stage==2&&!copy.busy()){
                require(copy.status().equals("Copy complete"),"retry completed "+copy.error());
                var target=(NativeBuilderEntity)world.getBlockEntity(output);
                require(target!=null&&target.available()&&target.formed()&&target.design().equals(design)
                    &&target.facing()==source.facing()&&!target.key().equals(source.key())&&target.isEmpty()
                    &&target.activation()==source.activation()&&target.selectedDesign()==source.selectedDesign()
                    &&target.channels().equals(ChannelList.of(88)),
                    "fresh owner retains configured builder facing and translated channel");
                require(source.available()&&source.channels().equals(ChannelList.of(51)),"original owner remains unchanged");
                for(int x=0;x<design.width();x++)for(int y=0;y<design.height();y++)for(int z=0;z<design.depth();z++){
                    var a=source.cell(x,y,z);
                    var b=target.cell(x,y,z);
                    require(NativeBuilderBindings.ownerAt(world,a)==source&&NativeBuilderBindings.ownerAt(world,b)==target,
                        "every frame/interior member resolves its own root");
                }
                require(supply.getItem(0).getCount()==1&&supply.getItem(1).getCount()==1,"pay only one core and physical frame matter");
                require(world.getEntitiesOfClass(ItemEntity.class,new AABB(root).inflate(12)).isEmpty(),"no loose rollback resources");
                System.out.println(MARKER+" profile PASS "+design.id()+" paidMatter="+matter);
                NativeCopyService.fault=null;
                if(++profile<PROFILES.size())setup();
                else{
                    System.out.println(MARKER+" PASS nativeFormation/chipRefusal/partialRefusal/rollback/retry/exactFrameMaterials");
                    world=null;
                    event.getServer().halt(false);
                }
            }
        }catch(Throwable error){fail(error);}
    }

    private static void refuse(String fragment){
        try{
            copy.request(actor);
            throw new AssertionError("Unsafe Copy accepted");
        }catch(IllegalArgumentException expected){
            require(expected.getMessage().contains(fragment),"expected "+fragment+" but got "+expected.getMessage());
        }
        require(!copy.busy()&&supply.getItem(0).getCount()==2&&supply.getItem(1).getCount()==matter+1,"refusal holds no resources");
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

    private NativeCopyBuilderChecks(){}
}
