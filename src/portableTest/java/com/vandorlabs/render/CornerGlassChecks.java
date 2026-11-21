package com.vandorlabs.render;

import java.util.List;

/** Corner placement and non-folding checks shared by both platform renderers. */
public final class CornerGlassChecks {
    private static int checks;
    private static void require(boolean value,String reason){checks++;if(!value)throw new AssertionError(reason);}
    private static CornerGlassGeometry.Neighbor[] empty(){
        CornerGlassGeometry.Neighbor[] result=new CornerGlassGeometry.Neighbor[4];
        java.util.Arrays.fill(result,CornerGlassGeometry.Neighbor.NONE);return result;
    }
    /** Diagonal glass centre depth from its facing face, as DiagonalPanelContact defines the slope. */
    private static double wallDepth(int mode,boolean inverted,double y){
        if(mode==2){double band=inverted?.5:0,u=(y-band)/.5;return inverted?1-u:u;}
        return (inverted?1-y:y)*(mode==1?1:.5);
    }
    private static void diagonal(){
        double[] a=new double[3];
        for(int mode=0;mode<3;mode++)for(int meta=0;meta<16;meta++){
            CornerGlassGeometry geometry=CornerGlassGeometry.diagonal(mode,meta);
            require(geometry.metadata()==meta,"diagonal metadata round trip");
            require(mode!=2||Math.abs(geometry.high()-geometry.low()-.5)<1E-9,"wide corners fill half the height");
            for(int row=0;row<=16;row++){
                // A horizontal slice through the slab at this height keeps the full four-pixel
                // thickness, as diagonal glass does where it meets a block face (it was once
                // squeezed to a line there). Only a bevel shrinking into the cell's corner point
                // is cut off at that point. c is the bevel centre at this height, local and before the turn.
                double y=row/16D;
                List<double[]> slice=geometry.projectQuad(new double[]{0,y,.375},new double[]{1,y,.375},new double[]{1,y,.625},new double[]{0,y,.625});
                double area=0;
                for(int i=0;i<4;i++){double[] p=slice.get(i),n=slice.get((i+1)%4);area+=(p[0]*n[2]-n[0]*p[2])/2;}
                for(double[] p:slice)require(p[1]>=geometry.low()-1E-9&&p[1]<=geometry.high()+1E-9&&p[0]>=-.125-1E-9&&p[0]<=1.125+1E-9&&p[2]>=-.125-1E-9&&p[2]<=1.125+1E-9,"diagonal corner overhangs its cell by at most two pixels");
                geometry.project(0,y,.5,a);double lx=a[0],lz=a[2];
                for(int t=0;t<(meta&3);t++){double old=lx;lx=lz;lz=1-old;}
                double c=lx+lz,inner=Math.max(0,c-.125),outer=c+.125,expected=(outer*outer-inner*inner)/2;
                require(Math.abs(Math.abs(area)-expected)<1E-6,"diagonal corner keeps its full thickness at height "+y+" mode="+mode+" meta="+meta+" area="+Math.abs(area)+" expected="+expected);
            }
            // In plan the bevel always runs at 45 degrees: both ends sit the same distance from the corner.
            for(double y:new double[]{geometry.low(),geometry.high()}){
                int first=CornerGlassGeometry.first(meta&3),second=CornerGlassGeometry.second(meta&3);
                double p=geometry.edge(first,y),q=geometry.edge(second,y);
                int turn=meta&3;double dp=(turn==1||turn==2)?1-p:p,dq=(turn==2||turn==3)?1-q:q;
                require(Math.abs(dp-dq)<1E-9,"diagonal bevel is at 45 degrees in plan");
            }
            CornerGlassGeometry.Neighbor[] none=empty();
            require(CornerGlassGeometry.placementDiagonal(mode,-1,.2,.8,none,meta,.5,5)==meta,"matching diagonal corner above or below keeps its bevel");
        }
        // Every diagonal glass wall, clicked on its end, gets a corner that continues it exactly.
        for(int side=0;side<4;side++)for(int facing=0;facing<4;facing++){
            if(((facing^side)&1)==0)continue;
            for(int mode=0;mode<3;mode++)for(boolean inverted:new boolean[]{false,true}){
                CornerGlassGeometry.Neighbor[] around=empty();around[side]=CornerGlassGeometry.Neighbor.diagonal(facing,mode,inverted);
                int meta=CornerGlassGeometry.placementDiagonal(mode,side,.5,.5,around,-1,.5,.5);
                CornerGlassGeometry placed=CornerGlassGeometry.diagonal(mode,meta);
                require(placed.joinsDiagonal(side,facing,mode,inverted),"clicked diagonal glass is continued side="+side+" facing="+facing+" mode="+mode+" inverted="+inverted);
                boolean fromZero=facing==0||facing==3;
                for(double y:new double[]{placed.low(),(placed.low()+placed.high())/2,placed.high()}){
                    double d=wallDepth(mode,inverted,y);
                    require(Math.abs(placed.edge(side,y)-(fromZero?d:1-d))<1E-9,"the bevel meets the slope across the whole shared face");
                }
                // Only the same size, lean and in-block position join.
                for(int other=0;other<3;other++)if(other!=mode)require(!placed.joinsDiagonal(side,facing,other,inverted),"different size never joins");
                require(!placed.joinsDiagonal(side,facing,mode,!inverted),"different lean never joins");
                require(!placed.joinsDiagonal(side,facing^2,mode,inverted),"glass anchored to the opposite face (near versus far) never joins");
            }
        }
        // Two diagonal corners continue each other only where their slopes meet.
        for(int mode=0;mode<3;mode++)for(int meta=0;meta<16;meta++)for(int end=0;end<2;end++){
            CornerGlassGeometry support=CornerGlassGeometry.diagonal(mode,meta);
            int edge=end==0?CornerGlassGeometry.first(meta&3):CornerGlassGeometry.second(meta&3),side=(edge+2)&3;
            CornerGlassGeometry.Neighbor[] around=empty();around[side]=CornerGlassGeometry.Neighbor.corner(support);
            CornerGlassGeometry placed=CornerGlassGeometry.diagonal(mode,CornerGlassGeometry.placementDiagonal(mode,side,.5,.5,around,-1,.5,.5));
            require(placed.joinsEdge(side,support),"clicked diagonal corner endpoint is continued");
            require(!placed.joinsEdge(side,CornerGlassGeometry.diagonal((mode+1)%3,meta)),"corners of another size do not join");
        }
    }
    public static void main(String[] args){
        double[] a=new double[3],b=new double[3];
        for(boolean slope:new boolean[]{false})for(int depth=0;depth<3;depth++)for(int turn=0;turn<4;turn++){
            CornerGlassGeometry geometry=new CornerGlassGeometry(turn,depth,slope);
            for(int row=0;row<=16;row++){
                double y=row/16D;
                geometry.project(.5,y,.39,a);geometry.project(.5,y,.40,b);
                require(Math.abs(a[0]-b[0])+Math.abs(a[2]-b[2])>1E-8,"frame cross sections must not fold together");
                for(double normal:new double[]{.375,.4375,.5,.5625,.625})for(double along:new double[]{0,.5,1}){
                    geometry.project(along,y,normal,a);
                    require(a[0]>=-1E-9&&a[0]<=1+1E-9&&a[2]>=-1E-9&&a[2]<=1+1E-9,"keep all frame and pane points inside the cell");
                }
            }
            CornerGlassGeometry.Neighbor[] none=empty();
            require(CornerGlassGeometry.placement(slope,-1,.2,.8,none,depth*4+turn,.5,5)==depth*4+turn,"matching corner above or below keeps its bevel");
        }
        int[] mirror={0,2,1};
        for(boolean slope:new boolean[]{false})for(int turn=0;turn<4;turn++)for(int depth=0;depth<3;depth++)for(int end=0;end<2;end++){
            CornerGlassGeometry support=new CornerGlassGeometry(turn,depth,slope);
            int supportEdge=end==0?CornerGlassGeometry.first(turn):CornerGlassGeometry.second(turn);
            int side=(supportEdge+2)&3;
            CornerGlassGeometry.Neighbor[] around=empty();around[side]=CornerGlassGeometry.Neighbor.corner(turn,depth,slope);
            int state=CornerGlassGeometry.placement(slope,side,.5,.5,around,-1,.5,.5);
            require(new CornerGlassGeometry(state&3,state/4,slope).joinsEdge(side,support),"clicked corner endpoint must align");
        }
        for(int side=0;side<4;side++)for(int depth=0;depth<3;depth++){
            boolean rotated=(side&1)==0;
            CornerGlassGeometry.Neighbor[] around=empty();around[side]=CornerGlassGeometry.Neighbor.straight(rotated,depth);
            int[] bends=new int[2];
            for(int towards=0;towards<2;towards++){
                // Stand on either side of the clicked pane's plane, beyond the new cell.
                double beyond=side==0?3:side==2?-2:side==1?-2:3;
                double px=rotated?(towards==0?.5-3:.5+3):beyond,pz=rotated?beyond:(towards==0?.5-3:.5+3);
                int state=CornerGlassGeometry.placement(false,side,.5,.5,around,-1,px,pz);
                CornerGlassGeometry placed=new CornerGlassGeometry(state&3,state/4,false);
                require(placed.joinsStraight(side,rotated,depth),"clicked straight pane must align at every depth side="+side+" depth="+depth);
                placed.project(0,.5,.5,a);placed.project(1,.5,.5,b);
                double mid=rotated?(a[0]+b[0])/2:(a[2]+b[2])/2,player=rotated?px:pz;
                require((mid-.5)*(player-.5)>=-1E-9,"bend towards the placing player side="+side+" depth="+depth+" towards="+towards);
                bends[towards]=state;
            }
            require(bends[0]!=bends[1],"either bend is reachable from a straight pane end");
        }
        for(int depth=0;depth<3;depth++){
            // A floor click beside a pane joins it and bends towards the clicked half.
            CornerGlassGeometry.Neighbor[] around=empty();around[3]=CornerGlassGeometry.Neighbor.straight(false,depth);
            for(double z:new double[]{.1,.9}){
                int state=CornerGlassGeometry.placement(false,-1,.5,z,around,-1,.5,.5);
                CornerGlassGeometry placed=new CornerGlassGeometry(state&3,state/4,false);
                require(placed.joinsStraight(3,false,depth),"floor click beside a pane joins it depth="+depth);
            }
            // Panes on two sides make one bevel that joins both.
            around[0]=CornerGlassGeometry.Neighbor.straight(true,mirror[depth]);
            require(CornerGlassGeometry.placement(false,-1,.9,.9,around,-1,.5,.5)==depth*4,"bevel joins both adjacent panes depth="+depth);
        }
        {
            // A distant placer must not outweigh a second adjacent pane.
            CornerGlassGeometry.Neighbor[] around=empty();
            around[3]=CornerGlassGeometry.Neighbor.straight(false,0);around[0]=CornerGlassGeometry.Neighbor.straight(true,0);
            require(CornerGlassGeometry.placement(false,3,0,.5,around,-1,-484,411)==0,"far placer keeps the two-pane bevel");
        }
        // Side clicks use the new cell: clicking the south face of a block places a north bevel.
        for(double x:new double[]{.1,.3,.7,.9}){
            int state=CornerGlassGeometry.placement(false,0,x,0,empty(),-1,x,3);
            CornerGlassGeometry placed=new CornerGlassGeometry(state&3,state/4,false);
            placed.project(0,.5,.5,a);placed.project(1,.5,.5,b);
            require(Math.min(a[2],b[2])<1E-9&&Math.abs((a[2]<1E-9?a[0]:b[0])-x)<.25,"clicked wall face keeps the bevel against that wall x="+x);
        }
        require((CornerGlassGeometry.placement(false,-1,.1,.1,empty(),-1,.5,.5)&3)==0,"northwest click");
        require((CornerGlassGeometry.placement(false,-1,.9,.9,empty(),-1,.5,.5)&3)==2,"southeast click");
        require(CornerGlassGeometry.placement(false,-1,.05,.05,empty(),-1,.5,.5)==4,"near click");
        require(CornerGlassGeometry.placement(false,-1,.42,.42,empty(),-1,.5,.5)==8,"far click");
        diagonal();
        System.out.println("corner-glass portable PASS checks="+checks);
    }
}
