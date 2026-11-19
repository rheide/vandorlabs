package com.vandorlabs.client.constructors;

import com.vandorlabs.geometry.BuilderFabricationMesh;
import java.util.*;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemStack;

/** Baked-model occupancy and atlas pixels, printed in stable bottom-up Minecraft-pixel order. */
final class BuilderFabricationVoxels {
    private static final LinkedList<Model> CACHE=new LinkedList<>();
    private static long retained;
    private static final long BUDGET=12L*1024*1024;
    static void clear(){CACHE.clear();retained=0;}
    static Model get(ItemStack stack,boolean flat){BuilderItemGeometry source=BuilderItemGeometry.get(stack);for(Iterator<Model> it=CACHE.iterator();it.hasNext();){Model old=it.next();if(old.source==source&&old.flat==flat){it.remove();CACHE.addFirst(old);return old;}}Model model=new Model(source,flat);while(!CACHE.isEmpty()&&(CACHE.size()>=16||retained+model.bytes>BUDGET)){Model old=CACHE.removeLast();retained-=old.memory();}CACHE.addFirst(model);retained+=model.memory();return model;}
    static final class Model {
        final BuilderItemGeometry source;
        final boolean flat;
        final BuilderFabricationMesh.Model mesh;
        final double minX,minZ,maxX,maxZ;
        final long bytes;
        int lastCount=-1;
        int[] vertices;
        long memory(){return bytes+(vertices==null?0:vertices.length*4L);}
        Model(BuilderItemGeometry source,boolean flat){
            this.source=source;
            this.flat=flat;
            boolean module=source.stack.getItem()==com.vandorlabs.items.ModItems.NAVIGATION_DATA_MODULE
                    ||source.stack.getItem()==com.vandorlabs.items.ModItems.BATTERY_MODULE
                    ||source.stack.getItem()==com.vandorlabs.items.ModItems.BLANK_MODULE
                    ||source.stack.getItem()==com.vandorlabs.items.ModItems.AUTOPILOT_MODULE;
            List<BuilderFabricationMesh.Triangle> triangles=new ArrayList<>();
            for(BuilderItemGeometry.Triangle triangle:source.triangles){
                triangles.add(new BuilderFabricationMesh.Triangle(triangle.p,triangle.uv,triangle.surface,triangle::sample));
            }
            mesh=new BuilderFabricationMesh.Model(triangles,flat,module,source.fixedScale);
            minX=mesh.minX;minZ=mesh.minZ;maxX=mesh.maxX;maxZ=mesh.maxZ;
            bytes=mesh.bytes;
        }
        double height(double progress){return mesh.height(progress);}
        void emit(BufferBuilder buffer,double progress){int count=mesh.count(progress);int[] data=vertices;if(lastCount!=count||data==null){data=mesh.build(count);if(vertices!=null){retained-=vertices.length*4L;vertices=null;}while(CACHE.size()>1&&retained+data.length*4L>BUDGET){Model old=CACHE.removeLast();retained-=old.memory();}if(data.length*4L+bytes<=BUDGET){vertices=data;lastCount=count;retained+=data.length*4L;}}
            TextureAtlasSprite white=Minecraft.getMinecraft().getTextureMapBlocks().getAtlasSprite("vandorlabs:blocks/core_flat");double u=white.getInterpolatedU(8),v=white.getInterpolatedV(8);for(int at=0;at<data.length;at+=4){int color=data[at+3];buffer.pos(Float.intBitsToFloat(data[at]),Float.intBitsToFloat(data[at+1]),Float.intBitsToFloat(data[at+2])).color(color>>16&255,color>>8&255,color&255,255).tex(u,v).lightmap(240,240).endVertex();}}
    }
    private BuilderFabricationVoxels(){}
}
