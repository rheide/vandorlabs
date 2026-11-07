package com.vandorlabs.ramp;

/** Released belt eligibility. Call eligible before sampling a lazy world-dependent pose. */
public final class RampBeltPolicy {
 private RampBeltPolicy(){}
 public static boolean eligible(int mode,boolean moving){return mode!=0&&!moving;}
 public static boolean deployed(int mode,boolean moving,double pose){return eligible(mode,moving)&&pose>=1;}
 public static boolean reservations(int mode,double from,double to){return mode!=0&&from>=1&&to>=1;}
}
