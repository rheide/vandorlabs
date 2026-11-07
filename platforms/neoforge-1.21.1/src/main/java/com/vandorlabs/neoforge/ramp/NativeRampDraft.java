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
 private static int cycle(int value,int count,int direction){return Math.floorMod(value+(direction<0?-1:1),count);}
 private static NativeRampSettings copy(NativeRampSettings s,int start,boolean power,boolean lift,boolean extend,int speed,boolean match,int interpolation,int escalator,boolean moat,boolean vertical,int travel,int face,boolean analog){return new NativeRampSettings(start,s.endHalf(),s.treadPixels(),power,lift,extend,speed,match,interpolation,escalator,moat,vertical,travel,true,face,analog,s.channels());}
 public NativeRampDraft action(int id,int direction){var s=settings;int start=s.startHalf(),speed=s.speed(),interpolation=s.interpolation(),escalator=s.escalator(),travel=s.travelAxis(),face=s.direction();boolean power=s.powerOn(),lift=s.elevator(),extend=s.extend(),match=s.matchTextures(),moat=s.moat(),vertical=s.verticalLayout(),analog=s.analogDeployment();
  switch(id){
   case 1->{int mode=cycle(mode(),7,direction);moat=mode==6;escalator=mode>=4&&mode<6?mode-3:0;lift=mode==2||mode==3;extend=mode<4&&(mode&1)!=0;if(escalator!=0){travel=0;start=0;}if(moat)travel=0;}
   case 3->power=!power;
   case 4->speed=cycle(speed,3,direction);
   case 6->{if(vertical)return this;face=(face+2)%4;}
   case 17->{if(escalator!=0||moat)return this;travel=travel==0?2:0;}
   case 18->match=!match;
   case 19->{if(lift)return this;interpolation=cycle(interpolation,3,direction);}
   case 22->{if(escalator!=0)return this;analog=!analog;}
   case 24->{var choices=RampAxis.choices(RampAxis.valueOf(scanFacing.getAxis().name()));var selected=vertical?RampAxis.Y:RampAxis.valueOf(Direction.from2DDataValue(face).getAxis().name());var next=choices[0]==selected?choices[1]:choices[0];vertical=next==RampAxis.Y;var selectedFace=vertical?scanFacing:Direction.fromAxisAndDirection(Direction.Axis.valueOf(next.name()),Direction.from2DDataValue(face).getAxisDirection());face=selectedFace.get2DDataValue();}
   default->{return this;}
  }
  return new NativeRampDraft(copy(s,start,power,lift,extend,speed,match,interpolation,escalator,moat,vertical,travel,face,analog),scanFacing,horizontalFacing);
 }
 public NativeRampDraft offsets(int start,int end,int tread,com.vandorlabs.redstone.ChannelList channels){var s=settings;return new NativeRampDraft(new NativeRampSettings(start,end,tread,s.powerOn(),s.elevator(),s.extend(),s.speed(),s.matchTextures(),s.interpolation(),s.escalator(),s.moat(),s.verticalLayout(),s.travelAxis(),s.hasDirection(),s.direction(),s.analogDeployment(),channels),scanFacing,horizontalFacing);}
}
