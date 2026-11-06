package com.vandorlabs.screen;

import com.vandorlabs.render.InputSurfaceLayout;
import com.vandorlabs.render.ScreenSurface;

/** Standalone display pixels and authoritative ray projection; renderer and controls share these regions. */
public final class ScreenControlLayout {
    public static final int WIDTH=128,FULL_HEIGHT=128,HALF_HEIGHT=64,FULL_ROW_TOP=24,HALF_ROW_TOP=8,SLIDER_LABEL=11,SLIDER_LABEL_WIDTH=50,ROW_LEFT=6,ROW_RIGHT=122,ROW_HEIGHT=12,ROW_INK=10,SLIDER_LEFT=60,SLIDER_RIGHT=120,SLIDER_STEPS=4,BUTTON_LEFT=98,BUTTON_RIGHT=120;
    public static final class Sample {
        public final double column,line;
        private final int top;
        private Sample(double column,double line,int top){this.column=column;this.line=line;this.top=top;}
        public int row(int count){if(count<0||count>12)throw new IllegalArgumentException("Unsupported row capacity");int row=(int)Math.floor((line-top)/ROW_HEIGHT);return column>=ROW_LEFT&&column<=ROW_RIGHT&&row>=0&&row<count&&line-top-row*ROW_HEIGHT<ROW_INK?row:-1;}
        public int sliderStep(){return column<SLIDER_LEFT||column>=SLIDER_RIGHT?-1:(int)((column-SLIDER_LEFT)*SLIDER_STEPS/(SLIDER_RIGHT-SLIDER_LEFT));}
    }
    private static final Sample MISS=new Sample(-1,-1,24);
    public static final class Projection {
        public final ScreenSurface.Quad surface;
        public final int height,rowTop;
        private final int facing;
        private Projection(ScreenSurface.Quad surface,int facing,boolean half){this.surface=surface;this.facing=facing;height=half?HALF_HEIGHT:FULL_HEIGHT;rowTop=half?HALF_ROW_TOP:FULL_ROW_TOP;}
        public Sample sample(double sx,double sy,double sz,double ex,double ey,double ez){
            double x=sx-.5,y=sy-.5,z=sz-.5,lx=x,ly=y,lz=z;
            switch(facing){case 5:lx=z;lz=-x;break;case 3:lx=-x;lz=-z;break;case 4:lx=-z;lz=x;break;case 1:lx=-x;ly=-z;lz=-y;break;case 0:ly=-z;lz=y;break;default:break;}
            double ax=(lx+.5)*16,ay=(ly+.5)*16,az=(lz+.5)*16;
            x=ex-.5;y=ey-.5;z=ez-.5;lx=x;ly=y;lz=z;
            switch(facing){case 5:lx=z;lz=-x;break;case 3:lx=-x;lz=-z;break;case 4:lx=-z;lz=x;break;case 1:lx=-x;ly=-z;lz=-y;break;case 0:ly=-z;lz=y;break;default:break;}
            double dx=(lx+.5)*16-ax,dy=(ly+.5)*16-ay,dz=(lz+.5)*16-az,denom=surface.ny*dy+surface.nz*dz;if(denom>=-1e-8)return MISS;
            double t=(surface.ny*(surface.topRight.y-ay)+surface.nz*(surface.topRight.z-az))/denom;if(t<0||t>1)return MISS;
            double hx=ax+dx*t,hy=ay+dy*t,hz=az+dz*t;
            double column=(surface.topRight.x-hx)/(surface.topRight.x-surface.topLeft.x)*WIDTH,sySpan=surface.bottomRight.y-surface.topRight.y,szSpan=surface.bottomRight.z-surface.topRight.z;
            double line=((hy-surface.topRight.y)*sySpan+(hz-surface.topRight.z)*szSpan)/(sySpan*sySpan+szSpan*szSpan)*height;
            return new Sample(column,line,rowTop);
        }
    }
    public static Projection of(ScreenFamily family,ScreenFamily.State state,int slot,boolean small,int wall,boolean ceiling,int ceilingPosition,boolean chipDeck){
        ScreenRenderPose.requireState(family,state);if(!family.supportsSlot(slot)||wall< -1||wall>2||ceilingPosition< -1||ceilingPosition>2)throw new IllegalArgumentException("Invalid control surface pose");ScreenSurface.Quad quad;
        if(family.keyboard){boolean full=family==ScreenFamily.INPUT;InputSurfaceLayout.Mounted input=ceiling?InputSurfaceLayout.ceilingInput(full,small,ceilingPosition<0?(full?1:2):ceilingPosition):full?InputSurfaceLayout.fullInput(state.keyboard,state.upper,small):InputSurfaceLayout.halfInput(state.keyboard,state.upper,wall<0?(state.upper?2:0):wall,small);quad=ScreenSurface.input(input.surface);}
        else if(family==ScreenFamily.HALF_DIAGONAL)quad=ScreenSurface.halfDiagonal(state.upper);
        else if(family==ScreenFamily.CONSOLE)quad=slot==1?ScreenSurface.input(InputSurfaceLayout.halfConsoleFront()):ScreenSurface.quad(ScreenSurface.Kind.CONSOLE,false);
        else if(family==ScreenFamily.HALF_CONSOLE){quad=ScreenSurface.input(slot==1?InputSurfaceLayout.halfConsoleRear():InputSurfaceLayout.halfConsoleFront());if(slot==0&&chipDeck)quad=ScreenSurface.inset(quad,.40,0,0,0);}
        else quad=ScreenSurface.quad(family==ScreenFamily.DIAGONAL?ScreenSurface.Kind.DIAGONAL:ScreenSurface.Kind.FLAT,state.inverted);
        return new Projection(quad,state.facing,family.half(slot));
    }
    private ScreenControlLayout(){}
}
