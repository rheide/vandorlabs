package com.vandorlabs.neoforge.screen;

import com.vandorlabs.neoforge.VandorLabsModern;
import com.vandorlabs.neoforge.redstone.ModernRedstoneChannels;
import com.vandorlabs.neoforge.redstone.WirelessBlockEntity;
import com.vandorlabs.neoforge.redstone.WirelessContent;
import com.vandorlabs.redstone.ChannelList;
import com.vandorlabs.redstone.RedstoneScreenPolicy;
import com.vandorlabs.ui.ScreenSelectorDialog;
import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/** Opt-in native installed-screen controls, saved levels and bounded editing-session checks. */
public final class RedstoneOverlayRuntimeChecks {
    private static final BlockPos A = new BlockPos(24, 80, 0), B = new BlockPos(28, 80, 0), RECEIVER = new BlockPos(32, 80, 0);
    private static CompoundTag initialFirst, initialSecond;
    private RedstoneOverlayRuntimeChecks() { }
    private static void require(boolean value, String message) { if (!value) throw new IllegalStateException("Redstone overlay runtime: " + message); }
    private static RedstoneConfigurationPayload.Row row(String label, int kind, int... channels) { return new RedstoneConfigurationPayload.Row(label, ChannelList.of(channels), kind, 5, 15); }
    private static CompoundTag rowTag(RedstoneConfigurationPayload.Row row) {
        CompoundTag tag = new CompoundTag(); tag.putString("Label", row.label()); tag.putIntArray("RedstoneChannels", row.channels().toArray());
        tag.putBoolean("Slider", row.kind() == 1); tag.putBoolean("Pulse", row.kind() == 2); tag.putInt("Min", row.min()); tag.putInt("Max", row.max()); return tag;
    }
    private static ListTag rowTags(List<RedstoneConfigurationPayload.Row> rows) { ListTag result = new ListTag(); for (var row : rows) result.add(rowTag(row)); return result; }
    private static ProgrammableScreenEntity place(ServerLevel level, BlockPos pos) {
        level.getChunkAt(pos); level.setBlock(pos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
        level.setBlock(pos, ScreenRegistration.BLOCK.get().defaultBlockState(), 3);
        return (ProgrammableScreenEntity) level.getBlockEntity(pos);
    }
    private static void offWithOverlay(ProgrammableScreenEntity screen) {
        screen.configure("engineering_screen", new ScreenSelectorDialog.State(false, 0, 0, 1, 0, 1, true, false), ChannelList.EMPTY, 3, RedstoneScreenPolicy.REDSTONE_SURFACE);
    }
    public static void clientFixture(ServerLevel level) {
        ProgrammableScreenEntity first = place(level, new BlockPos(6, 80, 0)), linked = place(level, new BlockPos(8, 80, 0));
        offWithOverlay(first); offWithOverlay(linked);
        first.redstone().applyRowConfiguration("REDSTONE CONTROL", rowTags(List.of(row("DOOR", 0, 77), row("THROTTLE", 1, 77, 78), row("DEPART", 2, 99))));
        linked.redstone().applyRowConfiguration("LINKED", rowTags(List.of(row("LINKED", 0, 77))));
    }
    public static void run(ServerLevel level) {
        ProgrammableScreenEntity first = place(level, A), second = place(level, B); offWithOverlay(first); offWithOverlay(second);
        var player = FakePlayerFactory.getMinecraft(level); player.setGameMode(GameType.CREATIVE); player.setPos(24.5, 81, .5);
        player.containerMenu = new ScreenMenu(9, player.getInventory(), A, true);
        List<RedstoneConfigurationPayload.Row> aRows = List.of(row("PRIMARY", 0, 301, 302), row("THROTTLE", 1, 303, 304), row("DEPART", 2, 301, 302));
        List<RedstoneConfigurationPayload.Row> bRows = List.of(row("LINKED", 0, 302, 305), row("THROTTLE", 1, 304, 306));
        RedstoneConfigurationPayload settings = new RedstoneConfigurationPayload(9, A, "PRIMARY", aRows, 3);
        require(RedstoneConfigurationPayload.apply(player, settings), "authorized row settings");
        require(second.redstone().applyRowConfiguration("LINKED", rowTags(bRows)), "linked row configuration");
        require(first.data().displayMode == 0 && first.getBlockState().getLightEmission(level, A) == 11 && first.surface() == -2, "overlay overrides underlying OFF surface/light");
        require(!RedstoneConfigurationPayload.apply(player, new RedstoneConfigurationPayload(8, A, "PRIMARY", aRows, 3)), "wrong window accepted");
        require(!RedstoneConfigurationPayload.apply(player, new RedstoneConfigurationPayload(9, B, "PRIMARY", aRows, 3)), "wrong installed target accepted");
        player.containerMenu = new ScreenMenu(9, player.getInventory(), A, false);
        require(!RedstoneConfigurationPayload.apply(player, settings), "ordinary screen selector session accepted");
        player.containerMenu = new ScreenMenu(9, player.getInventory(), A, true);
        player.setGameMode(GameType.SURVIVAL); player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        require(!RedstoneConfigurationPayload.apply(player, settings), "survival without configurizer accepted");
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(VandorLabsModern.CONFIGURIZER.get()));
        require(RedstoneConfigurationPayload.apply(player, settings), "survival configurizer denied"); player.setGameMode(GameType.CREATIVE);
        player.setPos(200, 81, 200); require(!RedstoneConfigurationPayload.apply(player, settings), "remote row editor accepted"); player.setPos(24.5, 81, .5);
        int[] maximum = new int[64]; for (int i = 0; i < maximum.length; i++) maximum[i] = Integer.MAX_VALUE - i;
        List<RedstoneConfigurationPayload.Row> eight = new ArrayList<>(); for (int i = 0; i < 8; i++) eight.add(row("ROW " + i, 0, maximum));
        RedstoneConfigurationPayload maxPacket = new RedstoneConfigurationPayload(9, A, "MAXIMUM", eight, 3);
        RegistryFriendlyByteBuf bytes = new RegistryFriendlyByteBuf(Unpooled.buffer(), level.registryAccess()); int encodedBytes;
        try { RedstoneConfigurationPayload.CODEC.encode(bytes, maxPacket); encodedBytes = bytes.readableBytes(); require(RedstoneConfigurationPayload.CODEC.decode(bytes).equals(maxPacket), "eight rows of64 max-int channel codec roundtrip"); } finally { bytes.release(); }
        require(RedstoneConfigurationPayload.apply(player, maxPacket) && first.redstone().rows().size() == 8, "eight rows actual native capacity");
        List<RedstoneConfigurationPayload.Row> nine = new ArrayList<>(eight); nine.add(row("NINE", 0, 1));
        require(!RedstoneConfigurationPayload.apply(player, new RedstoneConfigurationPayload(9, A, "TOO MANY", nine, 3)), "ninth row accepted");
        RegistryFriendlyByteBuf malformed = new RegistryFriendlyByteBuf(Unpooled.buffer(), level.registryAccess());
        try {
            malformed.writeVarInt(9); malformed.writeBlockPos(A); malformed.writeUtf("", 32); malformed.writeByte(9);
            try { RedstoneConfigurationPayload.CODEC.decode(malformed); throw new IllegalStateException("ninth row allocated"); } catch (IllegalArgumentException expected) { }
        } finally { malformed.release(); }
        require(RedstoneConfigurationPayload.apply(player, settings), "restore after maximum capacity test");
        ModernRedstoneChannels.onLevelTick(new LevelTickEvent.Post(() -> true, level));
        var a = first.redstone(); var b = second.redstone();
        checkSameTickBodyAim(first, player);
        a.toggleRow(0); require(a.rows().get(0).active() && !b.rows().get(0).active() && b.rows().get(0).latchedLevel(302) == 15 && b.rows().get(0).latchedLevel(305) == 0, "partial toggle overlap");
        b.toggleRow(0); require(a.rows().get(0).active() && b.rows().get(0).active(), "linked toggle in opposite direction");
        a.toggleRow(0); require(!b.rows().get(0).active() && b.rows().get(0).latchedLevel(305) == 15 && b.rows().get(0).latchedLevel(302) == 0, "shared OFF preserves unrelated channel");
        b.toggleRow(0); b.toggleRow(0); a.toggleRow(0);
        a.selectSegment(1, 2); require(a.rows().get(1).level() == 10 && b.rows().get(1).latchedLevel(304) == 10 && b.rows().get(1).latchedLevel(306) == 0, "partial slider exact level");
        b.selectSegment(1, 1); require(a.rows().get(1).latchedLevel(303) == 10 && a.rows().get(1).latchedLevel(304) == 5, "reverse slider partial bank");
        a.selectSegment(1, 3); b.selectSegment(1, 2);
        level.getChunkAt(RECEIVER); level.setBlock(RECEIVER, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3); level.setBlock(RECEIVER, WirelessContent.RECEIVER.get().defaultBlockState(), 3);
        WirelessBlockEntity consumer = (WirelessBlockEntity) level.getBlockEntity(RECEIVER); consumer.configure(ChannelList.of(304), true, 15, 0, Direction.NORTH);
        require(consumer.outputLevel() == 10, "actual carried-independent wired consumer gets exact slider level");
        a.pulseRow(2); long deadline = a.rows().get(2).pulseUntil(); require(deadline - level.getGameTime() == 20, "pulse duration20 server ticks");
        a.toggleRow(0); require(a.rows().get(0).latchedLevel(301) == 0 && ModernRedstoneChannels.level(level, 301) == 15, "pulse remains independent while linked latch switched off");
        CompoundTag pulsing = first.saveWithoutMetadata(level.registryAccess()); var pulsingCopy = new ProgrammableScreenEntity(A, first.getBlockState()); pulsingCopy.loadWithComponents(pulsing, level.registryAccess());
        require(pulsingCopy.redstone().rows().get(2).pulseUntil() == deadline, "pulse deadline saved without restarting");
        var drops = Block.getDrops(first.getBlockState(), level, A, first, player, new ItemStack(Items.IRON_PICKAXE));
        require(drops.size() == 1 && drops.get(0).has(net.minecraft.core.component.DataComponents.BLOCK_ENTITY_DATA), "configured overlay drop");
        ListTag droppedRows = drops.get(0).get(net.minecraft.core.component.DataComponents.BLOCK_ENTITY_DATA).copyTag().getCompound("RedstonePrimary").getList("Rows", 10);
        require(droppedRows.size() == 3 && !droppedRows.getCompound(2).contains("PulseUntil") && !droppedRows.getCompound(0).contains("Levels") && !droppedRows.getCompound(0).contains("Active"), "configured drop must exclude transient bank/pulse state");
        require(a.expirePulses(deadline - 1) && ModernRedstoneChannels.level(level, 301) == 15, "pulse remains until exact deadline");
        require(!a.expirePulses(deadline) && ModernRedstoneChannels.level(level, 301) == 0, "native pulse expiry clears independent source");
        a.toggleRow(0);
        CompoundTag saved = first.saveWithoutMetadata(level.registryAccess()); var restored = new ProgrammableScreenEntity(A, first.getBlockState()); restored.loadWithComponents(saved, level.registryAccess());
        require(restored.redstone().rows().get(1).latchedLevel(303) == 15 && restored.redstone().rows().get(1).latchedLevel(304) == 10 && restored.redstone().rows().get(1).level() == 15, "exact partial-bank NBT roundtrip");
        first.onChunkUnloaded();
        require(ModernRedstoneChannels.level(level, 301) == 0 && ModernRedstoneChannels.level(level, 303) == 0
                && ModernRedstoneChannels.level(level, 304) == 10, "owner unload removes logical rows immediately while preserving other loaded sources");
        first.channelLoaded();
        require(ModernRedstoneChannels.level(level, 301) == 15 && ModernRedstoneChannels.level(level, 303) == 15, "deferred owner reload republishes saved logical row levels");
        System.out.println("[vandorlabs][modern] redstone-overlay-write PASS installed bidirectional/partial toggle and slider banks,20tick pulse independence/expiry,8rows/64channels codec=" + encodedBytes + "bytes, editing permissions, exact NBT and transient-free drops");
    }
    private static net.minecraft.world.phys.Vec3 aimPoint(BlockPos pos, double column, double pixelY) {
        var q = com.vandorlabs.render.ScreenSurface.quad(com.vandorlabs.render.ScreenSurface.Kind.FLAT, false);
        double vertical = pixelY / 128D;
        return new net.minecraft.world.phys.Vec3(pos.getX() + (q.topRight.x - column / 128D * (q.topRight.x - q.topLeft.x)) / 16D,
                pos.getY() + (q.topRight.y + vertical * (q.bottomRight.y - q.topRight.y)) / 16D,
                pos.getZ() + (q.topRight.z + vertical * (q.bottomRight.z - q.topRight.z)) / 16D);
    }
    private static void checkSameTickBodyAim(ProgrammableScreenEntity screen, net.minecraft.server.level.ServerPlayer player) {
        player.setPos(A.getX() + .5, A.getY() + 1, A.getZ() - 3); player.setShiftKeyDown(false); player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        var eye = player.getEyePosition(); var current = aimPoint(A, 97.5, 41).subtract(eye); var previous = aimPoint(A, 112.5, 41).subtract(eye);
        float bodyYaw = (float) Math.toDegrees(Math.atan2(-current.x, current.z));
        float pitch = (float) -Math.toDegrees(Math.atan2(current.y, Math.sqrt(current.x * current.x + current.z * current.z)));
        player.setYRot(bodyYaw); player.setXRot(pitch);
        player.setYHeadRot((float) Math.toDegrees(Math.atan2(-previous.x, previous.z)));
        var localEye = eye.subtract(A.getX(), A.getY(), A.getZ()); var oldEnd = localEye.add(player.getViewVector(1).scale(Math.min(8, player.blockInteractionRange())));
        var layout = new com.vandorlabs.ui.RedstoneScreenLayout(false);
        var stale = layout.hit(com.vandorlabs.render.ScreenSurface.quad(com.vandorlabs.render.ScreenSurface.Kind.FLAT, false), Direction.NORTH.get3DDataValue(),
                new double[]{localEye.x, localEye.y, localEye.z}, new double[]{oldEnd.x, oldEnd.y, oldEnd.z}, screen.redstone().rows().size());
        int staleSegment = com.vandorlabs.ui.RedstoneScreenLayout.sliderSegment(stale.column, 4);
        require(stale.row == 1 && staleSegment == 3, "stale-head negative control: row=" + stale.row + " column=" + stale.column + " pixelY=" + stale.pixelY + " segment=" + staleSegment);
        var actual = RedstoneScreenInteraction.hit(screen, player);
        int actualSegment = com.vandorlabs.ui.RedstoneScreenLayout.sliderSegment(actual.column, 4);
        // Native Mth uses a float sine lookup table. Its ray quantization is
        // about 0.02 authored pixels here; the selected detent must remain exact.
        require(actual.row == 1 && actualSegment == 2 && Math.abs(actual.column - 97.5) < .1,
                "same-tick body aim: row=" + actual.row + " column=" + actual.column + " pixelY=" + actual.pixelY + " segment=" + actualSegment + " yaw=" + player.getYRot() + " head=" + player.getYHeadRot());
        require(RedstoneScreenInteraction.activate(screen, player) && screen.redstone().rows().get(1).latchedLevel(303) == 10
                && screen.redstone().rows().get(1).latchedLevel(304) == 10, "same-tick native slider interaction must apply10 rather than stale15");
        System.out.println("[vandorlabs][modern] redstone-yaw PASS stale-head selects 15; body ray row=" + actual.row + " column=" + actual.column + " pixelY=" + actual.pixelY + " segment=" + actualSegment + " selects 10 on actual native row");
    }
    public static void read(ServerLevel level) {
        level.getChunkAt(A); level.getChunkAt(B); level.getChunkAt(RECEIVER);
        require(level.getBlockEntity(A) instanceof ProgrammableScreenEntity && level.getBlockEntity(B) instanceof ProgrammableScreenEntity, "saved installed screens missing");
        var first = ((ProgrammableScreenEntity) level.getBlockEntity(A)).redstone(); var second = ((ProgrammableScreenEntity) level.getBlockEntity(B)).redstone();
        require(first.enabled() && first.rows().size() == 3 && second.rows().size() == 2, "saved row metadata");
        require(first.rows().get(0).latchedLevel(301) == 15 && second.rows().get(0).latchedLevel(302) == 15 && second.rows().get(0).latchedLevel(305) == 0, "saved partial toggle levels");
        require(first.rows().get(1).latchedLevel(303) == 15 && first.rows().get(1).latchedLevel(304) == 10 && second.rows().get(1).latchedLevel(306) == 10, "saved partial slider levels");
        require(((WirelessBlockEntity) level.getBlockEntity(RECEIVER)).outputLevel() == 10 && ModernRedstoneChannels.level(level, 303) == 15, "reloaded actual channels publish preserved levels");
        require(initialFirst != null && initialFirst.equals(first.save(new CompoundTag())) && initialSecond.equals(second.save(new CompoundTag())), "deferred registration changed original saved row state");
        System.out.println("[vandorlabs][modern] redstone-overlay-read PASS exact installed row kinds, partial channel banks and wired consumer recovered from disk");
    }
    /** Load saved identities locally before real world ticks deliver onLoad and deferred registration. */
    public static void prepareRead(ServerLevel level) {
        for (BlockPos pos : new BlockPos[]{A, B, RECEIVER}) { level.getChunkAt(pos); require(level.getBlockEntity(pos) != null, "saved fixture identity missing before real tick"); }
        initialFirst = ((ProgrammableScreenEntity) level.getBlockEntity(A)).redstone().save(new CompoundTag());
        initialSecond = ((ProgrammableScreenEntity) level.getBlockEntity(B)).redstone().save(new CompoundTag());
    }
}
