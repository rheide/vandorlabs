package com.vandorlabs.neoforge.screen;
import com.vandorlabs.screen.*;
import com.vandorlabs.persistence.FrameSettings;
import java.util.List;
/** World-free signature adapter. Membership discovery and loaded identity checks belong to the authority. */
public final class NativeScreenJoining {
 private NativeScreenJoining(){}
 public static ScreenJoinSignature signature(ScreenFamily family,NativeScreenSettings settings,FrameSettings frames,List<NativeScreenControls> controls,NativeScreenEntity.Catalog catalog){var scalar=settings.scalar();var activation=settings.activation().data();int slot=family.activationSlot(),raw=slot==0?settings.primarySurface():settings.secondarySurface();boolean input=family==ScreenFamily.HALF_INPUT||family==ScreenFamily.HALF_CONSOLE||family==ScreenFamily.HALF_DIAGONAL;int nativeTexture=input?catalog.inputArtwork(slot==1?scalar.secondaryInputPanel:scalar.inputPanel):catalog.screenArtwork(scalar.selectedScreen);int[] values=new int[12];for(int state=0;state<4;state++){int texture=activation.texture(state),playback=activation.playback(state),speed=activation.speed(state);values[state*3]=texture>=0?texture:raw>=0?raw:nativeTexture;values[state*3+1]=playback>=0?playback:state==0?0:scalar.displayMode==0?2:scalar.displayMode;values[state*3+2]=speed>=0?speed:scalar.animationSpeedIndex;}int mode=activation.mode<0?(scalar.redstoneEnabled?1:0):activation.mode,shared=controls.get(slot).enabled()?-2:raw;return new ScreenJoinSignature(mode,settings.inverted(),settings.ceilingMounted(),scalar.smallInput,scalar.wallPosition,settings.ceilingPosition(),frames.displayFit,frames.split,frames.splitTop,frames.splitBottom,shared==-1?nativeTexture:shared,values);}
}
