package com.vandorlabs.render;

/** Junction regressions and independent polygon/collision occupancy checks. */
public final class FlatWallTopologyChecks {
    private static void need(boolean value,String message){if(!value)throw new AssertionError(message);}
    private static FlatWallTopology.Panel panel(int facing,double near){return new FlatWallTopology.Panel(true,true,true,true,facing,facing,near,near);}
    private static FlatWallTopology.Corner joined(FlatWallTopology.Panel owner,FlatWallTopology.Panel[] neighbors){return FlatWallTopology.corner(owner,direction->neighbors[direction]);}
    private static boolean polygon(double[][] points,double x,double z){boolean inside=false;for(int i=0,j=points.length-1;i<points.length;j=i++){
        double[] a=points[i],b=points[j];if((a[1]>z)!=(b[1]>z)&&x<(b[0]-a[0])*(z-a[1])/(b[1]-a[1])+a[0])inside=!inside;
    }return inside;}
    public static void main(String[] args){
        int cases=0;
        for(int facing=2;facing<6;facing++){
            int cw=FlatWallTopology.clockwise(facing),ccw=StairJoinPolicy.counterClockwise(facing),opposite=facing^1;
            FlatWallTopology.Panel owner=panel(facing,6);FlatWallTopology.Panel[] neighbors=new FlatWallTopology.Panel[6];
            need(joined(owner,neighbors)==null,"standalone wall acquired a corner");
            neighbors[facing]=panel(facing,6);need(joined(owner,neighbors)==null,"parallel neighbour acquired an arm");
            neighbors[facing]=panel(cw,6);FlatWallTopology.Corner c=joined(owner,neighbors);
            need(c.frontArm==6&&c.frontRight&&c.left()==6&&c.right()==16,"single right arm lost free tail");
            neighbors[opposite]=panel(ccw,0);c=joined(owner,neighbors);
            need(c.frontArm==6&&c.backArm==0&&c.left()==0&&c.right()==10,"two arms produced false cross");
            neighbors[ccw]=new FlatWallTopology.Panel(true,false,true,false,facing,facing,6,6);c=joined(owner,neighbors);
            need(c.straightLeft&&!c.straightRight&&c.left()==0&&c.right()==10,"full porthole must be a straight target");
            neighbors[cw]=panel(facing,6);c=joined(owner,neighbors);need(c.right()==16,"true cross lost right tail");
            neighbors[cw]=panel(facing,0);need(!joined(owner,neighbors).straightRight,"different depth joined");
            neighbors[cw]=panel(opposite,6);need(joined(owner,neighbors).straightRight,"opposite canonical orientation did not align");
            FlatWallTopology.Panel blocked=new FlatWallTopology.Panel(true,true,true,false,facing,facing,6,6);
            need(joined(blocked,neighbors)==null,"disabled porthole joined");
            FlatWallTopology.Panel full=new FlatWallTopology.Panel(true,false,true,true,facing,facing,6,6);
            need(joined(full,neighbors)==null,"full-depth block formed an arm");
            for(int near:new int[]{0,6,12})for(int front=0;front<7;front++)for(int back=0;back<7;back++)for(int tails=0;tails<4;tails++){
                owner=panel(facing,near);neighbors=new FlatWallTopology.Panel[6];
                neighbors[facing]=front==0?null:panel(front<=3?cw:ccw,new int[]{0,6,12}[(front-1)%3]);
                neighbors[opposite]=back==0?null:panel(back<=3?cw:ccw,new int[]{0,6,12}[(back-1)%3]);
                if((tails&1)!=0)neighbors[ccw]=panel(facing,near);if((tails&2)!=0)neighbors[cw]=panel(facing,near);
                c=joined(owner,neighbors);double[][] outline=FlatWallTopology.outline(near,c),boxes=FlatWallTopology.boxes(near,c);
                for(int x=0;x<16;x++)for(int z=0;z<16;z++){
                    boolean solid=false;for(double[] box:boxes)if(x+.5>=box[0]&&x+.5<box[2]&&z+.5>=box[1]&&z+.5<box[3])solid=true;
                    need(polygon(outline,x+.5,z+.5)==solid,"emitted outline/collision occupancy differs at "+facing+":"+near+":"+front+":"+back+":"+tails+":"+x+":"+z);
                }
                cases++;
            }
        }
        System.out.println("Flat wall topology PASS "+cases+" junctions: no false crosses, full-depth straight targets, orientation/depth/disabled boundaries and polygon/collision occupancy");
    }
}
