package com.vandorlabs.ui;
import java.util.*;
/** One logical mechanism/redstone dialog; platform adapters supply widgets and authoritative values. */
public final class GearDialog {
 public static final int MECHANISM=20,REDSTONE=21,CHANNELS=22;
 public enum Kind {BUTTON,SLIDER,FIELD}
 /** Rebuild-local view only; no gameplay authority or world references. */
 public static final class State {
  public boolean rotating,fixed,landing,rest,atEndpoint,hingeEditable,canJoin,canCarry,canPush,canFoot;
  public boolean join,carry,push,rotate,target,anglesConfigured;
  public int trigger,joinRange,loadHeight;public double start,end;
  public String fold="North",foot="North",hinge="Single",kink="Inward",track;
 }
 public static final class Panel {
  public final int width,height,leftColumn,rightColumn,columnWidth,doneY;
  public Panel(int screenWidth,int screenHeight){width=Math.min(380,screenWidth-12);height=Math.min(244,screenHeight-12);if(width<232||height<188)throw new IllegalArgumentException("Mechanism dialog needs a larger view");leftColumn=12;columnWidth=(width-36)/2;rightColumn=24+columnWidth;doneY=height-54;}
 }
 public static final class Control extends DialogControl {
  public final Kind kind;public final int minimum,maximum;public final double value;
  private Control(int id,int x,int y,int width,int height,String label,boolean tab,boolean enabled,Kind kind,int minimum,int maximum,double value){super(id,x,y,width,height,label,tab,enabled);this.kind=kind;this.minimum=minimum;this.maximum=maximum;this.value=value;}
 }
 private static Control button(int id,String label,boolean enabled){return new Control(id,0,0,0,20,label,false,enabled,Kind.BUTTON,0,0,0);}
 private static Control slider(int id,String label,int low,int high,double value,boolean enabled){return new Control(id,0,0,0,20,label,false,enabled,Kind.SLIDER,low,high,value);}
 private static Control placed(Control c,int x,int y,int width,int height){return new Control(c.id,x,y,width,height,c.label,c.tab,c.enabled,c.kind,c.minimum,c.maximum,c.value);}
 public static List<Control> controls(Panel p,State s,int page){
  if(page<0||page>1)throw new IllegalArgumentException("Unknown gear page");List<Control> out=new ArrayList<Control>();out.add(new Control(MECHANISM,12,28,p.columnWidth,20,"Mechanism",true,page!=0,Kind.BUTTON,0,0,0));out.add(new Control(REDSTONE,p.rightColumn,28,p.columnWidth,20,"Redstone",true,page!=1,Kind.BUTTON,0,0,0));
  if(page==1){out.add(placed(button(0,"Activation: "+RedstoneActivation.label(s.trigger),!s.fixed),12,60,p.width-24,20));out.add(new Control(CHANNELS,12,100,p.width-24,18,"Receive channels",false,!s.fixed,Kind.FIELD,0,0,0));}
  else{
   List<Control[]> rows=new ArrayList<Control[]>();String target=s.fixed?"Always deployed":s.landing&&!s.anglesConfigured?(s.target?"Target: Deployed":"Target: Stowed"):(s.target?"Target: Start":"Target: End");rows.add(new Control[]{button(2,target,!s.fixed&&s.trigger==0)});
   if(s.rotating){rows.add(new Control[]{button(1,"Fold: "+s.fold,s.rest&&s.target),button(7,s.rotate?"Payload: rotates":"Payload: stays level",s.rest)});rows.add(new Control[]{button(18,"Hinge: "+s.hinge,s.hingeEditable),button(19,"Kink: "+s.kink,s.hingeEditable)});rows.add(new Control[]{slider(16,"Start",-180,180,s.start,s.rest),slider(13,"End",-180,180,s.end,s.rest)});}
   else if(s.canPush||s.canCarry)rows.add(new Control[]{s.canPush?button(14,"Push blocks: "+(s.push?"On":"Off"),s.atEndpoint):null,s.canCarry?button(11,"Carry load: "+(s.carry?"On":"Off"),s.rest&&s.join):null});
   if(s.canJoin)rows.add(new Control[]{button(9,"Join arms: "+(s.join?"On":"Off"),s.rest),slider(10,"Join range",1,32,s.joinRange,s.rest&&s.join)});
   if(s.canCarry&&s.join&&s.carry)rows.add(new Control[]{slider(12,"Load height",1,32,s.loadHeight,s.rest)});
   if(s.canFoot||s.track!=null)rows.add(new Control[]{s.canFoot?button(17,"Foot: "+s.foot,s.rest):null,s.track!=null?button(15,"Track: "+s.track,s.rest):null});
   int step=rows.size()<2?26:Math.min(26,(p.doneY-6-54)/rows.size()),h=Math.min(20,step-2);
   for(int i=0;i<rows.size();i++){Control[] row=rows.get(i);int y=54+i*step;if(row.length==1)out.add(placed(row[0],12,y,p.width-24,h));else{if(row[0]!=null)out.add(placed(row[0],12,y,p.columnWidth,h));if(row[1]!=null)out.add(placed(row[1],p.rightColumn,y,p.columnWidth,h));}}
  }
  out.add(placed(button(4,"Done",true),(p.width-Math.min(200,p.width-24))/2,p.doneY,Math.min(200,p.width-24),20));return Collections.unmodifiableList(out);
 }
 public static String sliderLabel(int id,double value){String label=id==10?"Join range":id==12?"Load height":id==16?"Start":"End";return label+": "+(int)Math.round(value)+(id==10||id==12?" blocks":" deg");}
 private GearDialog(){}
}
