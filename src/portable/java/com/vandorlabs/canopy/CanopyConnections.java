package com.vandorlabs.canopy;

/** Released contact rules, after the platform resolves direct/released cells and constituent roles. */
public final class CanopyConnections {
    private CanopyConnections(){}
    private static boolean opening(String kind){return "canopy".equals(kind)||"long_canopy".equals(kind);}
    private static int x(int facing){return facing==4?-1:facing==5?1:0;}
    private static int z(int facing){return facing==2?-1:facing==3?1:0;}
    public static boolean connects(int bit,String selfKind,int selfLength,int selfFacing,int selfMount,
            String peerKind,int peerLength,int peerFacing,int peerMount,int dx,int dy,int dz,boolean peerConstituent){
        if(bit!=1&&bit!=2&&bit!=4&&bit!=8)throw new IllegalArgumentException("Canopy contact bit");
        if(peerKind==null||!peerConstituent||selfMount!=peerMount)return false;
        boolean regular="regular_canopy_glass".equals(selfKind),peerRegular="regular_canopy_glass".equals(peerKind);
        boolean same=selfKind.equals(peerKind)&&(regular||peerFacing==selfFacing);
        boolean rearMeets=dx-x(peerFacing)*peerLength==0&&dy==0&&dz-z(peerFacing)*peerLength==0;
        boolean seam=bit==8&&selfKind.startsWith("angled_")&&peerRegular
            ||regular&&peerKind.startsWith("angled_")&&rearMeets;
        seam|=regular&&opening(peerKind)&&rearMeets||opening(selfKind)&&bit==8&&peerRegular;
        seam|=opening(selfKind)&&bit==8&&opening(peerKind)&&(peerFacing^1)==selfFacing
            &&dx+x(selfFacing)*(peerLength-1)==-x(selfFacing)*selfLength&&dy==0
            &&dz+z(selfFacing)*(peerLength-1)==-z(selfFacing)*selfLength;
        return same||seam;
    }
    /** Fixed glazing retains its roof frame where a static opening shell can leave the seam. */
    public static boolean cutsRoof(String constituentKind){return opening(constituentKind);}
}
