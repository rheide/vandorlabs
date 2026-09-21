package com.vandorlabs.animation;

/** Minecraft-independent state machine for a reversible 0..1 door animation. */
public final class DoorAnimation {
    private final double duration;
    private boolean initialized;
    private boolean targetOpen;
    private double startTime;
    private double startPose;
    private double lastPose;

    public DoorAnimation(double duration) {
        if (!(duration > 0) || !Double.isFinite(duration)) {
            throw new IllegalArgumentException("duration must be finite and positive");
        }
        this.duration = duration;
    }

    public double sample(boolean open, double now) {
        if (!initialized) {
            initialized = true;
            targetOpen = open;
            startTime = now - duration;
            startPose = open ? 1 : 0;
            lastPose = startPose;
        } else if (open != targetOpen) {
            startPose = lastPose;
            targetOpen = open;
            startTime = now;
        }
        double elapsed = Math.max(0, Math.min(1, (now - startTime) / duration));
        double eased = elapsed * elapsed * (3 - 2 * elapsed);
        lastPose = startPose + ((targetOpen ? 1 : 0) - startPose) * eased;
        return lastPose;
    }
}
