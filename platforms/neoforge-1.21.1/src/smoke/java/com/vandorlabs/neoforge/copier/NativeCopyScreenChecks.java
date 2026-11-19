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
import com.vandorlabs.neoforge.redstone.NativeChannelMember;
import com.vandorlabs.neoforge.redstone.NativeRedstoneChannels;
import com.vandorlabs.screen.ScreenFamily;
import com.vandorlabs.screen.ScreenJoinGeometry;
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
    private static final List<ScreenFamily> PROFILES=List.of(ScreenFamily.SCREEN,ScreenFamily.CONSOLE,ScreenFamily.INPUT,
        ScreenFamily.HALF_INPUT,ScreenFamily.HALF_CONSOLE,ScreenFamily.HALF_DIAGONAL,ScreenFamily.DIAGONAL,ScreenFamily.DIAGONAL);
    private static BlockPos root,inputPos,outputPos,groupStep;
    private static int height,depth;
    private static ScreenFamily family;
    private static ServerLevel world;
    private static ServerPlayer actor;
    private static NativeCopyControllerEntity copy;
    private static ChestBlockEntity supply;
    private static int ticks,stage,writes,artwork,profile;
    private static final int SHIFT=37;
    private static int sourceLevel,targetLevel;
    private static NativeChannelMember emitter;

    @SubscribeEvent
    public static void start(ServerStartedEvent event){
        if(!Boolean.getBoolean("vandorlabs.copyScreensCheck"))return;
        world=event.getServer().overworld();
        try{
            for(int x=0;x<=1;x++)for(int z=0;z<=12;z++){
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
        boolean diagonal=family==ScreenFamily.DIAGONAL;
        groupStep=diagonal?joinStep(2):new BlockPos(1,0,0);
        height=diagonal?2:1;
        depth=diagonal?2:1;
        int startZ=groupStep.getZ()<0?1:0;
        inputPos=root.east().above().south(startZ);
        outputPos=root.west(2).above().south(startZ);
        actor.setPos(root.getX()+.5,root.getY(),root.getZ()-1.5);
        world.setBlockAndUpdate(root,NativeCopyBindings.BLOCK.defaultBlockState());
        world.setBlockAndUpdate(root.south(),Blocks.CHEST.defaultBlockState());
        copy=(NativeCopyControllerEntity)world.getBlockEntity(root);
        supply=(ChestBlockEntity)world.getBlockEntity(root.south());
        for(int x=1;x<=2;x++)for(int z=0;z<depth;z++){
            world.setBlockAndUpdate(root.east(x).south(z),NativeSurfaceBindings.BLOCK.defaultBlockState());
            world.setBlockAndUpdate(root.west(x).south(z),NativeSurfaceBindings.BLOCK.defaultBlockState());
        }
        screen(inputPos);
        screen(inputPos.offset(groupStep));
        var item=NativeScreenRegistrations.ITEMS.get(family).get();
        supply.setItem(0,new ItemStack(item,3));
        var reserved=new ItemStack(item);
        var custom=new CompoundTag();
        custom.putString("PrivateCopyProof","retained");
        reserved.set(DataComponents.CUSTOM_DATA,CustomData.of(custom));
        supply.setItem(1,reserved);
        sourceLevel=15;
        targetLevel=9;
        var issuingTile=supply;
        int sourceChannel=sourceBank().first(),targetChannel=targetBank().first();
        emitter=new NativeChannelMember(){
            public net.minecraft.world.level.block.entity.BlockEntity channelBlockEntity(){return issuingTile;}
            public ChannelList getRedstoneChannels(){return ChannelList.EMPTY;}
            public ChannelList transmittedChannels(){return ChannelList.of(sourceChannel,targetChannel);}
            public int transmissionRange(){return 16;}
            public int localSignalLevel(int channel){return channel==sourceChannel?sourceLevel:targetLevel;}
            public void setChannelLevel(int ignored){}
        };
        NativeRedstoneChannels.flush(world);
        NativeRedstoneChannels.channelsChanged(emitter);
        ticks=stage=writes=0;
    }

    @SubscribeEvent
    public static void tick(ServerTickEvent.Post event){
        if(world==null)return;
        try{
            require(++ticks<350,"bounded asynchronous copy "+copy.status()+" "+copy.error());
            if(stage==0){
                require(copy.configure(actor,new CopySettings(height,SHIFT,0,ChannelList.EMPTY,false)),"configure paired scan");
                var external=inputPos.offset(groupStep.multiply(2));
                screen(external);
                refuse("complete Screen group");
                world.removeBlock(external,false);
                if(family==ScreenFamily.DIAGONAL){
                    var externalTarget=outputPos.offset(groupStep.multiply(2));
                    screen(externalTarget);
                    refuse("complete Screen group");
                    world.removeBlock(externalTarget,false);
                }

                var input=(NativeScreenEntity)world.getBlockEntity(inputPos);
                require(input.screenSettings().channelLevel()==15,"live original receives its authored channel");
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
                require(NativeScreenGroups.editMembers(input).size()==groupSize(),"expected source join available");
                supply.getItem(0).setCount(1);
                refuse("Missing material",1);
                supply.getItem(0).setCount(3);
                NativeCopyService.fault=(header,cell)->{
                    if(!(world.getBlockEntity(outputPos) instanceof NativeScreenEntity output))return;
                    NativeRedstoneChannels.flush(world);
                    if(writes==0){
                        require(output.screenSettings().channelLevel()==0&&!output.isChannelMemberActive()
                            &&output.getRedstoneChannels().equals(targetBank()),"held output starts with a zero received level and shifted bank");
                        require(!input.isChannelMemberActive(),"source receivers frozen for the same lease");
                        sourceLevel=6;
                        targetLevel=12;
                        NativeRedstoneChannels.outputChanged(emitter);
                        require(input.screenSettings().channelLevel()==15&&output.screenSettings().channelLevel()==0,
                            "external power change cannot mutate held before/after images");
                        writes++;
                        throw new IllegalStateException("Expected staged Screen copy failure");
                    }
                    require(output.screenSettings().channelLevel()==0&&!output.isChannelMemberActive(),"retry remains frozen while held");
                };
                copy.request(actor);
                stage=1;
            }else if(stage==1&&!copy.busy()){
                if(receiver(inputPos).screenSettings().channelLevel()!=6)return;
                require(writes==1&&copy.status().equals("Copy rolled back")&&world.getBlockState(outputPos).isAir()
                    &&world.getBlockState(outputPos.offset(groupStep)).isAir(),"caught rollback cleared outputs");
                require(supply.getItem(0).getCount()==3&&privateMaterialRemains(),"all exact materials refunded");
                require(world.getEntitiesOfClass(ItemEntity.class,new AABB(root).inflate(10)).isEmpty(),"rollback produced no item drops");
                copy.request(actor);
                stage=2;
            }else if(stage==2&&!copy.busy()){
                require(copy.status().equals("Copy complete"),"retry completed "+copy.error());
                var output=receiver(outputPos);
                if(output!=null&&output.screenSettings().channelLevel()!=12)return;
                require(output!=null&&!output.inert()&&NativeScreenGroups.editMembers(output).size()==groupSize(),"translated Screen pair really joins");
                for(var at:List.of(outputPos,outputPos.offset(groupStep))){
                    var screen=(NativeScreenEntity)world.getBlockEntity(at);
                    require(screen.getRedstoneChannels().equals(targetBank())&&screen.screenSettings().channelLevel()==12
                        &&screen.localOutput()==0&&screen.screenSettings().primarySurface()==artwork&&!screen.visual().frames().displayFit
                        &&screen.getBlockState()==state()&&screen.family()==family
                        &&(!family.secondary||screen.screenSettings().secondarySurface()==artwork),"exact face, artwork and frame fit");
                }
                require(supply.getItem(0).getCount()==1&&privateMaterialRemains(),"pay two plain items and retain configured item");
                require(NativeScreenGroups.editMembers((NativeScreenEntity)world.getBlockEntity(inputPos)).size()==groupSize(),"source join remains intact");
                require(receiver(inputPos).getRedstoneChannels().equals(sourceBank())&&receiver(inputPos).screenSettings().channelLevel()==6,
                    "source still receives its own channel");
                NativeRedstoneChannels.unregister(emitter);
                require(receiver(inputPos).screenSettings().channelLevel()==0&&output.screenSettings().channelLevel()==0,
                    "both groups release received power when the independent transmitter is removed");
                System.out.println(MARKER+" profile PASS "+family);
                NativeCopyService.fault=null;
                if(++profile<PROFILES.size())setup();
                else{
                    System.out.println(MARKER+" PASS externalNeighbors/privateChipRefusal/rollback/retry/heldReceiverBank/realJoins/exactMaterials");
                    world=null;
                    event.getServer().halt(false);
                }
            }
        }catch(Throwable error){fail(error);}
    }

    private static ChannelList sourceBank(){return ChannelList.of(200+100*profile,204+100*profile);}
    private static ChannelList targetBank(){var bank=sourceBank();return ChannelList.of(bank.get(0)+SHIFT,bank.get(1)+SHIFT);}
    private static NativeScreenEntity receiver(BlockPos pos){return (NativeScreenEntity)world.getBlockEntity(pos);}

    private static int groupSize(){return ScreenJoinGeometry.supports(family,false)?2:1;}

    private static BlockPos joinStep(int step){
        var state=state();
        var block=(NativeScreenBlock)state.getBlock();
        var basis=ScreenJoinGeometry.basis(family,block.screenState(state),false);
        return new BlockPos(basis.dx(step),basis.dy(step),basis.dz(step));
    }

    private static net.minecraft.world.level.block.state.BlockState state(){
        var state=NativeScreenRegistrations.BLOCKS.get(family).get().defaultBlockState();
        if(family.keyboard)return state.setValue(NativeScreenBlock.HORIZONTAL_FACING,Direction.NORTH)
            .setValue(NativeScreenBlock.KEYBOARD,true).setValue(NativeScreenBlock.UPPER,true);
        if(family==ScreenFamily.DIAGONAL)return state.setValue(NativeScreenBlock.HORIZONTAL_FACING,Direction.NORTH)
            .setValue(NativeScreenBlock.INVERTED,profile==PROFILES.size()-1);
        if(family==ScreenFamily.HALF_DIAGONAL)state=state.setValue(NativeScreenBlock.UPPER,true);
        return state.setValue(NativeScreenBlock.FACING,Direction.NORTH);
    }

    private static void screen(BlockPos pos){
        world.setBlockAndUpdate(pos,state());
        var tile=(NativeScreenEntity)world.getBlockEntity(pos);
        var data=tile.saveWithFullMetadata(world.registryAccess());
        data.putIntArray("RedstoneChannels",sourceBank().toArray());
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
