package com.vandorlabs.redstone;

import java.util.HashMap;
import java.util.Map;

/** Executes the exact network implementation linked by both adapters. */
public final class ChannelNetworkTest {
    private static class Member implements ChannelMember {
        ChannelList channels;
        int source, received, notifications;
        boolean active = true;
        Member(int channel, int source) { channels = ChannelList.of(channel); this.source = source; }
        @Override public ChannelList getRedstoneChannels() { return channels; }
        @Override public boolean isChannelMemberActive() { return active; }
        @Override public int localSignalLevel(int channel) { return source; }
        @Override public void setChannelLevel(int level) { received = level; notifications++; }
        // Distinct logical controls with equal values must still be separate members.
        @Override public boolean equals(Object other) { return other instanceof Member; }
        @Override public int hashCode() { return 0; }
    }
    private static final class Latch extends Member implements ChannelLatch {
        Map<Integer, Integer> levels = new HashMap<>();
        Latch(ChannelList channels, int savedLevel) {
            super(1, 0); this.channels = channels;
            for (int channel : channels.toArray()) levels.put(channel, savedLevel);
        }
        @Override public boolean isChannelLatch() { return true; }
        @Override public int latchedLevel(int channel) { return levels.getOrDefault(channel, 0); }
        @Override public int localSignalLevel(int channel) { return latchedLevel(channel); }
        @Override public void applyLinkedLevels(Map<Integer, Integer> next) { levels = new HashMap<>(next); }
    }
    private static void expect(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        ChannelNetwork network = new ChannelNetwork(Runnable::run);
        Member source = new Member(1, 7), consumer = new Member(1, 0), pulse = new Member(1, 15);
        network.register(consumer); network.register(source);
        expect(consumer.received == 7 && network.level(1) == 7, "exact source level");
        network.register(pulse);
        expect(consumer.received == 15, "strongest independent source");
        pulse.source = 3; network.inputChanged(pulse);
        expect(consumer.received == 7, "decreased maximum rescans other sources");
        source.source = -10; network.inputChanged(source);
        expect(consumer.received == 3, "negative source clamped");
        pulse.source = 99; network.inputChanged(pulse);
        expect(consumer.received == 15, "oversized source clamped");
        pulse.active = false; network.unregister(pulse);
        expect(consumer.received == 0, "unloaded strongest source removed");
        expect(!network.allPowered(ChannelList.EMPTY), "empty AND not powered");

        source.channels = ChannelList.of(2); source.source = 11; network.register(source);
        expect(network.level(1) == 0 && network.level(2) == 11, "channel edit removes old membership");
        consumer.channels = ChannelList.of(1, 2); network.register(consumer);
        expect(consumer.received == 11 && !network.allPowered(consumer.channels), "consumer OR versus allPowered AND");
        Member other = new Member(1, 5); network.register(other);
        expect(network.allPowered(consumer.channels), "two powered channels");
        int before = consumer.notifications;
        network.beginTransfer(); network.beginTransfer();
        source.source = 4; network.inputChanged(source);
        other.source = 8; network.inputChanged(other);
        network.endTransfer();
        expect(consumer.notifications == before && consumer.received == 11, "nested transfer hides intermediate state");
        network.endTransfer();
        expect(consumer.notifications == before + 1 && consumer.received == 8, "transfer notifies complete settled OR once");
        try { network.endTransfer(); throw new AssertionError("unbalanced transaction accepted"); }
        catch (IllegalStateException expected) { }

        ChannelNetwork linked = new ChannelNetwork(Runnable::run);
        Latch first = new Latch(ChannelList.of(3, 4), 6), second = new Latch(ChannelList.of(4, 5), 12);
        Member receiver = new Member(4, 0), independent = new Member(4, 15);
        linked.register(first); linked.register(second); linked.register(receiver);
        expect(second.latchedLevel(4) == 6 && second.latchedLevel(5) == 12, "live bank wins on partial overlap");
        linked.latchChanged(second, 0);
        expect(first.latchedLevel(3) == 6 && first.latchedLevel(4) == 0 && receiver.received == 0,
                "linked off updates shared channel without clearing unrelated channels");
        linked.register(independent); linked.latchChanged(first, 9);
        linked.latchChanged(second, 0);
        expect(receiver.received == 15 && first.latchedLevel(4) == 0, "pulse powers consumer without holding latch on");
        linked.unregister(independent);
        expect(receiver.received == 0, "pulse expiry exposes off latch");
        linked.unregister(first); linked.unregister(second);
        Latch reloaded = new Latch(ChannelList.of(4), 4);
        linked.register(reloaded);
        expect(receiver.received == 4, "last latch removal allows saved level restore");
        reloaded.active = false; linked.inputChanged(reloaded);
        expect(receiver.received == 0, "inactive member cannot power consumer");
        System.out.println("Channel network PASS: exact levels, identity, liveness, linked overlap, pulse isolation and nested transfer");
    }
}
