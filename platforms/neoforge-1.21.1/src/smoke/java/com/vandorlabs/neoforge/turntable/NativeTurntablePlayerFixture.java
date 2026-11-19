package com.vandorlabs.neoforge.turntable;

import com.vandorlabs.neoforge.VandorLabsBootstrap;
import com.vandorlabs.neoforge.ProgrammableBlock;
import com.vandorlabs.neoforge.ProgrammableBlockEntity;
import com.vandorlabs.ui.ProgrammableBlockDialog;
import com.vandorlabs.neoforge.redstone.NativeRedstoneChannels;
import com.vandorlabs.neoforge.redstone.NativeTransmitterItem;
import com.vandorlabs.redstone.ChannelList;
import com.vandorlabs.turntable.TurntableSettings;
import com.vandorlabs.neoforge.surface.NativeSurfaceBindings;
import com.vandorlabs.neoforge.surface.NativeSurfaceBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

@EventBusSubscriber(modid = "vandorlabs")
public final class NativeTurntablePlayerFixture {
    static final BlockPos ROOT = new BlockPos(14, 130, 6);
    static final BlockPos SOURCE = ROOT.east(2).above();
    static final BlockPos TARGET = SOURCE.south();
    static final BlockPos CUBE = ROOT.east().above().south(), ROTATED_CUBE = ROOT.east().above();
    static final Vec3 ACTOR = new Vec3(14.5, 130, 3.5);
    private static final boolean ENABLED = Boolean.getBoolean("vandorlabs.turntablePlayerChecks");
    private static ServerPlayer player;
    private static boolean captured;
    private static boolean reported;
    private static int signalStage;
    private static long phaseTick;
    private static int riderStage;

    static Vec3 feet(BlockPos block) {
        return Vec3.atBottomCenterOf(block.above(2));
    }

    static boolean enabled() { return ENABLED; }

    @SubscribeEvent public static void started(ServerStartedEvent event) {
        if (!enabled()) return;
        var world = event.getServer().overworld();
        if (world.getRecipeManager().byKey(NativeTurntableBindings.ID).isEmpty()) {
            throw new AssertionError("Experimental Turntable recipe unavailable");
        }
        world.getChunkAt(ROOT);
        world.getChunkAt(SOURCE);
        world.setBlockAndUpdate(BlockPos.containing(ACTOR).below(), Blocks.STONE.defaultBlockState());
        world.setBlockAndUpdate(ROOT, NativeTurntableBindings.block.defaultBlockState()
                .setValue(NativeTurntableBlock.FACING, Direction.EAST));
        var pad = NativeSurfaceBindings.BLOCK.defaultBlockState().setValue(NativeSurfaceBlock.FACING, Direction.UP);
        for (int x = 1; x <= 2; x++) for (int z = 0; z < 2; z++) world.setBlock(ROOT.offset(x, 0, z), pad, Block.UPDATE_ALL);
        world.setBlockAndUpdate(SOURCE, Blocks.OAK_SIGN.defaultBlockState());
        world.setBlockAndUpdate(SOURCE.above(), Blocks.STONE.defaultBlockState());
        world.setBlockAndUpdate(CUBE, VandorLabsBootstrap.PROGRAMMABLE_BLOCK.get().defaultBlockState()
                .setValue(ProgrammableBlock.FACING, Direction.NORTH));
        ((ProgrammableBlockEntity)world.getBlockEntity(CUBE)).apply(
                new ProgrammableBlockDialog.State(9, 1, false, new int[]{-1,-1,-1,-1,-1,-1}, -1));
        ((SignBlockEntity) world.getBlockEntity(SOURCE)).updateText(
                line -> line.setMessage(0, Component.literal("Turntable player sign")), true);
    }

    @SubscribeEvent public static void joined(PlayerEvent.PlayerLoggedInEvent event) {
        if (!enabled() || !(event.getEntity() instanceof ServerPlayer actor)) return;
        player = actor;
        actor.setGameMode(GameType.SURVIVAL);
        actor.getInventory().selected = 0;
        actor.getInventory().setItem(0, ItemStack.EMPTY);
        actor.getInventory().setItem(1, new ItemStack(VandorLabsBootstrap.CONFIGURIZER.get()));
        actor.connection.teleport(ACTOR.x, ACTOR.y, ACTOR.z, 0, 0);
    }

