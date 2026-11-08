package com.vandorlabs.vehicle.core;
/** Monotonic retry policy for a rejected ownership delta; no source or world references. */
public final class OwnershipSourceRetry {
    public static final long INTERVAL_NANOS=2000000000L;
    private OwnershipSourceRetry(){}
    public static boolean due(boolean pending,long deadline,long now,boolean decoderActive){return pending&&!decoderActive&&now-deadline>=0;}
    public static long after(long now){return now+INTERVAL_NANOS;}
}
