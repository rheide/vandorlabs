package com.vandorlabs.canopy;

/** Item-only silhouette bounds. The authored world meshes and their collision remain untouched. */
public final class CanopyItemDetail {
    private double minX=Double.POSITIVE_INFINITY,minY=minX,minZ=minX;
    private double maxX=Double.NEGATIVE_INFINITY,maxY=maxX,maxZ=maxX;
    public void include(double x,double y,double z){minX=Math.min(minX,x);minY=Math.min(minY,y);minZ=Math.min(minZ,z);maxX=Math.max(maxX,x);maxY=Math.max(maxY,y);maxZ=Math.max(maxZ,z);}
    public double span(){return Math.max(1E-9,Math.max(maxX-minX,Math.max(maxY-minY,maxZ-minZ)));}
    public double x(double x){return .5+(x-(minX+maxX)*.5)/span();}
    public double y(double y){return .5+(y-(minY+maxY)*.5)/span();}
    public double z(double z){return .5+(z-(minZ+maxZ)*.5)/span();}
    /** Keep glazing and the visible frame; subpixel bolts, seals and mechanism faces add no item detail. */
    public boolean keep(String material,double area){return material.equals("glass")||area/(span()*span())>=.0002;}
}