    @SubscribeEvent public static void tick(ServerTickEvent.Post event) {
        if (!enabled() || reported || player == null || player.isRemoved()) return;
        var world = player.serverLevel();
        if (!(world.getBlockEntity(ROOT) instanceof NativeTurntableEntity controller)) return;
        if (controller.hasCapture() && world.getBlockState(SOURCE).isAir()) captured = true;
        if (riderStage == 0 && captured && !controller.busy()) {
            var feet = feet(SOURCE);
            player.connection.teleport(feet.x, feet.y, feet.z, 20, 0);
            riderStage = 1;
        }
        if (riderStage == 1 && controller.positionAngle() == 90 && !controller.moving()
                && player.containerMenu == player.inventoryMenu) {
            if (player.position().distanceToSqr(feet(TARGET)) > .025 || Math.abs(player.getYRot() - 110) > 2) {
                throw new AssertionError("Server rider did not follow the rotating stone: " + player.position()
                        + " heading=" + player.getYRot());
            }
            player.connection.teleport(ACTOR.x, ACTOR.y, ACTOR.z, 0, 0);
            riderStage = 2;
            System.out.println("[vandorlabs][turntable-player] authoritative rider pose PASS");
        }
        if (controller.busy() || !captured || !(player.containerMenu instanceof NativeTurntableMenu)
                || !player.getMainHandItem().is(VandorLabsBootstrap.CONFIGURIZER.get())) return;
        long time = world.getGameTime();
        switch (signalStage) {
            case 0 -> {
                if (controller.hasCapture() || controller.settings().height != 2 || controller.settings().scanMode != TurntableSettings.SIGNAL_ON
                        || !controller.settings().scanChannels.equals(ChannelList.of(5291))) return;
                if (!controller.settings().rotationChannels.equals(ChannelList.of(5292, 5293))
                        || !controller.settings().directionChannels.equals(ChannelList.of(5294))
                        || !controller.settings().restoreChannels.equals(ChannelList.of(5295))) {
                    throw new AssertionError("Configured Turntable signal banks were not saved");
                }
                pulse(5291);
                signalStage++;
            }
            case 1 -> {
                if (!controller.hasCapture() || !world.getBlockState(TARGET).isAir()) return;
                pulse(5292);
                phaseTick = time;
                signalStage++;
            }
            case 2 -> {
                if (time < phaseTick + 8) return;
                if (controller.moving() || controller.positionAngle() != 0) throw new AssertionError("Partial channel bank moved body");
                pulse(5293);
                signalStage++;
            }
            case 3 -> {
                if (!controller.moving() || controller.positionAngle() < 24) return;
                pulse(5294);
                signalStage++;
            }
            case 4 -> {
                if (controller.settings().clockwise || !controller.moving() || controller.positionAngle() > 14) return;
                pulse(5293);
                signalStage++;
            }
            case 5 -> {
                if (controller.moving()) return;
                if (!controller.hasCapture() || controller.positionAngle() == 0) throw new AssertionError("Paused away from snap angle");
                pulse(5295);
                signalStage++;
            }
            case 6 -> {
                if (controller.hasCapture() || !controller.status().equals("Restored at the selected heading")) return;
                if (!(world.getBlockEntity(TARGET) instanceof SignBlockEntity moved)
                        || !moved.getFrontText().getMessage(0, false).getString().equals("Turntable player sign")
                        || !world.getBlockState(SOURCE).isAir()) throw new AssertionError("Signal Restore changed or duplicated Sign");
                reported = true;
                System.out.println("[vandorlabs][turntable-player] server PASS exact owners, channel banks, scan, rotation, reversal and restore");
            }
            default -> throw new AssertionError("Unknown Turntable signal stage");
        }
    }

    private static void pulse(int channel) {
        var transmitter = NativeTransmitterItem.variant(VandorLabsBootstrap.REDSTONE_TRANSMITTER.get(), 1);
        NativeTransmitterItem.configure(transmitter, ChannelList.of(channel));
        if (!NativeRedstoneChannels.manualToggle(player, transmitter)) throw new AssertionError("Transmitter rejected " + channel);
    }

    @SubscribeEvent public static void left(PlayerEvent.PlayerLoggedOutEvent event) {
        if (enabled() && reported && event.getEntity() == player) player.getServer().halt(false);
    }
}
