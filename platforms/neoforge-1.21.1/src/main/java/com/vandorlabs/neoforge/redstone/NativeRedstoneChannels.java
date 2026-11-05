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
    // Late host callbacks cannot retain an unloaded world again. The tombstone itself is weak.
    private static final Set<ServerLevel> CLOSED=Collections.newSetFromMap(new WeakHashMap<>());
    private static <M> Set<M> identities(){return Collections.newSetFromMap(new IdentityHashMap<>());}
    private static void thread(ServerLevel level){if(!level.getServer().isSameThread())throw new IllegalStateException("Channel access requires server thread");}
    private static ServerLevel level(NativeChannelMember member){var reference=member.channelBlockEntity().getLevel();if(reference instanceof ServerLevel level){thread(level);return CLOSED.contains(level)?null:level;}return null;}
    private static Network network(ServerLevel level){thread(level);if(CLOSED.contains(level))throw new IllegalStateException("Channel world is unloaded");return NETWORKS.computeIfAbsent(level,Network::new);}
    public static void queueRegistration(NativeChannelMember member){ServerLevel level=level(member);if(level!=null){thread(level);PENDING.computeIfAbsent(level,unused->identities()).add(member);}}
    public static void channelsChanged(NativeChannelMember member){ServerLevel level=level(member);if(level!=null){thread(level);if(member.isChannelMemberActive()){var n=network(level);n.mutate(()->n.register(member));}}}
    public static void outputChanged(NativeChannelMember member){ServerLevel level=level(member);if(level!=null){thread(level);var n=network(level);n.mutate(()->n.refresh(member));}}
    public static void positionChanged(NativeChannelMember member){ServerLevel level=level(member);if(level!=null){thread(level);var n=network(level);n.mutate(()->n.positionChanged(member));}}
    public static void latchLevelChanged(NativeChannelLatch member,int value){ServerLevel level=level(member);if(level!=null){thread(level);if(value<0||value>15)return;var n=network(level);n.mutate(()->n.latchChanged(member,value));}}
    /** Coalesce receiver delivery. An unloaded world rejects the batch without running its action.
     * Derived physical neighbor jobs remain host-owned. */
    public static void batch(ServerLevel level,Runnable action){network(level).mutate(action);}
    public static void unregister(NativeChannelMember member){ServerLevel level=level(member);if(level==null)return;thread(level);Set<NativeChannelMember> pending=PENDING.get(level);if(pending!=null){pending.remove(member);if(pending.isEmpty())PENDING.remove(level);}Network n=NETWORKS.get(level);if(n!=null)n.mutate(()->n.remove(member));}
    public static int level(ServerLevel level,BlockPos pos,int channel){thread(level);Network n=NETWORKS.get(level);return n==null?0:n.at(pos,channel);}
    public static long[] diagnostics(ServerLevel level){thread(level);Network n=NETWORKS.get(level);return n==null?new long[5]:new long[]{n.stats.receiverBuckets,n.stats.receiverCandidates,n.stats.sourceBuckets,n.stats.sourceCandidates,n.stats.deliveries};}
    public static boolean manualToggle(ServerPlayer player,ItemStack stack){
        ServerLevel level=player.serverLevel();thread(level);if(CLOSED.contains(level))return false;BlockPos pos=player.blockPosition();
        if(!NativeTransmitterItem.valid(stack)||!player.isAlive()||player.isSpectator()||!level.hasChunkAt(pos)||!level.mayInteract(player,pos)||!player.mayUseItemAt(pos,net.minecraft.core.Direction.UP,stack))return false;
        ChannelList channels=NativeTransmitterItem.channels(stack);if(channels.isEmpty())return false;Network n=network(level);boolean on=false;
        for(int i=0;i<channels.size();i++)if(n.at(pos,channels.get(i))==0){on=true;break;}
        final int value=on?15:0;n.mutate(()->n.manual(pos,channels,NativeTransmitterItem.range(stack),value));return true;
    }
    /** Deliberately no registration or neighbor reads in block-entity onLoad. */
    public static void flush(ServerLevel level){thread(level);if(CLOSED.contains(level))return;Set<NativeChannelMember> ready=PENDING.remove(level);if(ready==null)return;var n=network(level);n.mutate(()->{for(var member:ready)if(level(member)==level&&member.isChannelMemberActive())n.register(member);});}
    @SubscribeEvent public static void tick(LevelTickEvent.Post event){if(event.getLevel() instanceof ServerLevel level)flush(level);}
    @SubscribeEvent public static void load(LevelEvent.Load event){if(event.getLevel() instanceof ServerLevel level){thread(level);CLOSED.remove(level);}}
    @SubscribeEvent public static void unload(LevelEvent.Unload event){if(event.getLevel() instanceof ServerLevel level){thread(level);CLOSED.add(level);NETWORKS.remove(level);PENDING.remove(level);}}
    private static final class Stats extends ChannelSourceIndex.QueryContext<Object> implements ChannelRecipientIndex.Counters {
        long receiverBuckets,receiverCandidates,deliveries;final Set<ManualSavedData.Entry> manual=identities();
        protected boolean active(Object source){return source instanceof NativeChannelMember member?member.isChannelMemberActive():manual.contains(source);}
        public void bucketVisited(){receiverBuckets++;}public void candidateVisited(){receiverCandidates++;}
    }
    private static final class Recipients extends ChannelRecipientIndex<NativeChannelMember,BlockPos> {
        Recipients(Stats stats){super(stats);}protected BlockPos snapshot(BlockPos pos){return pos.immutable();}protected int x(BlockPos pos){return pos.getX();}protected int y(BlockPos pos){return pos.getY();}protected int z(BlockPos pos){return pos.getZ();}
    }
    private record Source(BlockPos pos,int range,int level) implements ChannelSourceIndex.SourceSnapshot {
        Source{pos=pos.immutable();}public int x(){return pos.getX();}public int y(){return pos.getY();}public int z(){return pos.getZ();}
    }
    private static ChannelList union(ChannelList a,ChannelList b){return a.equals(b)?a:a.isEmpty()?b:b.isEmpty()?a:a.union(b);}
    private static boolean latch(NativeChannelMember member){return member instanceof NativeChannelLatch value&&value.isChannelLatch();}
    private static boolean inside(BlockPos source,BlockPos target,int range){return range==0||ChannelSpatialPolicy.finiteContains(source.getX(),source.getY(),source.getZ(),target.getX(),target.getY(),target.getZ(),range);}
    private static final class Network {
        final ManualSavedData data;final Stats stats=new Stats();
        final Map<NativeChannelMember,ChannelList> subscriptions=new IdentityHashMap<>(),outputs=new IdentityHashMap<>();
        final Map<NativeChannelMember,int[]> delivered=new IdentityHashMap<>();
        final Map<Integer,Recipients> recipients=new HashMap<>();
        final Map<Integer,Set<NativeChannelMember>> members=new HashMap<>();
        final Map<Integer,ChannelSourceIndex<Object,ChannelSourceIndex.SourceSnapshot>> sources=new HashMap<>();
        final Set<NativeChannelMember> affected=identities(),pendingClears=identities();
        int depth;boolean settling;
        Network(ServerLevel level){data=ManualSavedData.get(level);syncManual();}
        void mutate(Runnable action){depth++;try{action.run();}finally{if(--depth==0&&!settling)settle();}}
        int at(BlockPos pos,int channel){var index=sources.get(channel);return index==null?0:index.at(pos.getX(),pos.getY(),pos.getZ());}
        void affect(int channel,ChannelSourceIndex.SourceSnapshot source){if(source==null)return;var grid=recipients.get(channel);if(grid!=null)affected.addAll(grid.find(source instanceof Source tile?tile.pos():((ManualSavedData.Entry)source).pos,source.range()));}
        void withdraw(Object owner,ChannelList previous){if(previous!=null)for(int i=0;i<previous.size();i++){int c=previous.get(i);var index=sources.get(c);if(index!=null){affect(c,index.source(owner));index.remove(owner);if(index.isEmpty())sources.remove(c);}}}
        void remove(NativeChannelMember member){
            pendingClears.remove(member);affected.remove(member);withdraw(member,outputs.remove(member));ChannelList previous=subscriptions.remove(member);delivered.remove(member);
            if(previous!=null)for(int i=0;i<previous.size();i++){int c=previous.get(i);var set=members.get(c);if(set!=null){set.remove(member);if(set.isEmpty())members.remove(c);}var grid=recipients.get(c);if(grid!=null){grid.remove(member);if(grid.isEmpty())recipients.remove(c);}}
        }
        void register(NativeChannelMember member){
            ChannelList next=union(member.getRedstoneChannels(),member.transmittedChannels()),previous=subscriptions.get(member);
            if(next.equals(previous)){affected.add(member);refresh(member);return;}
            // Reindex subscriptions without erasing the prior output before all new members are installed.
            if(previous!=null)for(int i=0;i<previous.size();i++){int c=previous.get(i);var set=members.get(c);if(set!=null){set.remove(member);if(set.isEmpty())members.remove(c);}var grid=recipients.get(c);if(grid!=null){grid.remove(member);if(grid.isEmpty())recipients.remove(c);}}
            if(next.isEmpty()){subscriptions.remove(member);delivered.remove(member);pendingClears.add(member);}else{subscriptions.put(member,next);pendingClears.remove(member);for(int i=0;i<next.size();i++){int c=next.get(i);members.computeIfAbsent(c,unused->identities()).add(member);recipients.computeIfAbsent(c,unused->new Recipients(stats)).put(member,member.channelPosition());}affected.add(member);}
            if(latch(member)&&!member.getRedstoneChannels().isEmpty()&&member.transmissionRange()>=0){
                var control=(NativeChannelLatch)member;Map<Integer,Integer> levels=new HashMap<>();ChannelList list=member.getRedstoneChannels();
                for(int i=0;i<list.size();i++){int c=list.get(i),value=control.latchedLevel(c),maximum=0;boolean found=false;var set=members.get(c);
                    if(set!=null)for(var other:set)if(other!=member&&latch(other)&&other.isChannelMemberActive()&&other.transmittedChannels().contains(c)&&other.transmissionRange()>=0&&inside(other.channelPosition(),member.channelPosition(),other.transmissionRange())){found=true;maximum=Math.max(maximum,((NativeChannelLatch)other).latchedLevel(c));}
                    levels.put(c,found?maximum:value);
                }control.applyLinkedLevels(levels);
            }
            refresh(member);
        }
        void refresh(NativeChannelMember member){
            withdraw(member,outputs.remove(member));ChannelList list=member.transmittedChannels();int range=member.transmissionRange();if(range<0||!member.isChannelMemberActive())return;
            outputs.put(member,list);for(int i=0;i<list.size();i++){int c=list.get(i),value=Math.max(0,Math.min(15,member.localSignalLevel(c)));if(value>0){var source=new Source(member.channelPosition(),range,value);sources.computeIfAbsent(c,unused->new ChannelSourceIndex<>(stats)).put(member,source);affect(c,source);}}
        }
        void positionChanged(NativeChannelMember member){var list=subscriptions.get(member);if(list!=null){for(int i=0;i<list.size();i++)recipients.computeIfAbsent(list.get(i),unused->new Recipients(stats)).put(member,member.channelPosition());affected.add(member);}refresh(member);}
        void latchChanged(NativeChannelLatch member,int value){
            if(!member.isChannelMemberActive())return;ChannelList selected=member.transmittedChannels();int range=member.transmissionRange();if(range<0||selected.isEmpty())return;
            applyLatch(member.channelPosition(),selected,range,value);if(data.clearCovered(member.channelPosition(),selected,range))syncManual();refresh(member);
        }
        void applyLatch(BlockPos pos,ChannelList selected,int range,int value){
            Set<NativeChannelMember> changed=identities();for(int i=0;i<selected.size();i++){var grid=recipients.get(selected.get(i));if(grid!=null)for(var target:grid.find(pos,range))if(latch(target)&&target.isChannelMemberActive())changed.add(target);}
            for(var target:changed){var control=(NativeChannelLatch)target;Map<Integer,Integer> levels=new HashMap<>();ChannelList list=target.getRedstoneChannels();for(int i=0;i<list.size();i++){int c=list.get(i);levels.put(c,selected.contains(c)?value:control.latchedLevel(c));}control.applyLinkedLevels(levels);refresh(target);}
        }
        void manual(BlockPos pos,ChannelList selected,int range,int value){applyLatch(pos,selected,range,value);data.apply(pos,selected,range,value>0);syncManual();}
        void syncManual(){
            Set<ManualSavedData.Entry> retained=identities();retained.addAll(data.entries());
            for(var iterator=stats.manual.iterator();iterator.hasNext();){var entry=iterator.next();if(retained.contains(entry))continue;affect(entry.channel,entry);var index=sources.get(entry.channel);if(index!=null){index.remove(entry);if(index.isEmpty())sources.remove(entry.channel);}iterator.remove();}
            for(var entry:data.entries())if(stats.manual.add(entry)){sources.computeIfAbsent(entry.channel,unused->new ChannelSourceIndex<>(stats)).put(entry,entry);affect(entry.channel,entry);}
        }
        void settle(){
            settling=true;try{int rounds=0;while(!affected.isEmpty()||!pendingClears.isEmpty()){
                // Diagnostic guard for nonconverging adapter callbacks, not a world-feedback solver.
                if(++rounds>10000)throw new IllegalStateException("Channel callbacks did not settle");
                Set<NativeChannelMember> notify=identities();notify.addAll(affected);affected.clear();Set<NativeChannelMember> clear=identities();clear.addAll(pendingClears);pendingClears.clear();
                for(var member:clear)if(!subscriptions.containsKey(member)&&member.isChannelMemberActive()){stats.deliveries++;member.setChannelLevel(0);}
                for(var member:notify)if(subscriptions.containsKey(member)&&member.isChannelMemberActive())deliver(member);
            }}finally{settling=false;}
        }
        void deliver(NativeChannelMember member){
            ChannelList channels=member.getRedstoneChannels();int[] bank=delivered.get(member);boolean changed=bank==null||bank.length!=channels.size();if(changed){bank=new int[channels.size()];delivered.put(member,bank);}int maximum=0;
            for(int i=0;i<channels.size();i++){int value=at(member.channelPosition(),channels.get(i));if(bank[i]!=value)changed=true;bank[i]=value;maximum=Math.max(maximum,value);}if(changed){stats.deliveries++;member.setChannelLevel(maximum);}
        }
    }
    private NativeRedstoneChannels(){}
}
