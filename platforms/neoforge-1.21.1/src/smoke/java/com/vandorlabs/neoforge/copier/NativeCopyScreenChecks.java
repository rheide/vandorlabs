package com.vandorlabs.neoforge.copier;

import com.mojang.authlib.GameProfile;
import com.vandorlabs.copier.core.CopySettings;
import com.vandorlabs.neoforge.credentials.NativeCredentialItems;
import com.vandorlabs.neoforge.credentials.NativeCredentialReaderBlock;
import com.vandorlabs.neoforge.credentials.NativeCredentialReaderEntity;
import com.vandorlabs.neoforge.credentials.NativeReaderRegistrations;
import com.vandorlabs.neoforge.screen.*;
import com.vandorlabs.neoforge.surface.NativeSurfaceBindings;
import com.vandorlabs.redstone.ChannelList;
import com.vandorlabs.screen.ScreenFamily;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
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
public final class NativeCopyScreenChecks {
    private static final String MARKER="[vandorlabs][copy-screens]";
    private static final List<ScreenFamily> PROFILES=List.of(ScreenFamily.SCREEN,ScreenFamily.CONSOLE,ScreenFamily.INPUT);
    private static BlockPos root,inputPos,outputPos;
    private static ScreenFamily family;
    private static ServerLevel world;
    private static ServerPlayer actor;
    private static NativeCopyControllerEntity copy;
    private static ChestBlockEntity supply;
    private static int ticks,stage,writes,artwork,profile;

    @SubscribeEvent
    public static void start(ServerStartedEvent event){
        if(!Boolean.getBoolean("vandorlabs.copyScreensCheck"))return;
        world=event.getServer().overworld();
        try{
            for(int x=0;x<=1;x++)for(int z=0;z<=4;z++){
                world.setChunkForced(x,z,true);
                world.getChunk(x,z);
            }
            actor=new FakePlayer(world,new GameProfile(new UUID(0,0x436f7053),"ScreenCopy"));
            actor.setGameMode(GameType.SURVIVAL);
            artwork=NativeScreenCatalog.INSTANCE.sourceChoice("console_inputs/console_controls_biology_controls_static");
            require(artwork>=0,"fixture artwork available");
            setup();
        }catch(Throwable error){fail(error);}
    }

    private static void setup(){
        family=PROFILES.get(profile);
        root=new BlockPos(15,100,8+profile*24);
        inputPos=root.east().above();
        outputPos=root.west(2).above();
        actor.setPos(root.getX()+.5,root.getY(),root.getZ()-1.5);
        world.setBlockAndUpdate(root,NativeCopyBindings.BLOCK.defaultBlockState());
        world.setBlockAndUpdate(root.south(),Blocks.CHEST.defaultBlockState());
        copy=(NativeCopyControllerEntity)world.getBlockEntity(root);
        supply=(ChestBlockEntity)world.getBlockEntity(root.south());
        for(int x=1;x<=2;x++){
            world.setBlockAndUpdate(root.east(x),NativeSurfaceBindings.BLOCK.defaultBlockState());
            world.setBlockAndUpdate(root.west(x),NativeSurfaceBindings.BLOCK.defaultBlockState());
        }
        screen(inputPos);
        screen(inputPos.east());
        var item=NativeScreenRegistrations.ITEMS.get(family).get();
        supply.setItem(0,new ItemStack(item,3));
        var reserved=new ItemStack(item);
        var custom=new CompoundTag();
        custom.putString("PrivateCopyProof","retained");
        reserved.set(DataComponents.CUSTOM_DATA,CustomData.of(custom));
        supply.setItem(1,reserved);
        ticks=stage=writes=0;
    }

