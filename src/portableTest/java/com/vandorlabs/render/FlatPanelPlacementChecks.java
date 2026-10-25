package com.vandorlabs.render;

public final class FlatPanelPlacementChecks {
    private static void need(boolean value,String message){if(!value)throw new AssertionError(message);}
    public static void main(String[] args){
        float third=1F/3F,twoThirds=2F/3F;
        need(FlatPanelPlacement.depth(third)==0&&FlatPanelPlacement.depth(twoThirds)==0,"exact depth thresholds must retain central panel");
        need(FlatPanelPlacement.depth(Math.nextAfter(third,Double.NEGATIVE_INFINITY))==1,"lower click edge lost");
        need(FlatPanelPlacement.depth(Math.nextAfter(twoThirds,Double.POSITIVE_INFINITY))==2,"upper click edge lost");
        need(FlatPanelPlacement.near(0)==6&&FlatPanelPlacement.near(1)==0&&FlatPanelPlacement.near(2)==12&&FlatPanelPlacement.near(-1)==6,"saved depth mapping/fallback changed");
        int points=0;
        for(int facing=2;facing<6;facing++)for(int depth=0;depth<3;depth++){
            double near=FlatPanelPlacement.near(depth),mid=near/16+.125;
            for(double z:new double[]{-.01,0,.125,.375,.5,.625,.875,1,1.01})for(double x:new double[]{0,.25,.5,.75,1}){
                ControlMount.Point rotated=FlatPanelPlacement.rotate(new ControlMount.Point(x,.5,z),facing);
                need(Math.abs(FlatPanelPlacement.coordinate(facing,rotated.x,rotated.z)-z)<1E-12,"stable side coordinate changed under quarter-turn");
                need(FlatPanelPlacement.side(facing,near,rotated.x,rotated.z)==(z<mid?0:1),"partition boundary acquired wrong owner");
                points++;
            }
            ControlMount.Point front=FlatPanelPlacement.rotate(new ControlMount.Point(.5,.5,.125),facing);
            ControlMount.Point center=FlatPanelPlacement.rotate(new ControlMount.Point(.5,.5,.5),facing);
            ControlMount.Point back=FlatPanelPlacement.rotate(new ControlMount.Point(.5,.5,.875),facing);
            need(FlatPanelPlacement.depth(facing,(float)front.x,(float)front.z)==1&&FlatPanelPlacement.depth(facing,(float)center.x,(float)center.z)==0&&FlatPanelPlacement.depth(facing,(float)back.x,(float)back.z)==2,"placement and geometry disagree on panel normal");
        }
        System.out.println("Flat panel placement PASS "+points+" rotated side/half-open boundary samples, exact float depth thresholds and legacy depth IDs");
    }
}
