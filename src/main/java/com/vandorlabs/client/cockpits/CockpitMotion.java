package com.vandorlabs.client.cockpits;

/** Java 8 math; detents are OFF=0, LOW=1, MEDIUM=2, HIGH=3. */
public final class CockpitMotion {
    private CockpitMotion() {}

    public static double clampDetent(double value) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException("A detent must be finite");
        }
        return Math.max(0.0, Math.min(3.0, value));
    }

    /** Values may be angles in degrees or translations in blocks. */
    public static double sample(double[] values, double detent) {
        if (values == null || values.length != 4) {
            throw new IllegalArgumentException("Expected four detent values");
        }
        double position = clampDetent(detent);
        int lower = Math.min(2, (int) Math.floor(position));
        double fraction = position - lower;
        return values[lower] + (values[lower + 1] - values[lower]) * fraction;
    }

    /**
     * Optional visual easing. Store one instance per moving part on the CLIENT
     * tile, not in a shared renderer. The server remains authoritative for target.
     */
    public static final class Control {
        private double previous;
        private double current;
        private int target;

        public Control(int initialDetent) {
            snapTo(initialDetent);
        }

        public void setTarget(int detent) {
            if (detent < 0 || detent > 3) {
                throw new IllegalArgumentException("Detent must be 0, 1, 2 or 3");
            }
            target = detent;
        }

        /** Use once on client load to avoid a startup sweep from OFF. */
        public void snapTo(int detent) {
            setTarget(detent);
            previous = current = detent;
        }

        /** Call once per CLIENT game tick; 0.25 gives four ticks per detent. */
        public void tick(double detentsPerTick) {
            if (!Double.isFinite(detentsPerTick) || detentsPerTick <= 0) {
                throw new IllegalArgumentException("Animation speed must be positive");
            }
            previous = current;
            double delta = target - current;
            current += Math.copySign(Math.min(Math.abs(delta), detentsPerTick), delta);
        }

        public double interpolated(float partialTicks) {
            double fraction = Math.max(0.0, Math.min(1.0, partialTicks));
            return previous + (current - previous) * fraction;
        }
    }
}
