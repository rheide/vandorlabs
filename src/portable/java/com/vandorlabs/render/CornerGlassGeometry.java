package com.vandorlabs.render;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A beveled panel between the centers of two adjacent cell faces, always at 45 degrees in plan.
 * A flat corner stands at one of three depths. A diagonal corner follows the cross section of
 * Programmable Diagonal Glass, so it continues those slopes around a corner: size (tall / half
 * width, full width, wide / half height), lean, and whether the slope hugs the near or far faces.
 */
public final class CornerGlassGeometry {
    private static final int STEPS=16;
    private static final double HALF_THICKNESS=2D/16;
    private static final double EPSILON=1E-9;
    /** Diagonal sizes, numbered like diagonal glass: tall / half width, full width, wide / half height. */
    public static final int TALL=0,FULL=1,WIDE=2;

    private final int turn,depth,mode;
    private final boolean sloped,inverted,far;
    private final double low,high;
    /** A flat corner at {@code depth}, or with {@code sloped} the legacy full-width diagonal for that metadata. */
    public CornerGlassGeometry(int turn,int depth,boolean sloped){
        this(turn,sloped?0:depth,sloped,FULL,sloped&&depth==1,sloped&&depth==2);
    }
    private CornerGlassGeometry(int turn,int depth,boolean sloped,int mode,boolean inverted,boolean far){
        if(turn<0||turn>3||depth<0||depth>2||mode<0||mode>2)throw new IllegalArgumentException("Corner glass placement");
        this.turn=turn;this.depth=depth;this.sloped=sloped;this.mode=sloped?mode:FULL;this.inverted=inverted;this.far=far;
        low=sloped&&mode==WIDE&&inverted?.5:0;high=sloped&&mode==WIDE&&!inverted?.5:1;
    }
    /** A diagonal corner; metadata is {@code turn | inverted<<2 | far<<3}. */
    public static CornerGlassGeometry diagonal(int turn,int mode,boolean inverted,boolean far){
        return new CornerGlassGeometry(turn,0,true,mode,inverted,far);
    }
    public static CornerGlassGeometry diagonal(int mode,int metadata){
        return diagonal(metadata&3,mode,(metadata&4)!=0,(metadata&8)!=0);
    }
    public int metadata(){return sloped?turn|(inverted?4:0)|(far?8:0):depth*4+turn;}
    public int turn(){return turn;}
    public int mode(){return mode;}
    public boolean inverted(){return inverted;}
    public boolean far(){return far;}
    public boolean sloped(){return sloped;}
    public double low(){return low;}
    public double high(){return high;}

    public static double center(int depth){return (PanelDepthPolicy.start(depth)+2)/16D;}
    /**
     * Depth of a diagonal glass slope's centre from the face it is anchored to, at height
     * {@code y}: diagonal glass is {@code (inverted?1-y:y)*span} for tall and full sizes, and the
     * wide size rises across the whole depth within its lower (or, inverted, upper) half.
     */
    public static double slopeDepth(int mode,boolean inverted,double y){
        if(mode==WIDE){
            double band=inverted?.5:0,u=Math.max(0,Math.min(1,(y-band)/.5));
            return inverted?1-u:u;
        }
        double t=Math.max(0,Math.min(1,y));
        return (inverted?1-t:t)*(mode==FULL?1:.5);
    }
    private double middle(double y){
        if(!sloped)return center(depth);
        double d=slopeDepth(mode,inverted,y);
        return far?1-d:d;
    }
    /**
     * The full cross section around the bevel, as on diagonal glass, whose slab overhangs its
     * block face by up to two pixels where the slope meets it. The corner keeps the same
     * thickness there, so the two meet face to face. Only where the bevel shrinks into the
     * cell's corner point is the inner side held at that point, so faces never fold through it.
     */
    private double distance(double y,double normal){return Math.max(0,middle(y)+(normal-.5));}
    private double height(double y){return low+(high-low)*y;}

    /**
     * Project one flat-glass quad onto the bevel. Each vertex is {@code {x,y,z,attributes...}} in
     * local unit coordinates; attributes are kept. Returns the four projected vertices; both
     * versions' renderers use this so their corner meshes are identical.
     */
    public List<double[]> projectQuad(double[]... quad){
        List<double[]> projected=new ArrayList<>(quad.length);double[] point=new double[3];
        for(double[] vertex:quad){
            double[] out=vertex.clone();project(vertex[0],vertex[1],vertex[2],point);
            out[0]=point[0];out[1]=point[1];out[2]=point[2];projected.add(out);
        }
        return projected;
    }
    /** Project local flat-glass coordinates; like diagonal glass, the slab may overhang the block face. */
    public void project(double along,double y,double normal,double[] out){
        double at=height(y),span=distance(at,normal);
        rotate(along*span,at,(1-along)*span,turn,out);
    }

    private static void rotate(double x,double y,double z,int turns,double[] out){
        for(int i=0;i<turns;i++){double oldX=x;x=1-z;z=oldX;}
        out[0]=x;out[1]=y;out[2]=z;
    }

