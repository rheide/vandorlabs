package com.vandorlabs.rack;

/** Shared compact notebook widths. Keep the full standard captions readable with all four module tabs. */
public final class ModuleRackTabs {
    public static final int GAP=4;
    private ModuleRackTabs() { }
    public static int width(int available,int modules,int index) {
        if(available<260||modules<0||modules>4||index<0||index>=modules+2)throw new IllegalArgumentException("Rack tab layout");
        int count=modules+2,main=modules==0?(available-GAP)/2:Math.max(64,(available-GAP*(count-1))/count);
        if(index==0)return main;
        if(modules==0)return available-GAP-main;
        if(index==1)return main;
        int remaining=available-GAP*(count-1)-2*main;
        return remaining/modules+(index-2<remaining%modules?1:0);
    }
    public static int offset(int available,int modules,int index) {
        int x=0;for(int before=0;before<index;before++)x+=width(available,modules,before)+GAP;return x;
    }
    public static String moduleLabel(int slot) {
        if(!ModuleRackGeometry.validSlot(slot))throw new IllegalArgumentException("Rack module slot");return "Auto"+(slot+1);
    }
}
