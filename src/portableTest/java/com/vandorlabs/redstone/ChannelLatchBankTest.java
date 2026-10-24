package com.vandorlabs.redstone;

import java.util.Map;

/** Partial overlap, loaded control precedence and saved level contracts. */
public final class ChannelLatchBankTest {
    private static void expect(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        ChannelLatchBank bank = new ChannelLatchBank();
        ChannelList first = ChannelList.of(1, 2), second = ChannelList.of(2, 3);
        bank.initialize(1, 4);
        bank.initialize(2, 8);
        bank.initialize(2, 15);
        bank.initialize(3, 12);
        expect(bank.level(1) == 4 && bank.level(2) == 8, "loaded bank wins over later saved control");
        bank.set(first, 15);
        expect(bank.snapshot(second).get(2) == 15 && bank.level(3) == 12, "partial overlap mirrors exact channel");
        bank.set(second, 0);
        expect(bank.level(1) == 15 && bank.level(2) == 0 && bank.level(3) == 0, "linked off clears overlapping channel");
        Map<Integer, Integer> saved = bank.snapshot(first);
        expect(saved.size() == 2 && saved.get(2) == 0, "save includes zero level");
        saved.put(1, 0);
        expect(bank.level(1) == 15, "snapshot must be detached");
        bank.remove(1);
        expect(!bank.contains(1) && bank.level(1) == 0, "last latch removal forgets state");
        bank.initialize(1, 6);
        expect(bank.level(1) == 6, "later load restores saved level");
        for (int level = 0; level <= 15; level++) {
            bank.set(first, level);
            ChannelLatchBank restored = new ChannelLatchBank();
            for (Map.Entry<Integer, Integer> entry : bank.snapshot(first).entrySet()) {
                restored.initialize(entry.getKey(), entry.getValue());
            }
            expect(restored.snapshot(first).equals(bank.snapshot(first)), "all signal levels survive reload");
        }
        bank.set(ChannelList.EMPTY, 15);
        expect(bank.snapshot(ChannelList.EMPTY).isEmpty(), "empty subscriptions");
        System.out.println("Channel latch bank PASS: partial overlap, linked off, saved/reloaded levels and lifetime");
    }
}