    public static int first(int turn){return (turn+3)&3;}
    public static int second(int turn){return turn;}
    /** World-local coordinate where the bevel crosses face {@code side} at height {@code y}, or -1. */
    public double edge(int side,double y){
        double at=middle(y);
        if(side==first(turn))return turn==1||turn==2?1-at:at;
        if(side==second(turn))return turn==2||turn==3?1-at:at;
        return -1;
    }
    public boolean joinsEdge(int side,CornerGlassGeometry other){
        int opposite=(side+2)&3;
        return edge(side,low)>=0&&same(low,other.low)&&same(high,other.high)
                &&same(edge(side,low),other.edge(opposite,low))&&same(edge(side,high),other.edge(opposite,high));
    }
    public boolean joinsStraight(int side,boolean rotated,int depth){
        return !sloped&&rotated==((side&1)==0)&&same(edge(side,.5),rotated?1-center(depth):center(depth));
    }
    /**
     * A diagonal glass wall across face {@code side} (north, east, south, west), facing
     * {@code facing} in the same numbering. It continues the bevel only where its slope crosses the
     * shared face at the same place over the same heights: same size, lean and in-block position.
     */
    public boolean joinsDiagonal(int side,int facing,int wallMode,boolean wallInverted){
        if(!sloped||wallMode!=mode||edge(side,low)<0||((facing^side)&1)==0)return false;
        double wallLow=wallMode==WIDE&&wallInverted?.5:0,wallHigh=wallMode==WIDE&&!wallInverted?.5:1;
        if(!same(low,wallLow)||!same(high,wallHigh))return false;
        // Diagonal glass is anchored to its facing face: north and west at coordinate 0.
        boolean fromZero=facing==0||facing==3;
        for(double y:new double[]{low,high}){
            double d=slopeDepth(wallMode,wallInverted,y);
            if(!same(edge(side,y),fromZero?d:1-d))return false;
        }
        return true;
    }
    public boolean joinsVertical(boolean above,CornerGlassGeometry other){
        if(turn!=other.turn||!same(above?high:low,above?1:0)||!same(above?other.low:other.high,above?0:1))return false;
        return same(middle(above?1:0),other.middle(above?0:1));
    }
    private static boolean same(double first,double second){return Math.abs(first-second)<EPSILON;}
    public int side(double x,double y,double z){
        double normal=turn==0?x+z:turn==1?1-x+z:turn==2?2-x-z:1+x-z;
        return normal<middle(Math.max(low,Math.min(high,y)))?0:1;
    }
    /** North, east, south and west, in that order. */
    public static int orientation(int face,double x,double z){
        switch(face){
            case 0:return x<=.5?0:1;
            case 1:return z<=.5?1:2;
            case 2:return x<=.5?3:2;
            default:return z<=.5?0:3;
        }
    }

    public static int placementDepth(int turn,double x,double z,boolean horizontalFace){
        double[] point=new double[3];
        rotate(x,0,z,(4-turn)&3,point);
        return PanelDepthPolicy.fromHit((float)(point[0]+point[2]));
    }

