package com.vandorlabs.neoforge.redstone;

import com.vandorlabs.redstone.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/** Per-dimension server-thread host over the current exact source and recipient indices. */
@EventBusSubscriber(modid="vandorlabs")
public final class NativeRedstoneChannels {
    private static final Map<ServerLevel,Network> NETWORKS=new IdentityHashMap<>();
    private static final Map<ServerLevel,Set<NativeChannelMember>> PENDING=new IdentityHashMap<>();
    private static <M> Set<M> identities(){return Collections.newSetFromMap(new IdentityHashMap<>());}
    private static void thread(ServerLevel level){if(!level.getServer().isSameThread())throw new IllegalStateException("Channel access requires server thread");}
    private static ServerLevel level(NativeChannelMember member){return member.channelBlockEntity().getLevel() instanceof ServerLevel level?level:null;}
    private static Network network(ServerLevel level){thread(level);return NETWORKS.computeIfAbsent(level,Network::new);}
    public static void queueRegistration(NativeChannelMember member){ServerLevel level=level(member);if(level!=null){thread(level);PENDING.computeIfAbsent(level,unused->identities()).add(member);}}
    public static void channelsChanged(NativeChannelMember member){ServerLevel level=level(member);if(level!=null){thread(level);if(member.isChannelMemberActive())network(level).register(member);}}
    public static void unregister(NativeChannelMember member){ServerLevel level=level(member);if(level==null)return;thread(level);Set<NativeChannelMember> pending=PENDING.get(level);if(pending!=null){pending.remove(member);if(pending.isEmpty())PENDING.remove(level);}Network network=NETWORKS.get(level);if(network!=null)network.remove(member);}
    public static int level(ServerLevel level,BlockPos pos,int channel){thread(level);Network network=NETWORKS.get(level);return network==null?0:network.at(pos,channel);}
    public static long[] diagnostics(ServerLevel level){thread(level);Network network=NETWORKS.get(level);return network==null?new long[5]:new long[]{network.stats.receiverBuckets,network.stats.receiverCandidates,network.stats.sourceBuckets,network.stats.sourceCandidates,network.stats.deliveries};}
    public static boolean manualToggle(ServerPlayer player,ItemStack stack){
        ServerLevel level=player.serverLevel();thread(level);BlockPos pos=player.blockPosition();
        if(!NativeTransmitterItem.valid(stack)||!player.isAlive()||player.isSpectator()||!level.hasChunkAt(pos)||!level.mayInteract(player,pos)||!player.mayUseItemAt(pos,net.minecraft.core.Direction.UP,stack))return false;
        ChannelList channels=NativeTransmitterItem.channels(stack);if(channels.isEmpty())return false;Network network=network(level);boolean on=false;
        for(int i=0;i<channels.size();i++)if(network.at(pos,channels.get(i))==0){on=true;break;}
        network.data.apply(pos,channels,NativeTransmitterItem.range(stack),on);network.syncManual();return true;
    }
    /** Queue settlement boundary; deliberately no registration or neighbor reads in block-entity onLoad. */
    public static void flush(ServerLevel level){thread(level);Set<NativeChannelMember> ready=PENDING.remove(level);if(ready==null)return;for(NativeChannelMember member:ready)if(level(member)==level&&member.isChannelMemberActive())network(level).register(member);}

    @SubscribeEvent public static void tick(LevelTickEvent.Post event){if(event.getLevel() instanceof ServerLevel level)flush(level);}
    @SubscribeEvent public static void unload(LevelEvent.Unload event){if(event.getLevel() instanceof ServerLevel level){thread(level);NETWORKS.remove(level);PENDING.remove(level);}}
    private static final class Stats extends ChannelSourceIndex.QueryContext<ManualSavedData.Entry> implements ChannelRecipientIndex.Counters {
        long receiverBuckets,receiverCandidates,deliveries;final Set<ManualSavedData.Entry> active=identities();
        protected boolean active(ManualSavedData.Entry entry){return active.contains(entry);}
        public void bucketVisited(){receiverBuckets++;}public void candidateVisited(){receiverCandidates++;}
    }
    private static final class Recipients extends ChannelRecipientIndex<NativeChannelMember,BlockPos> {
        Recipients(Stats stats){super(stats);}protected BlockPos snapshot(BlockPos pos){return pos.immutable();}protected int x(BlockPos pos){return pos.getX();}protected int y(BlockPos pos){return pos.getY();}protected int z(BlockPos pos){return pos.getZ();}
    }
    private static final class Network {
        final ManualSavedData data;final Stats stats=new Stats();
        final Map<NativeChannelMember,ChannelList> subscriptions=new IdentityHashMap<>();final Map<NativeChannelMember,int[]> delivered=new IdentityHashMap<>();
        final Map<Integer,Recipients> recipients=new HashMap<>();final Map<Integer,ChannelSourceIndex<ManualSavedData.Entry,ManualSavedData.Entry>> sources=new HashMap<>();
        Network(ServerLevel level){data=ManualSavedData.get(level);syncManual();}
        int at(BlockPos pos,int channel){var index=sources.get(channel);return index==null?0:index.at(pos.getX(),pos.getY(),pos.getZ());}
        void remove(NativeChannelMember member){ChannelList previous=subscriptions.remove(member);delivered.remove(member);if(previous!=null)for(int i=0;i<previous.size();i++){int channel=previous.get(i);Recipients grid=recipients.get(channel);if(grid!=null){grid.remove(member);if(grid.isEmpty())recipients.remove(channel);}}}
        void register(NativeChannelMember member){ChannelList channels=member.getRedstoneChannels();ChannelList previous=subscriptions.get(member);if(channels.equals(previous))return;remove(member);if(!channels.isEmpty()){subscriptions.put(member,channels);for(int i=0;i<channels.size();i++)recipients.computeIfAbsent(channels.get(i),unused->new Recipients(stats)).put(member,member.channelPosition());}deliver(member);}
        void deliver(NativeChannelMember member){
            if(!member.isChannelMemberActive())return;ChannelList channels=member.getRedstoneChannels();int[] bank=delivered.get(member);boolean changed=bank==null||bank.length!=channels.size();if(changed){bank=new int[channels.size()];delivered.put(member,bank);}int maximum=0;
            for(int i=0;i<channels.size();i++){int value=at(member.channelPosition(),channels.get(i));if(bank[i]!=value)changed=true;bank[i]=value;maximum=Math.max(maximum,value);}if(changed){stats.deliveries++;member.setChannelLevel(maximum);}
        }
        void affect(Set<NativeChannelMember> affected,ManualSavedData.Entry entry){Recipients grid=recipients.get(entry.channel);if(grid!=null)affected.addAll(grid.find(entry.pos,entry.range));}
        void syncManual(){
            Set<ManualSavedData.Entry> retained=identities();retained.addAll(data.entries());Set<NativeChannelMember> affected=identities();
            for(var iterator=stats.active.iterator();iterator.hasNext();){var entry=iterator.next();if(retained.contains(entry))continue;affect(affected,entry);var index=sources.get(entry.channel);if(index!=null){index.remove(entry);if(index.isEmpty())sources.remove(entry.channel);}iterator.remove();}
            for(var entry:data.entries())if(stats.active.add(entry)){sources.computeIfAbsent(entry.channel,unused->new ChannelSourceIndex<>(stats)).put(entry,entry);affect(affected,entry);}
            for(var member:affected)if(subscriptions.containsKey(member))deliver(member);
        }
    }
    private NativeRedstoneChannels(){}
}
