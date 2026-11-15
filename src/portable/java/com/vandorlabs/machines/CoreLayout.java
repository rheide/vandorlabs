package com.vandorlabs.machines;

/** Authored Core frame coordinates; independent of world, tile factories and model rendering. */
public final class CoreLayout {
    public static final int MAX_AXIS=16,MAX_CELLS=4096;
    public final int width,height,depth,facing,cornerX,cornerZ,turns;
    private final int pivotX,pivotZ;
    public CoreLayout(int width,int height,int depth,int facing,int cornerX,int cornerZ){
        if(width<1||width>MAX_AXIS||height<2||height>MAX_AXIS||depth<1||depth>MAX_AXIS||facing<2||facing>5||cornerX<0||cornerX>=MAX_AXIS||cornerZ<0||cornerZ>=MAX_AXIS)throw new IllegalArgumentException("Invalid Core layout");
        this.width=width;this.height=height;this.depth=depth;this.facing=facing;this.cornerX=cornerX;this.cornerZ=cornerZ;turns=facing==2?0:facing==5?1:facing==3?2:3;pivotX=rotatedX(cornerX,cornerZ);pivotZ=rotatedZ(cornerX,cornerZ);
    }
    /** Old saved corner coordinates remain renderable; ownership admission requires a canonical corner. */
    public boolean canonicalCorner(){return (cornerX==0||cornerX==width-1)&&(cornerZ==0||cornerZ==depth-1);}
    public int cells(){return width*height*depth;}
    public int pivotX(){return pivotX;}public int pivotZ(){return pivotZ;}
    public int extentX(){return (turns&1)==0?width:depth;}public int extentZ(){return (turns&1)==0?depth:width;}
    public int x(int x,int z){return rotatedX(x,z)-pivotX;}public int z(int x,int z){return rotatedZ(x,z)-pivotZ;}
    private int rotatedX(int x,int z){switch(turns){case 1:return depth-1-z;case 2:return width-1-x;case 3:return z;default:return x;}}
    private int rotatedZ(int x,int z){switch(turns){case 1:return x;case 2:return depth-1-z;case 3:return width-1-x;default:return z;}}
    public boolean frame(int x,int y,int z){return y==0||(x==0||x==width-1)&&(z==0||z==depth-1)||y==height-1&&(x==0||x==width-1||z==0||z==depth-1);}
}
