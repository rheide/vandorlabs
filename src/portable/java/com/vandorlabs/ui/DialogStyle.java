package com.vandorlabs.ui;

/** Source (Forge 1.12.2) visual contracts in logical pixels. Platform adapters draw them verbatim. */
public final class DialogStyle {
    public static final String LOGO="vandorlabs:textures/gui/vandor_logo.png";
    public static final int LOGO_MAX_HEIGHT=21,LOGO_ASPECT_WIDTH=12,LOGO_ASPECT_HEIGHT=7,LOGO_RIGHT=8;
    public static final int TAB_IDLE=0xff24323e,TAB_HOVER=0xff354958,TAB_SELECTED=0xff405b6d;
    public static final int TAB_LINE=0xff506878,TAB_SELECTED_LINE=0xffa4d4e8,TAB_TEXT=0xffc4d0d8,TAB_SELECTED_TEXT=0xffffffff;
    public static final int LIGHT_SLIDER_TRACK=0xff555560,LIGHT_SLIDER_THUMB=0xffb8d7e8;
    public static final Shell DARK=new Shell(0xff101012,0xff202028,24,8,8,24);
    public static final Shell PROGRAMMABLE=new Shell(0xff19232c,0xff304858,24,12,8,24);
    public static final Shell RAMP=new Shell(0xff19232c,0xff304858,28,14,10,24);
    public static final Shell PORT=new Shell(0xff19232c,0xff304858,28,12,10,28);
    public static final Shell COPIFIER=new Shell(0xff15171c,0xff2a3039,22,10,7,24);
    public static final Shell RACK=new Shell(0xee171b23,0,0,12,10,24);
    public static final Shell SYNTHESIS=new Shell(0xf0171b23,0,0,12,10,24);
    public static final Shell SURFACE=new Shell(0xff101012,0xff202028,18,8,5,18);
    public static final Shell DARK_TITLE_12=new Shell(0xff101012,0xff202028,24,12,8,24);
    public static final Shell SIGNAL=new Shell(0xff101012,0xff304858,24,12,8,24);
    public static final Shell SEGMENTED=new Shell(0xee18212c,0xff304858,26,12,10,26);
    public static final Shell CANOPY=new Shell(0xee101012,0,0,8,8,24);
    public static final Shell GEAR=new Shell(0xff19232c,0,0,12,12,24);
    public static final Shell LANDING=new Shell(0xee171b23,0,0,14,12,24);
    public static final Shell CHAIR=new Shell(0xdd171b23,0,0,14,11,24);
    public static final Shell BUFFER=new Shell(0xff18212c,0,0,16,14,24);
    public static final Shell COPY=new Shell(0xee18212c,0,0,12,8,0);
    public static final Shell PROGRAMMER=new Shell(0xff19232c,0xff304858,24,15,8,0);
    /** Header fill height and logo positioning height deliberately differ in several Source dialogs. */
    public static final class Shell {
        public final int body,header,headerHeight,titleX,titleY,logoHeaderHeight;
        public Shell(int body,int header,int headerHeight,int titleX,int titleY,int logoHeaderHeight){
            if(headerHeight<0||logoHeaderHeight<0||logoHeaderHeight==1)throw new IllegalArgumentException("Dialog header geometry");
            this.body=body;this.header=header;this.headerHeight=headerHeight;this.titleX=titleX;this.titleY=titleY;this.logoHeaderHeight=logoHeaderHeight;
        }
    }
    public static int logoHeight(int headerHeight){return Math.max(0,Math.min(LOGO_MAX_HEIGHT,headerHeight-2));}
    public static int logoWidth(int headerHeight){return logoHeight(headerHeight)*LOGO_ASPECT_WIDTH/LOGO_ASPECT_HEIGHT;}
    public static int logoLeft(int left,int width,int headerHeight){return left+width-logoWidth(headerHeight)-LOGO_RIGHT;}
    public static int logoTop(int top,int headerHeight){return top+(headerHeight-logoHeight(headerHeight))/2;}
    public static int tabBody(boolean enabled,boolean hovered){return !enabled?TAB_SELECTED:hovered?TAB_HOVER:TAB_IDLE;}
    public static int tabLine(boolean enabled){return enabled?TAB_LINE:TAB_SELECTED_LINE;}
    public static int tabText(boolean enabled){return enabled?TAB_TEXT:TAB_SELECTED_TEXT;}
    private DialogStyle(){}
}
