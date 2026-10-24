package com.vandorlabs.ui;

/** Bounded option cycling and row scrolling in logical GUI coordinates. */
public final class DialogInput {
    private DialogInput(){}
    public static int direction(int mouseButton){return mouseButton==1?-1:1;}
    public static int cycle(int value,int count,int direction){return Math.floorMod(value+direction,count);}
    public static int cycle(int value,int low,int high,int direction){return low+cycle(value-low,high-low+1,direction);}
    public static int maxScroll(int entries,int visibleRows){return Math.max(0,entries-visibleRows);}
    public static int thumbHeight(int trackHeight,int visibleRows,int entries){return Math.max(8,trackHeight*visibleRows/Math.max(visibleRows,entries));}
    public static int thumbY(int trackTop,int trackHeight,int thumbHeight,int scroll,int maximum){return trackTop+(trackHeight-thumbHeight)*scroll/Math.max(1,maximum);}
    public static int wheel(int scroll,int maximum,int delta){return Math.max(0,Math.min(maximum,scroll+(delta>0?-1:delta<0?1:0)));}
    public static int scrollForDrag(int mouseY,int trackTop,int trackHeight,int thumbHeight,int maximum,int dragOffset){
        int travel=trackHeight-thumbHeight;
        if(maximum<=0||travel<=0)return 0;
        int thumbTop=Math.max(0,Math.min(travel,mouseY-trackTop-dragOffset));
        return Math.round((float)thumbTop*maximum/travel);
    }
}
