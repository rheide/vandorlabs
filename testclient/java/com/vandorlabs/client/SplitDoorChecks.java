package com.vandorlabs.client;

import com.vandorlabs.render.SplitDoorPanel;
import com.vandorlabs.tiles.*;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.vertex.VertexFormat;
import net.minecraft.nbt.NBTTagCompound;

/** Inspect real cut vertices, source UV coverage and full-open aperture clearance. */
final class SplitDoorChecks {
    static void run() {
        for(boolean vertical:new boolean[]{false,true}) {
            StaticSurfaceMesh.Capture capture=StaticSurfaceMesh.capture();
            for(float[] p:new float[][]{{0,0},{1,0},{1,2},{0,2}})
                capture.pos(p[0],p[1],9F/16).color(255,255,255,255).tex(p[0],p[1]/2).normal(0,0,1).endVertex();
            StaticSurfaceMesh[] panels=capture.finish().splitPanels(vertical,true);
            double area=0;int caps=0;
            for(int panel=0;panel<2;panel++) {
                VertexFormat format=BlockSurfaceFormat.get();BufferBuilder buffer=new BufferBuilder(4096);buffer.begin(7,format);
                panels[panel].draw(buffer,192,80);buffer.finishDrawing();int stride=format.getNextOffset(),uv=format.getUvOffsetById(0);
                float[][] points=new float[buffer.getVertexCount()][3];
                for(int v=0;v<points.length;v++) {
                    int offset=v*stride;float x=buffer.getByteBuffer().getFloat(offset),y=buffer.getByteBuffer().getFloat(offset+4),z=buffer.getByteBuffer().getFloat(offset+8);
                    points[v]=new float[]{x,y,z};
                    require(Math.abs(buffer.getByteBuffer().getFloat(offset+uv)-x)<1e-6 && Math.abs(buffer.getByteBuffer().getFloat(offset+uv+4)-y/2)<1e-6,"UV interpolation");
                    require(SplitDoorPanel.distance(vertical,panel,1,x,y)>=-1e-6,"cut crosses center");
                    double mx=x+SplitDoorPanel.shiftX(vertical,panel,1),my=y+SplitDoorPanel.shiftY(vertical,panel,1);
                    require(vertical?my<=1e-6 || my>=2-1e-6:mx<=1e-6 || mx>=1-1e-6,"open panel blocks aperture");
                }
                for(int v=0;v<points.length;v+=4) {
                    boolean front=true;for(int i=0;i<4;i++)front&=Math.abs(points[v+i][2]-9F/16)<1e-6;
                    if(front)area+=triangle(points[v],points[v+1],points[v+2])+triangle(points[v],points[v+2],points[v+3]);
                    else caps++;
                }
            }
            require(Math.abs(area-2)<1e-6 && caps==2,"closed coverage and two sealed cut edges");
        }
        StaticSurfaceMesh.Capture single=StaticSurfaceMesh.capture();
        for(float[] v:new float[][]{{0,0},{1,0},{1,2},{0,2}})
            single.pos(v[0],v[1],9F/16).color(255,255,255,255).tex(v[0],v[1]/2).normal(0,0,1).endVertex();
        StaticSurfaceMesh[] xPanels=single.finish().xPanels(0,true,1);
        double xArea=0;
        for(int panel=0;panel<4;panel++) {
            VertexFormat format=BlockSurfaceFormat.get();BufferBuilder buffer=new BufferBuilder(4096);buffer.begin(7,format);
            xPanels[panel].draw(buffer,192,80);buffer.finishDrawing();int stride=format.getNextOffset();
            float[][] points=new float[buffer.getVertexCount()][3];
            for(int v=0;v<points.length;v++) {
                int offset=v*stride;float x=buffer.getByteBuffer().getFloat(offset),y=buffer.getByteBuffer().getFloat(offset+4),z=buffer.getByteBuffer().getFloat(offset+8);
                points[v]=new float[]{x,y,z};
                double mx=x+.5*com.vandorlabs.render.XDoorPanel.shiftX(panel,1),my=y+com.vandorlabs.render.XDoorPanel.shiftY(panel,1);
                require(mx<=1e-6 || mx>=1-1e-6 || my<=1e-6 || my>=2-1e-6,"single X panel remains in aperture");
            }
            for(int v=0;v<points.length;v+=4) {
                boolean front=true;for(int i=0;i<4;i++)front&=Math.abs(points[v+i][2]-9F/16)<1e-6;
                if(front)xArea+=triangle(points[v],points[v+1],points[v+2])+triangle(points[v],points[v+2],points[v+3]);
            }
        }
        require(Math.abs(xArea-2)<1e-6,"single X closed coverage");
        for(int mode=3;mode<=7;mode++)for(boolean large:new boolean[]{false,true}) {
            TileEntitySpaceDoor door=large?new TileEntityLargeProgrammableDoor():new TileEntitySpaceDoor();
            door.configure(2,1,true,mode,false,true);
            TileEntitySpaceDoor copy=large?new TileEntityLargeProgrammableDoor():new TileEntitySpaceDoor();
            copy.applyItemSettings(door.itemSettings());require(copy.getSlideDirection()==mode,"picked mode");
            copy.readFromNBT(door.writeToNBT(new NBTTagCompound()));require(copy.getSlideDirection()==mode,"saved mode");
            if(mode==4 || mode==5)for(boolean right:new boolean[]{false,true}) {
                require(door.horizontalTravel(false,right)==(mode==4?-1:1)*(large?2:1),"whole single/large travel");
                require(door.horizontalTravel(true,right)==(mode==4?-2:2),"whole paired travel");
            }
        }
        System.out.println("PASS: horizontal/vertical split UV coverage, cut caps and aperture clearance; single/paired/large whole travel and NBT/item settings");
    }
    private static double triangle(float[] a,float[] b,float[] c){return Math.abs((b[0]-a[0])*(c[1]-a[1])-(b[1]-a[1])*(c[0]-a[0]))/2;}
    private static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
}
