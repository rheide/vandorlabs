package com.vandorlabs.redstone;

import com.vandorlabs.tiles.TileEntityBattery;
import com.vandorlabs.tiles.TileEntityHexTank;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

/** Fullness transmitter for each storage owner. Battery receive channels use the port policy. */
public final class StorageChannelOutput implements RedstoneChannelMember {
    private final TileEntity tile;
    private int published;

    public StorageChannelOutput(TileEntityBattery tile) { this.tile = tile; }
    public StorageChannelOutput(TileEntityHexTank tile) { this.tile = tile; }

    public static StorageChannelOutput of(TileEntity tile) {
        if (tile instanceof TileEntityBattery) return ((TileEntityBattery)tile).fullnessOutput;
        if (tile instanceof TileEntityHexTank) return ((TileEntityHexTank)tile).fullnessOutput;
        return null;
    }

    public int level() {
        if (tile instanceof TileEntityBattery) {
            TileEntityBattery battery = (TileEntityBattery)tile;
            return battery.inert() ? 0 : level(battery.stored(), TileEntityBattery.CAPACITY);
        }
        TileEntityHexTank tank = (TileEntityHexTank)tile;
        return level(tank.tank.getFluidAmount(), TileEntityHexTank.CAPACITY);
    }

    private static int level(int amount, int capacity) {
        return amount <= 0 ? 0 : Math.min(15, 1 + (int)(14L * amount / capacity));
    }

    @Override public TileEntity channelTile() { return tile; }
    @Override public int getRedstoneChannel() { return 0; }
    @Override public void setRedstoneChannel(int ignored) { throw new UnsupportedOperationException(); }
    @Override public boolean hasLocalRedstoneSignal() { return level() > 0; }
    @Override public int localSignalLevel(int channel) { return level(); }
    @Override public void setChannelSignal(boolean ignored) { }

    public void publish() {
        World world = tile.getWorld();
        if (world == null || world.isRemote || !isChannelMemberActive()) return;
        int next = level();
        if (next == published) return;
        published = next;
        if (!transmittedChannels().isEmpty()) RedstoneChannels.inputChanged(this);
        if (TransmitterSlots.localRedstone(tile)) TransmitterSlots.notifyLocalOutput(tile);
    }

    public static int localLevel(TileEntity tile) {
        StorageChannelOutput output = of(tile);
        return output != null && TransmitterSlots.localRedstone(tile) ? output.level() : 0;
    }
}
