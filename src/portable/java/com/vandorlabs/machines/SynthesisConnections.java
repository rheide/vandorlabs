package com.vandorlabs.machines;
/** Physical endpoint presence, independent of transfer direction, stored contents or simulation results. */
public final class SynthesisConnections {
 public static final int POWER_REAR=1,POWER_BOTTOM=2,ITEM_REAR=4,ITEM_SIDE=8,ITEM_BOTTOM=16;
 public static boolean power(int mask){return (mask&(POWER_REAR|POWER_BOTTOM))!=0;}
 private SynthesisConnections(){}
}
