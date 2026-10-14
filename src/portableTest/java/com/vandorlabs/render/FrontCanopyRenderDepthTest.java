package com.vandorlabs.render;

public final class FrontCanopyRenderDepthTest {
    private static void check(boolean yes,String message){if(!yes)throw new AssertionError(message);}
    public static void main(String[] args){
        for(double depth:new double[]{0,1D/16,1,3,5})for(boolean anchored:new boolean[]{false,true}){
            double extent=Math.max(1D/64,depth),rear=anchored?0:extent,front=anchored?extent:0;
            double shifted=FrontCanopyRenderDepth.coordinate(rear,depth,anchored);
            check(Math.abs(shifted-rear)>=1D/1024,"rear must not remain coplanar with the adjacent wall cap");
            check(Math.abs(FrontCanopyRenderDepth.coordinate(front,depth,anchored)-front)<1e-12,"front joint remains exact");
            check(Math.abs(shifted-rear)<=FrontCanopyRenderDepth.REAR_CLEARANCE+1e-12,"clearance is bounded independently of canopy depth");
            double previous=-1;
            for(int i=0;i<=100;i++){
                double source=extent*i/100,rendered=FrontCanopyRenderDepth.coordinate(source,depth,anchored);
                check(rendered>=0&&rendered<=extent,"render shell stays inside physical depth");
                check(rendered>previous,"zero-depth frames retain ordered nonzero thickness");previous=rendered;
                double duplicate=FrontCanopyRenderDepth.coordinate(source,depth,anchored);
                check(rendered==duplicate,"shared vertices receive identical clearance");
            }
            // A straight connecting edge remains straight; no kink is introduced
            // between the unchanged front and displaced rear at a wall junction.
            double midpoint=FrontCanopyRenderDepth.coordinate(extent/2,depth,anchored);
            check(Math.abs(midpoint-(FrontCanopyRenderDepth.coordinate(0,depth,anchored)+FrontCanopyRenderDepth.coordinate(extent,depth,anchored))/2)<1e-12,"coplanar adjoining faces remain planar");
        }
        System.out.println("Front canopy render depth PASS rear-wall separation front-joint exact shared-seams affine-planarity zero-depth shell");
    }
}