    @SubscribeEvent
    public static void tick(ServerTickEvent.Post event){
        if(world==null)return;
        try{
            require(++ticks<350,"bounded asynchronous copy "+copy.status()+" "+copy.error());
            if(stage==0){
                require(copy.configure(actor,new CopySettings(1,0,0,ChannelList.EMPTY,false)),"configure paired scan");
                screen(inputPos.east(2));
                refuse("complete Screen group");
                world.removeBlock(inputPos.east(2),false);
                if(profile==0){
                    var diagonal=NativeScreenRegistrations.BLOCKS.get(ScreenFamily.DIAGONAL).get();
                    world.setBlockAndUpdate(inputPos.east(),diagonal.defaultBlockState());
                    refuse("stationary Screens, Consoles and full Inputs");
                    screen(inputPos.east());
                }

                var input=(NativeScreenEntity)world.getBlockEntity(inputPos);
                var clean=input.saveWithFullMetadata(world.registryAccess());
                var credential=clean.copy();
                credential.putInt("PrimarySurfaceTexture",-4);
                var chip=NativeCredentialItems.dataChip(2);
                NativeChipJournal.write(chip,true,"Private title",new String[]{"Physical source chip"});
                var reader=new CompoundTag();
                reader.put("InsertedToken",NativeCredentialItems.writeStack(chip,world.registryAccess()));
                credential.put("ScreenChipReader",reader);
                input.loadWithComponents(credential,world.registryAccess());
                require(!input.inert(),"credential fixture is a supported native owner");
                var before=input.saveWithFullMetadata(world.registryAccess());
                require(before.getCompound("ScreenChipReader").contains("InsertedToken"),"physical token captured");
                refuse("contains credentials");
                require(before.equals(input.saveWithFullMetadata(world.registryAccess())),"refusal leaves physical token exact");
                input.loadWithComponents(clean,world.registryAccess());

                var adjacent=outputPos.west();
                var mountBlock=NativeReaderRegistrations.DATA_CHIP_SLOT.get();
                world.setBlockAndUpdate(adjacent,mountBlock.defaultBlockState().setValue(NativeCredentialReaderBlock.FACING,Direction.WEST));
                var mount=(NativeCredentialReaderEntity)world.getBlockEntity(adjacent);
                mount.setItem(0,chip);
                require(ItemStack.matches(mount.getItem(0),chip),"fixture mount owns the chip");
                refuse("adjacent credential readers");
                require(ItemStack.matches(mount.getItem(0),chip),"external chip remains exact after refusal");
                mount.removeItem(0,1);
                world.removeBlock(adjacent,false);
                require(NativeScreenGroups.editMembers(input).size()==2,"real source join available");
                supply.getItem(0).setCount(1);
                refuse("Missing material",1);
                supply.getItem(0).setCount(3);
                NativeCopyService.fault=(header,cell)->{
                    if(writes==0&&world.getBlockEntity(outputPos) instanceof NativeScreenEntity){
                        writes++;
                        throw new IllegalStateException("Expected staged Screen copy failure");
                    }
                };
                copy.request(actor);
                stage=1;
            }else if(stage==1&&!copy.busy()){
                require(writes==1&&copy.status().equals("Copy rolled back")&&world.getBlockState(outputPos).isAir()
                    &&world.getBlockState(outputPos.east()).isAir(),"caught rollback cleared outputs");
                require(supply.getItem(0).getCount()==3&&privateMaterialRemains(),"all exact materials refunded");
                require(world.getEntitiesOfClass(ItemEntity.class,new AABB(root).inflate(10)).isEmpty(),"rollback produced no item drops");
                copy.request(actor);
                stage=2;
            }else if(stage==2&&!copy.busy()){
                require(copy.status().equals("Copy complete"),"retry completed "+copy.error());
                var output=(NativeScreenEntity)world.getBlockEntity(outputPos);
                require(output!=null&&!output.inert()&&NativeScreenGroups.editMembers(output).size()==2,"translated Screen pair really joins");
                for(var screen:NativeScreenGroups.editMembers(output)){
                    require(screen.screenSettings().primarySurface()==artwork&&!screen.visual().frames().displayFit
                        &&screen.getBlockState()==state()&&screen.family()==family
                        &&(!family.secondary||screen.screenSettings().secondarySurface()==artwork),"exact face, artwork and frame fit");
                }
                require(supply.getItem(0).getCount()==1&&privateMaterialRemains(),"pay two plain items and retain configured item");
                require(NativeScreenGroups.editMembers((NativeScreenEntity)world.getBlockEntity(inputPos)).size()==2,"source join remains intact");
                System.out.println(MARKER+" profile PASS "+family);
                NativeCopyService.fault=null;
                if(++profile<PROFILES.size())setup();
                else{
                    System.out.println(MARKER+" PASS externalNeighbors/privateChipRefusal/rollback/retry/realJoins/exactMaterials");
                    world=null;
                    event.getServer().halt(false);
                }
            }
        }catch(Throwable error){fail(error);}
    }

    private static net.minecraft.world.level.block.state.BlockState state(){
        var state=NativeScreenRegistrations.BLOCKS.get(family).get().defaultBlockState();
        return family.keyboard?state.setValue(NativeScreenBlock.HORIZONTAL_FACING,Direction.NORTH)
            .setValue(NativeScreenBlock.KEYBOARD,true).setValue(NativeScreenBlock.UPPER,true)
            :state.setValue(NativeScreenBlock.FACING,Direction.NORTH);
    }

    private static void screen(BlockPos pos){
        world.setBlockAndUpdate(pos,state());
        var tile=(NativeScreenEntity)world.getBlockEntity(pos);
        var data=tile.saveWithFullMetadata(world.registryAccess());
        data.putInt("PrimarySurfaceTexture",artwork);
        if(family.secondary)data.putInt("SecondarySurfaceTexture",artwork);
        data.putBoolean("DisplayFit",false);
        tile.loadWithComponents(data,world.registryAccess());
        require(!tile.inert(),"fixture Screen loads");
    }

    private static boolean privateMaterialRemains(){
        return supply.getItem(1).getCount()==1
            &&supply.getItem(1).getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY)
                .copyTag().getString("PrivateCopyProof").equals("retained");
    }

    private static void refuse(String fragment){refuse(fragment,3);}

    private static void refuse(String fragment,int plain){
        try{
            copy.request(actor);
            throw new AssertionError("Unsafe Copy accepted");
        }catch(IllegalArgumentException expected){
            require(expected.getMessage().contains(fragment),"expected "+fragment+" but got "+expected.getMessage());
        }
        require(!copy.busy()&&supply.getItem(0).getCount()==plain&&privateMaterialRemains(),"refusal holds no resources");
    }

    private static void require(boolean valid,String why){
        if(!valid)throw new AssertionError(MARKER+" family="+family+" stage="+stage+" "+why);
    }

    private static void fail(Throwable error){
        error.printStackTrace();
        var server=world.getServer();
        world=null;
        NativeCopyService.fault=null;
        server.halt(false);
    }

    private NativeCopyScreenChecks(){}
}
