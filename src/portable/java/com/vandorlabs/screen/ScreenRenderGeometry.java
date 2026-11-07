package com.vandorlabs.screen;

import com.vandorlabs.render.*;
import java.util.*;

/** Immutable role assembly from the released renderer; artwork selection and joined rectangles are separate. */
public final class ScreenRenderGeometry {
    public static final class Body {
        public final ScreenHousingMesh mesh;public final boolean ceiling,applyLayout,basePose;
        private Body(ScreenHousingMesh mesh,boolean ceiling,boolean applyLayout){this(mesh,ceiling,applyLayout,false);}
        private Body(ScreenHousingMesh mesh,boolean ceiling,boolean applyLayout,boolean basePose){this.mesh=mesh;this.ceiling=ceiling;this.applyLayout=applyLayout;this.basePose=basePose;}
    }
    public static final class Surface {
        public final ScreenSurface.Quad quad;public final int slot;
        public final boolean inputArtwork,reverseU,frame,frameInput,ceiling,split;
        private Surface(ScreenSurface.Quad quad,int slot,boolean inputArtwork,boolean reverseU,boolean frame,boolean frameInput,boolean ceiling,boolean split){this.quad=quad;this.slot=slot;this.inputArtwork=inputArtwork;this.reverseU=reverseU;this.frame=frame;this.frameInput=frameInput;this.ceiling=ceiling;this.split=split;}
    }
    public static double deckEnd(double first,double last){return last+.40*(first-last);}
    public static ScreenSurface.Quad chipDeck(){return ScreenSurface.inset(ScreenSurface.input(InputSurfaceLayout.halfConsoleFront()),.40,0,0,0);}
    public final List<Body> bodies;public final List<Surface> surfaces;
    private ScreenRenderGeometry(List<Body> bodies,List<Surface> surfaces){this.bodies=Collections.unmodifiableList(bodies);this.surfaces=Collections.unmodifiableList(surfaces);}
    public static ScreenRenderGeometry of(ScreenFamily family,ScreenFamily.State state,boolean small,int wall,boolean ceiling,int ceilingPosition){
        return of(family,state,small,wall,ceiling,ceilingPosition,false);
    }
    public static ScreenRenderGeometry of(ScreenFamily family,ScreenFamily.State state,boolean small,int wall,boolean ceiling,int ceilingPosition,boolean chip){
        ScreenRenderPose.requireState(family,state);
        if(wall < -1||wall>2||ceilingPosition < -1||ceilingPosition>2)throw new IllegalArgumentException("Invalid Screen render geometry");
        List<Body> bodies=new ArrayList<>(2);List<Surface> surfaces=new ArrayList<>(2);
        if(family==ScreenFamily.SCREEN){bodies.add(new Body(ScreenHousingMesh.box(0,0,0,16,16,16),false,true));surfaces.add(new Surface(ScreenSurface.quad(ScreenSurface.Kind.FLAT,false),0,false,false,true,false,false,true));}
        else if(family==ScreenFamily.CONSOLE||family==ScreenFamily.HALF_CONSOLE){
            bodies.add(new Body(ScreenHousingMesh.box(0,0,0,16,1,16),false,true));bodies.add(new Body(family==ScreenFamily.CONSOLE?ScreenHousingMesh.console():ScreenHousingMesh.halfConsole(),false,true));
            if(family==ScreenFamily.CONSOLE){surfaces.add(new Surface((chip?chipDeck():ScreenSurface.input(InputSurfaceLayout.halfConsoleFront())),1,true,true,false,true,false,false));surfaces.add(new Surface(ScreenSurface.quad(ScreenSurface.Kind.CONSOLE,false),0,false,false,true,false,false,true));}
            else {surfaces.add(new Surface((chip?chipDeck():ScreenSurface.input(InputSurfaceLayout.halfConsoleFront())),0,true,true,false,true,false,false));surfaces.add(new Surface(ScreenSurface.input(InputSurfaceLayout.halfConsoleRear()),1,true,true,true,true,false,false));}
        }else if(family==ScreenFamily.INPUT||family==ScreenFamily.HALF_INPUT){
            boolean full=family==ScreenFamily.INPUT;InputSurfaceLayout.Mounted layout=ceiling?InputSurfaceLayout.ceilingInput(full,small,ceilingPosition<0?(full?1:2):ceilingPosition):full?InputSurfaceLayout.fullInput(state.keyboard,state.upper,small):InputSurfaceLayout.halfInput(state.keyboard,state.upper,wall<0?(state.upper?2:0):wall,small);InputSurfaceLayout.Box b=layout.housing;
            bodies.add(new Body(ScreenHousingMesh.box(b.x0,b.y0,b.z0,b.x1,b.y1,b.z1),false,true));surfaces.add(new Surface(ScreenSurface.input(layout.surface),0,!full,true,true,true,false,full));
        }else if(family==ScreenFamily.DIAGONAL){bodies.add(new Body(ScreenHousingMesh.diagonal(state.inverted),false,false));surfaces.add(new Surface(ScreenSurface.quad(ScreenSurface.Kind.DIAGONAL,state.inverted),0,false,false,true,false,false,true));}
        else {if(chip)bodies.add(new Body(ScreenHousingMesh.box(0,state.upper?15:0,0,16,state.upper?16:1,8),false,true,true));bodies.add(new Body(ScreenHousingMesh.diagonal(false),state.upper,true));surfaces.add(new Surface(ScreenSurface.quad(ScreenSurface.Kind.DIAGONAL,false),0,true,false,true,false,state.upper,false));}
        return new ScreenRenderGeometry(bodies,surfaces);
    }
}
