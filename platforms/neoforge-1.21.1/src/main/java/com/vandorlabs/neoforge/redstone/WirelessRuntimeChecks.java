package com.vandorlabs.neoforge.redstone;

import com.vandorlabs.redstone.ChannelList;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.WeightedPressurePlateBlock;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/** Invoked only by the opt-in disposable-world server smoke, never ordinary startup. */
public final class WirelessRuntimeChecks {
    private static final BlockPos TX = new BlockPos(10, 80, 0), RX = new BlockPos(14, 80, 0);
    private WirelessRuntimeChecks() { }
    private static void require(boolean value, String message) { if (!value) throw new IllegalStateException("Wireless runtime check: " + message); }
    private static void settle(ServerLevel level) { ModernRedstoneChannels.onLevelTick(new LevelTickEvent.Post(() -> true, level)); }
    private static void resetFixture(ServerLevel level) {
        // A write/client phase may reuse its disposable world. Replace owned
        // endpoints so same-state placement cannot retain their prior saved BE.
        for (BlockPos pos : new BlockPos[]{RX, TX, new BlockPos(20, 80, 0), new BlockPos(20, 80, -1)}) {
            level.getChunkAt(pos); level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
        }
        for (Direction face : Direction.values()) {
            BlockPos source = TX.relative(face); level.getChunkAt(source); level.setBlock(source, Blocks.AIR.defaultBlockState(), 3);
        }
    }

    /** Installs only the opt-in client smoke's reproducible endpoints, without running server assertions. */
    public static void clientFixture(ServerLevel level) {
        resetFixture(level);
        level.getChunkAt(TX); level.getChunkAt(RX); level.getChunkAt(TX.north());
        level.setBlock(TX.below(), Blocks.STONE.defaultBlockState(), 3);
        level.setBlock(RX.below(), Blocks.STONE.defaultBlockState(), 3);
        level.setBlock(TX.west().below(), Blocks.STONE.defaultBlockState(), 3);
        level.setBlock(TX, WirelessContent.TRANSMITTER.get().defaultBlockState().setValue(WirelessBlock.FACING, Direction.WEST), 3);
        level.setBlock(RX, WirelessContent.RECEIVER.get().defaultBlockState().setValue(WirelessBlock.FACING, Direction.UP), 3);
        WirelessBlockEntity transmitter = (WirelessBlockEntity) level.getBlockEntity(TX), receiver = (WirelessBlockEntity) level.getBlockEntity(RX);
        require(WirelessContent.TRANSMITTER.get().getExplosionResistance() == 6 && WirelessContent.RECEIVER.get().getExplosionResistance() == 6,
                "legacy effective explosion resistance (setter10 stores30 then exposes6)");
        transmitter.configure(ChannelList.of(101, 102), true, 15, 0, Direction.WEST);
        receiver.configure(ChannelList.of(102, 103), false, 9, 2, Direction.UP);
        level.setBlock(TX.west(), Blocks.LIGHT_WEIGHTED_PRESSURE_PLATE.defaultBlockState().setValue(WeightedPressurePlateBlock.POWER, 7), 2);
        transmitter.sampleInput(); transmitter.sync(); receiver.sync();
    }

