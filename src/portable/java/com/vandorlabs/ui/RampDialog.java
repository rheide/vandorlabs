package com.vandorlabs.ui;
import com.vandorlabs.ramp.ControllerPlatform;
import com.vandorlabs.ramp.RampAxis;
import java.util.*;
/** Released Forge Ramp dialog: logical pixels, option text, enabled rules and reversible choices. */
public final class RampDialog {
 public static final int WIDTH=320,HEIGHT=248;public static final DialogStyle.Shell STYLE=DialogStyle.RAMP;
 public static final String TREAD_LABEL="Tread px",RECEIVE_LABEL="Receive channels";
 private static final int[] REVERSE_IDS={1,3,4,6,17,18,19,22,24};
 public static int[] reverseOperations(){return REVERSE_IDS.clone();}
 public enum Kind{BUTTON,SLIDER,FIELD}
 public static final class State{
  public int start,end,tread=8,speed=1,interpolation,escalator,travel,direction=2,scanFacing=2;
  public boolean power=true,elevator,extend,match=true,moat,vertical,analog;
  public int mode(){return moat?6:escalator!=0?escalator+3:elevator?(extend?3:2):extend?1:0;}
  public State copy(){State s=new State();s.start=start;s.end=end;s.tread=tread;s.speed=speed;s.interpolation=interpolation;s.escalator=escalator;s.travel=travel;s.direction=direction;s.scanFacing=scanFacing;s.power=power;s.elevator=elevator;s.extend=extend;s.match=match;s.moat=moat;s.vertical=vertical;s.analog=analog;return s;}
 }
 public static final class Panel{public final int height;public final boolean compact;public Panel(int screenHeight){height=Math.min(HEIGHT,screenHeight-8);compact=height<HEIGHT;}public int statusY(){return height-24;}public int treadLabelY(){return compact?148:158;}}
 public static final class Control extends DialogControl{
  public final Kind kind;public final int minimum,maximum,value;
  private Control(int id,int x,int y,int w,int h,String text,boolean tab,boolean enabled,Kind kind,int min,int max,int value){super(id,x,y,w,h,text,tab,enabled);this.kind=kind;minimum=min;maximum=max;this.value=value;}
 }
 private static Control c(int id,int x,int y,int w,int h,String text,boolean enabled){return new Control(id,x,y,w,h,text,false,enabled,Kind.BUTTON,0,0,0);}
 public static String offsetLabel(String title,int half){return title+" offset: "+half/2D+" blocks";}
 private static String axis(int facing){return facing<2?"Y":facing<4?"Z":"X";}
 private static String direction(int facing){return new String[]{"DOWN","UP","NORTH","SOUTH","WEST","EAST"}[facing];}
 public static List<Control> controls(Panel p,State s,int page){
  if(page<0||page>1)throw new IllegalArgumentException("Ramp page");boolean compact=p.compact;int h=compact?18:20;List<Control> out=new ArrayList<Control>();
  out.add(new Control(30,14,30,140,20,"Ramp",true,page!=0,Kind.BUTTON,0,0,0));out.add(new Control(31,164,30,142,20,"Redstone",true,page!=1,Kind.BUTTON,0,0,0));
  if(page==0){
   out.add(new Control(20,14,compact?54:56,292,h,offsetLabel("Start / off",s.start),false,s.escalator==0,Kind.SLIDER,-ControllerPlatform.MAX_OFFSET_HALF_STEPS,ControllerPlatform.MAX_OFFSET_HALF_STEPS,s.start));
   out.add(new Control(21,14,compact?76:80,292,h,offsetLabel(s.escalator==0?"End / on":"Ramp height",s.end),false,true,Kind.SLIDER,-ControllerPlatform.MAX_OFFSET_HALF_STEPS,ControllerPlatform.MAX_OFFSET_HALF_STEPS,s.end));
   out.add(c(1,14,compact?98:104,142,h,"Mode: "+new String[]{"ramp","filled ramp","lift","extend","escalator up","escalator down","Moat"}[s.mode()],true));
   out.add(c(24,164,compact?98:104,64,h,"Axis: "+(s.vertical?"Y":axis(s.direction)),true));out.add(c(6,232,compact?98:104,74,h,"Dir: "+direction(s.direction),!s.vertical));
   out.add(c(4,14,compact?120:128,142,h,"Speed: "+new String[]{"fast","medium","slow"}[s.speed],true));out.add(c(17,164,compact?120:128,142,h,"Travel: "+(s.travel==0?(s.vertical?"out / in":"up / down"):"left / right"),s.escalator==0&&!s.moat));
   out.add(new Control(16,216,compact?142:153,36,18,"Tread pixels",false,!s.elevator,Kind.FIELD,1,16,s.tread));out.add(c(14,260,compact?142:152,20,h,"-",!s.elevator));out.add(c(15,282,compact?142:152,20,h,"+",!s.elevator));
   out.add(c(19,14,compact?164:176,292,h,"Interpolation: "+new String[]{"Linear","Curve In","Curve Out"}[s.interpolation],!s.elevator));out.add(c(18,14,compact?186:200,142,h,"Match textures: "+(s.match?"On":"Off"),true));
  }else{
   out.add(new Control(8,14,68,292,18,"Receive channels",false,true,Kind.FIELD,0,0,0));out.add(c(3,14,96,292,20,"Activation: "+RedstoneActivation.label(s.power?1:2),true));out.add(c(22,14,120,292,20,"Deployment: "+(s.analog?"Analog (0-15)":"Digital (on/off)"),s.escalator==0));
  }
  out.add(c(7,164,compact?186:200,142,h,"Done",true));return Collections.unmodifiableList(out);
 }
 public static Control control(Panel p,State state,int page,int id){for(Control c:controls(p,state,page))if(c.id==id)return c;throw new IllegalArgumentException("Ramp control "+id);}
 public static boolean reverse(int id){for(int option:REVERSE_IDS)if(option==id)return true;return false;}
 private static int cycle(int value,int count,int step){return Math.floorMod(value+(step<0?-1:1),count);}
 public static State action(State before,int id,int step){State s=before.copy();switch(id){
  case 1:int mode=cycle(s.mode(),7,step);s.moat=mode==6;s.escalator=mode>=4&&mode<6?mode-3:0;s.elevator=mode==2||mode==3;s.extend=mode<4&&(mode&1)!=0;if(s.escalator!=0){s.travel=0;s.start=0;}if(s.moat)s.travel=0;break;
  case 3:s.power=!s.power;break;case 4:s.speed=cycle(s.speed,3,step);break;case 6:if(!s.vertical)s.direction^=1;break;
  case 17:if(s.escalator==0&&!s.moat)s.travel=s.travel==0?2:0;break;case 18:s.match=!s.match;break;case 19:if(!s.elevator)s.interpolation=cycle(s.interpolation,3,step);break;case 22:if(s.escalator==0)s.analog=!s.analog;break;
  case 24:RampAxis[] choices=RampAxis.choices(RampAxis.valueOf(axis(s.scanFacing)));RampAxis selected=s.vertical?RampAxis.Y:RampAxis.valueOf(axis(s.direction));RampAxis next=choices[0]==selected?choices[1]:choices[0];s.vertical=next==RampAxis.Y;s.direction=s.vertical?s.scanFacing:next==RampAxis.X?((s.direction&1)==1?5:4):((s.direction&1)==1?3:2);break;
  case 14:case 15:if(!s.elevator)s.tread=ControllerPlatform.stepTreadPixels(s.tread,id==15);break;default:break;
 }return s;}
 private RampDialog(){}
}
