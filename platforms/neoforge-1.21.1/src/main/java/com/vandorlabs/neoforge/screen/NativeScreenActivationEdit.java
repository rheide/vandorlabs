package com.vandorlabs.neoforge.screen;
import com.vandorlabs.persistence.*;
import com.vandorlabs.screen.*;
import com.vandorlabs.neoforge.ModernFinishCatalog;
/** Strict command; source transition inheritance is evaluated against each member's pre-edit settings. */
public record NativeScreenActivationEdit(int mode,int state,int texture,int playback,int speed,boolean inverted) {
 public NativeScreenActivationEdit{if(!DisplayActivationData.valid(mode,state,texture,playback,speed,ModernFinishCatalog::validChoice))throw new IllegalArgumentException("Screen activation edit");}
 public NativeScreenSettings apply(ScreenFamily family,NativeScreenSettings old,ScreenJoinSignature signature,NativeScreenEntity.Catalog catalog){
  var previous=old.activation().data();var scalar=old.scalar();int sourceState=signature.mode==1?3:state;int shared=family.activationSlot()==1?old.secondarySurface():old.primarySurface();
  boolean input=family==ScreenFamily.HALF_INPUT||family==ScreenFamily.HALF_CONSOLE||family==ScreenFamily.HALF_DIAGONAL;int nativeTexture=input?catalog.inputArtwork(family.activationSlot()==1?scalar.secondaryInputPanel:scalar.inputPanel):catalog.screenArtwork(scalar.selectedScreen);
  int inheritedTexture=signature.mode>0?signature.texture(sourceState):shared>=0?shared:shared==-1?nativeTexture:-1;
  if(signature.mode>0&&previous.texture(sourceState)<0&&shared < -1)inheritedTexture=-1;
  int inheritedPlayback=signature.mode>0?signature.playback(sourceState):scalar.displayMode,inheritedSpeed=signature.mode>0?signature.speed(sourceState):scalar.animationSpeedIndex;
  var next=old.activation().configure(mode,state,texture,playback,speed);if((previous.mode<0||signature.mode!=mode)&&mode>0)next=next.inheritUnset(inheritedTexture,inheritedPlayback,inheritedSpeed);
  if(next.equals(old.activation())&&scalar.redstoneEnabled==(mode>0)&&old.inverted()==inverted)return old;
  var data=new ScreenData(scalar.selectedScreen,mode>0,scalar.displayMode,scalar.framed,scalar.animationSpeedIndex,scalar.inputPanel,scalar.secondaryInputPanel,scalar.wallPosition,scalar.smallInput,scalar.redstoneChannel,scalar.channelSignal,scalar.housingTexture);
  return new NativeScreenSettings(data,old.housing(),next,old.channels(),old.channelLevel(),inverted,old.primarySurface(),old.secondarySurface(),old.ceilingMounted(),old.ceilingPosition(),old.surfaceLayout(),old.surfaceTileSides());
 }
}
