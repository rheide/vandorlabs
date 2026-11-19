package com.vandorlabs.neoforge.turntable;

import com.mojang.authlib.GameProfile;
import com.vandorlabs.neoforge.surface.NativeSurfaceBindings;
import com.vandorlabs.neoforge.surface.NativeSurfaceBlock;
import com.vandorlabs.neoforge.surface.NativeSurfaceEntity;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

@EventBusSubscriber(modid = "vandorlabs")
public final class NativeTurntableCaptureChecks {
    private static final BlockPos SEED = new BlockPos(6, 130, 6);
    private static int checks;

    @SubscribeEvent
    public static void started(ServerStartedEvent event) {
        if (!Boolean.getBoolean("vandorlabs.turntableCaptureChecks")) return;
        var world = event.getServer().overworld();
        world.getChunkAt(SEED);
        var actor = new FakePlayer(world, new GameProfile(UUID.randomUUID(), "TurntableFixture"));
        actor.setPos(8, 132, 8);
        var identity = UUID.randomUUID();
        for (var normal : Direction.values()) {
            var controller = normal == Direction.WEST ? SEED.north() : SEED.west();
            var facing = normal == Direction.WEST ? Direction.SOUTH : Direction.EAST;
            var surface = NativeSurfaceBindings.BLOCK.defaultBlockState().setValue(NativeSurfaceBlock.FACING, normal);
            world.setBlock(SEED, surface, Block.UPDATE_ALL);
            var source = SEED.relative(normal);
            world.setBlock(source, Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
            var plan = NativeTurntableCapture.prepare(world, controller, identity, facing, 2, actor);
            need(plan.surface().normal == normal && plan.surface().cells.size() == 1, "authored normal " + normal);
            need(plan.prism().contains(source) && plan.prism().contains(source.relative(normal))
                    && !plan.prism().contains(SEED), "outward prism " + normal);
            need(plan.blocks().cells().size() == 2 && plan.blocks().matches(world, Objects::equals),
                    "exact before image " + normal);
            need(plan.atHeading(1).matches(world, Objects::equals), "one-cell quarter turn " + normal);
            need(((NativeSurfaceEntity) world.getBlockEntity(SEED)).claimedOwner() == null, "read-only Surface scan");
            world.setBlock(source, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        }

        world.setBlock(SEED, NativeSurfaceBindings.BLOCK.defaultBlockState().setValue(NativeSurfaceBlock.FACING, Direction.UP),
                Block.UPDATE_ALL);
        var source = SEED.above();
        world.setBlock(source, Blocks.OAK_SIGN.defaultBlockState(), Block.UPDATE_ALL);
        var sign = (SignBlockEntity) world.getBlockEntity(source);
        sign.updateText(text -> text.setMessage(0, Component.literal("Exact turntable snapshot")), true);
        var saved = sign.saveWithFullMetadata(world.registryAccess());
        var plan = NativeTurntableCapture.prepare(world, SEED.west(), identity, Direction.EAST, 1, actor);
        need(saved.equals(plan.blocks().cells().getFirst().tile()), "sign owns exact text in before image");
        need(world.getBlockEntity(source) == sign, "the original stays installed");
        sign.updateText(text -> text.setMessage(0, Component.literal("Changed")), true);
        need(!plan.blocks().matches(world, Objects::equals), "before-image detects own-field edits");

        world.setBlock(source, Blocks.CHEST.defaultBlockState(), Block.UPDATE_ALL);
        refused(() -> NativeTurntableCapture.prepare(world, SEED.west(), identity, Direction.EAST, 1, actor),
                "No Turntable capture adapter");
        need(world.getBlockState(source).is(Blocks.CHEST), "unsupported tile stays installed");
        refused(() -> NativeTurntableCapture.prepare(world, SEED.west(), identity, Direction.EAST, 17, actor),
                "Scan height must be");
        world.setBlock(SEED.east(), world.getBlockState(SEED), Block.UPDATE_ALL);
        refused(() -> NativeTurntableCapture.prepare(world, SEED.west(), identity, Direction.EAST, 1, actor),
                "filled square");
        world.setBlock(SEED.south(), world.getBlockState(SEED), Block.UPDATE_ALL);
        world.setBlock(SEED.south().east(), world.getBlockState(SEED), Block.UPDATE_ALL);
        world.setBlock(source, Blocks.OAK_SIGN.defaultBlockState(), Block.UPDATE_ALL);
        sign = (SignBlockEntity) world.getBlockEntity(source);
        sign.updateText(text -> text.setMessage(0, Component.literal("Turn the whole sign")), true);
        var square = NativeTurntableCapture.prepare(world, SEED.west(), identity, Direction.EAST, 1, actor);
        var encoded = square.save();
        var decoded = NativeTurntableCapture.read(world.registryAccess(), encoded, SEED.west());
        need(decoded.save().equals(encoded) && decoded.blocks().matches(world, Objects::equals),
                "saved square and exact Sign round trip");
        var damaged = encoded.copy();
        damaged.putInt("Height", 2);
        refused(() -> NativeTurntableCapture.read(world.registryAccess(), damaged, SEED.west()),
                "Incomplete Turntable before image");
        var turned = square.atHeading(1);
        var result = turned.cells().stream().filter(cell -> !cell.state.isAir()).findFirst().orElseThrow();
        var expected = sign.saveWithFullMetadata(world.registryAccess());
        expected.putInt("x", source.east().getX());
        need(result.pos.equals(source.east()) && result.tile().equals(expected), "quarter turn preserves sign ownership");
        need(result.state == world.getBlockState(source).rotate(Rotation.CLOCKWISE_90), "native sign rotation");
        need(square.blocks().matches(world, Objects::equals) && !turned.matches(world, Objects::equals),
                "projected heading preserves the installed source");
        for (int heading = 1; heading <= 3; heading++) {
            var intermediate = new NativeTurntableCapture(square.surface(), square.prism(), square.atHeading(heading));
            need(intermediate.atHeading(-heading).matches(world, Objects::equals), "endpoint inverse " + heading);
        }
        System.out.println("[vandorlabs][turntable-capture] PASS checks=" + checks);
    }

    private static void need(boolean condition, String description) {
        checks++;
        if (!condition) throw new AssertionError(description);
    }

    private static void refused(Runnable scan, String message) {
        try {
            scan.run();
        } catch (IllegalArgumentException refusal) {
            need(refusal.getMessage().contains(message), refusal.getMessage());
            return;
        }
        throw new AssertionError("Expected scan refusal: " + message);
    }
}
