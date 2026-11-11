package com.vandorlabs.client;

/** Counts admitted construction requests without a client, GL context or emitter mutation. */
public final class ThrusterParticleBudgetChecks {
    private static void require(boolean yes, String message) { if (!yes) throw new AssertionError(message); }

    private static int count(ThrusterParticleBudget budget, Object world, long tick, int setting) {
        int admitted = 0;
        for (int request = 0; request < 12000; request++)
            if (budget.allow(world, tick, setting, 64D * 64D)) admitted++;
        return admitted;
    }

    public static void main(String[] args) {
        Object world = new Object();
        ThrusterParticleBudget budget = new ThrusterParticleBudget();
        for (double distance : new double[]{Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY,
                -1, Math.nextUp(64D * 64D)})
            require(!budget.allow(world, 1, 0, distance), "invalid or distant request admitted");
        require(!budget.allow(null, 1, 0, 0), "missing world admitted");

        int all = count(budget, world, 1, 0);
        require(all == 256, "all-particle cap or inclusive 64-block boundary");
        require(count(budget, world, 1, 0) == 0, "same tick replenished the admission budget");
        require(budget.allow(world, 2, 0, 0), "next tick did not replenish the budget");
        Object nextWorld = new Object();
        require(count(budget, nextWorld, 2, 0) == 256, "world replacement did not reset the same-tick budget");

        int decreased = count(new ThrusterParticleBudget(), world, 3, 1);
        int minimal = count(new ThrusterParticleBudget(), world, 3, 2);
        require(decreased == 64 && minimal == 0, "particle-setting limits");
        int lone = 0;
        ThrusterParticleBudget single = new ThrusterParticleBudget();
        for (int tick = 4; tick < 24; tick++) if (single.allow(world, tick, 1, 0)) lone++;
        require(lone == 10, "a lone decreased-setting emitter must alternate across 20 ticks");
        require(single.allow(world, 0, 0, 0), "clock correction stranded the budget");

        System.out.println("thruster-particle-budget PASS requests=12000 all=" + all + " decreased=" + decreased
                + " minimal=" + minimal + "; invalid/distant=0; same-tick reset=0; next-tick/world reset;"
                + " lone decreased emitter over 20 ticks=" + lone);
    }
    private ThrusterParticleBudgetChecks() { }
}
