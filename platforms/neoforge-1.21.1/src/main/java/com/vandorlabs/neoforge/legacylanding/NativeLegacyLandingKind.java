package com.vandorlabs.neoforge.legacylanding;
/** Retired registry names remain distinct from modular feet and standalone side wheels. */
public enum NativeLegacyLandingKind {
 WHEEL("landing_gear",false,false,false),SKI("ski_landing_gear",true,true,false),STRUT("landing_strut",true,false,false),ANGLED("angled_landing_strut",true,false,true);
 public final String id;public final boolean nonWheeled,ski,angled;
 NativeLegacyLandingKind(String id,boolean nonWheeled,boolean ski,boolean angled){this.id=id;this.nonWheeled=nonWheeled;this.ski=ski;this.angled=angled;}
}
