package com.vandorlabs.rack;

/** Exact endpoint and event policy checks, without Minecraft or a render clock. */
public final class ModuleRackMotionTest {
    private ModuleRackMotionTest() { }
    public static void main(String[] args) {
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
        int cases = 0;
        for (int before = 0; before < 16; before++) for (int after = 0; after < 16; after++)
            for (int slot = 0; slot < 4; slot++) {
                boolean occupied = (after & (1 << slot)) != 0;
                boolean added = (before & (1 << slot)) == 0 && occupied;
                require(!ModuleRackMotion.starts(true, before, after, slot, 4, 5), "load snapshot replays insertion");
                require(ModuleRackMotion.starts(false, before, after, slot, 4, 4) == added, "unchanged sequence transition");
                require(ModuleRackMotion.starts(false, before, after, slot, 4, 5) == occupied,
                        "distinct insertion sequence must handle remove+reinsert between updates");
                cases++;
            }
        for (int slot : new int[] {-1, 4, Integer.MAX_VALUE})
            require(!ModuleRackMotion.starts(false, 0, 15, slot, 0, 1), "invalid slot");
        System.out.println("Module Rack motion PASS " + cases
                + " mask/event cases +1000 position samples; no load/duplicate replay, exact seated endpoint");
    }
    private static void require(boolean value, String reason) {
        if (!value) throw new AssertionError(reason);
    }
}
