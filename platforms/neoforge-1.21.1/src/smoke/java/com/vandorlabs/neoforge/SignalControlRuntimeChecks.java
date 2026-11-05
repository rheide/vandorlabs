package com.vandorlabs.neoforge;

import com.google.gson.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import com.vandorlabs.persistence.*;
import com.vandorlabs.redstone.ChannelList;
import com.vandorlabs.neoforge.redstone.*;
import io.netty.buffer.Unpooled;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.*;
import net.minecraft.world.level.block.*;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

/** Actual installed numeric controls, source transitions, atomic drafts and cross-JVM persistence. */
@EventBusSubscriber(modid="vandorlabs")
public final class SignalControlRuntimeChecks {
    private static final BlockPos POS=new BlockPos(4600,100,4600);private static ServerLevel world;private static int checks;
    private static void require(boolean value,String message){checks++;if(!value)throw new IllegalStateException("Native signal control: "+message);}
    private static NativeSignalControlBlock[] blocks(){return new NativeSignalControlBlock[]{VandorLabsBootstrap.THRUSTER_LEVER.get(),VandorLabsBootstrap.AIRLINER_THROTTLE.get(),VandorLabsBootstrap.FIGHTER_THROTTLE.get(),VandorLabsBootstrap.WALL_SLIDER.get()};}
    private static NativeSignalControlBlockEntity install(BlockPos pos,NativeSignalControlBlock block,Direction face){world.getChunkAt(pos);var state=block.defaultBlockState().setValue(NativeSignalControlBlock.FACING,face);var support=pos.relative(face.getOpposite());world.getChunkAt(support);world.setBlock(support,Blocks.STONE.defaultBlockState(),3);world.setBlock(pos,state,3);var tile=(NativeSignalControlBlockEntity)world.getBlockEntity(pos);tile.onLoad();NativeRedstoneChannels.flush(world);return tile;}
    private static CompoundTag bank(int a,int b){var tag=new CompoundTag();tag.putIntArray("RedstoneChannels",new int[]{a,b});tag.putBoolean("ChannelInitialized",true);tag.putBoolean("LocalOn",true);tag.putIntArray("LatchedChannels",new int[]{a,b});tag.putIntArray("LatchedLevels",new int[]{4,11});tag.putInt("LowLimit",5);tag.putInt("HighLimit",15);tag.putInt("ControlStep",2);var caps=new CompoundTag();caps.putString("opaque","retain");tag.put("ForgeCaps",caps);var forge=new CompoundTag();forge.putInt("foreign",23);tag.put("ForgeData",forge);return tag;}
    private static void numeric(){
        int index=0;for(var block:blocks()){
            var pos=POS.east(index*4);var tile=install(pos,block,Direction.NORTH);
            for(int low=1;low<=13;low++)for(int high=low+2;high<=15;high++){
                require(tile.configureSignal(ControlMountSettings.of(0,0,0),low,high,block.hasSelectableType()?block.controlType():-1),"valid limits");
                int[] expected={0,low,(low+high+1)/2,high};for(int step=0;step<4;step++){tile.setStep(step);require(tile.getStep()==step&&tile.getOutputLevel()==expected[step]&&tile.getBlockState().getValue(NativeSignalControlBlock.LEVEL)==step,"four detents "+low+"/"+high+"/"+step);for(Direction side:Direction.values())require(tile.getBlockState().getSignal(world,pos,side)==expected[step]&&tile.getBlockState().getDirectSignal(world,pos,side)==expected[step],"all-six weak/strong numeric");}
            }
            tile.configureTransmission(ChannelList.EMPTY,16,false);tile.setStep(1);require(tile.getOutputLevel()>0&&tile.getBlockState().getSignal(world,pos,Direction.NORTH)==0,"Local gates physical only");tile.configureTransmission(ChannelList.EMPTY,16,true);
            tile.configureSignal(ControlMountSettings.of(0,0,0),5,15,block.hasSelectableType()?block.controlType():-1);tile.setStep(2);require(tile.configureTransmission(ChannelList.of(11001+index*2,11002+index*2),64,true)&&tile.latchedLevel(11001+index*2)==10&&tile.latchedLevel(11002+index*2)==10,"binding preserves prior numeric output");tile.configureTransmission(ChannelList.EMPTY,64,true);require(tile.getStep()==2&&tile.getOutputLevel()==10,"unbinding preserves selected visible detent");
            tile.configureTransmission(ChannelList.of(13001+index*2,13002+index*2),16,true);for(int incoming=1;incoming<=15;incoming++){tile.applyLinkedLevels(Map.of(13001+index*2,incoming,13002+index*2,15));require(tile.getOutputLevel()==incoming&&tile.getStep()==(incoming<=7?1:incoming<=12?2:3),"positive external level nearest detent, ties lower "+incoming);}
            for(Direction face:Direction.values()){world.removeBlock(pos,false);tile=install(pos,block,face);tile.configureMount(ControlMountSettings.of(2,1,3));for(int step=0;step<4;step++){tile.setStep(step);var state=tile.getBlockState();require(!state.getShape(world,pos).isEmpty()&&state.getShape(world,pos).bounds().equals(state.getCollisionShape(world,pos).bounds()),"actual dynamic authored outline/collision "+block.controlKind()+"/"+face+"/"+step);}}
            world.removeBlock(pos,false);index++;
        }
    }
    private static void typesAndDrafts(){
        var tile=install(POS,VandorLabsBootstrap.THRUSTER_LEVER.get(),Direction.UP);var tag=bank(12001,12002);tile.loadWithComponents(tag,world.registryAccess());NativeRedstoneChannels.flush(world);tile.configureTransmission(ChannelList.of(12001,12002),64,false);var identity=world.getBlockEntity(POS);var player=net.neoforged.neoforge.common.util.FakePlayerFactory.getMinecraft(world);player.setPos(POS.getX()+.5,POS.getY(),POS.getZ()+2);player.containerMenu=new BinaryControlMenu(41,player.getInventory(),POS);
        for(int type:new int[]{1,2,0}){tile.configureType(type);require(world.getBlockEntity(POS)==identity&&tile.getControlType()==type&&tile.latchedLevel(12001)==4&&tile.latchedLevel(12002)==11&&tile.getOutputLevel()==4&&tile.transmissionRange()==64&&!tile.localRedstone()&&player.containerMenu.stillValid(player),"direct type preserves identity/partial bank/transmission/menu");}
        require(tile.saveWithFullMetadata(world.registryAccess()).getString("id").equals("vandorlabs:signal_control"),"retained signal_control saved BE id");
        var before=tile.saveWithFullMetadata(world.registryAccess());for(int[] limits:new int[][]{{0,15},{1,16},{14,15},{Integer.MAX_VALUE-1,Integer.MIN_VALUE}})require(!SignalControlPayload.apply(player,new SignalControlPayload(POS,41,ControlMountSettings.of(3,2,1),limits[0],limits[1],2))&&tile.saveWithFullMetadata(world.registryAccess()).equals(before),"invalid wire limits atomic");
        require(!SignalControlPayload.apply(player,new SignalControlPayload(POS,42,ControlMountSettings.of(3,2,1),5,15,2)),"stale exact window");
        require(SignalControlPayload.apply(player,new SignalControlPayload(POS,41,ControlMountSettings.of(3,2,1),5,15,2))&&world.getBlockEntity(POS)==identity&&tile.getControlType()==2&&tile.mountSettings().height==3&&tile.latchedLevel(12001)==5&&tile.latchedLevel(12002)==5,"Done reapplies visible detent even unchanged limits");
        require(tile.saveWithFullMetadata(world.registryAccess()).getCompound("ForgeCaps").equals(tag.getCompound("ForgeCaps"))&&tile.saveWithFullMetadata(world.registryAccess()).getCompound("ForgeData").getInt("foreign")==23,"opaque data survives type and Done");
        var detached=new NativeSignalControlBlockEntity(BlockPos.ZERO,VandorLabsBootstrap.WALL_SLIDER.get().defaultBlockState());detached.configureMount(ControlMountSettings.of(3,2,1));var invalidMount=new CompoundTag();invalidMount.putInt("ControlMountVersion",2);invalidMount.putInt("BaseHeight",4);invalidMount.putInt("BaseTilt",2);invalidMount.putInt("TiltDirection",1);detached.loadWithComponents(invalidMount,world.registryAccess());require(detached.mountSettings().height==0&&detached.mountSettings().tilt==0&&detached.mountSettings().direction==0,"Signal invalid saved mount resets zero rather than retaining prior mount");
        var config=tile.configuration();require(config.getInt("ControlType")==2&&!config.contains("Channel")&&!config.contains("ControlStep")&&!config.contains("LatchedLevels")&&!config.contains("ForgeData"),"legacy signal item subset");
        var bytes=new RegistryFriendlyByteBuf(Unpooled.buffer(),world.registryAccess());SignalControlPayload.CODEC.encode(bytes,new SignalControlPayload(POS,41,ControlMountSettings.of(3,2,1),3,13,2));require(bytes.readableBytes()==18&&SignalControlPayload.CODEC.decode(bytes).high()==13,"bounded18byte draft");bytes.release();for(int length:new int[]{0,17,19}){var bad=new RegistryFriendlyByteBuf(Unpooled.buffer(),world.registryAccess());bad.writeZero(length);try{SignalControlPayload.CODEC.decode(bad);throw new AssertionError("accepted signal payload length="+length);}catch(IllegalArgumentException expected){}finally{bad.release();}}
        for(int[] raw:new int[][]{{0,15,0,0},{1,16,0,0},{1,2,0,0},{1,15,3,0},{1,15,-2,0},{1,15,0,4}}){var bad=new RegistryFriendlyByteBuf(Unpooled.buffer(),world.registryAccess());bad.writeBlockPos(POS);bad.writeInt(41);bad.writeByte(raw[3]);bad.writeByte(0);bad.writeByte(0);bad.writeByte(raw[0]);bad.writeByte(raw[1]);bad.writeByte(raw[2]);try{SignalControlPayload.CODEC.decode(bad);throw new AssertionError("accepted invalid wire scalars "+Arrays.toString(raw));}catch(IllegalArgumentException expected){}finally{bad.release();}}
        player.containerMenu=player.inventoryMenu;tile.loadWithComponents(tag,world.registryAccess());tile.configureType(1);NativeRedstoneChannels.flush(world);
    }
    private static void forgeReference(){
        String file=System.getProperty("vandorlabs.signalLegacyReference","");if(file.isEmpty())return;
        try{
            byte[] bytes=Files.readAllBytes(Path.of(file));String sha=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));require(sha.equals("0fa651bbaddb468f1357fb0884617e860a84e2c22522d0a69504cefaaab9a734"),"frozen actual Forge reference hash");
            var root=JsonParser.parseString(new String(bytes,StandardCharsets.UTF_8)).getAsJsonObject();require(root.get("schema").getAsInt()==1&&root.get("legacyGolden").getAsString().equals("77f78abc1fe00fa142027bc603687c065926d9287a152cb659efb51fb8ed27a8"),"frozen legacy scalar golden");int count=0;
            for(var row:root.getAsJsonArray("cases")){
                var c=row.getAsJsonObject();var input=TagParser.parseTag(c.get("input").getAsString());var original=input.copy();var tile=new NativeSignalControlBlockEntity(BlockPos.ZERO,VandorLabsBootstrap.WALL_SLIDER.get().defaultBlockState());tile.configureSignal(ControlMountSettings.of(0,0,0),root.get("initialLow").getAsInt(),root.get("initialHigh").getAsInt(),-1);tile.setStep(root.get("initialStep").getAsInt());tile.loadWithComponents(input,world.registryAccess());
                require(input.equals(original)&&tile.getLowLimit()==c.get("low").getAsInt()&&tile.getHighLimit()==c.get("high").getAsInt()&&tile.getStep()==c.get("visibleStep").getAsInt()&&tile.getOutputLevel()==c.get("output").getAsInt()&&tile.isLocalOn()==c.get("localOn").getAsBoolean(),"actual Forge scalar/visible/output "+count);
                var saved=tile.saveWithFullMetadata(world.registryAccess());require(saved.getInt("ControlStep")==c.get("rawStep").getAsInt()&&saved.getCompound("ForgeData").equals(input.getCompound("ForgeData"))&&tile.configuration().equals(TagParser.parseTag(c.get("configuration").getAsString())),"actual Forge raw/configuration/opaque "+count);count++;
            }
            require(count==4840,"actual Forge reference count");System.out.println("[vandorlabs][signal-controls] Forge-reference PASS cases="+count+" SHA256="+sha);
        }catch(Exception failed){throw new IllegalStateException("Actual Forge signal reference failed",failed);}
    }
    @SubscribeEvent public static void started(ServerStartedEvent event){String phase=System.getProperty("vandorlabs.transmitterPhase","");if(!Set.of("signal-write","signal-read").contains(phase))return;world=event.getServer().overworld();checks=0;if(phase.equals("signal-write")){forgeReference();numeric();typesAndDrafts();}else{world.getChunkAt(POS);var tile=(NativeSignalControlBlockEntity)world.getBlockEntity(POS);require(tile!=null&&tile.getControlType()==1&&tile.latchedLevel(12001)==4&&tile.latchedLevel(12002)==11&&tile.getOutputLevel()==4,"second JVM savedID/type/partialbank");require(tile.saveWithFullMetadata(world.registryAccess()).getCompound("ForgeCaps").getString("opaque").equals("retain"),"second JVM opaque data");}System.out.println("[vandorlabs][signal-controls] server-"+phase+" PASS checks="+checks);event.getServer().halt(false);}
}
