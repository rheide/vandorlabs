package com.vandorlabs.screen;
import java.util.Arrays;
/** Separate authored-edit and local-power signatures; received channels are deliberately absent. */
public final class ScreenJoinSignature {
 public final int mode,wallPosition,ceilingPosition,top,bottom,sharedArtwork;public final boolean inverted,ceiling,small,fit,split;private final int[] activation;
 public ScreenJoinSignature(int mode,boolean inverted,boolean ceiling,boolean small,int wallPosition,int ceilingPosition,boolean fit,boolean split,int top,int bottom,int sharedArtwork,int[] activation){if(mode<0||mode>2||wallPosition < -1||wallPosition>2||ceilingPosition < -1||ceilingPosition>2||activation.length!=12)throw new IllegalArgumentException("Screen join signature");this.mode=mode;this.inverted=inverted;this.ceiling=ceiling;this.small=small;this.wallPosition=wallPosition;this.ceilingPosition=ceilingPosition;this.fit=fit;this.split=split;this.top=top;this.bottom=bottom;this.sharedArtwork=sharedArtwork;this.activation=activation.clone();}
 private boolean shape(ScreenJoinSignature other){return ceiling==other.ceiling&&small==other.small&&wallPosition==other.wallPosition&&ceilingPosition==other.ceilingPosition;}
 private boolean frame(ScreenJoinSignature other){return fit==other.fit&&split==other.split&&(!split||top==other.top&&bottom==other.bottom);}
 public boolean editMatches(ScreenJoinSignature other){return other!=null&&shape(other)&&frame(other)&&sharedArtwork==other.sharedArtwork;}
 public boolean powerMatches(ScreenJoinSignature other){return other!=null&&shape(other)&&frame(other)&&mode==other.mode&&inverted==other.inverted&&Arrays.equals(activation,other.activation);}
 public boolean editEligible(ScreenFamily family){return ScreenJoinGeometry.supports(family,small)&&(sharedArtwork>=0||sharedArtwork==-4);}
 public boolean powerEligible(ScreenFamily family){return mode!=0&&ScreenJoinGeometry.supports(family,small);}
 public int texture(int state){return activation[state*3];}public int playback(int state){return activation[state*3+1];}public int speed(int state){return activation[state*3+2];}
}
