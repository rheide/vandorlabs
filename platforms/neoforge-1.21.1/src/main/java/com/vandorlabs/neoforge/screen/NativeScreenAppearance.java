package com.vandorlabs.neoforge.screen;
import com.vandorlabs.screen.*;
import com.vandorlabs.persistence.FrameSettings;
import java.util.List;
/** Native catalog adapter for the shared pure appearance policy; no sprites, world reads or NBT. */
public final class NativeScreenAppearance {
 private NativeScreenAppearance(){}
 public static ScreenAppearance of(ScreenFamily family,NativeScreenSettings settings,FrameSettings frames,List<NativeScreenControls> controls,int physicalPower,NativeScreenEntity.Catalog catalog){if(controls.size()!=2)throw new IllegalArgumentException("Screen control projections");boolean input=family==ScreenFamily.HALF_INPUT||family==ScreenFamily.HALF_CONSOLE||family==ScreenFamily.HALF_DIAGONAL;var scalar=settings.scalar();int primary=input?catalog.inputArtwork(scalar.inputPanel):catalog.screenArtwork(scalar.selectedScreen),active=family.activationSlot()==1?catalog.inputArtwork(scalar.secondaryInputPanel):primary;return ScreenAppearance.compute(family,scalar,settings.activation().data(),settings.inverted(),settings.primarySurface(),settings.secondarySurface(),controls.get(0).enabled(),controls.get(1).enabled(),frames.split,Math.max(settings.channelLevel(),physicalPower),scalar.channelSignal||physicalPower>0,primary,active);}
}
