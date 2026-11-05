package com.vandorlabs.neoforge.shape;

import com.vandorlabs.surface.WindowOutline;

/** Aperture coordinates and direct rail contacts. This descriptor never builds a WindowOutline. */
public record NativePortholeLayout(NativePanelLayout pane, int shape, int extraBorder,
                                   boolean hideLeft, boolean hideRight,
                                   boolean hideLower, boolean hideUpper, boolean solid) {
    public NativePortholeLayout(NativePanelLayout pane,int shape,int extraBorder,boolean left,boolean right,boolean lower,boolean upper){this(pane,shape,extraBorder,left,right,lower,upper,false);}
    private static final NativePortholeLayout[] UNITS = {
            new NativePortholeLayout(NativePanelLayout.UNIT,0,0,false,false,false,false),
            new NativePortholeLayout(NativePanelLayout.UNIT,1,0,false,false,false,false),
            new NativePortholeLayout(NativePanelLayout.UNIT,2,0,false,false,false,false),
            new NativePortholeLayout(NativePanelLayout.UNIT,3,0,false,false,false,false)};
    private static final NativePortholeLayout[] SOLIDS = {
            new NativePortholeLayout(NativePanelLayout.UNIT,0,0,false,false,false,false,true),
            new NativePortholeLayout(NativePanelLayout.UNIT,1,0,false,false,false,false,true),
            new NativePortholeLayout(NativePanelLayout.UNIT,2,0,false,false,false,false,true),
            new NativePortholeLayout(NativePanelLayout.UNIT,3,0,false,false,false,false,true)};
    public NativePortholeLayout {
        if (pane == null || !WindowOutline.validShape(shape) || extraBorder != 0
                || pane.columns()<1 || pane.rows()<1 || pane.column()<0 || pane.row()<0
                || pane.column()>=pane.columns() || pane.row()>=pane.rows()
                || (long)pane.columns()*pane.rows()>NativePanelGroups.LIMIT)
            throw new IllegalArgumentException("Porthole aperture descriptor");
    }
    public static NativePortholeLayout contacts(NativePanelLayout pane,int shape,int mask) {
        if((mask&~15)!=0)throw new IllegalArgumentException("Porthole rail contacts");
        if(mask==0&&pane.equals(NativePanelLayout.UNIT))return unit(shape);
        return new NativePortholeLayout(pane,shape,0,(mask&1)!=0,(mask&2)!=0,(mask&4)!=0,(mask&8)!=0);
    }
    public static NativePortholeLayout solidUnit(int shape){if(!WindowOutline.validShape(shape))throw new IllegalArgumentException("Porthole shape");return SOLIDS[shape];}
    public static NativePortholeLayout unit(int shape) {
        if(!WindowOutline.validShape(shape))throw new IllegalArgumentException("Porthole shape");
        return UNITS[shape];
    }
}
