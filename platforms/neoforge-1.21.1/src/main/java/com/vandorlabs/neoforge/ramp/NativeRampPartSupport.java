package com.vandorlabs.neoforge.ramp;
/** Released top attachment predicate, prepared when the immutable part recipe changes. */
public final class NativeRampPartSupport {
 public static boolean stationaryTop(NativeRampGeometry.Spec spec,int beltMode){
  if(beltMode!=0||spec.moat()||spec.verticalLayout())return false;
  var off=NativeRampGeometry.snapshot(spec,0).pieces();var on=NativeRampGeometry.snapshot(spec,1).pieces();if(off.size()!=1||on.size()!=1)return false;
  var a=off.get(0).box();var b=on.get(0).box();return Math.abs(a.minX)<1e-8&&Math.abs(a.minZ)<1e-8&&Math.abs(a.maxX-1)<1e-8&&Math.abs(a.maxZ-1)<1e-8&&Math.abs(a.maxY-1)<1e-8&&Math.abs(a.minX-b.minX)+Math.abs(a.minY-b.minY)+Math.abs(a.minZ-b.minZ)+Math.abs(a.maxX-b.maxX)+Math.abs(a.maxY-b.maxY)+Math.abs(a.maxZ-b.maxZ)<1e-8;
 }
 private NativeRampPartSupport(){}
}
