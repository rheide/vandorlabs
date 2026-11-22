package com.vandorlabs.neoforge.builders;

import net.minecraft.server.level.ServerPlayer;
import com.vandorlabs.redstone.ChannelList;
import com.vandorlabs.neoforge.vehicle.NativeGroundVehicle;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

public final class NativeDriveMenu extends AbstractContainerMenu {
    public static final int PURGE_TANK = 0;
    public final NativeBuilderEntity tile;
    final BlockPos target;
    private final Block block;
    final NativeGroundVehicle craft;
    private final NativeDriveBindings.Edit initial;

    NativeDriveMenu(MenuType<?> type, int window, NativeBuilderEntity tile, BlockPos target) {
        super(type, window);
        this.tile = tile;
        this.target = target.immutable();
        block = tile.getBlockState().getBlock();
        craft = null;
        initial = null;
    }

    NativeDriveMenu(MenuType<?> type, int window, Inventory inventory, BlockPos root, BlockPos target) {
        this(type, window, require(inventory.player, root), target);
    }

    NativeDriveMenu(MenuType<?> type, int window, NativeGroundVehicle craft,
                    BlockPos target, NativeDriveBindings.Edit initial) {
        super(type, window);
        this.craft = craft;
        this.initial = initial;
        this.target = target.immutable();
        block = null;
        tile = null;
    }

    public BlockPos root() { return tile == null ? initial.root() : tile.getBlockPos(); }
    public NativeDriveDesign design() { return tile == null ? NativeDriveDesign.LONGBURN : tile.drive(); }
    public boolean maxwell() { return design() == NativeDriveDesign.LONGBURN; }
    public NativeDriveSettings settings() { return tile == null ? initial.config() : tile.driveSettings(); }
    public ChannelList channels() { return tile == null ? initial.channels() : tile.channels(); }
    public int fuel() { return tile == null ? Math.max(0, craft.maxwellGauge(root())) >>> 4 : tile.maxwellFuel(); }
    public int throttle() { return tile == null ? Math.max(0, craft.maxwellGauge(root())) & 15 : tile.driveLevel(); }

    private static NativeBuilderEntity require(Player player, BlockPos root) {
        var owner = NativeBuilderBindings.ownerAt(player.level(), root);
        if (owner == null || !owner.getBlockPos().equals(root) || owner.drive() == null)
            throw new IllegalArgumentException("Missing loaded drive");
        return owner;
    }

    @Override public boolean stillValid(Player player) {
        return craft == null ? tile.getBlockState().getBlock() == block && NativeDriveBindings.canUse(player, tile, target)
            : craft.canUseEngine(player, root(), target);
    }

    @Override public boolean clickMenuButton(Player player, int id) {
        return id == PURGE_TANK && player instanceof ServerPlayer server
                && player.containerMenu == this && stillValid(player)
                && (craft == null ? tile.purgeMaxwell() : craft.purgeEngine(server, root(), target));
    }

    @Override public ItemStack quickMoveStack(Player player, int index) { return ItemStack.EMPTY; }
}
