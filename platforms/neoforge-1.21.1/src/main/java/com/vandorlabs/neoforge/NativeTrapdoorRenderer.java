package com.vandorlabs.neoforge;

import com.mojang.blaze3d.vertex.PoseStack;
import com.vandorlabs.neoforge.trapdoor.NativeTrapdoorVisual;
import com.vandorlabs.render.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.Material;
import net.minecraft.core.BlockPos;
import net.minecraft.server.packs.resources.ResourceManager;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ModelEvent;

/** Inactive shared regular/diagonal orchestration. Host owns publication, fluids and registration. */
@EventBusSubscriber(modid="vandorlabs",bus=EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
public final class NativeTrapdoorRenderer {
    private NativeTrapdoorRenderer(){}
    private static final int BUDGET=16*1024*1024;
    private static final ThreadLocal<Scratch> SCRATCH=ThreadLocal.withInitial(Scratch::new);
    private static final class Scratch {final double[][] moving=new double[8][3],frame=new double[8][3];}
    private static final class Draw {
        final int texture,layout,joins;final boolean rawTile,tall;final TrapdoorLeafPose pose;
        final NativeTrapdoorVisual.ArtworkBounds artwork;final NativeTrapdoorMesh mesh;final boolean framed;
        Draw(NativeTrapdoorVisual visual,BlockPos owner,Materials materials){
            var cfg=visual.settings().choices();texture=cfg.texture;layout=cfg.layout();rawTile=cfg.tileTexture;
            joins=visual.frameJoins();pose=visual.pose();artwork=visual.artwork();tall=pose.diagonal&&pose.position!=2;
            boolean widthX=pose.diagonal?(pose.turns&1)==0:(pose.turns&1)!=0;
            int originU=widthX?owner.getX():owner.getZ(),originV=tall?owner.getY():widthX?owner.getZ():owner.getX();
            var surface=materials.surface(texture);double[][] closed=new double[8][3],uv=new double[8][3];pose.write(0,true,closed);
            boolean tiled=TrapdoorArtworkCoordinates.write(closed,uv,widthX,tall,pose.diagonal,pose.position,originU,originV,
                    artwork.minU(),artwork.minV(),artwork.maxU(),artwork.maxV(),layout,rawTile,surface.doorArtwork(),surface.aspect());
            int first=tall?2:0;framed=surface.borders()!=null;
            mesh=framed?NativeTrapdoorMesh.frame(TrapdoorFrameLayout.build(uv,first,tiled,pose.diagonal?2/16D:TrapdoorGeometry.THICKNESS,joins,surface.borders().fractions(),surface.frameAspect()),surface.upper(),materials.metal())
                    :NativeTrapdoorMesh.surface(TrapdoorSurfaceLayout.build(uv,first,first+2,tiled,tiled&&surface.doorArtwork()&&layout!=2,surface.customDoor()),surface.upper(),surface.lower());
        }
        boolean matches(NativeTrapdoorVisual visual){var cfg=visual.settings().choices();return texture==cfg.texture&&layout==cfg.layout()&&rawTile==cfg.tileTexture&&joins==visual.frameJoins()&&pose.equals(visual.pose())&&artwork.equals(visual.artwork());}
        long bytes(){return 1024+mesh.retainedBytes();}
    }
    public interface Materials {NativeTrapdoorMaterials.Surface surface(int choice);com.vandorlabs.segmented.SegmentedPanelMesh.Texture metal();}
    public static final class Bake {
        private final Materials materials;
        private final LinkedHashMap<BlockPos,Draw> draws=new LinkedHashMap<>(32,.75F,true);
        private long bytes,builds;
        Bake(NativeFlatWallModel.Bake materials,Function<Material,TextureAtlasSprite> sprites,ResourceManager resources){this(new NativeTrapdoorMaterials(materials,sprites,resources));}
        Bake(Materials materials){this.materials=Objects.requireNonNull(materials);}
        private Draw draw(NativeTrapdoorVisual visual,BlockPos owner){var found=draws.get(owner);if(found!=null&&found.matches(visual))return found;
            var next=new Draw(visual,owner,materials);builds++;if(found!=null){draws.remove(owner);bytes-=found.bytes();}
            if(next.bytes()<=BUDGET){draws.put(owner.immutable(),next);bytes+=next.bytes();while(bytes>BUDGET){var iterator=draws.values().iterator();bytes-=iterator.next().bytes();iterator.remove();}}return next;
        }
        public long retainedBytes(){return bytes;}
        public long meshBuilds(){return builds;}
    }
    private record Prepared(long generation,Bake bake){}
    private static final AtomicLong GENERATION=new AtomicLong();
    private static final com.google.common.cache.Cache<Object,Prepared> PENDING=com.google.common.cache.CacheBuilder.newBuilder().weakKeys().maximumSize(2).build();
    private static volatile Prepared active;
    static void prepare(Object bakery,Function<Material,TextureAtlasSprite> sprites,NativeFlatWallModel.Bake materials,ResourceManager resources){PENDING.put(bakery,new Prepared(GENERATION.incrementAndGet(),new Bake(materials,sprites,resources)));}
    @SubscribeEvent public static synchronized void completed(ModelEvent.BakingCompleted event){var next=PENDING.getIfPresent(event.getModelBakery());PENDING.invalidate(event.getModelBakery());if(next!=null&&(active==null||next.generation>active.generation))active=next;}
    public static void render(NativeTrapdoorVisual visual,Object world,BlockPos owner,PoseStack poses,MultiBufferSource buffers,int light,int overlay){var current=active;if(current==null)return;renderAt(current.bake,visual,owner,NativeTrapdoorVisualClock.pose(world,owner,visual.open()),poses,buffers,light,overlay);}
    public static void renderAt(Bake bake,NativeTrapdoorVisual visual,BlockPos owner,double progress,PoseStack poses,MultiBufferSource buffers,int light,int overlay){
        emitAt(bake,visual,owner,progress,poses.last(),buffers.getBuffer(NativePanelPlaybackRenderer.frameRenderType()),light,overlay);
    }
    public static void emitAt(Bake bake,NativeTrapdoorVisual visual,BlockPos owner,double progress,PoseStack.Pose matrix,com.mojang.blaze3d.vertex.VertexConsumer target,int light,int overlay){
        var draw=bake.draw(visual,owner);var scratch=SCRATCH.get();draw.pose.write(progress,false,scratch.moving);
        draw.mesh.emit(scratch.moving,scratch.frame,matrix,target,light,overlay);
        if(!draw.framed)NativeTrapdoorMesh.emitEdges(scratch.moving,draw.pose.diagonal,draw.tall,bake.materials.metal(),matrix,target,light,overlay);
    }
}
