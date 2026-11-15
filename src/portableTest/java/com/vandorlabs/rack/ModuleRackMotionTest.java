package com.vandorlabs.rack;

/** Exact endpoint and event policy checks, without Minecraft or a render clock. */
public final class ModuleRackMotionTest {
    private ModuleRackMotionTest() { }
    public static void main(String[] args) {
        require(ModuleRackMotion.starts(false, 1, 0, 0, 4, 4), "removing a seated module starts an extraction");
        require(ModuleRackMotion.offset(-10) == ModuleRackMotion.TRAVEL_BLOCKS, "negative age");
        require(ModuleRackMotion.offset(0) == ModuleRackMotion.TRAVEL_BLOCKS, "initial position");
        require(ModuleRackMotion.offset(ModuleRackMotion.DURATION_TICKS) == 0, "seated endpoint");
        require(ModuleRackMotion.offset(1000000) == 0, "late update stays seated");
        require(ModuleRackMotion.offset(Double.NaN) == ModuleRackMotion.TRAVEL_BLOCKS, "invalid clock must not emit NaN geometry");
        double previous = ModuleRackMotion.offset(0);
        for (int index = 1; index <= 1000; index++) {
            double next = ModuleRackMotion.offset(ModuleRackMotion.DURATION_TICKS * index / 1000.0);
            require(next >= 0 && next <= previous, "slide must be monotonic and cannot overshoot");
            previous = next;
        }
        for (double from : new double[] {0, .125, .25, ModuleRackMotion.TRAVEL_BLOCKS})
            for (double to : new double[] {0, ModuleRackMotion.TRAVEL_BLOCKS}) {
                require(ModuleRackMotion.between(0, from, to) == from, "reversal starts at current displayed pose");
                require(ModuleRackMotion.between(10, from, to) == to, "insertion and extraction have exact endpoints");
                double last = from;
                for (int index = 1; index <= 100; index++) {
                    double value = ModuleRackMotion.between(index / 10.0, from, to);
                    require(value >= Math.min(from, to) && value <= Math.max(from, to), "reversal does not overshoot");
                    require(to >= from ? value >= last : value <= last, "reversal remains monotonic");
                    last = value;
                }
            }
        int cases = 0;
        for (int before = 0; before < 16; before++) for (int after = 0; after < 16; after++)
            for (int slot = 0; slot < 4; slot++) {
                boolean occupied = (after & (1 << slot)) != 0;
                boolean changed = ((before ^ after) & (1 << slot)) != 0;
                require(!ModuleRackMotion.starts(true, before, after, slot, 4, 5), "load snapshot replays insertion");
                require(ModuleRackMotion.starts(false, before, after, slot, 4, 4) == changed, "unchanged sequence transition");
                require(ModuleRackMotion.starts(false, before, after, slot, 4, 5) == (changed || occupied),
                        "distinct insertion sequence must handle remove+reinsert between updates");
                cases++;
            }
        for (int slot : new int[] {-1, 4, Integer.MAX_VALUE})
            require(!ModuleRackMotion.starts(false, 0, 15, slot, 0, 1), "invalid slot");
        System.out.println("Module Rack motion PASS " + cases
                + " mask/event cases +1000 position samples; no load/duplicate replay, extraction + continuous reversal endpoints");
    }
    private static void require(boolean value, String reason) {
        if (!value) throw new AssertionError(reason);
    }
}
