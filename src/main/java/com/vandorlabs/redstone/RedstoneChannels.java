package com.vandorlabs.redstone;

import com.vandorlabs.VandorLabs;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/** Loaded-chunk-only redstone channels. Registries are isolated per World. */
@Mod.EventBusSubscriber(modid = VandorLabs.MODID)
public final class RedstoneChannels {
    private static final Map<World, Network> NETWORKS = new WeakHashMap<>();

    private RedstoneChannels() { }

    private static Network network(World world) {
        synchronized (NETWORKS) {
            Network result = NETWORKS.get(world);
            if (result == null) {
                result = new Network(world);
                NETWORKS.put(world, result);
            }
            return result;
        }
    }

    private static Network existingNetwork(World world) {
        if(world instanceof com.vandorlabs.vehicle.VehicleWorld)world=((com.vandorlabs.vehicle.VehicleWorld)world).parent;
        synchronized (NETWORKS) { return NETWORKS.get(world); }
    }

    @SubscribeEvent public static void worldUnloaded(WorldEvent.Unload event) {
        synchronized (NETWORKS) { NETWORKS.remove(event.getWorld()); }
    }

    /** Nested conversions collect logical changes and deliver only their final installed state. */
    public static void beginTransfer(World world){network(world).transferDepth++;}
    public static void endTransfer(World world){Network network=existingNetwork(world);if(network==null||network.transferDepth<=0)throw new IllegalStateException("Unbalanced channel transaction");network.finishTransfer();}

    public static void register(RedstoneChannelMember member) {
        TileEntity tile = member.channelTile();
        if (tile.getWorld() == null || tile.getWorld().isRemote) return;
        network(tile.getWorld()).register(member);
    }

    public static void unregister(RedstoneChannelMember member) {
        TileEntity tile = member.channelTile();
        if (tile.getWorld() == null || tile.getWorld().isRemote) return;
        Network network = existingNetwork(tile.getWorld());
        if (network != null) network.unregister(member);
    }

    public static void channelChanged(RedstoneChannelMember member, int oldChannel) {
        TileEntity tile = member.channelTile();
        if (tile.getWorld() == null || tile.getWorld().isRemote) return;
        Network network = network(tile.getWorld());
        network.register(member);
    }

    public static void channelChanged(RedstoneChannelMember member,ChannelList previous) {
        channelChanged(member,previous.first());
    }

    public static void inputChanged(RedstoneChannelMember member) {
        TileEntity tile = member.channelTile();
        if (tile.getWorld() == null || tile.getWorld().isRemote) return;
        network(tile.getWorld()).inputChanged(member);
    }

    public static void latchChanged(RedstoneChannelLatch source,boolean on) {
        TileEntity tile=source.channelTile();
        if (tile.getWorld()==null || tile.getWorld().isRemote) return;
        network(tile.getWorld()).latchChanged(source,on?15:0);
    }

    public static void latchLevelChanged(RedstoneChannelLatch source,int level) {
        TileEntity tile=source.channelTile();
        if(tile.getWorld()==null || tile.getWorld().isRemote || level<0 || level>15)return;
        network(tile.getWorld()).latchChanged(source,level);
    }

    /** Read existing loaded-channel state without loading a chunk or creating a registry. */
    public static boolean allPowered(World world,ChannelList channels) {
        Network network=existingNetwork(world);
        if(network==null || channels.isEmpty())return false;
        for(int i=0;i<channels.size();i++)if(!network.powerSources.containsKey(channels.get(i)))return false;
        return true;
    }

    public static int level(World world,int channel) {
        Network network=existingNetwork(world);
        return network==null?0:network.level(channel);
    }

