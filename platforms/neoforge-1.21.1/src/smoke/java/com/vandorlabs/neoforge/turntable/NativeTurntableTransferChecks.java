package com.vandorlabs.neoforge.turntable;

import com.mojang.authlib.GameProfile;
import io.netty.buffer.Unpooled;
import net.minecraft.network.RegistryFriendlyByteBuf;
import com.vandorlabs.neoforge.mixin.NativeRampChunkStorageAccess;
import com.vandorlabs.neoforge.surface.NativeSurfaceBindings;
import com.vandorlabs.neoforge.surface.NativeSurfaceBlock;
import com.vandorlabs.neoforge.transfer.CellTransferFaultFixture;
import com.vandorlabs.neoforge.transfer.NativeCellTransactions;
import com.vandorlabs.neoforge.transfer.NativeTransferHolds;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.chunk.storage.ChunkSerializer;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

@EventBusSubscriber(modid = "vandorlabs")
public final class NativeTurntableTransferChecks {
    private static final BlockPos SEED = new BlockPos(15, 130, 6);
    private static final BlockPos ROOT = SEED.west();
    private static final BlockPos SOURCE = SEED.east().above();
    private static final BlockPos TARGET = SOURCE.south();
    private static final String PHASE = System.getProperty("vandorlabs.turntableTransferChecks", "");
    private static ServerLevel world;
    private static FakePlayer actor;
    private static NativeCellTransactions.Request request;
    private static NativeCellTransactions.Operation<?> operation;
    private static CompoundTag originalSign;
    private static UUID originalKey;
    private static int stage;
    private static int ticks;
    private static int checks;

