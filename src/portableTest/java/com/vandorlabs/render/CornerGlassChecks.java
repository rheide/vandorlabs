package com.vandorlabs.render;

/** Corner placement and non-folding checks shared by both platform renderers. */
public final class CornerGlassChecks {
    private static int checks;
    private static void require(boolean value,String reason){checks++;if(!value)throw new AssertionError(reason);}
    public static void main(String[] args){
        double[] a=new double[3],b=new double[3];
        for(boolean slope:new boolean[]{false,true})for(int depth=0;depth<3;depth++)for(int turn=0;turn<4;turn++){
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
            require(CornerGlassGeometry.placement(-1,.2,.8,2,turn,depth,false)==depth*4+turn,"stacked corners retain orientation and depth");
        }
        for(int side=0;side<4;side++)for(int depth=0;depth<3;depth++)for(boolean rotated:new boolean[]{false,true}){
            if(rotated!=((side&1)==0))continue;
            for(double hit:new double[]{.1,.9}){
                int state=CornerGlassGeometry.placement(side,side%2==0?hit:.5,side%2==0?.5:hit,1,0,depth,rotated);
                require(new CornerGlassGeometry(state&3,state/4,false).joinsStraight(side,rotated,depth),"clicked straight pane must align at all depths");
            }
        }
        for(int turn=0;turn<4;turn++)for(int depth=0;depth<3;depth++)for(int end=0;end<2;end++){
            CornerGlassGeometry support=new CornerGlassGeometry(turn,depth,false);
            int supportEdge=end==0?CornerGlassGeometry.first(turn):CornerGlassGeometry.second(turn);
            int side=(supportEdge+2)&3;
            int state=CornerGlassGeometry.placement(side,.5,.5,2,turn,depth,false);
            require(new CornerGlassGeometry(state&3,state/4,false).joinsEdge(side,support),"clicked corner endpoint must align");
        }
        require((CornerGlassGeometry.placement(-1,.1,.1,0,0,0,false)&3)==0,"northwest click");
        require((CornerGlassGeometry.placement(-1,.9,.9,0,0,0,false)&3)==2,"southeast click");
        System.out.println("corner-glass portable PASS checks="+checks);
    }
}