    /** A horizontal neighbour seen from the cell being placed. */
    public static final class Neighbor {
        public static final Neighbor NONE=new Neighbor(0,false,0,null,0,0,false);
        final int kind;final boolean rotated;final int depth;final CornerGlassGeometry corner;
        final int facing,mode;final boolean inverted;
        private Neighbor(int kind,boolean rotated,int depth,CornerGlassGeometry corner,int facing,int mode,boolean inverted){
            this.kind=kind;this.rotated=rotated;this.depth=depth;this.corner=corner;this.facing=facing;this.mode=mode;this.inverted=inverted;
        }
        public static Neighbor straight(boolean rotated,int depth){return new Neighbor(1,rotated,depth,null,0,0,false);}
        public static Neighbor corner(int turn,int depth,boolean sloped){return corner(new CornerGlassGeometry(turn,depth,sloped));}
        public static Neighbor corner(CornerGlassGeometry geometry){return new Neighbor(2,false,0,geometry,0,0,false);}
        /** Diagonal glass facing {@code facing} (north, east, south, west) with its size and lean. */
        public static Neighbor diagonal(int facing,int mode,boolean inverted){return new Neighbor(3,false,0,null,facing,mode,inverted);}
        public boolean diagonal(){return kind==3||kind==2&&corner.sloped;}
        public int mode(){return kind==3?mode:kind==2?corner.mode:-1;}
    }
    private boolean joins(int side,Neighbor neighbor){
        if(neighbor==null||neighbor.kind==0)return false;
        if(neighbor.kind==2)return joinsEdge(side,neighbor.corner);
        if(neighbor.kind==3)return joinsDiagonal(side,neighbor.facing,neighbor.mode,neighbor.inverted);
        return joinsStraight(side,neighbor.rotated,neighbor.depth);
    }
    private static double segmentDistance(double px,double pz,double[] a,double[] b){
        double dx=b[0]-a[0],dz=b[2]-a[2],length=dx*dx+dz*dz;
        double t=length<EPSILON?0:Math.max(0,Math.min(1,((px-a[0])*dx+(pz-a[2])*dz)/length));
        double ex=a[0]+t*dx-px,ez=a[2]+t*dz-pz;
        return Math.sqrt(ex*ex+ez*ez);
    }
    /**
     * Shared placement policy for flat corners. The hit and player coordinates are relative to the
     * cell being placed, not the clicked block. {@code clickedSide} is the N/E/S/W side holding the
     * clicked support, or -1 for a click on a top or bottom face. Neighbours are N/E/S/W.
     * A bevel that meets the clicked pane wins, then one that meets other adjacent panes, then the
     * bevel closest to the click. When both bends meet the clicked pane end at the same point,
     * bend towards the player. {@code stacked} is the placement of a matching corner directly
     * above or below, or -1.
     */
    public static int placement(boolean sloped,int clickedSide,double x,double z,Neighbor[] neighbors,int stacked,double playerX,double playerZ){
        List<CornerGlassGeometry> candidates=new ArrayList<>();
        for(int depth=0;depth<3;depth++)for(int turn=0;turn<4;turn++)candidates.add(new CornerGlassGeometry(turn,depth,sloped));
        return choose(candidates,clickedSide,x,z,neighbors,stacked,playerX,playerZ);
    }
    /** The same policy for a diagonal corner of size {@code mode}; returns its metadata. */
    public static int placementDiagonal(int mode,int clickedSide,double x,double z,Neighbor[] neighbors,int stacked,double playerX,double playerZ){
        List<CornerGlassGeometry> candidates=new ArrayList<>();
        for(int meta=0;meta<16;meta++)candidates.add(diagonal(mode,meta));
        return choose(candidates,clickedSide,x,z,neighbors,stacked,playerX,playerZ);
    }
    private static int choose(List<CornerGlassGeometry> candidates,int clickedSide,double x,double z,Neighbor[] neighbors,int stacked,double playerX,double playerZ){
        x=Math.max(0,Math.min(1,x));z=Math.max(0,Math.min(1,z));
        double[] a=new double[3],b=new double[3];
        int found=candidates.get(0).metadata();double best=Double.NEGATIVE_INFINITY;
        for(CornerGlassGeometry candidate:candidates){
            double score=0;
            boolean clicked=false;
            for(int side=0;side<4;side++)if(candidate.joins(side,neighbors[side])){score+=side==clickedSide?100:10;clicked|=side==clickedSide;}
            if(candidate.metadata()==stacked)score+=5;
            candidate.project(0,.5,.5,a);candidate.project(1,.5,.5,b);
            // Compare against the cell center so a distant player only breaks ties.
            double mx=(a[0]+b[0])/2-playerX,mz=(a[2]+b[2])/2-playerZ,cx=.5-playerX,cz=.5-playerZ;
            double player=Math.sqrt(mx*mx+mz*mz)-Math.sqrt(cx*cx+cz*cz);
            // A click on a pane end only identifies the pane; the player's side chooses the bend.
            score-=clicked?player:segmentDistance(x,z,a,b)+1E-3*player;
            if(score>best+EPSILON){best=score;found=candidate.metadata();}
        }
        return found;
    }

    public <T> List<T> collision(SpaceDoorBounds.Factory<T> factory){
        List<T> result=new ArrayList<>();
        double[] a=new double[3],b=new double[3];
        int rows=sloped?STEPS:1;
        for(int row=0;row<rows;row++){
            double y0=height(row/(double)rows),y1=height((row+1D)/rows);
            double min=Math.min(distance(y0,.5-HALF_THICKNESS),distance(y1,.5-HALF_THICKNESS));
            double max=Math.max(distance(y0,.5+HALF_THICKNESS),distance(y1,.5+HALF_THICKNESS));
            for(int col=0;col<STEPS;col++){
                double x0=col/(double)STEPS,x1=Math.min(Math.min(1,max),(col+1D)/STEPS);
                if(x1<=x0+EPSILON)break;
                double z0=Math.max(0,min-x1),z1=Math.min(1,max-x0);
                if(z1<=z0+EPSILON)continue;
                rotate(x0,y0,z0,turn,a);
                rotate(x1,y1,z1,turn,b);
                result.add(factory.create(Math.min(a[0],b[0]),y0,Math.min(a[2],b[2]),
                        Math.max(a[0],b[0]),y1,Math.max(a[2],b[2])));
            }
        }
        return Collections.unmodifiableList(result);
    }

    public <T> T bounds(SpaceDoorBounds.Factory<T> factory){
        double max=Math.min(1,Math.max(distance(low,.5+HALF_THICKNESS),distance(high,.5+HALF_THICKNESS)));
        double[] a=new double[3],b=new double[3];
        rotate(0,low,0,turn,a);rotate(max,high,max,turn,b);
        return factory.create(Math.min(a[0],b[0]),low,Math.min(a[2],b[2]),Math.max(a[0],b[0]),high,Math.max(a[2],b[2]));
    }
}
