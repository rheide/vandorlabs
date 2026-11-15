package com.vandorlabs.gear.core;
/** Optional landing-angle authority. Absent old saves retain ignored raw generic fields. */
public final class ArmLandingAngles {
 public static final String SAVED="ArmLandingAnglesVersion",CHOICE="LandingAnglesVersion";
 public final int version;public final double rawStart,rawEnd;
 public ArmLandingAngles(int version,double start,double end){if(version< -1||version>1||!Double.isFinite(start)||!Double.isFinite(end)||Math.abs(start)>180||Math.abs(end)>180)throw new IllegalArgumentException("Unknown landing angle settings");this.version=version;rawStart=start;rawEnd=end;}
 public static int version(boolean present,boolean integer,int value){if(!present)return -1;if(!integer||value<0||value>1)throw new IllegalArgumentException("Unknown landing angle settings version");return value;}
 public boolean configured(){return version==1;}
 public double start(boolean landing){return landing&&!configured()?0:rawStart;}
 public double end(boolean landing){return landing&&!configured()?90:rawEnd;}
 public ArmLandingAngles edit(boolean landing,boolean start,double value){step(value);return new ArmLandingAngles(1,start?value:start(landing),start?end(landing):value);}
 /** Semantic copy is explicit user intent; never export ignored generic angle remnants. */
 public ArmLandingAngles copied(boolean landing){return new ArmLandingAngles(1,start(landing),end(landing));}
 public static void step(double value){if(!Double.isFinite(value)||Math.abs(value)>180||Math.rint(value/15)*15!=value)throw new IllegalArgumentException("Angles must use 15 degree steps within -180 to 180");}
 private ArmLandingAngles(){throw new AssertionError();}
}
