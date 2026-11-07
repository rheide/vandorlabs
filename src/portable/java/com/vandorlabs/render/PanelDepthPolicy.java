package com.vandorlabs.render;

/** Four-pixel panel placement; face IDs are Down/Up/North/South/West/East. */
public final class PanelDepthPolicy {
    private PanelDepthPolicy() { }
    public static int fromHit(float coordinate){return coordinate<1F/3F?1:coordinate>2F/3F?2:0;}
    public static int fromHit(int face,float hitX,float hitZ){
        float normal=face==3?1F-hitZ:face==5?1F-hitX:face==4?hitX:hitZ;
        return fromHit(normal);
    }
    /** Horizontal normal-face placement anchors in the new cell against its support. */
    public static int fromPlacement(int facing,int clickedFace,float hitX,float hitZ){
        boolean x=facing==4||facing==5;
        if(x&&(clickedFace==4||clickedFace==5))hitX=clickedFace==4?1:0;
        else if(!x&&(clickedFace==2||clickedFace==3))hitZ=clickedFace==2?1:0;
        return fromHit(facing,hitX,hitZ);
    }
    public static int start(int depth){return depth==1?0:depth==2?12:6;}
    public static int offset(int depth){return start(depth)-6;}
}