    @SubscribeEvent
    public static void started(ServerStartedEvent event) {
        if (PHASE.isEmpty()) return;
        world = event.getServer().overworld();
        world.getChunkAt(ROOT);
        world.getChunkAt(SOURCE);
        if (PHASE.equals("read")) {
            need(owner().capture() == null && sign(SOURCE).getFrontText().getMessage(0, false).getString().equals("Turntable owner"),
                    "partial controller save restored the one equipped source");
            need(world.getBlockState(TARGET).isAir() && !NativeTransferHolds.active(world), "cold lease retired");
            noDrops();
            pass();
            event.getServer().halt(false);
            return;
        }

        world.setChunkForced(ROOT.getX() >> 4, ROOT.getZ() >> 4, true);
        world.setChunkForced(SOURCE.getX() >> 4, SOURCE.getZ() >> 4, true);
        actor = new FakePlayer(world, new GameProfile(UUID.randomUUID(), "TurntableFixture"));
        actor.setPos(ROOT.getX() - 1, ROOT.getY() + 1, ROOT.getZ());
        world.setBlock(ROOT, NativeTurntableBindings.block.defaultBlockState().setValue(NativeTurntableBlock.FACING, Direction.EAST),
                Block.UPDATE_ALL);
        var surface = NativeSurfaceBindings.BLOCK.defaultBlockState().setValue(NativeSurfaceBlock.FACING, Direction.UP);
        for (int x = 0; x < 2; x++) for (int z = 0; z < 2; z++) world.setBlock(SEED.offset(x, 0, z), surface, Block.UPDATE_ALL);
        world.setBlock(SOURCE, Blocks.OAK_SIGN.defaultBlockState(), Block.UPDATE_ALL);
        world.setBlock(SOURCE.above(), Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
        verifyAnglePacket();
        sign(SOURCE).updateText(t -> t.setMessage(0, Component.literal("Turntable owner")), true);
        originalSign = sign(SOURCE).saveWithFullMetadata(world.registryAccess());
        originalKey = owner().key();
        if (PHASE.equals("crash")) event.getServer().saveEverything(true, true, true);
        request = NativeTurntableTransfers.prepare(owner(), actor, false, 0);
        CellTransferFaultFixture.afterCell((header, index) -> fault(index));
    }

    @SubscribeEvent
    public static void tick(ServerTickEvent.Post event) {
        if (world == null || PHASE.equals("read")) return;
        ticks++;
        need(ticks < 600, "bounded journal lifecycle stage=" + stage + " status="
                + (operation == null ? "queued" : operation.status() + " " + operation.error()) + " angle=" + owner().positionAngle()
                + " status=" + owner().status() + " saving=" + !world.noSave);
        if (stage == 0 && ticks <= 3 && !PHASE.equals("crash")) {
            world.noSave = true;
            need(NativeTurntableTransfers.submit(world, actor, request) == null, "saving disabled leaves the operation pending");
            need(owner().capture() == null && sign(SOURCE).saveWithFullMetadata(world.registryAccess()).equals(originalSign),
                    "pending preparation preserves the physical sign");
            return;
        }
        if (stage == 0 && ticks == 4 && !PHASE.equals("crash")) world.noSave = false;
        if (operation == null) {
            operation = NativeTurntableTransfers.submit(world, actor, request);
            return;
        }
        if (operation.status() == NativeCellTransactions.Status.FAILED) {
            throw new AssertionError("Turntable journal failed " + operation.error());
        }
        if (operation.status() != NativeCellTransactions.Status.DONE) return;
        if (stage == 0) {
            need(!operation.error().isEmpty() && owner().capture() == null && owner().key().equals(originalKey),
                    "caught rollback retained the original owner");
            need(sign(SOURCE).saveWithFullMetadata(world.registryAccess()).equals(originalSign), "rollback restored exact sign contents");
            noDrops();
            CellTransferFaultFixture.afterCell(null);
            stage = 1;
            request = NativeTurntableTransfers.prepare(owner(), actor, false, 0);
            operation = null;
        } else if (stage == 1) {
            need(operation.error().isEmpty() && owner().capture() != null && owner().key().equals(originalKey),
                    "capture committed to the same controller key");
            need(world.getBlockState(SOURCE).isAir() && world.getBlockEntity(SOURCE) == null, "the source sign has no second owner");
            var capturedStone = new AABB(SOURCE.above()).deflate(.1);
            need(!world.getEntityCollisions(actor, capturedStone).isEmpty(), "captured solid remains a physical collision");
            need(!world.setBlockAndUpdate(ROOT, Blocks.STONE.defaultBlockState()), "captured owner resists native replacement");
            world.setBlockAndUpdate(TARGET, Blocks.STONE.defaultBlockState());
            boolean refused = false;
            try {
                NativeTurntableTransfers.prepare(owner(), actor, true, 1);
            } catch (IllegalArgumentException expected) {
                refused = expected.getMessage().startsWith("Restore space occupied");
            }
            need(refused && owner().capture() != null, "occupied restore leaves the stored capture intact");
            world.setBlockAndUpdate(TARGET, Blocks.AIR.defaultBlockState());
            world.setBlockAndUpdate(TARGET.above(), Blocks.STONE.defaultBlockState());
            need(owner().setSpeed(20), "ten RPM accepted");
            need(owner().turn(1), "Turn accepted a supported heading");
            stage = 2;
        } else if (stage == 2) {
            if (owner().moving()) return;
            need(owner().status().contains("blocked by terrain") && owner().positionAngle() < 90
                    && owner().hasCapture(), "terrain stops authoritative motion with one retained owner");
            world.setBlockAndUpdate(TARGET.above(), Blocks.AIR.defaultBlockState());
            need(owner().turn(1), "obstructed Turn can be retried with the same capture");
            stage = 3;
        } else if (stage == 3) {
            if (owner().moving()) return;
            need(owner().positionAngle() == 90 && owner().status().endsWith("90 degrees"), "motion settled at the authoritative heading");
            need(!world.getEntityCollisions(actor, new AABB(TARGET.above()).deflate(.1)).isEmpty()
                    && world.getEntityCollisions(actor, new AABB(SOURCE.above()).deflate(.1)).isEmpty(),
                    "physical collision follows the rotated solid");
            var savedRoot = owner().saveWithFullMetadata(world.registryAccess());
            need(savedRoot.getDouble(NativeTurntableEntity.ANGLE) == 90,
                    "the completed pose is saved independently of client interpolation");
            stage = 4;
            request = NativeTurntableTransfers.prepare(owner(), actor, true, 1);
            operation = null;
        } else {
            need(operation.error().isEmpty() && owner().key().equals(originalKey) && owner().capture() == null,
                    "successful restore released the controller capture");
            var saved = originalSign.copy();
            saved.putInt("z", TARGET.getZ());
            need(sign(TARGET).saveWithFullMetadata(world.registryAccess()).equals(saved)
                    && world.getBlockState(TARGET) == Blocks.OAK_SIGN.defaultBlockState().rotate(Rotation.CLOCKWISE_90),
                    "rotated restore published the exact sign once");
            need(world.getBlockState(SOURCE).isAir() && world.getBlockState(SOURCE.above()).isAir()
                    && world.getBlockState(TARGET.above()).is(Blocks.STONE) && !NativeTransferHolds.active(world)
                    && owner().positionAngle() == 0, "final rotated payload and leases retired");
            noDrops();
            pass();
            world = null;
            event.getServer().halt(false);
        }
    }

    private static void verifyAnglePacket() {
        var packet = new NativeTurntableAnglePayload(ROOT, owner().key(), Long.MAX_VALUE, -0.25F);
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), world.registryAccess());
        try {
            NativeTurntableAnglePayload.CODEC.encode(buffer, packet);
            int bytes = buffer.readableBytes();
            need(bytes <= 38 && packet.equals(NativeTurntableAnglePayload.CODEC.decode(buffer)) && !buffer.isReadable(),
                    "one bounded angle packet holds no payload artwork");
            System.out.println("[vandorlabs][turntable-transfer] angle packet bytes=" + bytes);
        } finally {
            buffer.release();
        }
    }

    private static void fault(int index) {
        if (PHASE.equals("crash") && index == 0) {
            need(owner().capture() != null && sign(SOURCE) != null, "controller published before untouched source");
            need(!new ChunkPos(ROOT).equals(new ChunkPos(SOURCE)) && NativeTransferHolds.reserved(world, SOURCE),
                    "both chunk owners protected at real armed boundary");
            noDrops();
            var capturedWorld = world;
            capturedWorld.noSave = true;
            var cp = new ChunkPos(ROOT);
            var chunk = capturedWorld.getChunkSource().getChunkNow(cp.x, cp.z);
            var storage = capturedWorld.getChunkSource().chunkMap;
            storage.write(cp, ChunkSerializer.write(capturedWorld, chunk))
                    .thenCompose(ignored -> ((NativeRampChunkStorageAccess) (Object) storage).vandorlabs$rampWorker().synchronize(true))
                    .whenComplete((ignored, failure) -> {
                        if (failure != null) {
                            failure.printStackTrace();
                            Runtime.getRuntime().halt(2);
                        }
                        pass();
                        System.out.flush();
                        Runtime.getRuntime().halt(0);
                    });
            throw new IllegalStateException("Awaiting abrupt controller-only checkpoint");
        }
        if (stage == 0 && index > 0) {
            need(owner().capture() != null && world.getBlockState(SOURCE).isAir(), "caught fault followed physical owner handoff");
            throw new IllegalStateException("Expected caught Turntable transfer fault");
        }
    }

    private static SignBlockEntity sign(BlockPos pos) { return (SignBlockEntity) world.getBlockEntity(pos); }
    private static NativeTurntableEntity owner() { return (NativeTurntableEntity) world.getBlockEntity(ROOT); }
    private static void noDrops() {
        need(world.getEntitiesOfClass(ItemEntity.class, new AABB(ROOT).inflate(8)).isEmpty(), "no loose ownership copies");
    }
    private static void pass() { System.out.println("[vandorlabs][turntable-transfer] " + PHASE + " PASS checks=" + checks); }
    private static void need(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
}
