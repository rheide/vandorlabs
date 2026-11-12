package com.vandorlabs.client;

import com.vandorlabs.redstone.ChannelList;
import com.vandorlabs.redstone.RedstoneChannelMember;
import com.vandorlabs.redstone.RedstoneChannels;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.minecraft.init.Bootstrap;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;

/** Real channel-network checks: stable inputs, analog transitions and publication lifetime. */
public final class ChannelSourceRefreshChecks {
    private static final int FIRST = 2000000000;
    private static final ChannelList BANK = ChannelList.of(FIRST, FIRST + 1, FIRST + 2, FIRST + 3);

    private static final class Member extends TileEntity implements RedstoneChannelMember {
        ChannelList inputs = BANK, outputs = ChannelList.EMPTY;
        final int[] levels = new int[4];
        int range = 64, received, calls, reads;
        boolean active = true;

        Member(NonRenderingChecks.MemoryWorld world, BlockPos position) {
            setWorld(world);
            setPos(position);
            world.tiles.put(position, this);
        }

        public TileEntity channelTile() { return this; }
        public boolean isChannelMemberActive() {
            return active && RedstoneChannelMember.super.isChannelMemberActive();
        }
        public int getRedstoneChannel() { return inputs.first(); }
        public void setRedstoneChannel(int channel) { inputs = ChannelList.of(channel); }
        public ChannelList getRedstoneChannels() { return inputs; }
        public ChannelList transmittedChannels() { return outputs; }
        public int transmissionRange() { return range; }
        public boolean hasLocalRedstoneSignal() { return levels[0] > 0; }
        public int localSignalLevel(int channel) { reads++; return levels[channel - FIRST]; }
        public void setChannelSignal(boolean on) { setChannelLevel(on ? 15 : 0); }
        public void setChannelLevel(int level) { received = level; calls++; }
        void close() { active = false; RedstoneChannels.unregister(this); }
    }

    public static void main(String[] args) {
        Bootstrap.register();
        boolean measure = args.length != 0 && "--measure".equals(args[0]);
        stableInputs(measure);
        transitions();
        ChannelReconciliationChecks.run();
        System.out.println("Channel source refresh PASS analog range movement channel-edits liveness transfers world-isolation"
                + (measure ? " measurement-only-cost" : " zero-stable-index-work"));
    }

    private static void stableInputs(boolean measure) {
        NonRenderingChecks.MemoryWorld world = new NonRenderingChecks.MemoryWorld(false);
        List<Member> members = new ArrayList<>();
        Member source = new Member(world, new BlockPos(-1, 100, -1));
        source.inputs = ChannelList.EMPTY;
        source.outputs = BANK;
        Arrays.fill(source.levels, 8);
        members.add(source);
        try {
            RedstoneChannels.beginTransfer(world);
            try {
                RedstoneChannels.register(source);
                for (int i = 0; i < 256; i++) {
                    Member receiver = new Member(world, new BlockPos(i % 16, 100, i / 16));
                    members.add(receiver);
                    RedstoneChannels.register(receiver);
                }
            } finally { RedstoneChannels.endTransfer(world); }
            for (int i = 1; i < members.size(); i++) require(members.get(i).received == 8, "initial analog delivery");
            for (int i = 0; i < 16; i++) RedstoneChannels.inputChanged(source);
            int reads = source.reads;
            long[] before = RedstoneChannels.diagnostics(world);
            long started = System.nanoTime();
            for (int i = 0; i < 256; i++) RedstoneChannels.inputChanged(source);
            long elapsed = System.nanoTime() - started;
            long[] after = RedstoneChannels.diagnostics(world);
            long receiverCandidates = after[1] - before[1], sourceCandidates = after[3] - before[3];
            require(source.reads - reads == 256 * BANK.size(), "each notification samples only its own channel bank");
            require(after[4] == before[4], "identical output must not deliver duplicate levels");
            if (!measure) for (int i = 0; i < before.length; i++)
                require(after[i] == before[i], "identical output must not visit channel indexes counter=" + i);
            System.out.println("Channel source refresh measurement receivers=256 channels=4 notifications=256"
                    + " receiverCandidates=" + receiverCandidates + " sourceCandidates=" + sourceCandidates
                    + " receiverBuckets=" + (after[0] - before[0]) + " sourceBuckets=" + (after[2] - before[2])
                    + " callbacks=" + (after[4] - before[4]) + " elapsedNanos=" + elapsed);
        } finally { for (Member member : members) member.close(); }
    }

