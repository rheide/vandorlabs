package com.vandorlabs.render;

/** Exact six authored numeric-pad faces. Model coordinates and artwork coordinates stay in pixels. */
public final class DoorControlPadMesh {
    public interface UV { float u(double pixels);float v(double pixels); }
    public interface Sink { void quad(float x0,float y0,float z0,float x1,float y1,float z1,float x2,float y2,float z2,float x3,float y3,float z3,float u0,float v0,float u1,float v1,float u2,float v2,float u3,float v3); }
    private DoorControlPadMesh(){}
    public static void emit(boolean sliding,boolean farEdge,SpaceDoorControlPanel.Side side,UV buttons,UV wall,Sink sink){
        if(side==SpaceDoorControlPanel.Side.NONE)return;
        float x0=(float)SpaceDoorControlPanel.x0(side),x1=(float)SpaceDoorControlPanel.x1(side);
        float y0=(float)SpaceDoorControlPanel.Y0,y1=(float)SpaceDoorControlPanel.Y1;
        float z0=(float)SpaceDoorControlPanel.z0(sliding,farEdge),
                z1=(float)SpaceDoorControlPanel.z1(sliding,farEdge);
        float u0=buttons.u(0),u1=buttons.u(16);
        float v0=buttons.v(0),v1=buttons.v(16);
        // Match the Programmable Console's deck-side housing: U follows
        // depth and V follows the local height, using the native atlas pixels.
        // Keep UVs in the original sprite even when the far-edge pad's local
        // model coordinates extend beyond 16 before the placement offset.
        double textureZ0=SpaceDoorControlPanel.z0(sliding),
                textureZ1=SpaceDoorControlPanel.z1(sliding);
        float sideU0=wall.u(textureZ0),sideU1=wall.u(textureZ1);
        float sideV0=wall.v(32-y1),sideV1=wall.v(32-y0);
        float topV0=wall.v(textureZ0),topV1=wall.v(textureZ1);
        float wallX0=wall.u(x0),wallX1=wall.u(x1);
        if (side==SpaceDoorControlPanel.Side.LEFT) {
            sink.quad(x1,y1,z1,x1,y0,z1,x1,y0,z0,x1,y1,z0,
                    u0,v0,u0,v1,u1,v1,u1,v0);
            sink.quad(x0,y1,z0,x0,y0,z0,x0,y0,z1,x0,y1,z1,
                    sideU0,sideV0,sideU0,sideV1,sideU1,sideV1,sideU1,sideV0);
        } else {
            sink.quad(x0,y1,z0,x0,y0,z0,x0,y0,z1,x0,y1,z1,
                    u0,v0,u0,v1,u1,v1,u1,v0);
            sink.quad(x1,y1,z1,x1,y0,z1,x1,y0,z0,x1,y1,z0,
                    sideU1,sideV0,sideU1,sideV1,sideU0,sideV1,sideU0,sideV0);
        }
        sink.quad(x0,y1,z0,x0,y1,z1,x1,y1,z1,x1,y1,z0,
                wallX0,topV0,wallX0,topV1,wallX1,topV1,wallX1,topV0);
        sink.quad(x0,y0,z1,x0,y0,z0,x1,y0,z0,x1,y0,z1,
                wallX0,topV1,wallX0,topV0,wallX1,topV0,wallX1,topV1);
        sink.quad(x0,y1,z1,x0,y0,z1,x1,y0,z1,x1,y1,z1,
                wallX0,sideV0,wallX0,sideV1,wallX1,sideV1,wallX1,sideV0);
        sink.quad(x1,y1,z0,x1,y0,z0,x0,y0,z0,x0,y1,z0,
                wallX1,sideV0,wallX1,sideV1,wallX0,sideV1,wallX0,sideV0);
    }
}
