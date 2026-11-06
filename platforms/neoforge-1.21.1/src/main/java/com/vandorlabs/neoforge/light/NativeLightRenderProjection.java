package com.vandorlabs.neoforge.light;

/** Owning-thread artwork projection. Face bits use the released local six-face order; UVs are 0..16. */
public record NativeLightRenderProjection(int visibleFaces,double left,double top,double right,double bottom) {
    public static final NativeLightRenderProjection SINGLE=new NativeLightRenderProjection(63,0,0,16,16);
    public NativeLightRenderProjection {
        if((visibleFaces&~63)!=0||!Double.isFinite(left)||!Double.isFinite(top)||!Double.isFinite(right)||!Double.isFinite(bottom)
                ||left<0||top<0||right>16||bottom>16||left>=right||top>=bottom)
            throw new IllegalArgumentException("Invalid Light artwork projection");
    }
    public boolean visible(int localFace){if(localFace<0||localFace>5)throw new IllegalArgumentException("Light face");return (visibleFaces&(1<<localFace))!=0;}
}
