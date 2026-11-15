package com.vandorlabs.neoforge.gear.client;
/** Narrow endpoint reparameterization oracle; ordinary repeat/reversal smoothing stays unchanged. */
public final class NativeLandingAngleVisualChecks {
 private static int checks;private static void need(boolean ok,String why){checks++;if(!ok)throw new AssertionError(why);}
 public static void run(){for(double interval:new double[]{1,3,12}){
  var old=new NativeGearVisualClock.Sample();NativeGearVisualClock.advance(old,1,0);NativeGearVisualClock.advance(old,.01,interval);double oldReset=NativeGearVisualClock.advance(old,1,interval+1);need(oldReset<.1,"old scalar path demonstrates wrong post-normalization angle");
  var sample=new NativeGearVisualClock.Sample();need(NativeGearVisualClock.advance(sample,1,0,false)==1,"initial rest");NativeGearVisualClock.advance(sample,1,1,true);NativeGearVisualClock.advance(sample,.5,1+interval,true);NativeGearVisualClock.advance(sample,0,1+interval*2,true);
  need(NativeGearVisualClock.advance(sample,1,2+interval*2,false)==1,"completed adjustment adopts equivalent normalized endpoint");need(NativeGearVisualClock.advance(sample,1,2+interval*2,false)==1,"same-frame repeat does not restart");need(NativeGearVisualClock.advance(sample,1,3+interval*2,false)==1,"delayed repeat remains settled");need(NativeGearVisualClock.advance(sample,.75,4+interval*2,false)==1,"ordinary reversal retains existing interpolation");need(NativeGearVisualClock.advance(sample,.75,30+interval*2,false)==.75,"ordinary reversal settles");
 }System.out.println("[vandorlabs][landing-angle-player] visual-clock PASS checks="+checks+" completion/repeat/reversal/lag");}
}
