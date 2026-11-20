package com.vandorlabs.render;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** A beveled panel between the centers of two adjacent cell faces. */
public final class CornerGlassGeometry {
    private static final int STEPS=16;
    private static final double HALF_THICKNESS=2D/16;
    private static final double EPSILON=1E-9;

    private final int turn,depth;
    private final boolean sloped;
    public CornerGlassGeometry(int turn,int depth,boolean sloped){
        if(turn<0||turn>3||depth<0||depth>2)throw new IllegalArgumentException("Corner glass placement");
        this.turn=turn;this.depth=depth;this.sloped=sloped;
    }
    public static double center(int depth){return (PanelDepthPolicy.start(depth)+2)/16D;}
    private double middle(double y){
        // Keep each end inside the cell even at the extreme depth positions.
        return center(depth)+(sloped?(y-.5)*(depth==0?.75:.1875):0);
    }
    private double distance(double y,double normal){
        double center=middle(y);
        // Compress the full cross section together at the corner. Clamping each
        // vertex independently folds several frame faces onto the same plane.
        double scale=Math.min(1,Math.min(center,1-center)/HALF_THICKNESS);
        return center+(normal-.5)*scale;
    }

    /** Project local flat-glass coordinates. The two ends remain flush with the cell boundaries. */
    public void project(double along,double y,double normal,double[] out){
        double span=distance(y,normal);
        rotate(along*span,y,(1-along)*span,turn,out);
    }

    private static void rotate(double x,double y,double z,int turns,double[] out){
        for(int i=0;i<turns;i++){double oldX=x;x=1-z;z=oldX;}
        out[0]=x;out[1]=y;out[2]=z;
    }

    public static int first(int turn){return (turn+3)&3;}
    public static int second(int turn){return turn;}
    public double edge(int side,double y){
        double at=middle(y);
        if(side==first(turn))return turn==1||turn==2?1-at:at;
        if(side==second(turn))return turn==2||turn==3?1-at:at;
        return -1;
    }
    public boolean joinsEdge(int side,CornerGlassGeometry other){
        int opposite=(side+2)&3;
        return edge(side,0)>=0&&same(edge(side,0),other.edge(opposite,0))&&same(edge(side,1),other.edge(opposite,1));
    }
    public boolean joinsStraight(int side,boolean rotated,int depth){
        return !sloped&&rotated==((side&1)==0)&&same(edge(side,.5),rotated?1-center(depth):center(depth));
    }
    public boolean joinsVertical(boolean above,CornerGlassGeometry other){
        return turn==other.turn&&same(middle(above?1:0),other.middle(above?0:1));
    }
    private static boolean same(double first,double second){return Math.abs(first-second)<EPSILON;}
    public int side(double x,double y,double z){
        double normal=turn==0?x+z:turn==1?1-x+z:turn==2?2-x-z:1+x-z;
        return normal<middle(y)?0:1;
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
        public static final Neighbor NONE=new Neighbor(0,false,0,null);
        final int kind;final boolean rotated;final int depth;final CornerGlassGeometry corner;
        private Neighbor(int kind,boolean rotated,int depth,CornerGlassGeometry corner){this.kind=kind;this.rotated=rotated;this.depth=depth;this.corner=corner;}
        public static Neighbor straight(boolean rotated,int depth){return new Neighbor(1,rotated,depth,null);}
        public static Neighbor corner(int turn,int depth,boolean sloped){return new Neighbor(2,false,depth,new CornerGlassGeometry(turn,depth,sloped));}
    }
    private boolean joins(int side,Neighbor neighbor){
        if(neighbor==null||neighbor.kind==0)return false;
        return neighbor.kind==2?joinsEdge(side,neighbor.corner):joinsStraight(side,neighbor.rotated,neighbor.depth);
    }
    private static double segmentDistance(double px,double pz,double[] a,double[] b){
        double dx=b[0]-a[0],dz=b[2]-a[2],length=dx*dx+dz*dz;
        double t=length<EPSILON?0:Math.max(0,Math.min(1,((px-a[0])*dx+(pz-a[2])*dz)/length));
        double ex=a[0]+t*dx-px,ez=a[2]+t*dz-pz;
        return Math.sqrt(ex*ex+ez*ez);
    }
    /**
     * Shared placement policy. The hit and player coordinates are relative to the cell being
     * placed, not the clicked block. {@code clickedSide} is the N/E/S/W side holding the clicked
     * support, or -1 for a click on a top or bottom face. Neighbours are N/E/S/W.
     * A bevel that meets the clicked pane wins, then one that meets other adjacent panes, then the
     * bevel closest to the click. When both bends meet the clicked pane end at the same point,
     * bend towards the player. {@code stacked} is the placement of a matching corner directly
     * above or below, or -1.
     */
    public static int placement(boolean sloped,int clickedSide,double x,double z,Neighbor[] neighbors,int stacked,double playerX,double playerZ){
        x=Math.max(0,Math.min(1,x));z=Math.max(0,Math.min(1,z));
        double[] a=new double[3],b=new double[3];
        int found=0;double best=Double.NEGATIVE_INFINITY;
        for(int depth=0;depth<3;depth++)for(int turn=0;turn<4;turn++){
            CornerGlassGeometry candidate=new CornerGlassGeometry(turn,depth,sloped);
            double score=0;
            boolean clicked=false;
            for(int side=0;side<4;side++)if(candidate.joins(side,neighbors[side])){score+=side==clickedSide?100:10;clicked|=side==clickedSide;}
            if(depth*4+turn==stacked)score+=5;
            candidate.project(0,.5,.5,a);candidate.project(1,.5,.5,b);
            // Compare against the cell center so a distant player only breaks ties.
            double mx=(a[0]+b[0])/2-playerX,mz=(a[2]+b[2])/2-playerZ,cx=.5-playerX,cz=.5-playerZ;
            double player=Math.sqrt(mx*mx+mz*mz)-Math.sqrt(cx*cx+cz*cz);
            // A click on a pane end only identifies the pane; the player's side chooses the bend.
            score-=clicked?player:segmentDistance(x,z,a,b)+1E-3*player;
            if(score>best+EPSILON){best=score;found=depth*4+turn;}
        }
        return found;
    }

    public <T> List<T> collision(SpaceDoorBounds.Factory<T> factory){
        List<T> result=new ArrayList<>();
        double[] a=new double[3],b=new double[3];
        int rows=sloped?STEPS:1;
        for(int row=0;row<rows;row++){
            double y0=row/(double)rows,y1=(row+1D)/rows;
            double min=distance(y0,.5-HALF_THICKNESS),max=distance(y1,.5+HALF_THICKNESS);
            for(int col=0;col<STEPS;col++){
                double x0=col/(double)STEPS,x1=Math.min(max,(col+1D)/STEPS);
                if(x1<=x0+EPSILON)break;
                double z0=Math.max(0,min-x1),z1=max-x0;
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
        double max=distance(1,.5+HALF_THICKNESS);
        double[] a=new double[3],b=new double[3];
        rotate(0,0,0,turn,a);rotate(max,1,max,turn,b);
        return factory.create(Math.min(a[0],b[0]),0,Math.min(a[2],b[2]),Math.max(a[0],b[0]),1,Math.max(a[2],b[2]));
    }
}
