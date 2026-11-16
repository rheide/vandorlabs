package com.vandorlabs.ui;
/** One standard Infuser panel shared by Forge and NeoForge; platform adapters only draw widgets/items. */
public final class InfuserDialog {
 public static final int WIDTH=320,HEIGHT=222,TITLE_X=12,TITLE_Y=10,SUN_X=12,SUN_Y=36;
 public static final DialogStyle.Shell STYLE=DialogStyle.PORT;
 public static final int TITLE_COLOR=0xffffff,TEXT_COLOR=0xdae8f0,MUTED_COLOR=0x9faebb,SUN_COLOR=0x62ed82,NO_SUN_COLOR=0xff9988;
 public static final int FRAME=0xff607c8c,SLOT=0xff10181f,PROGRESS=0xff62ed82;
 public static final int INVENTORY_X=100,INVENTORY_Y=124,HOTBAR_Y=182,SLOT_STEP=18;
 public static final int PROGRESS_X=160,PROGRESS_Y=77,PROGRESS_WIDTH=84,PROGRESS_HEIGHT=10,DETAIL_Y=92,FOOTER_Y=210;
 public static final int LABEL_ROW_Y=60,INPUT_LABEL_X=28,CATALYST_LABEL_X=86,OUTPUT_LABEL_X=252,INVENTORY_LABEL_Y=112;
 public static final int LEFT_X=12;
 /** Legacy layout coordinates retained for callers; the dialog has no Done control. */
 public static final int DONE_ID=0,DONE_X=12,DONE_Y=182,DONE_WIDTH=72,DONE_HEIGHT=20;
 public static final String INPUT="Input",CATALYST="Catalyst",OUTPUT="Output",INVENTORY="Inventory";
 public static final String TITLE="Matter Infuser";
 public static int slotX(int index){if(index<0||index>=39)throw new IllegalArgumentException("Infuser slot");return index==0?40:index==1?112:index==2?264:INVENTORY_X+((index-3)%9)*SLOT_STEP;}
 public static int slotY(int index){if(index<0||index>=39)throw new IllegalArgumentException("Infuser slot");return index<3?74:index<30?INVENTORY_Y+((index-3)/9)*SLOT_STEP:HOTBAR_Y;}
 public static String sunlight(boolean sunlight){return sunlight?"Sunlight":"Needs sunlight";}
 public static int sunlightColor(boolean sunlight){return sunlight?SUN_COLOR:NO_SUN_COLOR;}
 public static String workLabel(boolean battery){return battery?"Battery charge":"Work";}
 public static int fill(int work,boolean battery,int energy,int capacity){int value=battery?energy:work,maximum=battery?Math.max(1,capacity):200;return Math.max(0,Math.min(PROGRESS_WIDTH,(int)((long)value*PROGRESS_WIDTH/maximum)));}
 public static String detail(int work,boolean battery,int energy,int capacity){return battery?energy+"/"+capacity+" RF":Math.max(0,Math.min(100,work*100/200))+"%";}
 public static String status(boolean input,boolean output,boolean sunlight,boolean fuel,boolean battery){return output?"Output ready":!input?"Insert iron or a Battery Module.":!sunlight?"Processing paused: needs sunlight.":!fuel?"Add redstone catalyst.":battery?"Charging Battery Module...":"Infusing Programmable Matter...";}
 private InfuserDialog(){}
}
