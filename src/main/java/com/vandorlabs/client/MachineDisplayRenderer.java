package com.vandorlabs.client;

import com.vandorlabs.blocks.*;
import com.vandorlabs.tiles.TileEntityConstructor;
import net.minecraft.client.renderer.*;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.*;
import java.util.*;

/** Authored planes share the navigation/cockpit texture and animation renderer. */
public final class MachineDisplayRenderer {
    private static final Map<TileEntityConstructor,Planes> CACHE=new WeakHashMap<>();
    private static final class Planes {
        final FrameMachineDesign spec;final EnumFacing facing;final BlockPos pivot;final Vec3d[][] points;final Vec3d[] normals;
        final double[][] uv;final ResourceLocation[] textures;
        Planes(TileEntityConstructor tile){
            spec=tile.core().specification();facing=tile.facing();pivot=tile.pivot();points=new Vec3d[spec.surfaces.length][4];normals=new Vec3d[points.length];
            uv=new double[points.length][4];textures=new ResourceLocation[points.length];
            com.vandorlabs.navigation.NavigationLayout.Facing direction=com.vandorlabs.navigation.NavigationLayout.Facing.valueOf(facing.name());
            for(int slot=0;slot<points.length;slot++){
                FrameMachineDesign.Surface surface=spec.surfaces[slot];
                for(int i=0;i<4;i++){double[] p=surface.corners[i];points[slot][i]=MachineMountSurface.rotate(new Vec3d(p[0],p[1],p[2]),facing,spec.width,spec.depth).subtract(new Vec3d(pivot));}
                double[] n=surface.normal;com.vandorlabs.navigation.NavigationLayout.Vec3 normal=direction.rotateDirection(new com.vandorlabs.navigation.NavigationLayout.Vec3(n[0],n[1],n[2]));normals[slot]=new Vec3d(normal.x,normal.y,normal.z);
            }
        }
        boolean matches(TileEntityConstructor tile){return tile.core()!=null&&spec==tile.core().specification()&&facing==tile.facing()&&pivot.equals(tile.pivot());}
    }
    public static void render(TileEntityConstructor tile,double x,double y,double z){
        if(!tile.renderReady()||tile.surfaceCount()==0)return;
        Planes planes=CACHE.get(tile);if(planes==null||!planes.matches(tile)){planes=new Planes(tile);CACHE.put(tile,planes);}
        GlStateManager.pushMatrix();try(TurntableRenderState saved=new TurntableRenderState()){
            GlStateManager.translate(x,y,z);GlStateManager.disableLighting();GlStateManager.disableCull();GlStateManager.color(1,1,1,1);
            int light=tile.getWorld().getCombinedLight(tile.getPos(),0);OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit,light&65535,light>>>16);
            boolean active=tile.screenOn(),animated=tile.screenSettings().animated;int speed=tile.screenSettings().speed;
            // Resolve every slot before drawing: lazy animation uploads may bind
            // textures even when a neighbouring display will reuse the atlas.
            for(int slot=0;slot<planes.points.length;slot++){
                int choice=tile.surfaceChoice(slot);
                planes.textures[slot]=choice==-2&&active?null:TEAnimatedScreenSelector.resolveDisplay(tile,choice,active,animated,speed,planes.uv[slot]);
            }
            Tessellator tessellator=Tessellator.getInstance();BufferBuilder buffer=tessellator.getBuffer();
            for(int first=0;first<planes.points.length;){
                if(planes.textures[first]==null){first++;continue;}
                int end=runEnd(planes.textures,first);
                net.minecraft.client.Minecraft.getMinecraft().getTextureManager().bindTexture(planes.textures[first]);
                buffer.setTranslation(0,0,0);buffer.begin(7,BlockSurfaceFormat.get());
                for(int slot=first;slot<end;slot++)if(planes.textures[slot]!=null)emitPlane(buffer,planes.points[slot],planes.normals[slot],planes.uv[slot]);
                tessellator.draw();first=end;
            }
        }finally{GlStateManager.popMatrix();}
    }
    /** Hidden slots carry no geometry; visible texture transitions retain authored order. */
    static int runEnd(ResourceLocation[] textures,int first){
        for(int next=first+1;next<textures.length;next++)if(textures[next]!=null&&!textures[first].equals(textures[next]))return next;
        return textures.length;
    }
    static void emitPlane(BufferBuilder buffer,Vec3d[] points,Vec3d normal,double[] uv){
        for(int i=0;i<4;i++){Vec3d p=points[i];double u=i==1||i==2?1:0,v=i<2?1:0;WorldSurface.vertex(buffer,p.x,p.y,p.z,uv[2]+u*(uv[3]-uv[2]),uv[0]+v*(uv[1]-uv[0]),(float)normal.x,(float)normal.y,(float)normal.z);}
    }
}
