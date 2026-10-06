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
                result = new Network();
                NETWORKS.put(world, result);
            }
            return result;
        }
    }

    private static Network existingNetwork(World world) {
        synchronized (NETWORKS) { return NETWORKS.get(world); }
    }

    @SubscribeEvent public static void worldUnloaded(WorldEvent.Unload event) {
        synchronized (NETWORKS) { NETWORKS.remove(event.getWorld()); }
    }

    public static void register(RedstoneChannelMember member) {
        TileEntity tile = member.channelTile();
        if (tile.getWorld() == null || tile.getWorld().isRemote || member.getRedstoneChannels().isEmpty()) return;
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
        if (tile.getWorld() == null || tile.getWorld().isRemote || member.getRedstoneChannels().isEmpty()) return;
        network(tile.getWorld()).inputChanged(member);
    }

    public static void latchChanged(RedstoneChannelLatch source,boolean on) {
        TileEntity tile=source.channelTile();
        if (tile.getWorld()==null || tile.getWorld().isRemote || source.getRedstoneChannels().isEmpty()) return;
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

    private static final class Network {
        private final Map<Integer, Set<RedstoneChannelMember>> members=new java.util.HashMap<>();
        private final Map<RedstoneChannelMember,ChannelList> subscriptions=new IdentityHashMap<>();
        private final Map<Integer,RedstoneChannelMember> strongestSources=new java.util.HashMap<>();
        private final Map<Integer,Integer> powerSources=new java.util.HashMap<>();
        private final Map<Integer,Integer> latchStates=new java.util.HashMap<>();

        void register(RedstoneChannelMember member) {
            ChannelList next=member.getRedstoneChannels(),previous=subscriptions.get(member);
            if(next.equals(previous))return;
            Set<Integer> changed=new java.util.LinkedHashSet<>();
            if(previous!=null)for(int i=0;i<previous.size();i++) {
                int channel=previous.get(i);changed.add(channel);
                Set<RedstoneChannelMember> set=members.get(channel);
                if(set!=null)set.remove(member);
            }
            if(next.isEmpty())subscriptions.remove(member);else subscriptions.put(member,next);
            boolean latch=isLatch(member);
            ChannelList initial=latch?((RedstoneChannelLatch)member).latchedChannels():ChannelList.EMPTY;
            for(int i=0;i<next.size();i++) {
                int channel=next.get(i);changed.add(channel);
                members.computeIfAbsent(channel,unused->identitySet()).add(member);
                if(latch && !latchStates.containsKey(channel))latchStates.put(channel,((RedstoneChannelLatch)member).latchedLevel(channel));
            }
            settle(changed,true,member);
        }

        void unregister(RedstoneChannelMember member) {
            ChannelList previous=subscriptions.remove(member);
            if(previous==null)return;
            Set<Integer> changed=channels(previous);
            for(int channel:changed) {
                Set<RedstoneChannelMember> set=members.get(channel);
                if(set!=null)set.remove(member);
            }
            settle(changed,true,member);
        }

        void inputChanged(RedstoneChannelMember member) {
            ChannelList channels=subscriptions.get(member);
            if(channels==null)return;
            Set<RedstoneChannelMember> notify=null;
            for(int i=0;i<channels.size();i++) {
                int channel=channels.get(i);Set<RedstoneChannelMember> set=members.get(channel);
                if(set==null)continue;
                int before=level(channel);
                if(before!=reconcile(channel,set)) {
                    if(notify==null)notify=identitySet();
                    notify.addAll(set);
                }
            }
            if(notify!=null) {
                Set<RedstoneChannelMember> ready=notify;
                SignalUpdateBatch.apply(()->{
                    for(RedstoneChannelMember target:ready)target.setChannelLevel(anyPowered(subscriptions.get(target)));
                });
            }
        }

        void latchChanged(RedstoneChannelLatch source,int level) {
            ChannelList channels=subscriptions.get(source);
            if(channels==null)return;
            for(int i=0;i<channels.size();i++)latchStates.put(channels.get(i),level);
            settle(channels(channels),true,null);
        }

        private void settle(Set<Integer> changed,boolean syncLatches,RedstoneChannelMember addedOrRemoved) {
            SignalUpdateBatch.apply(()-> {
                if(syncLatches) {
                    Set<RedstoneChannelMember> latches=identitySet();
                    for(int channel:changed) {
                        Set<RedstoneChannelMember> set=members.get(channel);boolean hasLatch=false;
                        if(set!=null)for(RedstoneChannelMember member:new ArrayList<>(set))if(isLatch(member)) {
                            hasLatch=true;latches.add(member);
                        }
                        if(!hasLatch)latchStates.remove(channel);
                    }
                    // Mirror the complete list once, avoiding intermediate physical handle states.
                    for(RedstoneChannelMember member:latches) {
                        ChannelList channels=subscriptions.get(member);
                        if(channels==null)continue;
                        Map<Integer,Integer> levels=new java.util.HashMap<>();
                        for(int i=0;i<channels.size();i++)levels.put(channels.get(i),latchStates.getOrDefault(channels.get(i),0));
                        ((RedstoneChannelLatch)member).applyLinkedLevels(levels);
                    }
                }
                Set<RedstoneChannelMember> notify=identitySet();
                if(addedOrRemoved!=null)notify.add(addedOrRemoved);
                // Settle every affected channel before computing any consumer's OR.
                for(int channel:changed) {
                    int before=level(channel);
                    Set<RedstoneChannelMember> set=members.get(channel);
                    if(set==null || set.isEmpty()) {
                        members.remove(channel);powerSources.remove(channel);strongestSources.remove(channel);latchStates.remove(channel);
                    } else if(before!=reconcile(channel,set))notify.addAll(set);
                }
                for(RedstoneChannelMember member:notify)member.setChannelLevel(anyPowered(subscriptions.get(member)));
            });
        }

        private int level(int channel){return powerSources.getOrDefault(channel,0);}
        private int anyPowered(ChannelList channels) {
            int level=0;
            if(channels!=null)for(int i=0;i<channels.size();i++)level=Math.max(level,level(channels.get(i)));
            return level;
        }

        private int reconcile(int channel,Set<RedstoneChannelMember> set) {
            RedstoneChannelMember previous=strongestSources.get(channel);
            // A still-loaded maximum-strength source proves the maximum without a scan.
            if(previous!=null && set.contains(previous) && previous.localSignalLevel(channel)==15){powerSources.put(channel,15);return 15;}
            int level=0;RedstoneChannelMember strongest=null;
            for(RedstoneChannelMember member:new ArrayList<>(set)) {
                int candidate=Math.max(0,Math.min(15,member.localSignalLevel(channel)));
                if(candidate>level){level=candidate;strongest=member;}
                if(level==15)break;
            }
            if(level==0){powerSources.remove(channel);strongestSources.remove(channel);}else{powerSources.put(channel,level);strongestSources.put(channel,strongest);}
            return level;
        }
        private static boolean isLatch(RedstoneChannelMember member) {
            return member instanceof RedstoneChannelLatch && ((RedstoneChannelLatch)member).isChannelLatch();
        }
        private static Set<RedstoneChannelMember> identitySet() {
            return Collections.newSetFromMap(new IdentityHashMap<RedstoneChannelMember,Boolean>());
        }
        private static Set<Integer> channels(ChannelList list) {
            Set<Integer> channels=new java.util.LinkedHashSet<>();
            for(int i=0;i<list.size();i++)channels.add(list.get(i));
            return channels;
        }
    }
}
