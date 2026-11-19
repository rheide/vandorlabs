package com.vandorlabs.render;

import java.util.List;

/** Diagonal Large Door panel coordinates retained for existing callers. */
public final class XDoorPanel {
    private XDoorPanel() {}
    public static double travel(double progress,boolean framed,double scale){return DoorPanelSplit.travel(progress,framed,scale);}
    public static double shiftX(int panel,double progress){return DoorPanelSplit.DIAGONAL.shiftX(panel,progress);}
    public static double shiftY(int panel,double progress){return DoorPanelSplit.DIAGONAL.shiftY(panel,progress);}
    public static double distance(int panel,int plane,double x,double y){return DoorPanelSplit.DIAGONAL.distance(panel,plane,x,y);}
    public static List<float[]> clip(List<float[]> polygon,int hand,int panel){return DoorPanelSplit.DIAGONAL.clip(polygon,hand,panel);}
}
