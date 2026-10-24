package com.vandorlabs.redstone;

import com.vandorlabs.VandorLabs;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import java.util.Map;
import java.util.WeakHashMap;

/** Loaded-chunk-only redstone channels. Registries are isolated per World. */
@Mod.EventBusSubscriber(modid = VandorLabs.MODID)
public final class RedstoneChannels {
    private static final Map<World, ChannelNetwork> NETWORKS = new WeakHashMap<>();

    private RedstoneChannels() { }

    private static ChannelNetwork network(World world) {
        synchronized (NETWORKS) {
            ChannelNetwork result = NETWORKS.get(world);
            if (result == null) {
                result = new ChannelNetwork(SignalUpdateBatch::apply);
                NETWORKS.put(world, result);
            }
            return result;
        }
    }

    private static ChannelNetwork existingChannelNetwork(World world) {
        if(world instanceof com.vandorlabs.vehicle.VehicleWorld)world=((com.vandorlabs.vehicle.VehicleWorld)world).parent;
        synchronized (NETWORKS) { return NETWORKS.get(world); }
    }

    @SubscribeEvent public static void worldUnloaded(WorldEvent.Unload event) {
        synchronized (NETWORKS) { NETWORKS.remove(event.getWorld()); }
    }

    /** Nested conversions collect logical changes and deliver only their final installed state. */
    public static void beginTransfer(World world){network(world).beginTransfer();}
    public static void endTransfer(World world){ChannelNetwork network=existingChannelNetwork(world);if(network==null)throw new IllegalStateException("Unbalanced channel transaction");network.endTransfer();}

    public static void register(RedstoneChannelMember member) {
        TileEntity tile = member.channelTile();
        if (tile.getWorld() == null || tile.getWorld().isRemote || member.getRedstoneChannels().isEmpty()) return;
        network(tile.getWorld()).register(member);
    }

    public static void unregister(RedstoneChannelMember member) {
        TileEntity tile = member.channelTile();
        if (tile.getWorld() == null || tile.getWorld().isRemote) return;
        ChannelNetwork network = existingChannelNetwork(tile.getWorld());
        if (network != null) network.unregister(member);
    }

    public static void channelChanged(RedstoneChannelMember member, int oldChannel) {
        TileEntity tile = member.channelTile();
        if (tile.getWorld() == null || tile.getWorld().isRemote) return;
        ChannelNetwork network = network(tile.getWorld());
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
        ChannelNetwork network=existingChannelNetwork(world);
        return network!=null && network.allPowered(channels);
    }

    public static int level(World world,int channel) {
        ChannelNetwork network=existingChannelNetwork(world);
        return network==null?0:network.level(channel);
    }

}
