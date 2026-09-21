package com.vandorlabs.ramp;

import com.vandorlabs.animation.DoorAnimation;
import com.vandorlabs.animation.AnimationFrames;
import com.vandorlabs.animation.ScreenBehavior;
import com.vandorlabs.render.ConnectedSquare;
import com.vandorlabs.render.GlassConnections;
import com.vandorlabs.render.DoorLeaf;
import com.vandorlabs.render.DoorLeafTransform;
import com.vandorlabs.render.ScreenSurface;
import com.vandorlabs.render.DoorPanelLayout;
import com.vandorlabs.render.CuboidMesh;
import com.vandorlabs.render.InputSurfaceLayout;
import com.vandorlabs.render.ScreenHousingMesh;
import java.util.HashSet;
import java.util.Set;

public final class ControllerPlatformTest {
    private static int assertions;
    private static void check(boolean value,String message) {
        assertions++; if (!value) throw new AssertionError(message);
    }
    private static void close(double a,double b,String message) { check(Math.abs(a-b)<1e-8,message); }
    private static Set<ControllerPlatform.Cell> cells(int width,int length) {
        Set<ControllerPlatform.Cell> result=new HashSet<>();
        for (int x=0;x<width;x++) for (int z=0;z<length;z++) result.add(new ControllerPlatform.Cell(x,0,z));
        return result;
    }
    public static void main(String[] args) {
        ControllerPlatform.Cell seed=new ControllerPlatform.Cell(0,0,0);
        Set<ControllerPlatform.Cell> platform=cells(4,3);
        platform.add(seed.offset(0,1,0)); platform.add(seed.offset(8,0,8));
        check(ControllerPlatform.discover(seed,platform::contains).size()==12,"plane and face adjacency");
        check(ControllerPlatform.discover(seed,cells(8,8)::contains).size()==64,"exact eight-by-eight footprint");
        check(ControllerPlatform.discover(seed,cells(17,8)::contains).equals(cells(8,8)),"oversized matching floor clipped without rejection");
        check(ControllerPlatform.discover(seed,cells(1,40)::contains).equals(cells(1,8)),"length capped independently of area");
        check(ControllerPlatform.discover(seed,cells(40,1)::contains).equals(cells(8,1)),"width capped independently of area");
        int[] reads={0};
        Set<ControllerPlatform.Cell> infinite=ControllerPlatform.discover(seed,c->{reads[0]++;return true;});
        check(infinite.size()==64 && reads[0]==64,"unbounded floor stops querying beyond footprint");
        check(infinite.equals(ControllerPlatform.discover(seed,c->true)),"centered seed clipping deterministic");
        check(infinite.stream().mapToInt(c->c.x).max().getAsInt()-infinite.stream().mapToInt(c->c.x).min().getAsInt()==7,"centered width at most eight");
        check(infinite.stream().mapToInt(c->c.z).max().getAsInt()-infinite.stream().mapToInt(c->c.z).min().getAsInt()==7,"centered length at most eight");
        platform.remove(seed.offset(1,0,0));
        // A painted point must keep its UV as the segment crosses cell boundaries,
        // for whole blocks, both slab halves, both travel signs and every tread.
        for (boolean top:new boolean[]{true,false}) for (boolean lift:new boolean[]{true,false})
        for (int segments:new int[]{2,8}) for (int step=0;step<segments;step++)
        for (double sourceY:new double[]{0,.125,.5,.75,1}) for (int tick=0;tick<=80;tick++) {
            double offset=ControllerPlatform.offset(2,step,3,segments,8,tick/80.0,top,lift);
            double worldY=40+sourceY+offset;
            int cellY=(int)Math.floor(worldY);
            close(ControllerPlatform.sideTextureV(cellY,worldY-cellY,40,offset),1-sourceY,"side texture moves with painted point");
            // Both representations of a world-cell boundary must sample the same UV.
            close(ControllerPlatform.sideTextureV(cellY,0,40,offset),
                    ControllerPlatform.sideTextureV(cellY-1,1,40,offset),"no UV seam between clipped cells");
        }
        check(!ControllerPlatform.discover(seed,platform::contains).contains(seed.offset(1,0,0)),"holes preserved");
        DoorAnimation door=new DoorAnimation(10);
        close(door.sample(false,100),0,"closed door initializes closed");
        close(door.sample(true,100),0,"door reversal starts continuously");
        close(door.sample(true,105),.5,"door easing midpoint");
        close(door.sample(false,105),.5,"mid-animation reversal is continuous");
        close(door.sample(false,110),.25,"reversal preserves speed and easing");
        close(door.sample(false,120),0,"door closes and clamps");
        check(new VerticalBounds(-64,384).contains(-64),"modern lower world bound included");
        check(!new VerticalBounds(-64,384).contains(320),"modern upper world bound excluded");
        for (RampGeometry.Direction direction:RampGeometry.Direction.values()) {
            java.util.List<RampGeometry.Box> boxes=RampGeometry.boxes(direction,40,40,0,1,
                    1,3,3,2,0,false,false);
            check(boxes.size()==2,"two ramp segments generated for "+direction);
            check(RampGeometry.segmentAt(direction,boxes.get(0),2,false)==0,"segment orientation for "+direction);
            check(RampGeometry.segmentAt(direction,boxes.get(1),2,false)==1,"second segment orientation for "+direction);
        }
        ConnectedSquare.Part connected=ConnectedSquare.find(8,(x,y)->x>=-1&&x<=0&&y>=0&&y<=1);
        check(connected.size==2&&connected.x==1&&connected.y==0,"isolated square location");
        check(ConnectedSquare.find(8,(x,y)->x>=0&&x<3&&y>=0&&y<2).isSingle(),"rectangle stays separate");
        check(ConnectedSquare.find(8,(x,y)->x>=0&&x<2&&y>=0&&y<2||x==2&&y==0).isSingle(),"attached extra stays separate");
        check(ConnectedSquare.find(8,(x,y)->x>=-4&&x<=4&&y>=-4&&y<=4).isSingle(),"oversized square stays separate");
        java.util.Set<String> glass=new java.util.HashSet<>();
        glass.add("0,1"); glass.add("-1,0");
        GlassConnections connections=GlassConnections.calculate((x,y)->glass.contains(x+","+y));
        check(!connections.top&&connections.bottom&&!connections.left&&connections.right,"glass exposed edges");
        check(connections.innerTopLeft,"glass missing diagonal creates inner corner");
        glass.add("-1,1");
        check(!GlassConnections.calculate((x,y)->glass.contains(x+","+y)).innerTopLeft,"glass diagonal fills inner corner");
        check(DoorLeaf.LEFT.legacyMetadata==1&&"right_leaf".equals(DoorLeaf.RIGHT.modelSuffix),"door leaf identity");
        DoorLeafTransform sliding=DoorLeafTransform.calculate(true,-15,0,0,0,.5);
        close(sliding.translateX,-15.0/32,"sliding leaf translation");
        DoorLeafTransform hinged=DoorLeafTransform.calculate(false,0,1,2,-90,.5);
        close(hinged.pivotX,1.0/16,"hinged leaf pivot"); close(hinged.pivotZ,2.0/16,"hinged leaf depth");
        close(hinged.angleDegrees,-45,"hinged leaf angle");
        check(AnimationFrames.frame(19,10,4)==1,"global animation frame");
        close(AnimationFrames.top(1,4),.25,"animation UV top");
        close(AnimationFrames.bottom(1,4),.5,"animation UV bottom");
        ScreenSurface.Quad surface=ScreenSurface.quad(ScreenSurface.Kind.DIAGONAL,false);
        close(surface.topLeft.x,.5,"screen surface inset"); close(surface.topLeft.y,14.85,"screen surface top");
        check(RampGeometry.firstOccupiedY(10,.5,-1.0,-.25)==9,"occupied range floor");
        check(RampGeometry.lastOccupiedY(10,1,-1.0,-.25)==10,"occupied range ceiling");
        close(RampGeometry.riderTarget(10,10.25,10,.5),10.25,"rider follows surface");
        check(Double.isNaN(RampGeometry.riderTarget(10,10.25,11,.15)),"distant entity is not a rider");
        DoorPanelLayout.Panel[] split=DoorPanelLayout.calculate(
                DoorPanelLayout.Kind.SLIDING_SPLIT,true,false,.5);
        check(split.length==2&&!split[0].swung,"split slider emits two panels");
        close(split[0].x,-3.5,"west sliding panel"); close(split[1].x,11.5,"east sliding panel");
        DoorPanelLayout.Panel hingedPanel=DoorPanelLayout.calculate(
                DoorPanelLayout.Kind.HINGED,true,true,.5)[0];
        check(hingedPanel.swung&&hingedPanel.usesWindow,"hinged panel traits");
        close(hingedPanel.angle,-45,"hinged panel motion"); close(hingedPanel.u0,16,"reversed glass paint");
        RampGeometry.Box cube=new RampGeometry.Box(0,.25,0,1,.75,1);
        for (CuboidMesh.Face face:CuboidMesh.Face.values())
            check(CuboidMesh.face(cube,face,.2,.8).length==4,"cuboid face vertices for "+face);
        close(CuboidMesh.face(cube,CuboidMesh.Face.NORTH,.2,.8)[0].v,.2,"side lower UV");
        InputSurfaceLayout.Mounted smallWall=InputSurfaceLayout.halfInput(
                false,false,1,true);
        close(smallWall.housing.x0,2.4,"small wall centered horizontally");
        close(smallWall.housing.y0,5.2,"small wall centered vertically");
        close(smallWall.surface.vertices[0].y,10.55,"small wall artwork inset");
        InputSurfaceLayout.Mounted keyboard=InputSurfaceLayout.halfInput(
                true,false,0,false);
        close(keyboard.housing.z0,8,"half keyboard depth");
        close(keyboard.surface.vertices[2].z,8.25,"keyboard artwork inset");
        InputSurfaceLayout.Mounted fullFloor=InputSurfaceLayout.fullInput(true,true);
        close(fullFloor.housing.y0,15,"upper full input mount");
        close(InputSurfaceLayout.halfConsoleRear().vertices[2].z,7.70,
                "half-console rear slope");
        ScreenHousingMesh consoleMesh=ScreenHousingMesh.console();
        check(consoleMesh.quads.length==2&&consoleMesh.triangles.length==2,
                "console housing mesh topology");
        close(consoleMesh.quads[0].vertices[0].z,7.5,"console housing wedge depth");
        ScreenHousingMesh diagonal=ScreenHousingMesh.diagonal(false);
        ScreenHousingMesh invertedDiagonal=ScreenHousingMesh.diagonal(true);
        check(diagonal.quads.length==3&&diagonal.triangles.length==2,
                "diagonal housing mesh topology");
        close(diagonal.quads[0].vertices[0].y,0,"floor diagonal starts low");
        close(invertedDiagonal.quads[0].vertices[0].y,16,"ceiling diagonal starts high");
        check(ScreenBehavior.effectiveMode(ScreenBehavior.ANIMATED,true,false)==ScreenBehavior.OFF,"unpowered redstone screen sleeps");
        check(ScreenBehavior.effectiveMode(ScreenBehavior.OFF,false,true)==ScreenBehavior.ANIMATED,"powered off screen wakes animated");
        check(ScreenBehavior.animationTicks(-3)==20&&ScreenBehavior.animationTicks(99)==5,"screen speed clamps");
        for (int length=1;length<=128;length++) for (int height=1;height<=16;height++)
        for (int segments:new int[]{2,8}) for (boolean top:new boolean[]{true,false})
        for (boolean lift:new boolean[]{true,false}) {
            double sign=top?-1:1;
            close(ControllerPlatform.offset(0,0,length,segments,height,0,top,lift),0,"closed offset");
            close(ControllerPlatform.offset(length-1,segments-1,length,segments,height,1,top,lift),sign*height,"exact far endpoint");
            close(ControllerPlatform.offset(0,0,length,segments,height,1,top,lift),lift?sign*height:0,"elevator translates hinge too");
            double last=0;
            for (int i=0;i<=20;i++) {
                double p=i/20.0;
                double value=ControllerPlatform.offset(length-1,segments-1,length,segments,height,p,top,lift);
                check(Math.abs(value)>=Math.abs(last)-1e-8,"monotone travel"); last=value;
                close(value,-ControllerPlatform.offset(length-1,segments-1,length,segments,height,p,!top,lift),"mirrored top/bottom");
            }
            int duration=ControllerPlatform.duration(length,height,false);
            check(ControllerPlatform.duration(length,height,true)==2*duration,"slow is half base speed");
            check(duration==10*Math.max(length,height),"fast is twice the old fast speed");
            check(ControllerPlatform.duration(length,height,true)==20*Math.max(length,height),"slow matches old fast speed");
            close(ControllerPlatform.pose(0,true,duration/2.0,duration),.5,"midpoint");
            close(ControllerPlatform.pose(.5,false,0,duration),.5,"reversal continuity");
            close(ControllerPlatform.pose(.5,false,duration/4.0,duration),.25,"reversal speed");
            close(ControllerPlatform.pose(.5,false,duration,duration),0,"closed clamp");
        }
        System.out.println("Controller geometry PASS ("+assertions+" assertions)");
    }
}
