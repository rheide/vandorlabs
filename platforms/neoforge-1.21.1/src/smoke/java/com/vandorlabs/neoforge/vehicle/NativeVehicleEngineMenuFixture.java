package com.vandorlabs.neoforge.vehicle;

import com.vandorlabs.neoforge.VandorLabsBootstrap;
import com.vandorlabs.neoforge.builders.NativeBuilderEntity;
import com.vandorlabs.neoforge.builders.NativeDriveSettings;
import com.vandorlabs.neoforge.builders.NativeMaxwellTank;
import com.vandorlabs.neoforge.transfer.NativeCellTransactions;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

public final class NativeVehicleEngineMenuFixture {
    public static final BlockPos CORE = new BlockPos(3,0,0);
    public static final BlockPos TARGET = CORE.east().above();
    private static int stage;
    private static BlockPos parked;

    public static boolean enabled() { return Boolean.getBoolean("vandorlabs.carriedEngineMenu"); }

    static void prepare(ServerPlayer player, NativeGroundVehicle craft) {
        player.teleportTo(craft.getX() + TARGET.getX(), craft.getY(), craft.getZ() - 2);
        var other = net.neoforged.neoforge.common.util.FakePlayerFactory.get(player.serverLevel(),
            new com.mojang.authlib.GameProfile(new java.util.UUID(0, 931), "OtherPilot"));
        other.setPos(player.position());
        if (!craft.canUseEngine(player, CORE, TARGET) || craft.canUseEngine(other, CORE, TARGET)
                || craft.canUseEngine(player, CORE, BlockPos.ZERO)) {
            throw new AssertionError("Engine edits must match the owner and exact drive part");
        }
    }

    static boolean tick(ServerPlayer player, NativeGroundVehicle craft) {
        var world = player.serverLevel();
        if (stage == 2) {
            if (!craft.isRemoved() || NativeCellTransactions.busy(world, parked, player.getUUID())) return false;
            var tile = world.getBlockEntity(parked.offset(CORE));
            if (!(tile instanceof NativeBuilderEntity engine) || !engine.formed() || engine.maxwellFuel() != 0
                    || !engine.driveSettings().redstone() || !engine.driveSettings().levels()
                    || !engine.channels().contains(931)) throw new AssertionError("Parked Maxwell at " + parked.offset(CORE) + ": " + (tile == null ? "missing" : tile.saveWithFullMetadata(world.registryAccess())));
            player.displayClientMessage(net.minecraft.network.chat.Component.literal("Carried Maxwell settings verified"), false);
            System.out.println("[vandorlabs][vehicle-engine-player] server PASS native settings, purged tank and recovered physical owner");
            return true;
        }
        if (player.containerMenu != player.inventoryMenu) return false;
        var tag = craft.snapshot().cells().stream().filter(c -> c.pos.equals(CORE)).findFirst().orElseThrow().tile();
        var settings = NativeDriveSettings.readMaxwell(tag);
        if (!settings.redstone() || !settings.levels() || !java.util.Arrays.equals(tag.getIntArray("Channels"), new int[]{931})) return false;
        int fuel = NativeMaxwellTank.read(tag, world.registryAccess()).amount();
        if (stage == 0) {
            if (fuel != 1) throw new AssertionError("Editing thrust consumed unpowered Taxi fuel");
            player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,new ItemStack(VandorLabsBootstrap.CONFIGURIZER.get()));
            stage = 1;
        } else if (fuel == 0 && craft.canPark()) {
            parked = NativeVehicleTransfer.anchor(craft);
            NativeVehicleTransfer.park(player, craft);
            stage = 2;
        }
        return false;
    }

    private NativeVehicleEngineMenuFixture() {}
}
