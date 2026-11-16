package com.vandorlabs.ui;

/** Source-authored geometry and flat-tab states, independent of either widget API. */
public final class DialogStyleTest {
    public static void main(String[] args){
        int cases=0;
        for(int header:new int[]{18,22,24,26,28})for(int width:new int[]{240,308,320,348,460})for(int left:new int[]{0,17})for(int top:new int[]{0,13}){
            int h=Math.min(21,header-2),w=h*12/7;
            require(DialogStyle.logoHeight(header)==h&&DialogStyle.logoWidth(header)==w,"Source logo dimensions");
            require(DialogStyle.logoLeft(left,width,header)==left+width-w-8&&DialogStyle.logoTop(top,header)==top+(header-h)/2,"Source right-aligned logo bounds");cases++;
        }
        require(DialogStyle.RAMP.body==0xff19232c&&DialogStyle.RAMP.header==0xff304858&&DialogStyle.RAMP.headerHeight==28&&DialogStyle.RAMP.logoHeaderHeight==24,"Source Ramp header and logo use independent heights");
        require(DialogStyle.PROGRAMMABLE.headerHeight==24&&DialogStyle.PROGRAMMABLE.titleY==8,"Source Light/Screen/Trapdoor shell");
        require(DialogStyle.SURFACE.headerHeight==18&&DialogStyle.SURFACE.titleY==5,"Source Surface compact header");
        require(DialogStyle.tabBody(true,false)==0xff24323e&&DialogStyle.tabBody(true,true)==0xff354958&&DialogStyle.tabBody(false,true)==0xff405b6d,"Source idle/hover/selected tabs");
        require(DialogStyle.tabLine(false)==0xffa4d4e8&&DialogStyle.tabText(false)==0xffffffff,"Source selected tab underline/text");
        System.out.println("DialogStyleTest PASS logoCases="+cases+" Source shell/tab/light-slider contracts");
    }
    private static void require(boolean yes,String text){if(!yes)throw new AssertionError(text);}
}
