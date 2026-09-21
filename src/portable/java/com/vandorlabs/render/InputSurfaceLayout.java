package com.vandorlabs.render;

/** Renderer-neutral housing and artwork quads for programmable input blocks. */
public final class InputSurfaceLayout {
    public static final double SMALL_SCALE=.7;
    public static final class Box {
        public final double x0,y0,z0,x1,y1,z1;
        private Box(double x0,double y0,double z0,double x1,double y1,double z1) {
            this.x0=x0; this.y0=y0; this.z0=z0; this.x1=x1; this.y1=y1; this.z1=z1;
        }
    }
    public static final class Vertex {
        public final double x,y,z,u,v;
        private Vertex(double x,double y,double z,double u,double v) {
            this.x=x; this.y=y; this.z=z; this.u=u; this.v=v;
        }
    }
    public static final class Quad {
        public final Vertex[] vertices;
        private Quad(Vertex... vertices) { this.vertices=vertices; }
    }
    public static final class Mounted {
        public final Box housing;
        public final Quad surface;
        private Mounted(Box housing,Quad surface) { this.housing=housing; this.surface=surface; }
    }
    private static final Mounted FULL_WALL=new Mounted(new Box(0,0,15,16,16,16),
            vertical(.25,15.75,.25,15.75,14.98));
    private static final Mounted FULL_FLOOR_LOWER=new Mounted(new Box(0,7,0,16,8,16),
            horizontal(.25,15.75,.25,15.75,8.02));
    private static final Mounted FULL_FLOOR_UPPER=new Mounted(new Box(0,15,0,16,16,16),
            horizontal(.25,15.75,.25,15.75,16.02));
    private static final Quad HALF_CONSOLE_FRONT=horizontal(.25,15.75,.25,7.25,1.02);
    private static final Quad HALF_CONSOLE_REAR=new Quad(
            new Vertex(.25,7.75,15.70,0,0),new Vertex(15.75,7.75,15.70,1,0),
            new Vertex(15.75,1.25,7.70,1,1),new Vertex(.25,1.25,7.70,0,1));
    private static final Mounted[][][][] HALF_INPUTS=buildHalfInputs();
    private InputSurfaceLayout() { }

    public static Mounted halfInput(boolean keyboard,boolean upper,int wallPosition,boolean small) {
        return HALF_INPUTS[keyboard?1:0][upper?1:0]
                [Math.max(0,Math.min(2,wallPosition))][small?1:0];
    }

    private static Mounted createHalfInput(boolean keyboard,boolean upper,int wallPosition,
            boolean small) {
        double scale=small?SMALL_SCALE:1;
        double x0=(16-16*scale)/2,x1=16-x0;
        if (keyboard) {
            double y0=upper?15:7,z0=16-8*scale;
            return new Mounted(new Box(x0,y0,z0,x1,y0+1,16),
                    horizontal(x0+.25,x1-.25,z0+.25,15.75,y0+1.02));
        }
        double height=8*scale;
        double y0=wallPosition==0?0:wallPosition==2?16-height:(16-height)/2;
        return new Mounted(new Box(x0,y0,15,x1,y0+height,16),
                vertical(x0+.25,x1-.25,y0+.25,y0+height-.25,14.98));
    }

    private static Mounted[][][][] buildHalfInputs() {
        Mounted[][][][] result=new Mounted[2][2][3][2];
        for (int keyboard=0;keyboard<2;keyboard++) for (int upper=0;upper<2;upper++)
            for (int wall=0;wall<3;wall++) for (int small=0;small<2;small++)
                result[keyboard][upper][wall][small]=createHalfInput(
                        keyboard!=0,upper!=0,wall,small!=0);
        return result;
    }

    public static Mounted fullInput(boolean keyboard,boolean upper) {
        return keyboard?(upper?FULL_FLOOR_UPPER:FULL_FLOOR_LOWER):FULL_WALL;
    }

    public static Quad halfConsoleFront() { return HALF_CONSOLE_FRONT; }

    public static Quad halfConsoleRear() { return HALF_CONSOLE_REAR; }

    private static Quad horizontal(double x0,double x1,double z0,double z1,double y) {
        return new Quad(new Vertex(x0,y,z1,0,0),new Vertex(x1,y,z1,1,0),
                new Vertex(x1,y,z0,1,1),new Vertex(x0,y,z0,0,1));
    }

    private static Quad vertical(double x0,double x1,double y0,double y1,double z) {
        return new Quad(new Vertex(x0,y1,z,0,0),new Vertex(x1,y1,z,1,0),
                new Vertex(x1,y0,z,1,1),new Vertex(x0,y0,z,0,1));
    }
}
