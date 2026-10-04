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
        if (tile.getWorld() == null || tile.getWorld().isRemote || member.getRedstoneChannel() <= 0) return;
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
        network.remove(member, oldChannel);
        network.register(member);
    }

    public static void inputChanged(RedstoneChannelMember member) {
        TileEntity tile = member.channelTile();
        if (tile.getWorld() == null || tile.getWorld().isRemote || member.getRedstoneChannel() <= 0) return;
        network(tile.getWorld()).inputChanged(member);
    }

    public static void latchChanged(RedstoneChannelLatch source,boolean on) {
        TileEntity tile=source.channelTile();
        if (tile.getWorld()==null || tile.getWorld().isRemote || source.getRedstoneChannel()<=0) return;
        network(tile.getWorld()).latchChanged(source,on);
    }

    private static final class Network {
        private final Map<Integer, Set<RedstoneChannelMember>> members = new java.util.HashMap<>();
        private final Map<Integer, RedstoneChannelMember> powerSources = new java.util.HashMap<>();
        private final Map<Integer, Boolean> latchStates = new java.util.HashMap<>();

        void register(RedstoneChannelMember member) {
            int channel = member.getRedstoneChannel();
            if (channel <= 0) {
                member.setChannelSignal(false);
                return;
            }
            Set<RedstoneChannelMember> set = members.get(channel);
            if (set == null) {
                set = Collections.newSetFromMap(new IdentityHashMap<RedstoneChannelMember, Boolean>());
                members.put(channel, set);
            }
            if (!set.add(member)) return;
            if (member instanceof RedstoneChannelLatch
                    && ((RedstoneChannelLatch)member).isChannelLatch()) {
                RedstoneChannelLatch latch=(RedstoneChannelLatch)member;
                Boolean shared=latchStates.get(channel);
                if (shared==null) latchStates.put(channel,latch.latchOn());
                else latch.applyLinkedLatch(shared);
            }
            boolean before = powerSources.containsKey(channel);
            boolean after = reconcile(channel, set);
            if (before != after) notifyChannel(channel, after);
            else member.setChannelSignal(after);
        }

        void unregister(RedstoneChannelMember member) {
            remove(member, member.getRedstoneChannel());
        }

        void remove(RedstoneChannelMember member, int channel) {
            Set<RedstoneChannelMember> set = members.get(channel);
            if (set != null && set.remove(member)) {
                boolean before = powerSources.containsKey(channel);
                if (set.isEmpty()) {
                    members.remove(channel);
                    powerSources.remove(channel);
                    latchStates.remove(channel);
                } else {
                    boolean hasLatch=false;
                    for (RedstoneChannelMember remaining:set)
                        if (remaining instanceof RedstoneChannelLatch
                                && ((RedstoneChannelLatch)remaining).isChannelLatch()) {
                            hasLatch=true; break;
                        }
                    if (!hasLatch) latchStates.remove(channel);
                    boolean after = reconcile(channel, set);
                    if (before != after) notifyChannel(channel, after);
                }
            }
            member.setChannelSignal(false);
        }

        void inputChanged(RedstoneChannelMember member) {
            int channel = member.getRedstoneChannel();
            Set<RedstoneChannelMember> set = members.get(channel);
            if (set == null || !set.contains(member)) return;
            boolean before = powerSources.containsKey(channel);
            boolean after = reconcile(channel, set);
            if (before != after) notifyChannel(channel, after);
        }

        void latchChanged(RedstoneChannelLatch source,boolean on) {
            int channel=source.getRedstoneChannel();
            Set<RedstoneChannelMember> set=members.get(channel);
            if (set==null || !set.contains(source)) return;
            boolean before=powerSources.containsKey(channel);
            latchStates.put(channel,on);
            for (RedstoneChannelMember member:new ArrayList<>(set))
                if (member!=source && member instanceof RedstoneChannelLatch
                        && ((RedstoneChannelLatch)member).isChannelLatch())
                    ((RedstoneChannelLatch)member).applyLinkedLatch(on);
            boolean after=reconcile(channel,set);
            if (before!=after) notifyChannel(channel,after);
        }

        /**
         * Revalidate a known live source before searching the other loaded members.
         * A currently powered source proves the channel OR without counting every
         * contributor. When it stops, search the current snapshot from scratch:
         * coalesced/reordered neighbor events must never leave a stale count high.
         */
        private boolean reconcile(int channel, Set<RedstoneChannelMember> set) {
            RedstoneChannelMember previous=powerSources.get(channel);
            if(previous!=null && set.contains(previous) && previous.hasLocalRedstoneSignal())return true;
            for (RedstoneChannelMember member : new ArrayList<>(set)) {
                if (member!=previous && member.hasLocalRedstoneSignal()) {
                    powerSources.put(channel,member);
                    return true;
                }
            }
            powerSources.remove(channel);
            return false;
        }

        private void notifyChannel(int channel, boolean powered) {
            Set<RedstoneChannelMember> set = members.get(channel);
            if (set == null) return;
            SignalUpdateBatch.apply(() -> {
                for (RedstoneChannelMember member : new ArrayList<>(set)) member.setChannelSignal(powered);
            });
        }
    }
}
