package com.vandorlabs.ramp;
/** Released configuration order; world authorization and durable ownership stay in adapters. */
public final class RampConfigurationPolicy {
 public static final class Request {
  public final int startHalf,endHalf,treadPixels,direction,travel,speed,interpolation,escalator;public final boolean powerOn,elevator,extend,matchTextures,moat,verticalLayout;
  public Request(int startHalf,int endHalf,int treadPixels,boolean powerOn,boolean elevator,int direction,int travel,boolean extend,int speed,boolean matchTextures,int interpolation,int escalator,boolean moat,boolean verticalLayout){this.startHalf=startHalf;this.endHalf=endHalf;this.treadPixels=treadPixels;this.powerOn=powerOn;this.elevator=elevator;this.direction=direction;this.travel=travel;this.extend=extend;this.speed=speed;this.matchTextures=matchTextures;this.interpolation=interpolation;this.escalator=escalator;this.moat=moat;this.verticalLayout=verticalLayout;}
 }
 public static final class Admission {public final Request choices;public final String error;private Admission(Request choices,String error){this.choices=choices;this.error=error;}public boolean accepted(){return choices!=null;}}
 private static Admission refuse(String error){return new Admission(null,error);}
 public static Admission admit(Request r,int scanFace,boolean server,boolean usable,boolean connectedCopyAllowed){
  if(r==null||scanFace<0||scanFace>5)throw new IllegalArgumentException("Ramp request/context");
  if(r.verticalLayout&&scanFace<2)return refuse("Y layout needs a side-mounted controller");
  if(r.moat&&(r.elevator||r.extend||r.escalator!=0||r.travel!=RampGeometry.VERTICAL))return refuse("Moat mode uses vertical offsets without lift, fill or escalator travel");
  if(r.escalator<0||r.escalator>2)return refuse("Choose a valid escalator mode");
  if(r.escalator!=0&&(r.travel!=RampGeometry.VERTICAL||r.elevator||r.extend))return refuse("Escalators need vertical travel without lift or fill");
  if(r.interpolation<0||r.interpolation>2)return refuse("Choose a valid interpolation style");
  if(!server||!(usable||connectedCopyAllowed))return refuse(null);
  if(r.direction<2||r.direction>5)return refuse("Choose a horizontal ramp direction");
  if(r.travel<RampGeometry.VERTICAL||r.travel>RampGeometry.RIGHT)return refuse("Choose a travel direction");
  if(r.speed<0||r.speed>2)return refuse("Choose a valid speed");
  if(Math.abs((long)r.startHalf)>ControllerPlatform.MAX_OFFSET_HALF_STEPS||Math.abs((long)r.endHalf)>ControllerPlatform.MAX_OFFSET_HALF_STEPS)return refuse("Offsets must be -16 to 16 blocks");
  if(!ControllerPlatform.validTreadPixels(r.treadPixels))return refuse("Tread size must be 1, 2, 4, 8 or 16 pixels");
  return new Admission(new Request(r.escalator!=0?0:r.startHalf,r.endHalf,r.treadPixels,r.powerOn,r.elevator,r.verticalLayout?scanFace:r.direction,r.travel,r.extend,r.speed,r.matchTextures,r.interpolation,r.escalator,r.moat,r.verticalLayout),null);
 }
 public enum Stage { READY,PENDING,REFUSED }
 /** Implementations must validate installed authority and durably reset old ownership before READY. */
 public interface Transaction {Stage recoverPrevious();void commitChoices(Request choices);Stage activate();}
 public static final class Outcome {public final boolean choicesCommitted;public final Stage activation;private Outcome(boolean committed,Stage stage){choicesCommitted=committed;activation=stage;}}
 public static Outcome execute(Admission admission,Transaction transaction){if(admission==null||transaction==null)throw new NullPointerException();if(!admission.accepted())return new Outcome(false,Stage.REFUSED);Stage reset=java.util.Objects.requireNonNull(transaction.recoverPrevious());if(reset!=Stage.READY)return new Outcome(false,reset);transaction.commitChoices(admission.choices);return new Outcome(true,java.util.Objects.requireNonNull(transaction.activate()));}
 private RampConfigurationPolicy(){}
}
