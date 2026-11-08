package com.vandorlabs.gear.core;
import java.util.UUID;
import static com.vandorlabs.gear.core.GearMotionScope.Decision.*;
public final class GearMotionScopeTest {
 private static int checks;private static void need(boolean b,String why){checks++;if(!b)throw new AssertionError(why);}
 public static void main(String[] args){UUID a=new UUID(1,2),b=new UUID(3,4);
  need(GearMotionScope.classify(null,0,null,Long.MIN_VALUE,Long.MIN_VALUE,true,false)==APPLY,"legacy unchanged");
  need(GearMotionScope.classify(null,100,a,21,15,true,false)==DROP,"absent legacy cannot enter known scope");
  need(GearMotionScope.classify(a,20,a,21,15,true,false)==APPLY,"STATE A21 late Source A15 Motion A20 corrects source");
  need(GearMotionScope.classify(a,14,a,21,15,true,false)==DROP,"matching packet predates installed source");
  need(GearMotionScope.classify(a,15,a,21,15,true,false)==APPLY,"equal source clock remains usable");
  need(GearMotionScope.classify(b,5,b,21,15,false,false)==WAIT,"new scope waits before testing prior source floor");
  need(GearMotionScope.classify(b,5,b,21,15,true,true)==WAIT,"pending source waits before prior floor");
  need(GearMotionScope.classify(b,5,b,21,3,true,false)==APPLY,"matching installed source supplies actual lower floor");
  need(GearMotionScope.classify(a,20,b,21,15,true,false)==DROP,"old ownership drops despite same motion revision");
  need(GearMotionScope.classify(b,30,a,21,15,true,false)==WAIT,"future scope awaits external receipt");
  need(GearMotionScope.classify(b,21,a,21,15,true,true)==WAIT,"pending future source protects same-tick deltas");
  need(GearMotionScope.classify(a,20,b,21,15,false,true)==WAIT,"old pending message may wait but cannot apply");
  need(GearMotionScope.classify(a,20,b,21,15,true,false)==DROP,"drain rejects old pending after installed scope changes");
  for(boolean ready:new boolean[]{false,true})for(boolean pending:new boolean[]{false,true})for(long clock:new long[]{Long.MIN_VALUE,0,20,21,22}){
   GearMotionScope.Decision d=GearMotionScope.classify(a,20,b,clock,15,ready,pending);need(d!=APPLY,"mismatch never establishes authority");d=GearMotionScope.classify(null,20,b,clock,15,ready,pending);need(d==DROP,"absent-known never applies or queues");
  }
  System.out.println("Gear motion scope policy PASS checks="+checks+" separate STATE/source clocks/pending source/rechecks/no packet scope promotion");
 }
}
