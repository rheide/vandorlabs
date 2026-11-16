package com.vandorlabs.neoforge.ramp;

import com.vandorlabs.ramp.RampAxis;
import net.minecraft.core.Direction;

/** World-free released dialog transitions. Scan orientation and horizontal travel remain distinct. */
public record NativeRampDraft(NativeRampSettings settings,Direction scanFacing,Direction horizontalFacing) {
 public NativeRampDraft {
  java.util.Objects.requireNonNull(settings);java.util.Objects.requireNonNull(scanFacing);java.util.Objects.requireNonNull(horizontalFacing);
  if(!horizontalFacing.getAxis().isHorizontal())throw new IllegalArgumentException("Horizontal Ramp travel fallback");
  if(!settings.hasDirection())settings=copy(settings,settings.startHalf(),settings.powerOn(),settings.elevator(),settings.extend(),settings.speed(),settings.matchTextures(),settings.interpolation(),settings.escalator(),settings.moat(),settings.verticalLayout(),settings.travelAxis(),Direction.from3DDataValue(settings.resolvedDirection(horizontalFacing.get3DDataValue())).get2DDataValue(),settings.analogDeployment());
 }
 public int mode(){return settings.mode();}
 public com.vandorlabs.ui.RampDialog.State dialogState(){var s=settings;var state=new com.vandorlabs.ui.RampDialog.State();state.start=s.startHalf();state.end=s.endHalf();state.tread=s.treadPixels();state.power=s.powerOn();state.elevator=s.elevator();state.extend=s.extend();state.speed=s.speed();state.match=s.matchTextures();state.interpolation=s.interpolation();state.escalator=s.escalator();state.moat=s.moat();state.vertical=s.verticalLayout();state.travel=s.travelAxis();state.direction=Direction.from2DDataValue(s.direction()).get3DDataValue();state.scanFacing=scanFacing.get3DDataValue();state.analog=s.analogDeployment();return state;}
 private static NativeRampSettings copy(NativeRampSettings s,int start,boolean power,boolean lift,boolean extend,int speed,boolean match,int interpolation,int escalator,boolean moat,boolean vertical,int travel,int face,boolean analog){return new NativeRampSettings(start,s.endHalf(),s.treadPixels(),power,lift,extend,speed,match,interpolation,escalator,moat,vertical,travel,true,face,analog,s.channels());}
 public NativeRampDraft action(int id,int direction){var next=com.vandorlabs.ui.RampDialog.action(dialogState(),id,direction);int face=Direction.from3DDataValue(next.direction).get2DDataValue();return new NativeRampDraft(new NativeRampSettings(next.start,next.end,next.tread,next.power,next.elevator,next.extend,next.speed,next.match,next.interpolation,next.escalator,next.moat,next.vertical,next.travel,true,face,next.analog,settings.channels()),scanFacing,horizontalFacing);}
 public NativeRampDraft offsets(int start,int end,int tread,com.vandorlabs.redstone.ChannelList channels){var s=settings;return new NativeRampDraft(new NativeRampSettings(start,end,tread,s.powerOn(),s.elevator(),s.extend(),s.speed(),s.matchTextures(),s.interpolation(),s.escalator(),s.moat(),s.verticalLayout(),s.travelAxis(),s.hasDirection(),s.direction(),s.analogDeployment(),channels),scanFacing,horizontalFacing);}
}
