package com.vandorlabs.neoforge;

import com.mojang.authlib.GameProfile;
import com.vandorlabs.neoforge.redstone.*;
import com.vandorlabs.redstone.ChannelList;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.server.level.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

/** Installed real chest hosts exercise the network without introducing production control blocks. */
@EventBusSubscriber(modid="vandorlabs")
public final class ChannelLatchRuntimeChecks {
    private static final BlockPos ORIGIN=new BlockPos(3200,80,3200),SAVED=ORIGIN.south(32);
    private static final int A=66001,B=66002,PERSIST=66003;
    private static int checks;
    private static void require(boolean value,String message){checks++;if(!value)throw new IllegalStateException("Native channel latch: "+message);}
    private static final class Host implements NativeChannelLatch {
        final BlockEntity tile;ChannelList rx,tx;int range,received,calls;boolean control;
        final Map<Integer,Integer> levels=new HashMap<>();final List<Integer> callbacks=new ArrayList<>();Runnable callback,linkedCallback;
        BlockPos position;
        Host(BlockEntity tile,ChannelList rx,ChannelList tx,int range,boolean control){this.tile=tile;this.rx=rx;this.tx=tx;this.range=range;this.control=control;position=tile.getBlockPos();}
        public BlockEntity channelBlockEntity(){return tile;}public BlockPos channelPosition(){return position;}
        public ChannelList getRedstoneChannels(){return rx;}public ChannelList transmittedChannels(){return tx;}public int transmissionRange(){return range;}
        public int localSignalLevel(int channel){return levels.getOrDefault(channel,0);}public int latchedLevel(int channel){return localSignalLevel(channel);}
        public boolean isChannelLatch(){return control;}public boolean latchOn(){return levels.values().stream().anyMatch(value->value>0);}
        public void applyLinkedLatch(boolean on){for(int i=0;i<rx.size();i++)levels.put(rx.get(i),on?15:0);save();}
        public void applyLinkedLevels(Map<Integer,Integer> values){levels.clear();levels.putAll(values);save();if(linkedCallback!=null)linkedCallback.run();}
        public void setChannelLevel(int value){received=value;calls++;callbacks.add(value);if(callback!=null)callback.run();}
        void save(){CompoundTag tag=new CompoundTag();tag.putIntArray("Receive",rx.toArray());tag.putIntArray("Transmit",tx.toArray());tag.putInt("Range",range);tag.putBoolean("Latch",control);int[] values=new int[rx.size()];for(int i=0;i<values.length;i++)values[i]=latchedLevel(rx.get(i));tag.putIntArray("Levels",values);tile.getPersistentData().put("LatchProbe",tag);tile.setChanged();}
    }
    private static Host host(ServerLevel world,BlockPos pos,ChannelList rx,ChannelList tx,int range,boolean control){world.getChunkAt(pos);world.setBlock(pos,Blocks.CHEST.defaultBlockState(),3);var result=new Host(world.getBlockEntity(pos),rx,tx,range,control);NativeRedstoneChannels.queueRegistration(result);return result;}
    private static Host load(ServerLevel world,BlockPos pos){world.getChunkAt(pos);var tile=world.getBlockEntity(pos);require(tile!=null,"saved real chest missing");var tag=tile.getPersistentData().getCompound("LatchProbe");var result=new Host(tile,ChannelList.of(tag.getIntArray("Receive")),ChannelList.of(tag.getIntArray("Transmit")),tag.getInt("Range"),tag.getBoolean("Latch"));int[] values=tag.getIntArray("Levels");for(int i=0;i<values.length;i++)result.levels.put(result.rx.get(i),values[i]);NativeRedstoneChannels.queueRegistration(result);return result;}
    private static void drop(ServerLevel world,Host... hosts){NativeRedstoneChannels.batch(world,()->{for(var host:hosts)NativeRedstoneChannels.unregister(host);});}
    private static ItemStack handheld(ServerPlayer player,ChannelList channels){var stack=NativeTransmitterItem.variant(VandorLabsBootstrap.REDSTONE_TRANSMITTER.get(),0);NativeTransmitterItem.configure(stack,channels);player.setItemInHand(InteractionHand.MAIN_HAND,stack);return stack;}
    private static void toggle(ServerPlayer player,ChannelList channels){require(NativeRedstoneChannels.manualToggle(player,handheld(player,channels)),"ordinary manual command refused");}
    private static void matrices(ServerLevel world,ServerPlayer player){
        var source=host(world,ORIGIN,ChannelList.EMPTY,ChannelList.of(A),4,false);
        for(int range:new int[]{4,8,16,32,64,128,256,0}){
            source.range=range;var edge=host(world,ORIGIN.east(range==0?512:range),ChannelList.of(A),ChannelList.EMPTY,-1,false);var outside=host(world,ORIGIN.offset(range==0?513:range,1,0),ChannelList.of(A),ChannelList.EMPTY,-1,false);NativeRedstoneChannels.flush(world);
            for(int level=0;level<=15;level++){source.levels.put(A,level);NativeRedstoneChannels.outputChanged(source);require(edge.received==level&&outside.received==(range==0?level:0),"all-level sphere range="+range+" level="+level);}
            int calls=edge.calls;NativeRedstoneChannels.outputChanged(source);require(edge.calls==calls,"unchanged source delivered duplicate callback");drop(world,edge,outside);
        }
        source.levels.put(A,0);NativeRedstoneChannels.outputChanged(source);drop(world,source);
        var disabled=host(world,ORIGIN.west(1),ChannelList.of(A),ChannelList.of(A),-1,true);disabled.levels.put(A,15);NativeRedstoneChannels.flush(world);
        var first=host(world,ORIGIN,ChannelList.of(A,B),ChannelList.of(A),16,true);first.levels.put(A,2);first.levels.put(B,11);NativeRedstoneChannels.flush(world);require(first.latchedLevel(A)==2,"disabled negative-range latch cannot seed neighboring join");drop(world,disabled);
        var second=host(world,ORIGIN.east(2),ChannelList.of(A,B),ChannelList.of(A,B),16,true);second.levels.put(A,0);second.levels.put(B,7);NativeRedstoneChannels.flush(world);
        require(second.latchedLevel(A)==2&&second.latchedLevel(B)==7,"joining inherits only other transmitted channels");
        var receiver=host(world,ORIGIN.east(4),ChannelList.of(A),ChannelList.EMPTY,-1,false);var pulse=host(world,ORIGIN.north(2),ChannelList.EMPTY,ChannelList.of(A),16,false);NativeRedstoneChannels.flush(world);
        for(int level=0;level<=15;level++){NativeRedstoneChannels.latchLevelChanged(first,level);require(first.latchedLevel(A)==level&&second.latchedLevel(A)==level&&first.latchedLevel(B)==11&&second.latchedLevel(B)==7&&receiver.received==level,"partial latch bank level="+level);}
        int unchanged=receiver.calls;NativeRedstoneChannels.latchLevelChanged(first,-1);NativeRedstoneChannels.latchLevelChanged(first,16);require(first.latchedLevel(A)==15&&receiver.calls==unchanged,"out-of-range latch commands ignored");
        first.tx=ChannelList.of(A,B);NativeRedstoneChannels.channelsChanged(first);toggle(player,ChannelList.of(A,B));require(first.latchedLevel(A)==0&&second.latchedLevel(A)==0,"manual OFF applies to latches before withdrawing manual sources");
        first.linkedCallback=()->require(ManualSavedData.get(world).entries().stream().noneMatch(entry->entry.channel==A||entry.channel==B),"manual ON mutates latch before publishing stationary source");
        receiver.callback=()->require(first.latchedLevel(A)==15&&second.latchedLevel(A)==15&&ManualSavedData.get(world).entries().stream().anyMatch(entry->entry.channel==A),"receiver observes final complete manual/latch state");
        toggle(player,ChannelList.of(A,B));first.linkedCallback=null;receiver.callback=null;require(first.latchedLevel(A)==15&&first.latchedLevel(B)==15&&second.latchedLevel(A)==15&&second.latchedLevel(B)==15,"manual ON applies full selected bank");
        pulse.levels.put(A,9);NativeRedstoneChannels.outputChanged(pulse);first.tx=ChannelList.of(A);first.range=4;NativeRedstoneChannels.channelsChanged(first);NativeRedstoneChannels.latchLevelChanged(first,0);
        require(first.latchedLevel(A)==0&&second.latchedLevel(A)==0&&second.latchedLevel(B)==15&&receiver.received==9,"latch OFF retains unselected channel and independent pulse");
        require(ManualSavedData.get(world).entries().stream().noneMatch(entry->entry.channel==A)&&ManualSavedData.get(world).entries().stream().anyMatch(entry->entry.channel==B),"4-block latch clear selects manual channel only");
        pulse.levels.put(A,0);NativeRedstoneChannels.outputChanged(pulse);require(receiver.received==0,"pulse withdrawal reveals OFF latch");
        var solo=host(world,ORIGIN.west(2),ChannelList.EMPTY,ChannelList.EMPTY,16,true);solo.levels.put(A,13);NativeRedstoneChannels.flush(world);require(solo.latchedLevel(A)==13,"unchannelled control owns saved local state");
        var reentrant=receiver;reentrant.calls=0;reentrant.callbacks.clear();reentrant.callback=()->{if(reentrant.received==3){pulse.levels.put(A,7);NativeRedstoneChannels.outputChanged(pulse);}};
        NativeRedstoneChannels.batch(world,()->{pulse.levels.put(A,3);NativeRedstoneChannels.outputChanged(pulse);pulse.levels.put(A,5);NativeRedstoneChannels.outputChanged(pulse);pulse.levels.put(A,3);NativeRedstoneChannels.outputChanged(pulse);});
        require(reentrant.callbacks.equals(List.of(3,7)),"coalesced and reentrant callbacks="+reentrant.callbacks);reentrant.callback=null;
        receiver.position=ORIGIN.east(64);NativeRedstoneChannels.positionChanged(receiver);require(receiver.received==0,"receiver move leaves coverage");receiver.position=ORIGIN.east(4);NativeRedstoneChannels.positionChanged(receiver);require(receiver.received==7,"receiver move enters coverage");
        receiver.rx=ChannelList.of(B);NativeRedstoneChannels.channelsChanged(receiver);require(receiver.received==15,"receiver bank replacement");receiver.rx=ChannelList.EMPTY;NativeRedstoneChannels.channelsChanged(receiver);require(receiver.received==0,"empty receive bank clears delivered value");receiver.rx=ChannelList.of(A);NativeRedstoneChannels.channelsChanged(receiver);require(receiver.received==7,"receiver bank restore");
        pulse.position=ORIGIN.east(64);NativeRedstoneChannels.positionChanged(pulse);require(receiver.received==0,"source move withdraws old coverage");pulse.position=ORIGIN;NativeRedstoneChannels.positionChanged(pulse);require(receiver.received==7,"source move installs new coverage");
        pulse.tx=ChannelList.of(PERSIST);NativeRedstoneChannels.channelsChanged(pulse);require(receiver.received==0,"replacing transmitted bank withdraws old channel");
        pulse.tx=ChannelList.of(A);NativeRedstoneChannels.channelsChanged(pulse);require(receiver.received==7,"transmitted-bank restoration");world.removeBlock(pulse.tile.getBlockPos(),false);require(NativeRedstoneChannels.level(world,ORIGIN,A)==0,"replaced physical host is immediately inactive");NativeRedstoneChannels.unregister(pulse);require(receiver.received==0,"host removal notifies receiver");
        int before=receiver.calls;java.util.concurrent.CompletableFuture.runAsync(()->{try{NativeRedstoneChannels.outputChanged(first);throw new AssertionError("worker output accepted");}catch(IllegalStateException expected){}try{NativeRedstoneChannels.level(world,ORIGIN,A);throw new AssertionError("worker query accepted");}catch(IllegalStateException expected){}}).join();require(receiver.calls==before,"worker rejection before callbacks");
        var stale=host(world,ORIGIN.south(3),ChannelList.of(A),ChannelList.of(A),16,true);world.removeBlock(stale.tile.getBlockPos(),false);NativeRedstoneChannels.flush(world);require(stale.calls==0,"stale deferred registration rejected");drop(world,first,second,receiver,solo,stale);
        int coverage=66004;var local=host(world,ORIGIN,ChannelList.of(coverage),ChannelList.of(coverage),4,true);NativeRedstoneChannels.flush(world);player.setPos(ORIGIN.getX()+10.5,ORIGIN.getY(),ORIGIN.getZ()+.5);toggle(player,ChannelList.of(coverage));require(local.latchedLevel(coverage)==15,"manual command updates nearby latch first");NativeRedstoneChannels.latchLevelChanged(local,0);
        require(local.latchedLevel(coverage)==0&&NativeRedstoneChannels.level(world,ORIGIN,coverage)==15&&ManualSavedData.get(world).entries().stream().anyMatch(entry->entry.channel==coverage&&entry.pos.equals(ORIGIN.east(10))),"latch clear uses command sphere on manual centers, not overlapping manual radius");toggle(player,ChannelList.of(coverage));require(NativeRedstoneChannels.level(world,ORIGIN,coverage)==0,"ordinary manual OFF clears retained distant center");drop(world,local);player.setPos(ORIGIN.getX()+.5,ORIGIN.getY(),ORIGIN.getZ()+.5);
        ManualSavedData.get(world).clearCovered(ORIGIN,ChannelList.of(A,B,PERSIST,coverage),0);
        System.out.println("[vandorlabs][channel-latches] matrix PASS all16levels 8ranges partialbanks joining unchannelled manual-before-source independentpulse batched-reentrant move replacement removal worker deferred");
    }
    @SubscribeEvent public static void started(ServerStartedEvent event){
        String phase=System.getProperty("vandorlabs.transmitterPhase","");if(!Set.of("latch-write","latch-read").contains(phase))return;checks=0;var server=event.getServer();var world=server.overworld();world.getChunkAt(ORIGIN);
        if(phase.equals("latch-write")){
            var player=new ServerPlayer(server,world,new GameProfile(UUID.fromString("f132f517-e03b-4850-9e96-d55065d9af4b"),"LatchFixture"),ClientInformation.createDefault());player.setPos(ORIGIN.getX()+.5,ORIGIN.getY(),ORIGIN.getZ()+.5);matrices(world,player);
            toggle(player,ChannelList.of(PERSIST));var saved=host(world,SAVED,ChannelList.of(A,B),ChannelList.of(A,B),16,true);saved.levels.put(A,3);saved.levels.put(B,11);saved.save();NativeRedstoneChannels.flush(world);require(saved.latchedLevel(A)==3&&saved.latchedLevel(B)==11,"persisted independent bank");
        }else{
            var saved=load(world,SAVED);var receiver=host(world,SAVED.east(4),ChannelList.of(A,B),ChannelList.EMPTY,-1,false);NativeRedstoneChannels.flush(world);require(saved.latchedLevel(A)==3&&saved.latchedLevel(B)==11&&receiver.received==11,"new JVM actual chest bank restore and republished levels");require(NativeRedstoneChannels.level(world,ORIGIN,PERSIST)==15,"separate stationary manual tuple retained");
            var queued=host(world,SAVED.south(2),ChannelList.of(A),ChannelList.EMPTY,-1,false);
            var player=new ServerPlayer(server,world,new GameProfile(UUID.fromString("f132f517-e03b-4850-9e96-d55065d9af4b"),"LatchFixture"),ClientInformation.createDefault());var item=handheld(player,ChannelList.of(PERSIST));
            NativeRedstoneChannels.unload(new net.neoforged.neoforge.event.level.LevelEvent.Unload(world));require(NativeRedstoneChannels.level(world,SAVED,A)==0,"world unload clears published outputs");NativeRedstoneChannels.flush(world);require(queued.calls==0,"world unload clears pending registrations");
            NativeRedstoneChannels.queueRegistration(queued);NativeRedstoneChannels.channelsChanged(saved);NativeRedstoneChannels.outputChanged(saved);NativeRedstoneChannels.positionChanged(saved);NativeRedstoneChannels.latchLevelChanged(saved,15);NativeRedstoneChannels.flush(world);
            require(!NativeRedstoneChannels.manualToggle(player,item),"closed world refuses manual command before gameplay reads");
            try{NativeRedstoneChannels.batch(world,()->{throw new AssertionError("closed batch executed world action");});throw new AssertionError("closed batch accepted");}catch(IllegalStateException expected){}
            try{for(String name:new String[]{"NETWORKS","PENDING"}){var field=NativeRedstoneChannels.class.getDeclaredField(name);field.setAccessible(true);require(!((Map<?,?>)field.get(null)).containsKey(world),"late callback republished strong world entry "+name);}}catch(ReflectiveOperationException failure){throw new IllegalStateException(failure);}
            require(NativeRedstoneChannels.level(world,SAVED,A)==0&&Arrays.equals(NativeRedstoneChannels.diagnostics(world),new long[5]),"closed query/diagnostics remain empty");
            NativeRedstoneChannels.load(new net.neoforged.neoforge.event.level.LevelEvent.Load(world));NativeRedstoneChannels.queueRegistration(saved);NativeRedstoneChannels.flush(world);require(NativeRedstoneChannels.level(world,SAVED,B)==11,"explicit load clears weak closed marker");
        }
        System.out.println("[vandorlabs][channel-latches] server-"+phase+" PASS checks="+checks);server.halt(false);
    }
}
