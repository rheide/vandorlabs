package com.vandorlabs.persistence;
import java.util.Arrays;
import java.util.function.IntPredicate;
import java.util.function.IntUnaryOperator;
/** Immutable four-state artwork selection; -1 retains the legacy display gate. */
public final class DisplayActivationData {
 public static final DisplayActivationData LEGACY=new DisplayActivationData(-1,new int[]{-1,-1,-1,-1},new int[]{-1,-1,-1,-1},new int[]{-1,-1,-1,-1});
 public final int mode;private final int[] textures,playback,speeds;
 private DisplayActivationData(int mode,int[] textures,int[] playback,int[] speeds){this.mode=mode;this.textures=textures;this.playback=playback;this.speeds=speeds;}
 public int texture(int state){return textures[state];}public int playback(int state){return playback[state];}public int speed(int state){return speeds[state];}
 public int[] textures(){return textures.clone();}public int[] playback(){return playback.clone();}public int[] speeds(){return speeds.clone();}
 public static boolean valid(int mode,int state,int texture,int playback,int speed,IntPredicate material){return mode>=0&&mode<=2&&state>=0&&state<4&&(texture==-1||material.test(texture))&&playback>=-1&&playback<=2&&speed>=-1&&speed<3;}
 public static DisplayActivationData read(boolean compound,int mode,int[] textures,int[] playback,int[] speeds,IntPredicate valid,IntUnaryOperator canonical){
  if(!compound||textures.length!=4||playback.length!=4||speeds.length!=4)return LEGACY;
  for(int i=0;i<4;i++)if(!valid(mode,i,textures[i],playback[i],speeds[i],valid))return LEGACY;
  int[] t=textures.clone();for(int i=0;i<4;i++)t[i]=t[i]<0?-1:canonical.applyAsInt(t[i]);return new DisplayActivationData(mode,t,playback.clone(),speeds.clone());
 }
 public DisplayActivationData withMode(int next){if(next<0||next>2)throw new IllegalArgumentException("Display mode");return next==mode?this:new DisplayActivationData(next,textures,playback,speeds);}
 public DisplayActivationData configure(int mode,int state,int texture,int playback,int speed,IntPredicate material){
  if(!valid(mode,state,texture,playback,speed,material))throw new IllegalArgumentException("Invalid display activation");
  int[] t=textures.clone(),p=this.playback.clone(),s=speeds.clone();t[state]=texture;p[state]=playback;s[state]=speed;return new DisplayActivationData(mode,t,p,s);
 }
 /** The explicit visible mode preserves the legacy public mutable mode field. */
 public DisplayActivationData inheritUnset(int visibleMode,int texture,int playback,int speed){
  boolean changed=mode!=visibleMode;for(int i=0;i<4&&!changed;i++)changed=(visibleMode==2||i==0||i==3)&&textures[i]<0&&this.playback[i]<0&&speeds[i]<0;if(!changed)return this;
  int[] t=textures.clone(),p=this.playback.clone(),s=speeds.clone();
  for(int i=0;i<4;i++)if((visibleMode==2||i==0||i==3)&&t[i]<0&&p[i]<0&&s[i]<0){t[i]=texture;p[i]=playback;s[i]=speed;changed=true;}
  return changed?new DisplayActivationData(visibleMode,t,p,s):this;
 }
 @Override public boolean equals(Object other){if(this==other)return true;if(!(other instanceof DisplayActivationData))return false;DisplayActivationData d=(DisplayActivationData)other;return mode==d.mode&&Arrays.equals(textures,d.textures)&&Arrays.equals(playback,d.playback)&&Arrays.equals(speeds,d.speeds);}
 @Override public int hashCode(){return ((mode*31+Arrays.hashCode(textures))*31+Arrays.hashCode(playback))*31+Arrays.hashCode(speeds);}
}
