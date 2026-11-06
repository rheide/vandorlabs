package com.vandorlabs.render;

import com.vandorlabs.persistence.SpaceDoorData;

/** Released ordinary/Large leaf pose; outputs shiftX, shiftY, pivotX, pivotZ and angle. */
public final class DoorRenderPose {
 private DoorRenderPose(){}
 public static void write(boolean sliding,boolean framed,boolean paired,boolean right,int direction,double progress,double size,double[] out){
  if(out.length<5)throw new IllegalArgumentException("Door pose output");for(int i=0;i<5;i++)out[i]=0;
  if(sliding&&direction!=0){out[1]=SpaceDoorData.verticalTravel(framed,direction)*progress*size;return;}
  double p=Math.max(0,Math.min(1,progress));
  if(sliding)out[0]=(right?1:-1)*(paired&&framed?16F:15F)*p/16*size;
  else{out[2]=(right?15F:1F)/16*size;out[3]=((double)SpaceDoorBounds.PIN_Z)/16;out[4]=(right?-90F:90F)*p;}
 }
}
