package com.vandorlabs.navigation;

/** Physical-pixel layout shared by rendering, sampling and authoritative hit testing. */
public final class NavigationCanvas {
    public static final int PIXELS_PER_BLOCK=256, HEADER=20, BUTTON_WIDTH=24, BUTTON_HEIGHT=24, MAX_GRID=500;
    private static final int[] ACTIONS={1,2,3,4,5,6,8,7}; // Append HOME; preserve saved/wire WRITE=7.
    public final int width,height,mapTop,mapBottom,columns,rows,count,buttonLeft,buttonTop;
    public final boolean progressive;
    public NavigationCanvas(double widthBlocks,double heightBlocks,boolean writable){this(pixels(widthBlocks),pixels(heightBlocks),writable);}
    public NavigationCanvas(double widthBlocks,double heightBlocks,boolean writable,boolean progressive){
        this(pixels(widthBlocks),pixels(heightBlocks),writable,progressive);
    }
    public NavigationCanvas(int width,int height,boolean writable){this(width,height,writable,writable);}
    private NavigationCanvas(int width,int height,boolean writable,boolean progressive){
        if(width<96||width>65536||height<96||height>65536)throw new IllegalArgumentException("Navigation canvas bounds");
        this.width=width;this.height=height;this.progressive=progressive;count=writable?8:7;columns=Math.max(1,Math.min(count,width/BUTTON_WIDTH));rows=(count+columns-1)/columns;
        buttonLeft=(width-columns*BUTTON_WIDTH)/2;buttonTop=height-rows*BUTTON_HEIGHT-4;mapTop=HEADER;mapBottom=Math.max(HEADER+8,buttonTop-4);
    }
    private static int pixels(double blocks){return Math.max(96,Math.min(65536,(int)Math.round(blocks*PIXELS_PER_BLOCK)));}
    public double scale(int zoom){return NavigationMap.SPANS[zoom]/384.0;}
    public int gridWidth(int zoom){return gridWidth(zoom,false);}
    public int gridHeight(int zoom){return gridHeight(zoom,false);}
    public int gridWidth(int zoom,boolean refined){return gridSize(width,zoom,refined);}
    public int gridHeight(int zoom,boolean refined){return gridSize(mapBottom-mapTop,zoom,refined);}
    private int gridSize(int pixels,int zoom,boolean refined){
        int stride=stride(zoom);
        if(progressive&&zoom<=1)stride=2;
        if(refined)stride=Math.max(1,stride/2);
        return Math.min(MAX_GRID,(pixels+stride-1)/stride);
    }
    public boolean canRefine(int zoom){
        return progressive&&(gridWidth(zoom,true)>gridWidth(zoom)||gridHeight(zoom,true)>gridHeight(zoom));
    }
    public static int stride(int zoom){return zoom<=1?1:zoom<=3?2:4;}
    public static int terrainStep(int zoom,boolean refined){
        int step=zoom<=3?1:1<<Math.min(6,zoom-2);
        return refined?Math.max(1,step/2):step;
    }
    public int sampleX(int center,int zoom,int column){return sampleX(center,zoom,column,gridWidth(zoom));}
    public int sampleZ(int center,int zoom,int row){return sampleZ(center,zoom,row,gridHeight(zoom));}
    private int sampleX(int center,int zoom,int column,int columns){
        return sample(center,column,columns,width,scale(zoom));
    }
    private int sampleZ(int center,int zoom,int row,int rows){
        return sample(center,row,rows,mapBottom-mapTop,scale(zoom));
    }
    private static int sample(int center,int index,int samples,int pixels,double scale){
        return NavigationMap.bound(Math.round(center+((index+.5)*pixels/samples-pixels/2.0)*scale));
    }
    /** Precompute world coordinates once per axis instead of once per pixel. */
    public RasterGrid raster(int centerX,int centerZ,int zoom,boolean refined){
        int[] xs=new int[gridWidth(zoom,refined)],zs=new int[gridHeight(zoom,refined)];
        for(int i=0;i<xs.length;i++)xs[i]=sampleX(centerX,zoom,i,xs.length);
        for(int i=0;i<zs.length;i++)zs[i]=sampleZ(centerZ,zoom,i,zs.length);
        return new RasterGrid(xs,zs,terrainStep(zoom,refined));
    }
    public static final class RasterGrid {
        public final int[] xs,zs;
        public final int terrainStep;
        private RasterGrid(int[] xs,int[] zs,int terrainStep){this.xs=xs;this.zs=zs;this.terrainStep=terrainStep;}
    }
    public int action(double u,double v){
        int x=(int)Math.floor(u*width)-buttonLeft,y=(int)Math.floor(v*height)-buttonTop;
        if(x<0||x>=columns*BUTTON_WIDTH||y<0||y>=rows*BUTTON_HEIGHT)return 0;int index=y/BUTTON_HEIGHT*columns+x/BUTTON_WIDTH;return index<count?ACTIONS[index]:0;
    }
    public int actionAt(int index){return ACTIONS[index];}
    public int buttonX(int index){return buttonLeft+index%columns*BUTTON_WIDTH;}
    public int buttonY(int index){return buttonTop+index/columns*BUTTON_HEIGHT;}
    public double buttonU(int action){for(int i=0;i<count;i++)if(ACTIONS[i]==action)return (buttonX(i)+BUTTON_WIDTH/2.0)/width;return -1;}
    public double buttonV(int action){for(int i=0;i<count;i++)if(ACTIONS[i]==action)return (buttonY(i)+BUTTON_HEIGHT/2.0)/height;return -1;}
    public double mapU(int center,int world,int zoom){return .5+(world-(double)center)/(width*scale(zoom));}
    public double mapV(int center,int world,int zoom){return .5+(world-(double)center)/((mapBottom-mapTop)*scale(zoom));}
}
