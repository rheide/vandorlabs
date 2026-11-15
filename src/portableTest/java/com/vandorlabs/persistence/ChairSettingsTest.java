package com.vandorlabs.persistence;
/** Independent authored marker and legacy-default contracts, shared by Java 8/21 adapters. */
public final class ChairSettingsTest {
 private static int checks;
 private static void need(boolean value){checks++;if(!value)throw new AssertionError("Chair contract "+checks);}
 public static void main(String[] args){double[] seats={12,11,12,11,10},backs={24,21,23,23.5,18.5};for(int style=0;style<5;style++)for(int height=0;height<3;height++)for(int angle=0;angle<8;angle++){
  ChairSettings settings=new ChairSettings(style,height,angle,true);need(settings.style==style&&settings.height==height&&settings.angle==angle);need(ChairSettings.cushion(style,height)==(seats[style]+(height-1)*2)/16);need(ChairSettings.back(style,height)==(backs[style]+(height-1)*2)/16);SeatPose pose=new SeatPose(ChairSettings.cushion(style,height),90,angle);need(pose.x==.5&&pose.z==.5&&pose.yaw==90+angle*45);
 }for(int height=0;height<3;height++){need(ChairSettings.connectedCushion(true,height)==(9+(height-1)*2+1)/16D);need(ChairSettings.connectedCushion(false,height)==(8+(height-1)*2+1)/16D);}
 for(int height=0;height<4;height++)need(ChairSettings.pilotCushion(height)==7/16D+height/8D);
 need(ChairSettings.height(false,0)==1&&ChairSettings.height(true,-1)==0&&ChairSettings.height(true,3)==2);need(ChairSettings.style(-1)==0&&ChairSettings.style(5)==4&&ChairSettings.angle(-1)==0&&ChairSettings.angle(8)==7);
 for(boolean join:new boolean[]{false,true})for(boolean left:new boolean[]{false,true})for(boolean right:new boolean[]{false,true}){int expected=!join?0:left?(right?2:3):(right?1:0);need(ChairSettings.part(join,left,right)==expected);}
 for(int h=0;h<3;h++)for(int other=0;other<3;other++){need(ChairSettings.connects(true,true,true,true,h,other,true)==(h==other));for(int failed=0;failed<5;failed++)need(!ChairSettings.connects(failed!=0,failed!=1,failed!=2,failed!=3,h,other,failed!=4));}
 System.out.println("Chair settings PASS "+checks+" authored markers/legacy defaults/row parity");
 }
}