    private static void transitions() {
        NonRenderingChecks.MemoryWorld world = new NonRenderingChecks.MemoryWorld(false);
        NonRenderingChecks.MemoryWorld otherWorld = new NonRenderingChecks.MemoryWorld(false);
        Member source = new Member(world, new BlockPos(0, 100, 0));
        source.inputs = ChannelList.EMPTY;
        source.outputs = BANK;
        source.range = 16;
        Member near = new Member(world, new BlockPos(1, 100, 0));
        Member edge = new Member(world, new BlockPos(16, 100, 0));
        Member far = new Member(world, new BlockPos(17, 100, 0));
        Member isolated = new Member(otherWorld, new BlockPos(1, 100, 0));
        List<Member> members = new ArrayList<>(Arrays.asList(source, near, edge, far, isolated));
        try {
            for (Member member : members) RedstoneChannels.register(member);
            source.levels[0] = 7;
            RedstoneChannels.inputChanged(source);
            require(near.received == 7 && edge.received == 7 && far.received == 0 && isolated.received == 0, "finite edge and world isolation");
            source.levels[1] = 5;
            RedstoneChannels.inputChanged(source);
            require(RedstoneChannels.level(world, near.getPos(), FIRST + 1) == 5, "nonmaximum channel still publishes");
            int bankCallbacks = near.calls;
            source.levels[0] = 5;
            source.levels[1] = 7;
            RedstoneChannels.inputChanged(source);
            require(near.received == 7 && near.calls == bankCallbacks + 1
                    && RedstoneChannels.level(world, near.getPos(), FIRST) == 5
                    && RedstoneChannels.level(world, near.getPos(), FIRST + 1) == 7,
                    "swapping bank levels with the same maximum still publishes each channel");
            source.levels[0] = 7;
            source.levels[1] = 5;
            RedstoneChannels.inputChanged(source);
            require(near.received == 7 && RedstoneChannels.level(world, near.getPos(), FIRST) == 7
                    && RedstoneChannels.level(world, near.getPos(), FIRST + 1) == 5,
                    "restored bank preserves its unchanged aggregate");
            source.levels[0] = 3;
            RedstoneChannels.inputChanged(source);
            require(near.received == 5, "falling maximum exposes second channel");
            source.range = 64;
            RedstoneChannels.inputChanged(source);
            require(far.received == 5, "range expansion with unchanged levels");
            source.range = 16;
            RedstoneChannels.inputChanged(source);
            require(far.received == 0, "range contraction withdraws old recipients");
            int sourceReads = source.reads;
            move(world, near, new BlockPos(33, 100, 0));
            RedstoneChannels.positionChanged(near);
            require(near.received == 0 && source.reads == sourceReads,
                    "moving a source-free receiver settles without republishing the unchanged source");
            move(world, near, new BlockPos(1, 100, 0));
            RedstoneChannels.positionChanged(near);
            require(near.received == 5 && source.reads == sourceReads,
                    "source-free receiver returning to range reads the existing source");
            move(world, source, new BlockPos(-32, 100, 0));
            RedstoneChannels.positionChanged(source);
            require(near.received == 0 && edge.received == 0, "moving source withdraws old sphere");
            move(world, source, new BlockPos(0, 100, 0));
            RedstoneChannels.positionChanged(source);
            require(near.received == 5, "moving source restores new sphere");
            source.outputs = ChannelList.of(FIRST);
            RedstoneChannels.channelChanged(source, BANK);
            require(near.received == 3 && RedstoneChannels.level(world, FIRST + 1) == 0, "removed channel retires published source");
            source.outputs = BANK;
            RedstoneChannels.channelChanged(source, ChannelList.of(FIRST));
            require(near.received == 5, "readded channel publishes current level");
            source.active = false;
            RedstoneChannels.inputChanged(source);
            require(near.received == 0, "inactive source withdraws even with unchanged levels");
            source.active = true;
            RedstoneChannels.inputChanged(source);
            require(near.received == 5, "reactivated source republishes");
            source.range = -1;
            RedstoneChannels.inputChanged(source);
            require(near.received == 0, "disabled transmitter withdraws");
            source.range = 0;
            RedstoneChannels.inputChanged(source);
            require(far.received == 5, "unbounded source reaches remote receiver");
            Member joined = new Member(world, new BlockPos(1000, 100, 0));
            members.add(joined);
            RedstoneChannels.register(joined);
            require(joined.received == 5, "new receiver reads an unchanged source");
            RedstoneChannels.beginTransfer(world);
            try {
                source.levels[1] = 12;
                RedstoneChannels.inputChanged(source);
                RedstoneChannels.inputChanged(source);
                source.levels[1] = 0;
                RedstoneChannels.inputChanged(source);
                require(near.received == 5, "transfer defers intermediate delivery");
            } finally { RedstoneChannels.endTransfer(world); }
            require(near.received == 3 && far.received == 3, "transfer publishes final output after duplicate input");
            source.levels[0] = 99;
            RedstoneChannels.inputChanged(source);
            require(near.received == 15, "output clamps above analog maximum");
            source.levels[0] = 16;
            RedstoneChannels.inputChanged(source);
            require(near.received == 15, "equivalent clamped source remains unchanged");
            source.levels[0] = -1;
            RedstoneChannels.inputChanged(source);
            require(near.received == 0, "negative output clamps off");
            source.levels[0] = 8;
            RedstoneChannels.inputChanged(source);
            world.tiles.remove(source.getPos());
            RedstoneChannels.inputChanged(source);
            require(near.received == 0, "replaced tile identity withdraws stale source");
        } finally { for (Member member : members) member.close(); }
    }

    private static void move(NonRenderingChecks.MemoryWorld world, Member member, BlockPos position) {
        world.tiles.remove(member.getPos());
        member.setPos(position);
        world.tiles.put(position, member);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError("Channel source refresh: " + message);
    }
}
