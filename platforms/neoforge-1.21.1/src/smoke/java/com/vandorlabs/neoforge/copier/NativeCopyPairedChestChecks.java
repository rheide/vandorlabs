package com.vandorlabs.neoforge.copier;

import com.mojang.authlib.GameProfile;
import com.vandorlabs.copier.core.CopySettings;
import com.vandorlabs.neoforge.surface.NativeSurfaceBindings;
import com.vandorlabs.neoforge.transfer.NativeTransferHolds;
import com.vandorlabs.redstone.ChannelList;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.CompoundContainer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

@EventBusSubscriber(modid = "vandorlabs")
public final class NativeCopyPairedChestChecks {
    private static final BlockPos ROOT = new BlockPos(14, 120, 8);
    private static final BlockPos INPUT = ROOT.east(), OUTPUT = ROOT.west(), SUPPLY = ROOT.south();
    private static final BlockPos FIRST = INPUT.above(), SECOND = FIRST.east();
    private static final BlockPos FIRST_COPY = OUTPUT.west().above(), SECOND_COPY = OUTPUT.above();
    private static final String MARKER = "[vandorlabs][copy-paired-chest]";
    private static ServerLevel world;
    private static NativeCopyControllerEntity controller;
    private static ServerPlayer actor;
    private static CompoundTag first, second, supply;
    private static int ticks, stage, partialPublications;
    private static boolean observedJoinedDuringHold, automationBlocked;
    private static IItemHandler joinedHandler;

