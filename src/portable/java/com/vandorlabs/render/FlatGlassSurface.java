package com.vandorlabs.render;

/** Released ordinary pane winding and the inset pixel-space replacement-artwork template. */
public final class FlatGlassSurface {
    private FlatGlassSurface(){}
    public static <C,M> void builtin(PanelTextureMesh.Sink<C,M> sink,C context,M material,boolean back){
        for(int i=0;i<4;i++){int index=back?i:(4-i)&3;double x=index==1||index==2?1:0,y=index<2?0:1;sink.pixel(context,material,x,y,back?9D/16:7D/16,x*16,(1-y)*16,0,0,back?1:-1);}
    }
    /** Owned copy: mapping/clipping may change records without touching a shared template. */
    public static float[] materialPlane(){float[] result=new float[64];int at=0;for(boolean near:new boolean[]{true,false}){double z=(near?7D/16+.0001:9D/16-.0001)*16;for(int i=0;i<4;i++){int index=near?(4-i)&3:i;double x=index==1||index==2?16:0,y=index<2?0:16;result[at++]=(float)x;result[at++]=(float)y;result[at++]=(float)z;result[at++]=(float)(x/16);result[at++]=(float)(1-y/16);result[at++]=0;result[at++]=0;result[at++]=near?-1:1;}}return result;}
}