    public static net.minecraft.util.math.BlockPos physicalPosition(World world,net.minecraft.util.math.BlockPos pos){if(world instanceof com.vandorlabs.vehicle.VehicleWorld){com.vandorlabs.vehicle.VehicleWorld view=(com.vandorlabs.vehicle.VehicleWorld)world;if(view.craft!=null)return new net.minecraft.util.math.BlockPos(view.craft.toWorld(new net.minecraft.util.math.Vec3d(pos).addVector(.5,.5,.5)));}return pos;}
    public static int level(World world,net.minecraft.util.math.BlockPos position,int channel){Network n=existingNetwork(world);return n==null?0:n.at(physicalPosition(world,position),channel);}
    public static void positionChanged(RedstoneChannelMember member){Network n=existingNetwork(member.channelTile().getWorld());if(n!=null)n.positionChanged(member);}
    public static boolean allPowered(World world,net.minecraft.util.math.BlockPos position,ChannelList channels){if(channels.isEmpty())return false;for(int i=0;i<channels.size();i++)if(level(world,position,channels.get(i))==0)return false;return true;}
    /** Receiver buckets, receiver candidates, source buckets, source candidates, delivered callbacks. */
    public static long[] diagnostics(World world){Network n=existingNetwork(world);return n==null?new long[5]:new long[]{n.stats.receiverBuckets,n.stats.receiverCandidates,n.stats.sourceBuckets,n.stats.sourceCandidates,n.stats.deliveries};}
    public static void manualToggle(net.minecraft.entity.player.EntityPlayer player,net.minecraft.item.ItemStack stack){
        net.minecraft.util.math.BlockPos use=player.getPosition();if(player.isDead||player.isSpectator()||!player.world.isBlockLoaded(use)||!player.canPlayerEdit(use,net.minecraft.util.EnumFacing.UP,stack)||!player.world.isBlockModifiable(player,use))return;
        Network n=network(player.world);ChannelList channels=com.vandorlabs.items.ItemRedstoneTransmitter.channels(stack);if(channels.isEmpty())return;
        int range=com.vandorlabs.items.ItemRedstoneTransmitter.range(stack);boolean on=!allPowered(player.world,player.getPosition(),channels);
        n.manual(player.getPosition(),channels,range,on?15:0);
    }
    private static final class Network {
        private static final class Counters{long receiverBuckets,receiverCandidates,sourceBuckets,sourceCandidates,deliveries;}
        private final Counters stats=new Counters();
        private final World world;
        private final Map<ManualRedstoneData.Entry,RedstoneChannelMember> manualSources=new IdentityHashMap<>();
        Network(World world){this.world=world;if(!world.isRemote)syncManual();}
        private void syncManual(){
            for(RedstoneChannelMember member:manualSources.values()){ChannelList list=outputs.remove(member);if(list!=null)for(int c:channels(list)){Index index=indexes.get(c);if(index!=null){affect(c,index.all.get(member));index.remove(member);}}}
            manualSources.clear();
            for(ManualRedstoneData.Entry entry:ManualRedstoneData.get(world).entries){
                final TileEntity tile=new TileEntity(){};tile.setWorld(world);tile.setPos(entry.pos);
                RedstoneChannelMember member=new RedstoneChannelMember(){public TileEntity channelTile(){return tile;}public boolean isChannelMemberActive(){return true;}public int getRedstoneChannel(){return entry.channel;}public void setRedstoneChannel(int c){throw new UnsupportedOperationException();}public boolean hasLocalRedstoneSignal(){return true;}public void setChannelSignal(boolean on){}public ChannelList transmittedChannels(){return ChannelList.of(entry.channel);}public int transmissionRange(){return entry.range;}};
                manualSources.put(entry,member);refresh(member);
            }
        }
        private int transferDepth;
        private final Set<Integer> transferChannels=new java.util.LinkedHashSet<>();
        private final Map<Integer, Set<RedstoneChannelMember>> members=new java.util.HashMap<>();
        private final Map<RedstoneChannelMember,ChannelList> subscriptions=new IdentityHashMap<>();
        private final Map<RedstoneChannelMember,ChannelList> outputs=new IdentityHashMap<>();
        private final Map<Integer,Index> indexes=new java.util.HashMap<>();
        private final Map<Integer,Recipients> recipients=new java.util.HashMap<>();
        private final Set<RedstoneChannelMember> affected=identitySet();
        private final Map<Integer,Integer> powerSources=new java.util.HashMap<>();
        private final Map<RedstoneChannelMember,int[]> delivered=new IdentityHashMap<>();
        private static ChannelList union(ChannelList a,ChannelList b){return a.equals(b)?a:a.isEmpty()?b:b.isEmpty()?a:a.union(b);}
        private void finishTransfer(){if(--transferDepth==0){Set<Integer> changed=new java.util.LinkedHashSet<>(transferChannels);transferChannels.clear();settle(changed);}}
        void register(RedstoneChannelMember member){
            ChannelList next=union(member.getRedstoneChannels(),member.transmittedChannels()),previous=subscriptions.get(member);if(next.equals(previous))return;
            Set<Integer> changed=previous==null?channels(next):channels(union(previous,next));
            if(previous!=null)for(int c:channels(previous)){Set<RedstoneChannelMember> set=members.get(c);if(set!=null)set.remove(member);Recipients grid=recipients.get(c);if(grid!=null){grid.remove(member);if(grid.positions.isEmpty())recipients.remove(c);}}
            if(next.isEmpty()){subscriptions.remove(member);delivered.remove(member);}else {subscriptions.put(member,next);for(int c:channels(next)){members.computeIfAbsent(c,k->identitySet()).add(member);recipients.computeIfAbsent(c,k->new Recipients(stats)).put(member,member.channelPosition());}affected.add(member);}
            if(isLatch(member)&&member.transmissionRange()>=0){RedstoneChannelLatch latch=(RedstoneChannelLatch)member;Map<Integer,Integer> levels=new java.util.HashMap<>();ChannelList list=member.getRedstoneChannels();for(int i=0;i<list.size();i++){int channel=list.get(i),value=latch.latchedLevel(channel);Set<RedstoneChannelMember> set=members.get(channel);boolean found=false;int maximum=0;if(set!=null)for(RedstoneChannelMember other:set)if(other!=member&&isLatch(other)&&other.isChannelMemberActive()&&other.transmittedChannels().contains(channel)&&inside(other.channelPosition(),member.channelPosition(),other.transmissionRange())){found=true;maximum=Math.max(maximum,((RedstoneChannelLatch)other).latchedLevel(channel));}levels.put(channel,found?maximum:value);}latch.applyLinkedLevels(levels);}
            refresh(member);settle(changed);
        }
        void unregister(RedstoneChannelMember member){ChannelList previous=subscriptions.remove(member);ChannelList old=outputs.remove(member);if(old!=null)for(int c:channels(old)){Index index=indexes.get(c);if(index!=null){affect(c,index.all.get(member));index.remove(member);}}delivered.remove(member);if(previous==null)return;for(int c:channels(previous)){Set<RedstoneChannelMember> set=members.get(c);if(set!=null)set.remove(member);Recipients grid=recipients.get(c);if(grid!=null){grid.remove(member);if(grid.positions.isEmpty())recipients.remove(c);}}settle(channels(previous));}
        private void refresh(RedstoneChannelMember member){
            ChannelList previous=outputs.remove(member);if(previous!=null)for(int c:channels(previous)){Index index=indexes.get(c);if(index!=null){affect(c,index.all.get(member));index.remove(member);}}
            ChannelList list=member.transmittedChannels();if(member.transmissionRange()<0||!member.isChannelMemberActive())return;
            outputs.put(member,list);for(int c:channels(list)){int value=Math.max(0,Math.min(15,member.localSignalLevel(c)));if(value>0){Source source=new Source(member.channelPosition(),member.transmissionRange(),value);indexes.computeIfAbsent(c,k->new Index(stats)).put(member,source);affect(c,source);}}
        }
        void positionChanged(RedstoneChannelMember member){ChannelList list=subscriptions.get(member);if(list!=null){for(int c:channels(list))recipients.computeIfAbsent(c,k->new Recipients(stats)).put(member,member.channelPosition());affected.add(member);}refresh(member);if(list!=null)settle(channels(list));}
        void inputChanged(RedstoneChannelMember member){ChannelList previous=outputs.get(member);refresh(member);settle(channels(union(previous==null?ChannelList.EMPTY:previous,member.transmittedChannels())));}
        void latchChanged(RedstoneChannelLatch source,int level){
            ChannelList selected=source.transmittedChannels();if(source.transmissionRange()<0||selected.isEmpty())return;
            SignalUpdateBatch.apply(()->{applyLatch(source.channelPosition(),selected,source.transmissionRange(),level);ManualRedstoneData data=ManualRedstoneData.get(world);int count=data.entries.size();data.apply(source.channelPosition(),selected,source.transmissionRange(),false);if(data.entries.size()!=count)syncManual();refresh(source);settle(channels(selected));});
        }
        private void applyLatch(net.minecraft.util.math.BlockPos pos,ChannelList selected,int range,int value){
            Set<RedstoneChannelMember> changed=identitySet();for(int c:channels(selected)){Recipients grid=recipients.get(c);if(grid==null)continue;for(RedstoneChannelMember target:grid.find(pos,range))if(isLatch(target)&&target.isChannelMemberActive())changed.add(target);}
            for(RedstoneChannelMember target:changed){RedstoneChannelLatch latch=(RedstoneChannelLatch)target;Map<Integer,Integer> values=new java.util.HashMap<>();ChannelList list=target.getRedstoneChannels();for(int i=0;i<list.size();i++){int c=list.get(i);values.put(c,selected.contains(c)?value:latch.latchedLevel(c));}latch.applyLinkedLevels(values);refresh(target);}
        }
        void manual(net.minecraft.util.math.BlockPos pos,ChannelList selected,int range,int value){SignalUpdateBatch.apply(()->{applyLatch(pos,selected,range,value);ManualRedstoneData.get(world).apply(pos,selected,range,value>0);syncManual();settle(channels(selected));});}
        private void settle(Set<Integer> changed){
            if(transferDepth>0){transferChannels.addAll(changed);return;}
            SignalUpdateBatch.apply(()->{Set<RedstoneChannelMember> notify=identitySet();notify.addAll(affected);affected.clear();for(int c:changed){Index index=indexes.get(c);int maximum=index==null?0:index.maximum();if(index!=null&&index.all.isEmpty())indexes.remove(c);if(maximum==0)powerSources.remove(c);else powerSources.put(c,maximum);Set<RedstoneChannelMember> set=members.get(c);if(set!=null&&set.isEmpty())members.remove(c);}
                for(RedstoneChannelMember member:notify)if(subscriptions.containsKey(member)&&member.isChannelMemberActive()){int value=0;ChannelList list=member.getRedstoneChannels();int[] previous=delivered.get(member);boolean changedBank=previous==null||previous.length!=list.size();if(changedBank){previous=new int[list.size()];delivered.put(member,previous);}for(int i=0;i<list.size();i++){int channelLevel=at(member.channelPosition(),list.get(i));if(previous[i]!=channelLevel)changedBank=true;previous[i]=channelLevel;value=Math.max(value,channelLevel);}if(changedBank){stats.deliveries++;member.setChannelLevel(value);}}
            });
        }
        private void affect(int channel,Source source){if(source==null)return;Recipients grid=recipients.get(channel);if(grid!=null)affected.addAll(grid.find(source.pos,source.range));}
        /** Each subscriber is indexed in three grids; finite events visit 27 receiver buckets. */
        private static final class Recipients {
            final Counters stats;Recipients(Counters stats){this.stats=stats;}
            final Map<RedstoneChannelMember,net.minecraft.util.math.BlockPos> positions=new IdentityHashMap<>();
            final Map<Long,Set<RedstoneChannelMember>>[] grids=new Map[]{new java.util.HashMap<>(),new java.util.HashMap<>(),new java.util.HashMap<>()};
            void put(RedstoneChannelMember member,net.minecraft.util.math.BlockPos pos){remove(member);positions.put(member,pos.toImmutable());for(int t=0;t<3;t++)grids[t].computeIfAbsent(Index.key(pos,Index.size(t)),k->identitySet()).add(member);}
            void remove(RedstoneChannelMember member){net.minecraft.util.math.BlockPos pos=positions.remove(member);if(pos==null)return;for(int t=0;t<3;t++){long key=Index.key(pos,Index.size(t));Set<RedstoneChannelMember> set=grids[t].get(key);if(set!=null){set.remove(member);if(set.isEmpty())grids[t].remove(key);}}}
            Set<RedstoneChannelMember> find(net.minecraft.util.math.BlockPos pos,int range){Set<RedstoneChannelMember> result=identitySet();if(range==0){result.addAll(positions.keySet());return result;}if(range<0)return result;int t=Index.tier(range),size=Index.size(t),x=Math.floorDiv(pos.getX(),size),y=Math.floorDiv(pos.getY(),size),z=Math.floorDiv(pos.getZ(),size);for(int dx=-1;dx<=1;dx++)for(int dy=-1;dy<=1;dy++)for(int dz=-1;dz<=1;dz++){stats.receiverBuckets++;Set<RedstoneChannelMember> set=grids[t].get(Index.packed(x+dx,y+dy,z+dz));if(set!=null)for(RedstoneChannelMember member:set){stats.receiverCandidates++;if(inside(pos,positions.get(member),range))result.add(member);}}return result;}
        }
        private int at(net.minecraft.util.math.BlockPos pos,int channel){Index index=indexes.get(channel);return index==null?0:index.at(pos);}
        private int level(int channel){return powerSources.getOrDefault(channel,0);}
        private static boolean inside(net.minecraft.util.math.BlockPos source,net.minecraft.util.math.BlockPos target,int range){return range==0||source.distanceSq(target)<=(double)range*range;}
        private static final class Source {final net.minecraft.util.math.BlockPos pos;final int range,level;Source(net.minecraft.util.math.BlockPos pos,int range,int level){this.pos=pos.toImmutable();this.range=range;this.level=level;}}
        /** Three tier grids visit at most 81 buckets per receiver, regardless of radius. */
        private static final class Index {
            final Counters stats;Index(Counters stats){this.stats=stats;}
            final Map<RedstoneChannelMember,Source> all=new IdentityHashMap<>();final int[] strengths=new int[16];
            final Map<Long,Set<RedstoneChannelMember>>[] grids=new Map[]{new java.util.HashMap<>(),new java.util.HashMap<>(),new java.util.HashMap<>()};
            final Set<RedstoneChannelMember> infinite=identitySet();
            static int tier(int range){return range<=16?0:range<=64?1:2;}
            static int size(int tier){return tier==0?16:tier==1?64:256;}
            static long packed(int x,int y,int z){return ((long)x&67108863L)<<38|((long)y&4095L)<<26|((long)z&67108863L);}
            static long key(net.minecraft.util.math.BlockPos p,int size){return packed(Math.floorDiv(p.getX(),size),Math.floorDiv(p.getY(),size),Math.floorDiv(p.getZ(),size));}
            void put(RedstoneChannelMember member,Source source){remove(member);all.put(member,source);strengths[source.level]++;if(source.range==0)infinite.add(member);else{int t=tier(source.range);grids[t].computeIfAbsent(key(source.pos,size(t)),k->identitySet()).add(member);}}
            void remove(RedstoneChannelMember member){Source source=all.remove(member);if(source==null)return;strengths[source.level]--;if(source.range==0)infinite.remove(member);else {int t=tier(source.range);long key=key(source.pos,size(t));Set<RedstoneChannelMember> set=grids[t].get(key);if(set!=null){set.remove(member);if(set.isEmpty())grids[t].remove(key);}}}
            int at(net.minecraft.util.math.BlockPos pos){int result=0;for(RedstoneChannelMember member:infinite){stats.sourceCandidates++;Source source=all.get(member);if(member.isChannelMemberActive())result=Math.max(result,source.level);if(result==15)return result;}for(int t=0;t<3;t++){int size=size(t),x=Math.floorDiv(pos.getX(),size),y=Math.floorDiv(pos.getY(),size),z=Math.floorDiv(pos.getZ(),size);for(int dx=-1;dx<=1;dx++)for(int dy=-1;dy<=1;dy++)for(int dz=-1;dz<=1;dz++){stats.sourceBuckets++;Set<RedstoneChannelMember> set=grids[t].get(packed(x+dx,y+dy,z+dz));if(set!=null)for(RedstoneChannelMember member:set){stats.sourceCandidates++;Source source=all.get(member);if(member.isChannelMemberActive()&&inside(source.pos,pos,source.range))result=Math.max(result,source.level);if(result==15)return result;}}}return result;}
            int maximum(){for(int level=15;level>0;level--)if(strengths[level]>0)return level;return 0;}
        }
        private static boolean isLatch(RedstoneChannelMember member){return member instanceof RedstoneChannelLatch&&((RedstoneChannelLatch)member).isChannelLatch();}
        private static Set<RedstoneChannelMember> identitySet(){return Collections.newSetFromMap(new IdentityHashMap<RedstoneChannelMember,Boolean>());}
        private static Set<Integer> channels(ChannelList list){Set<Integer> set=new java.util.LinkedHashSet<>();for(int i=0;i<list.size();i++)set.add(list.get(i));return set;}
    }
}