    @SubscribeEvent
    public static void start(ServerStartedEvent event) {
        if (!Boolean.getBoolean("vandorlabs.copyPairedChestCheck")) return;
        world = event.getServer().overworld();
        try {
            world.setChunkForced(0, 0, true);
            world.setChunkForced(1, 0, true);
            world.getChunkAt(FIRST);
            world.getChunkAt(SECOND);
            world.setBlockAndUpdate(ROOT, NativeCopyBindings.BLOCK.defaultBlockState());
            world.setBlockAndUpdate(INPUT, NativeSurfaceBindings.BLOCK.defaultBlockState());
            world.setBlockAndUpdate(OUTPUT, NativeSurfaceBindings.BLOCK.defaultBlockState());
            world.setBlockAndUpdate(SUPPLY, Blocks.CHEST.defaultBlockState());
            var chestState = Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.NORTH);
            var left = chestState.setValue(BlockStateProperties.CHEST_TYPE, ChestType.LEFT);
            var right = chestState.setValue(BlockStateProperties.CHEST_TYPE, ChestType.RIGHT);
            if (ChestBlock.getConnectedDirection(left) != Direction.EAST) {
                var swap = left;
                left = right;
                right = swap;
            }
            world.setBlock(FIRST, left, Block.UPDATE_KNOWN_SHAPE);
            world.setBlock(SECOND, right, Block.UPDATE_KNOWN_SHAPE);
            chest(FIRST).setItem(0, gold("first", 3));
            chest(SECOND).setItem(26, gold("second", 5));
            chest(SUPPLY).setItem(0, new ItemStack(Items.CHEST, 3));
            chest(SUPPLY).setItem(1, gold("first", 9));
            chest(SUPPLY).setItem(2, gold("second", 11));
            first = saved(FIRST);
            second = saved(SECOND);
            supply = saved(SUPPLY);
            controller = (NativeCopyControllerEntity) world.getBlockEntity(ROOT);
            actor = new FakePlayer(world, new GameProfile(new UUID(0, 0x436f707a), "CopyPairedChest"));
            actor.setGameMode(GameType.SURVIVAL);
            actor.setPos(14.5, 120, 6.5);
            require(controller.configure(actor, new CopySettings(1, 0, 0, ChannelList.EMPTY, false)), "configure");
            try {
                controller.request(actor);
                throw new AssertionError("input half outside the scan prism was accepted");
            } catch (IllegalArgumentException expected) {
                require(expected.getMessage().contains("both matching halves"), "incomplete source refuses before payment");
            }
            require(saved(SUPPLY).equals(supply) && !controller.busy(), "incomplete source changes no ownership");
            world.setBlockAndUpdate(INPUT.east(), NativeSurfaceBindings.BLOCK.defaultBlockState());
            world.setBlockAndUpdate(OUTPUT.west(), NativeSurfaceBindings.BLOCK.defaultBlockState());
            NativeCopyService.fault = (header, target) -> {
                if (target.pos.equals(SECOND_COPY)) {
                    var joined = combined(FIRST_COPY);
                    observedJoinedDuringHold = NativeTransferHolds.blocked(joined);
                    joinedHandler = world.getCapability(Capabilities.ItemHandler.BLOCK, FIRST_COPY, Direction.DOWN);
                    automationBlocked = joinedHandler != null && simulatedExtractions(joinedHandler) == 0;
                }
                if (!target.pos.equals(FIRST_COPY)) return;
                require(NativeTransferHolds.reserved(world, FIRST_COPY) && NativeTransferHolds.reserved(world, SECOND_COPY),
                    "both output halves are protected during publication");
                require(world.getBlockState(SECOND_COPY).isAir(), "failure occurs after only the first half is published");
                if (++partialPublications == 1) throw new IllegalStateException("Expected interrupted paired-chest publication");
            };
            controller.request(actor);
            require(controller.busy(), "real asynchronous paired copy accepted");
        } catch (Throwable error) {
            fail(error);
        }
    }

    @SubscribeEvent
    public static void tick(ServerTickEvent.Post event) {
        if (world == null) return;
        try {
            require(++ticks < 300, "bounded journal completion");
            if (controller.busy()) return;
            require(saved(FIRST).equals(first) && saved(SECOND).equals(second), "unchanged exact input inventories");
            require(world.getEntitiesOfClass(ItemEntity.class, new AABB(ROOT).inflate(10)).isEmpty(), "no loose copied contents");
            if (stage++ == 0) {
                require(partialPublications == 1 && controller.status().equals("Copy rolled back"), "caught real partial publication");
                require(world.getBlockState(FIRST_COPY).isAir() && world.getBlockState(SECOND_COPY).isAir(),
                    "rollback removes published half without leaving an orphaned chest");
                require(saved(SUPPLY).equals(supply), "rollback returns two exact physical inventories and chest costs");
                controller.request(actor);
                return;
            }
            require(partialPublications == 2 && controller.status().equals("Copy complete"), "retry completes durable operation");
            var state = world.getBlockState(FIRST_COPY);
            require(state == world.getBlockState(FIRST) && world.getBlockState(SECOND_COPY) == world.getBlockState(SECOND),
                "reciprocal blockstate and original orientation");
            var joined = combined(FIRST_COPY);
            require(joined.getContainerSize() == 54 && joined.contains(chest(FIRST_COPY)) && joined.contains(chest(SECOND_COPY)),
                "vanilla joined inventory references exactly the two published owners");
            require(observedJoinedDuringHold && !NativeTransferHolds.blocked(joined),
                "joined inventory is frozen during publication and usable after retirement");
            require(automationBlocked && joinedHandler.getSlots() == 54 && simulatedExtractions(joinedHandler) == 2,
                "cached vanilla item capability denies extraction until both occupied slots are usable after retirement");
            require(saved(FIRST_COPY).equals(at(first, FIRST_COPY)) && saved(SECOND_COPY).equals(at(second, SECOND_COPY)),
                "private item components and physical slot ownership match both source halves");
            require(ItemStack.matches(chest(SUPPLY).getItem(0), new ItemStack(Items.CHEST))
                && ItemStack.matches(chest(SUPPLY).getItem(1), gold("first", 6))
                && ItemStack.matches(chest(SUPPLY).getItem(2), gold("second", 6)),
                "withdraw exactly two chest items and matching component-bearing contents once");
            System.out.println(MARKER + " PASS incompleteRefusal/actualPairedHalfRollback/retry/54SlotContainer/exactComponents");
            NativeCopyService.fault = null;
            world = null;
            event.getServer().halt(false);
        } catch (Throwable error) {
            fail(error);
        }
    }

    private static int simulatedExtractions(IItemHandler handler) {
        int slots = 0;
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            if (!handler.extractItem(slot, 1, true).isEmpty()) slots++;
        }
        return slots;
    }

    private static CompoundContainer combined(BlockPos pos) {
        var state = world.getBlockState(pos);
        var inventory = ChestBlock.getContainer((ChestBlock) state.getBlock(), state, world, pos, true);
        if (!(inventory instanceof CompoundContainer pair)) throw new AssertionError("Missing paired container at " + pos);
        return pair;
    }

    private static ChestBlockEntity chest(BlockPos pos) {
        return (ChestBlockEntity) world.getBlockEntity(pos);
    }

    private static CompoundTag saved(BlockPos pos) {
        return chest(pos).saveWithFullMetadata(world.registryAccess());
    }

    private static CompoundTag at(CompoundTag raw, BlockPos pos) {
        var tag = raw.copy();
        tag.putInt("x", pos.getX());
        tag.putInt("y", pos.getY());
        tag.putInt("z", pos.getZ());
        return tag;
    }

    private static ItemStack gold(String half, int count) {
        var item = new ItemStack(Items.GOLD_INGOT, count);
        item.set(DataComponents.CUSTOM_NAME, Component.literal(half + " chest gold"));
        var tag = new CompoundTag();
        tag.putString("Half", half);
        item.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        return item;
    }

    private static void require(boolean valid, String detail) {
        if (!valid) throw new AssertionError(MARKER + " stage " + stage + ": " + detail);
    }

    private static void fail(Throwable error) {
        error.printStackTrace();
        System.out.println(MARKER + " FAIL " + error);
        NativeCopyService.fault = null;
        var server = world.getServer();
        world = null;
        server.halt(false);
    }

    private NativeCopyPairedChestChecks() {}
}
