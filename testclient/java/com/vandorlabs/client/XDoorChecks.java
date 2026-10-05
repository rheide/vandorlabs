package com.vandorlabs.client;

import com.vandorlabs.render.XDoorPanel;
import com.vandorlabs.tiles.*;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.vertex.VertexFormat;
import net.minecraft.util.EnumFacing;
import java.util.*;

/** Real mesh cuts, atlas UV interpolation, closed coverage and four motion directions. */
final class XDoorChecks {
    static void run() {
        double[] areas=new double[4];int vertices=0;
        for(int hand=0;hand<2;hand++) {
            StaticSurfaceMesh.Capture capture=StaticSurfaceMesh.capture();
            for(float[] p:new float[][]{{0,0},{1,0},{1,2},{0,2}})
                capture.pos(p[0],p[1],9F/16).color(255,255,255,255).tex((p[0]+hand)/2,1-p[1]/2).normal(0,0,1).endVertex();
            StaticSurfaceMesh[] meshes=capture.finish().xPanels(hand,true);
            for(int panel=0;panel<4;panel++) {
                VertexFormat format=BlockSurfaceFormat.get();BufferBuilder buffer=new BufferBuilder(4096);buffer.begin(7,format);
                meshes[panel].draw(buffer,192,80);buffer.finishDrawing();int stride=format.getNextOffset(),uv=format.getUvOffsetById(0);
                float[][] points=new float[buffer.getVertexCount()][3];
                for(int v=0;v<points.length;v++) {
                    int offset=v*stride;float x=buffer.getByteBuffer().getFloat(offset),y=buffer.getByteBuffer().getFloat(offset+4),z=buffer.getByteBuffer().getFloat(offset+8);
                    points[v]=new float[]{x,y,z};
                    require(Math.abs(buffer.getByteBuffer().getFloat(offset+uv)-(x+hand)/2)<1e-6,"cut changed U");
                    require(Math.abs(buffer.getByteBuffer().getFloat(offset+uv+4)-(1-y/2))<1e-6,"cut changed V");
                    for(int plane=0;plane<2;plane++)require(XDoorPanel.distance(panel,plane,x+hand,y)>-1e-6,"vertex crossed diagonal");
                    double movedX=x+hand+XDoorPanel.shiftX(panel,1),movedY=y+XDoorPanel.shiftY(panel,1);
                    require(movedX<=1e-6 || movedX>=2-1e-6 || movedY<=1e-6 || movedY>=2-1e-6,"open panel remains inside aperture");
                    vertices++;
                }
                for(int v=0;v<points.length;v+=4) {
                    boolean front=true;for(int i=0;i<4;i++)front&=Math.abs(points[v+i][2]-9F/16)<1e-6;
                    if(front)areas[panel]+=triangle(points[v],points[v+1],points[v+2])+triangle(points[v],points[v+2],points[v+3]);
                }
                for(EnumFacing face:EnumFacing.Plane.HORIZONTAL)for(double progress:new double[]{0,.25,.5,1}) {
                    BufferBuilder batch=new BufferBuilder(4096);batch.begin(7,net.minecraft.client.renderer.vertex.DefaultVertexFormats.BLOCK);
                    meshes[panel].batchMesh().draw(batch,face,0,0,0,.125,0x00C00050,1.5*XDoorPanel.shiftX(panel,progress),1.5*XDoorPanel.shiftY(panel,progress),0,0,0,1.5,1.5*hand);
                    batch.finishDrawing();require(batch.getVertexCount()==points.length,"batch changed tessellation");
                    for(int v=0;v<points.length;v++) {
                        double x=1.5*(points[v][0]+hand+XDoorPanel.shiftX(panel,progress)),y=1.5*(points[v][1]+XDoorPanel.shiftY(panel,progress)),z=points[v][2]+.125;
                        double wx=face==EnumFacing.NORTH?1-x:face==EnumFacing.EAST?z:face==EnumFacing.WEST?1-z:x;
                        double wz=face==EnumFacing.NORTH?1-z:face==EnumFacing.EAST?1-x:face==EnumFacing.WEST?x:z;
                        require(Math.abs(batch.getByteBuffer().getFloat(v*28)-wx)<1e-6 && Math.abs(batch.getByteBuffer().getFloat(v*28+4)-y)<1e-6 && Math.abs(batch.getByteBuffer().getFloat(v*28+8)-wz)<1e-6,"batch/fallback pose mismatch");
                    }
                }
            }
        }
        for(double area:areas)require(Math.abs(area-1)<1e-6,"closed panels overlap or leave a hole");
        replacementMaterials();
        TileEntityLargeProgrammableDoor large=new TileEntityLargeProgrammableDoor();large.configure(23,2,true,3,false,true);
        TileEntityLargeProgrammableDoor copied=new TileEntityLargeProgrammableDoor();copied.applyItemSettings(large.itemSettings());require(copied.isXSplit(),"picked X mode lost");
        net.minecraft.nbt.NBTTagCompound saved=large.writeToNBT(new net.minecraft.nbt.NBTTagCompound());copied.readFromNBT(saved);require(copied.isXSplit(),"saved X mode lost");
        TileEntitySpaceDoor regular=new TileEntitySpaceDoor();regular.configure(2,1,true,3,false,true);require(regular.getSlideDirection()==0,"X mode accepted by regular door");
        System.out.println("PASS: X panels close without gaps/overlap, UVs and diagonal caps, four travel directions, "+vertices+" vertices, four-facing batch/fallback poses and saved/picked large-only motion (no GL)");
    }
    private static void replacementMaterials() {
        net.minecraft.client.renderer.texture.TextureAtlasSprite sprite=new net.minecraft.client.renderer.texture.TextureAtlasSprite("x_panel_material"){};
        sprite.setIconWidth(16);sprite.setIconHeight(16);sprite.initSprite(64,64,16,16,false);
        int[] data=new int[28];float[][] p={{0,0},{1,0},{1,2},{0,2}};
        for(int v=0;v<4;v++) {
            data[v*7]=Float.floatToRawIntBits(p[v][0]);data[v*7+1]=Float.floatToRawIntBits(p[v][1]);data[v*7+2]=Float.floatToRawIntBits(9F/16);
            data[v*7+3]=-1;data[v*7+4]=Float.floatToRawIntBits(sprite.getInterpolatedU(p[v][0]*16));data[v*7+5]=Float.floatToRawIntBits(sprite.getInterpolatedV(p[v][1]*8));
        }
        net.minecraft.client.renderer.block.model.BakedQuad quad=new net.minecraft.client.renderer.block.model.BakedQuad(data,-1,EnumFacing.SOUTH,sprite,false,net.minecraft.client.renderer.vertex.DefaultVertexFormats.ITEM);
        Map<EnumFacing,List<net.minecraft.client.renderer.block.model.BakedQuad>> faces=new EnumMap<>(EnumFacing.class);
        for(EnumFacing face:EnumFacing.values())faces.put(face,Collections.emptyList());
        net.minecraft.client.renderer.block.model.IBakedModel model=new net.minecraft.client.renderer.block.model.SimpleBakedModel(Collections.singletonList(quad),faces,false,true,sprite,net.minecraft.client.renderer.block.model.ItemCameraTransforms.DEFAULT,net.minecraft.client.renderer.block.model.ItemOverrideList.NONE);
        for(boolean right:new boolean[]{false,true})for(int choice:new int[]{-1,0})for(boolean tiled:new boolean[]{false,true}) {
            StaticSurfaceMesh[] panels=XDoorMeshes.build(model,right,choice,tiled,sprite,true);
            int count=0;
            for(StaticSurfaceMesh mesh:panels) {
                VertexFormat format=BlockSurfaceFormat.get();BufferBuilder buffer=new BufferBuilder(4096);buffer.begin(7,format);mesh.draw(buffer,192,80);buffer.finishDrawing();
                int stride=format.getNextOffset(),uv=format.getUvOffsetById(0);
                for(int v=0;v<buffer.getVertexCount();v++) {
                    float u=buffer.getByteBuffer().getFloat(v*stride+uv),w=buffer.getByteBuffer().getFloat(v*stride+uv+4);
                    require(u>=sprite.getMinU()-1e-6 && u<=sprite.getMaxU()+1e-6 && w>=sprite.getMinV()-1e-6 && w<=sprite.getMaxV()+1e-6,"split material samples outside atlas sprite");
                    count++;
                }
            }
            require(count>0,"replacement/native split disappeared");
        }
    }
    private static double triangle(float[] a,float[] b,float[] c){return Math.abs((b[0]-a[0])*(c[1]-a[1])-(b[1]-a[1])*(c[0]-a[0]))/2;}
    private static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
}
