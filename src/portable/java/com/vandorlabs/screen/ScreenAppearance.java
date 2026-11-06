package com.vandorlabs.screen;
import com.vandorlabs.animation.ScreenBehavior;
import com.vandorlabs.persistence.DisplayActivationData;
import com.vandorlabs.persistence.ScreenData;
/** Renderer-facing selections computed once per coherent settings/power publication. */
public final class ScreenAppearance {
 public static final ScreenAppearance DARK=new ScreenAppearance(0,0,0,0,0,1,false,false,-1,0,1,-1,0,1,0);
 public final int activationMode,state,sharedArtwork,primaryArtwork,effectiveMode,animationSpeed,primarySurface,primaryMode,primarySpeed,secondarySurface,secondaryMode,secondarySpeed,light;
 public final boolean split,overrides;
 private ScreenAppearance(int activationMode,int state,int sharedArtwork,int primaryArtwork,int effectiveMode,int animationSpeed,boolean split,boolean overrides,int primarySurface,int primaryMode,int primarySpeed,int secondarySurface,int secondaryMode,int secondarySpeed,int light){this.activationMode=activationMode;this.state=state;this.sharedArtwork=sharedArtwork;this.primaryArtwork=primaryArtwork;this.effectiveMode=effectiveMode;this.animationSpeed=animationSpeed;this.split=split;this.overrides=overrides;this.primarySurface=primarySurface;this.primaryMode=primaryMode;this.primarySpeed=primarySpeed;this.secondarySurface=secondarySurface;this.secondaryMode=secondaryMode;this.secondarySpeed=secondarySpeed;this.light=light;}
 public static ScreenAppearance compute(ScreenFamily family,ScreenData scalar,DisplayActivationData activation,boolean inverted,int primaryRaw,int secondaryRaw,boolean primaryRows,boolean secondaryRows,boolean split,int power,boolean powered,int nativePrimary,int nativeActivation){
  if(family==null||scalar==null||activation==null||power<0||power>15||primaryRaw < -4||secondaryRaw < -4)throw new IllegalArgumentException("Screen appearance state");
  int mode=activation.mode<0?(scalar.redstoneEnabled?1:0):activation.mode,level=inverted?15-power:power,state=mode==2?(level==0?0:level<=5?1:level<=10?2:3):level>0?3:0;
  int slot=family.activationSlot(),raw=slot==1?secondaryRaw:primaryRaw;boolean rows=slot==1?secondaryRows:primaryRows;
  int baseMode=ScreenBehavior.clampMode(scalar.displayMode),baseSpeed=ScreenBehavior.clampSpeedIndex(scalar.animationSpeedIndex);
  int chosen=activation.texture(state),texture=chosen>=0?chosen:raw>=0?raw:nativeActivation;
  boolean overrides=activation.mode>0&&chosen>=0;
  int playback=activation.playback(state),activatedMode=playback>=0?playback:state==0?0:baseMode==0?2:baseMode;
  int effective=activation.mode==0?baseMode:activation.mode>0?activatedMode:ScreenBehavior.effectiveMode(baseMode,scalar.redstoneEnabled,scalar.redstoneEnabled&&inverted?!powered:powered);
  int speed=activation.mode>0&&activation.speed(state)>=0?activation.speed(state):baseSpeed;
  int first=surface(0,slot,primaryRaw,primaryRows,activation.mode,overrides,texture),second=surface(1,slot,secondaryRaw,secondaryRows,activation.mode,overrides,texture);
  int firstMode=slot!=0&&activation.mode>0?baseMode:effective,secondMode=slot!=1&&activation.mode>0?baseMode:effective;
  int light=family.keyboard||family==ScreenFamily.HALF_CONSOLE||family==ScreenFamily.HALF_DIAGONAL?0:first==-2||second==-2?11:effective==0?0:11;
  return new ScreenAppearance(mode,state,(rows?-2:raw)==-1?nativeActivation:rows?-2:raw,first==-1?nativePrimary:first,effective,speed,split&&!overrides,overrides,first,firstMode,slot==0?speed:baseSpeed,second,secondMode,slot==1?speed:baseSpeed,light);
 }
 private static int surface(int slot,int active,int raw,boolean rows,int mode,boolean overrides,int texture){if(raw==-4&&!rows)return -4;if(slot==active&&overrides)return texture;if(rows)return -2;if(slot==active&&raw!=-3&&mode>0)return texture;return raw;}
 public int surface(int slot){if(slot<0||slot>1)throw new IllegalArgumentException("Screen slot");return slot==0?primarySurface:secondarySurface;}
 public int playback(int slot){if(slot<0||slot>1)throw new IllegalArgumentException("Screen slot");return slot==0?primaryMode:secondaryMode;}
 public int speed(int slot){if(slot<0||slot>1)throw new IllegalArgumentException("Screen slot");return slot==0?primarySpeed:secondarySpeed;}
}