    public static void run(ServerLevel level) {
        level.setBlock(RX.below(),Blocks.STONE.defaultBlockState(),3);
        resetFixture(level);
        require(WirelessContent.TRANSMITTER.get().getExplosionResistance() == 6 && WirelessContent.RECEIVER.get().getExplosionResistance() == 6,
                "legacy effective explosion resistance (setter10 stores30 then exposes6)");
        level.getChunkAt(TX); level.getChunkAt(RX);
        level.setBlock(TX, WirelessContent.TRANSMITTER.get().defaultBlockState().setValue(WirelessBlock.FACING, Direction.WEST), 3);
        level.setBlock(RX, WirelessContent.RECEIVER.get().defaultBlockState().setValue(WirelessBlock.FACING, Direction.EAST), 3);
        WirelessBlockEntity transmitter = (WirelessBlockEntity) level.getBlockEntity(TX), receiver = (WirelessBlockEntity) level.getBlockEntity(RX);
        transmitter.configure(ChannelList.of(101, 102), true, 15, 0, Direction.WEST);
        receiver.configure(ChannelList.of(102, 103), true, 9, 2, Direction.EAST);
        settle(level);
        require(receiver.outputLevel() == 2, "off level before input");
        level.setBlock(TX.west().below(), Blocks.STONE.defaultBlockState(), 2);
        level.setBlock(TX.west(), Blocks.LIGHT_WEIGHTED_PRESSURE_PLATE.defaultBlockState().setValue(WeightedPressurePlateBlock.POWER, 7), 2);
        transmitter.sampleInput();
        require(ModernRedstoneChannels.level(level, 101) == 7 && ModernRedstoneChannels.level(level, 102) == 7, "directional analog transmitter");
        require(receiver.outputLevel() == 7 && ModernRedstoneChannels.level(level, 103) == 0, "partial-overlap analog receiver");
        require(level.getBlockState(RX).getSignal(level, RX, Direction.WEST) == 7 && level.getBlockState(RX).getSignal(level, RX, Direction.EAST) == 0, "receiver output only on port");
        transmitter.configure(ChannelList.of(101, 102), false, 15, 0, Direction.WEST);
        require(receiver.outputLevel() == 15, "digital transmitter expands nonzero input");
        receiver.configure(ChannelList.of(102, 103), false, 9, 2, Direction.EAST);
        require(receiver.outputLevel() == 9, "digital receiver on level");
        level.setBlock(TX.west(), Blocks.AIR.defaultBlockState(), 2); transmitter.sampleInput();
        require(receiver.outputLevel() == 2, "receiver configured off level");
        for (Direction face : Direction.values()) {
            transmitter.configure(ChannelList.of(101, 102), true, 15, 0, face);
            receiver.configure(ChannelList.of(102, 103), true, 9, 2, face);
            level.setBlock(TX.relative(face), Blocks.REDSTONE_BLOCK.defaultBlockState(), 2); transmitter.sampleInput();
            require(receiver.outputLevel() == 15, "six-face transmitter input " + face);
            for (Direction query : Direction.values()) {
                require(level.getBlockState(RX).getSignal(level, RX, query) == (query == face.getOpposite() ? 15 : 0), "six-face receiver output " + face + "/" + query);
            }
            level.setBlock(TX.relative(face), Blocks.AIR.defaultBlockState(), 2); transmitter.sampleInput();
            require(receiver.outputLevel() == 2, "six-face input reset " + face);
        }

        var player = FakePlayerFactory.getMinecraft(level); player.setGameMode(GameType.CREATIVE); player.setPos(14.5, 81, .5);
        WirelessMenu menu = new WirelessMenu(7, player.getInventory(), RX); player.containerMenu = menu;
        WirelessPayload valid = new WirelessPayload(7, "102, 103", false, 9, 2, Direction.UP.get3DDataValue());
        require(WirelessPayload.apply(player, valid), "authorized session settings");
        require(!WirelessPayload.apply(player, new WirelessPayload(6, "102", true, 15, 0, 0)), "wrong menu window accepted");
        require(!WirelessPayload.apply(player, new WirelessPayload(7, "-1", true, 15, 0, 0)), "invalid channel accepted");
        require(!WirelessPayload.apply(player, new WirelessPayload(7, "102", true, 16, 0, 0)), "oversized level accepted");
        player.containerMenu = player.inventoryMenu;
        require(!WirelessPayload.apply(player, valid), "missing settings session accepted"); player.containerMenu = menu;
        player.setPos(200, 81, 200); require(!WirelessPayload.apply(player, valid), "distance permission accepted"); player.setPos(14.5, 81, .5);
        var previousHeld = player.getMainHandItem();
        player.setGameMode(GameType.ADVENTURE); player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.STICK));
        require(!WirelessPayload.apply(player, valid), "adventure edit without held-item permission accepted");
        var permitted = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.STICK);
        permitted.set(net.minecraft.core.component.DataComponents.CAN_PLACE_ON, new net.minecraft.world.item.AdventureModePredicate(java.util.List.of(
                net.minecraft.advancements.critereon.BlockPredicate.Builder.block().of(Blocks.STONE).build()), false));
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, permitted);
        require(WirelessPayload.apply(player, valid), "legacy adventure held-item edit permission rejected");
        permitted.set(net.minecraft.core.component.DataComponents.CAN_PLACE_ON, new net.minecraft.world.item.AdventureModePredicate(java.util.List.of(
                net.minecraft.advancements.critereon.BlockPredicate.Builder.block().of(WirelessContent.TRANSMITTER.get()).build()), false));
        require(!WirelessPayload.apply(player, valid), "adventure whitelist for another block accepted");
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, previousHeld); player.setGameMode(GameType.CREATIVE);
        net.minecraft.world.item.ItemStack held = player.getMainHandItem(); player.setGameMode(GameType.SURVIVAL);
        for (BlockPos endpoint : new BlockPos[]{TX, RX}) {
            require(level.getBlockState(endpoint).getMapColor(level, endpoint) == net.minecraft.world.level.material.MapColor.METAL, "legacy iron material map color");
            player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.WOODEN_PICKAXE));
            require(!player.hasCorrectToolForDrops(level.getBlockState(endpoint)), "wooden pickaxe must not harvest wireless endpoint");
            player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.STONE_PICKAXE));
            require(player.hasCorrectToolForDrops(level.getBlockState(endpoint)), "stone pickaxe must harvest wireless endpoint");
        }
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, held); player.setGameMode(GameType.CREATIVE);
        net.minecraft.world.item.ItemStack plainReceiver = null;
        for (BlockPos endpoint : new BlockPos[]{TX, RX}) {
            var state = level.getBlockState(endpoint); var tile = level.getBlockEntity(endpoint);
            var picked = state.getBlock().getCloneItemStack(state,
                    new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(endpoint), Direction.UP, endpoint, false), level, endpoint, player);
            var drops = net.minecraft.world.level.block.Block.getDrops(state, level, endpoint, tile, player,
                    new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.STONE_PICKAXE));
            require(picked.is(state.getBlock().asItem()) && !picked.has(net.minecraft.core.component.DataComponents.BLOCK_ENTITY_DATA)
                    && !picked.has(net.minecraft.core.component.DataComponents.CUSTOM_DATA), "wireless pick must retain legacy plain-item behavior");
            require(drops.size() == 1 && drops.get(0).is(state.getBlock().asItem())
                    && !drops.get(0).has(net.minecraft.core.component.DataComponents.BLOCK_ENTITY_DATA)
                    && !drops.get(0).has(net.minecraft.core.component.DataComponents.CUSTOM_DATA), "wireless drop must not preserve endpoint settings");
            if (endpoint.equals(RX)) plainReceiver = picked.copy();
        }

        RegistryFriendlyByteBuf bytes = new RegistryFriendlyByteBuf(Unpooled.buffer(), level.registryAccess());
        try {
            WirelessPayload.CODEC.encode(bytes, valid);
            require(WirelessPayload.CODEC.decode(bytes).equals(valid), "bounded native payload roundtrip");
        } finally { bytes.release(); }
        RegistryFriendlyByteBuf oversized = new RegistryFriendlyByteBuf(Unpooled.buffer(), level.registryAccess());
        try {
            oversized.writeInt(7); oversized.writeVarInt(ChannelList.MAX_TEXT_LENGTH * 3 + 1);
            try { WirelessPayload.CODEC.decode(oversized); throw new IllegalStateException("oversized declared string accepted"); }
            catch (io.netty.handler.codec.DecoderException expected) { }
        } finally { oversized.release(); }
        int[] maximum = new int[ChannelList.MAX_CHANNELS];
        for (int i = 0; i < maximum.length; i++) maximum[i] = Integer.MAX_VALUE - i;
        String maximumText = ChannelList.of(maximum).toString();
        require(maximumText.length() == 766, "maximum list fixture");
        WirelessPayload maximumPacket = new WirelessPayload(7, maximumText, true, 15, 0, 0);
        RegistryFriendlyByteBuf maximumBytes = new RegistryFriendlyByteBuf(Unpooled.buffer(), level.registryAccess());
        try {
            WirelessPayload.CODEC.encode(maximumBytes, maximumPacket);
            require(WirelessPayload.CODEC.decode(maximumBytes).equals(maximumPacket), "64 max-int channels payload roundtrip");
        } finally { maximumBytes.release(); }
        CompoundTag saved = receiver.saveWithoutMetadata(level.registryAccess());
        WirelessBlockEntity restored = new WirelessBlockEntity(RX, receiver.getBlockState()); restored.loadWithComponents(saved, level.registryAccess());
        require(restored.getRedstoneChannels().equals(receiver.getRedstoneChannels()) && !restored.analog() && restored.onLevel() == 9 && restored.offLevel() == 2, "saved settings roundtrip");

        BlockPos stalePos = new BlockPos(20, 80, 0); level.getChunkAt(stalePos); level.getChunkAt(stalePos.north());
        level.setBlock(stalePos, WirelessContent.TRANSMITTER.get().defaultBlockState(), 3);
        WirelessBlockEntity stale = (WirelessBlockEntity) level.getBlockEntity(stalePos);
        stale.configure(ChannelList.of(200), true, 15, 0, Direction.NORTH);
        level.setBlock(stalePos.north(), Blocks.REDSTONE_BLOCK.defaultBlockState(), 2); stale.sampleInput();
        require(ModernRedstoneChannels.level(level, 200) == 15, "stale identity fixture must first be a real powered source");
        ModernRedstoneChannels.queueRegistration(stale);
        level.setBlock(stalePos, Blocks.AIR.defaultBlockState(), 3);
        require(ModernRedstoneChannels.level(level, 200) == 0, "block removal must clear powered membership without a global tick scan");
        ModernRedstoneChannels.queueRegistration(stale); settle(level);
        require(!stale.isChannelMemberActive() && ModernRedstoneChannels.level(level, 200) == 0, "queued replaced identity resurrected");
        level.setBlock(TX.west(), Blocks.LIGHT_WEIGHTED_PRESSURE_PLATE.defaultBlockState().setValue(WeightedPressurePlateBlock.POWER, 7), 2);
        transmitter.configure(ChannelList.of(101, 102), true, 15, 0, Direction.WEST); transmitter.sampleInput();
        require(ModernRedstoneChannels.level(level, 101) == 7, "unload fixture must first be a real powered source");
        transmitter.onChunkUnloaded();
        require(ModernRedstoneChannels.level(level, 101) == 0, "chunk unload retained transmitter");
        ModernRedstoneChannels.queueRegistration(transmitter); settle(level);
        require(receiver.outputLevel() == 9, "wireless final saved state");
        BlockPos replacement = new BlockPos(20, 80, 0), support = replacement.below(); var previousSupport = level.getBlockState(support);
        level.setBlock(support, Blocks.STONE.defaultBlockState(), 3);
        player.setPos(20.5, 80, -2); player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, plainReceiver);
        var placement = plainReceiver.getItem().useOn(new net.minecraft.world.item.context.UseOnContext(player, net.minecraft.world.InteractionHand.MAIN_HAND,
                new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(support).add(0, .5, 0), Direction.UP, support, false)));
        require(placement.consumesAction() && level.getBlockEntity(replacement) instanceof WirelessBlockEntity, "plain endpoint item actual placement");
        WirelessBlockEntity placed = (WirelessBlockEntity) level.getBlockEntity(replacement);
        require(placed.getRedstoneChannels().isEmpty() && placed.analog() && placed.onLevel() == 15 && placed.offLevel() == 0,
                "plain endpoint placement must use legacy defaults rather than copied configuration");
        level.setBlock(replacement, Blocks.AIR.defaultBlockState(), 3); level.setBlock(support, previousSupport, 3);
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, held);
        System.out.println("[vandorlabs][modern] wireless-write PASS directional analog/digital, exact levels/partial overlap, menu permission rejection, payload/NBT roundtrip, deferred stale identity, unload cleanup");
    }

    public static void read(ServerLevel level) {
        level.getChunkAt(TX); level.getChunkAt(RX);
        require(level.getBlockEntity(TX) instanceof WirelessBlockEntity && level.getBlockEntity(RX) instanceof WirelessBlockEntity, "wireless endpoints absent after restart");
        WirelessBlockEntity transmitter = (WirelessBlockEntity) level.getBlockEntity(TX), receiver = (WirelessBlockEntity) level.getBlockEntity(RX);
        require(transmitter.getRedstoneChannels().equals(ChannelList.of(101, 102)) && transmitter.analog() && transmitter.port() == Direction.WEST, "transmitter settings disk reload");
        require(receiver.getRedstoneChannels().equals(ChannelList.of(102, 103)) && !receiver.analog() && receiver.onLevel() == 9 && receiver.offLevel() == 2 && receiver.port() == Direction.UP, "receiver settings disk reload");
        System.out.println("[vandorlabs][modern] wireless-read PASS exact channels, analog/digital levels and ports recovered from disk");
    }
}
