package com.vandorlabs.ui;

import com.vandorlabs.ports.ResourcePortSettings;
import java.util.*;

/** Source Port/Battery editor geometry, labels and option order; transport IDs belong to adapters. */
public final class ResourcePortDialog {
    public static final int WIDTH=308,HEIGHT=280,FIELD_X=12,FIELD_WIDTH=WIDTH-24,RATE_Y=158,CHANNEL_Y=84,FIELD_HEIGHT=18;
    public static final String RATE_LABEL="Shared output limit (blank for unlimited)",CHANNEL_LABEL="Receive channels",CHANNEL_HINT="Redstone gates incoming and outgoing transfers. Empty channels use adjacent redstone.";
    public static final DialogStyle.Shell STYLE=DialogStyle.PORT;
    public static final class Control extends DialogControl {private Control(int id,int x,int y,int width,int height,String label,boolean tab,boolean enabled){super(id,x,y,width,height,label,tab,enabled);}}
    public static List<Control> controls(int left,int top,int page,ResourcePortSettings settings,int mode,boolean battery,boolean block){
        if(page<0||page>2||mode<0||mode>2)throw new IllegalArgumentException("Port editor state");List<Control> result=new ArrayList<>();int half=(WIDTH-28)/2,tab=(WIDTH-32)/3;
        result.add(new Control(100,left+12,top+32,tab,20,"General",true,page!=0));result.add(new Control(102,left+16+tab,top+32,tab,20,"Housing",true,page!=2));result.add(new Control(101,left+20+2*tab,top+32,tab,20,"Redstone",true,page!=1));
        if(page==0){String[] sides={"Down","Up","North","South","West","East"};for(int side=0;side<6;side++)result.add(new Control(10+side,left+12+(side%2)*(half+4),top+62+(side/2)*26,half,20,sides[side]+": "+new String[]{"Disabled","Input","Output"}[settings.face(side).ordinal()],false,true));result.add(new Control(20,left+12,top+186,WIDTH-24,20,"Pull from inputs: "+(settings.pullInputs?"On":"Off"),false,true));result.add(new Control(21,left+12,top+212,WIDTH-24,20,"On texture: "+new String[]{"Redstone active","Connected","Transferring"}[settings.onTexture.ordinal()],false,true));}
        else if(page==1)result.add(new Control(30,left+12,top+116,WIDTH-24,20,"Input: "+new String[]{"Always active","ON activates","OFF activates"}[mode],false,true));
        if(page==2&&battery)result.add(new Control(40,left+WIDTH/2+8,top+194,WIDTH/2-20,20,"Shape: "+(block?"Block":"Hexagon"),false,true));
        result.add(new Control(0,left+12,top+HEIGHT-30,WIDTH-24,20,"Done",false,true));return Collections.unmodifiableList(result);
    }
    public static int housingWidth(boolean battery){return battery?WIDTH/2-22:WIDTH-31;}
    public static int housingRows(){return (HEIGHT-100)/22;}
    /** Preserve the existing native widget IDs while taking their definitions from Source. */
    public static int nativeId(int source){if(source>=100)return source-80;if(source>=10&&source<=15)return source-10;switch(source){case 0:return 9;case 20:return 6;case 21:return 7;case 30:return 8;case 40:return 10;default:throw new IllegalArgumentException("Port control ID");}}
    private ResourcePortDialog(){}
}
