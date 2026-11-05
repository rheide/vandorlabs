package com.vandorlabs.ui;

/** Immutable logical control data shared by family contracts and target widgets. */
public class DialogControl {
    public final int id,x,y,width,height;
    public final String label;
    public final boolean tab,enabled;
    protected DialogControl(int id,int x,int y,int width,int height,String label,boolean tab,boolean enabled){
        this.id=id;this.x=x;this.y=y;this.width=width;this.height=height;this.label=label;this.tab=tab;this.enabled=enabled;
    }
}
