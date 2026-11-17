package com.vandorlabs.ui;
import com.vandorlabs.ramp.RampAxis;
import java.util.*;
/** Exhaustive released control IDs/layouts and previous/next choices, independent of either GUI toolkit. */
public final class RampDialogTest {
 private static int checks;
 private static void need(boolean value,String why){checks++;if(!value)throw new AssertionError(why);}
 public static void main(String[] args){
  RampDialog.State west=new RampDialog.State();
  west.scanFacing=4;
  west.direction=4;
  west=RampDialog.action(west,24,1);
  need(!west.vertical&&west.direction==2,"west mount selects Z");
  west=RampDialog.action(west,6,1);
  west=RampDialog.action(west,17,1);
  need(west.scanFacing==4&&west.direction==3&&west.travel==2,"west mount, south heading, left/right travel");
  west=RampDialog.action(west,24,1);
  need(west.vertical,"side mount still selects Y");
  west=RampDialog.action(west,24,-1);
  need(!west.vertical&&west.direction<4,"previous axis from Y is Z");
  for(int height:new int[]{240,360})for(int scan=0;scan<6;scan++)for(int direction=2;direction<6;direction++){
   RampDialog.Panel panel=new RampDialog.Panel(height);RampDialog.State state=new RampDialog.State();state.scanFacing=scan;state.direction=direction;state.start=5;state.end=-7;
   for(int mode=0;mode<7;mode++){
    need(state.mode()==mode,"seven released modes");
    for(int page=0;page<2;page++){Set<Integer> ids=new HashSet<Integer>();for(RampDialog.Control c:RampDialog.controls(panel,state,page)){need(ids.add(c.id),"unique IDs");need(c.x>=0&&c.y>=0&&c.x+c.width<=320&&c.y+c.height<=panel.statusY(),"logical bounds "+c.id);if(c.id==30||c.id==31)need(c.tab&&c.enabled==(c.id!=30+page),"notebook selection");}need(ids.containsAll(Arrays.asList(7,30,31)),"preserved common operation IDs");}
    RampDialog.Control slider=RampDialog.control(panel,state,0,20);need(slider.y==(height==240?54:56)&&slider.width==292&&slider.minimum==-32&&slider.maximum==32,"authoritative half-step slider");need(slider.enabled==(state.escalator==0),"start disabled only for escalators");need(RampDialog.control(panel,state,0,14).enabled==!state.elevator,"lift disables tread controls");need(RampDialog.control(panel,state,0,19).enabled==!state.elevator,"lift disables interpolation");need(RampDialog.control(panel,state,1,22).enabled==(state.escalator==0),"escalator disables analog");need(RampDialog.control(panel,state,1,3).label.equals("Activation: Redstone ON"),"Source activation label");
    RampDialog.State speed=RampDialog.action(RampDialog.action(state,4,1),4,-1);need(speed.speed==state.speed,"speed previous undoes next");RampDialog.State flip=RampDialog.action(RampDialog.action(state,6,1),6,-1);need(flip.direction==state.direction,"direction opposite reversible");RampDialog.State interpolation=RampDialog.action(RampDialog.action(state,19,1),19,-1);need(interpolation.interpolation==state.interpolation,"interpolation reverse or disabled");need(RampDialog.action(state,22,1).analog==(state.escalator==0?!state.analog:state.analog),"analog enabled rule");
    RampDialog.State axis=RampDialog.action(state,24,1);RampAxis mount=scan<2?RampAxis.Y:scan<4?RampAxis.Z:RampAxis.X;RampAxis chosen=axis.vertical?RampAxis.Y:axis.direction<4?RampAxis.Z:RampAxis.X;need(RampAxis.allowed(mount,chosen),"mount-specific axis choices");need(RampDialog.action(state,1,-1).mode()==(mode+6)%7,"previous mode wraps");state=RampDialog.action(state,1,1);
   }
   need(state.start==0&&state.travel==0,"escalator and moat reset offsets/travel");
  }
  for(int id:new int[]{1,3,4,6,17,18,19,22,24})need(RampDialog.reverse(id),"all option IDs support right click");for(int id:new int[]{7,14,15,20,21,30,31})need(!RampDialog.reverse(id),"non-option operation IDs keep semantics");
  RampDialog.State tread=new RampDialog.State();tread.tread=1;need(RampDialog.action(tread,14,1).tread==1,"tread lower bound");for(int n:new int[]{2,4,8,16}){tread=RampDialog.action(tread,15,1);need(tread.tread==n,"power-of-two tread next");}need(RampDialog.action(tread,15,1).tread==16,"tread upper bound");need(RampDialog.STYLE.body==0xff19232c&&RampDialog.STYLE.header==0xff304858&&RampDialog.STYLE.logoHeaderHeight==24,"exact Source family palette/header/logo");
  System.out.println("RampDialogTest PASS checks="+checks+" full/compact/sixScanFaces/fourDirections/sevenModes/IDs/reverse/disabledRules");
 }
}
