package com.vandorlabs.neoforge;

import com.mojang.blaze3d.vertex.*;
import com.vandorlabs.light.ProgrammableLightMesh;
import com.vandorlabs.neoforge.light.*;
import com.vandorlabs.segmented.SegmentedPanelMesh;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ModelEvent;

/** Inactive Light backend. Host supplies one immutable publication and prepared packed neighbour light. */
@EventBusSubscriber(modid="vandorlabs",bus=EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
public final class NativeLightRenderer {
    private NativeLightRenderer(){}
    public record Surface(SegmentedPanelMesh.Texture texture,double aspect){public Surface{Objects.requireNonNull(texture);if(!Double.isFinite(aspect)||aspect<=0)throw new IllegalArgumentException("Light artwork aspect");}}
    public interface Materials {Surface surface(int choice);}
    private static final int BUDGET=8*1024*1024;
    private static final class Draw {
        final int kind,facing,main,layout,style,selected,off;final boolean upper,small,lit;final NativeLightRenderProjection projection;
        final NativeLightMesh housing,face;
        Draw(NativeLightEntity.Visual visual,NativeLightRenderProjection projection,Materials materials){
            kind=visual.kind().ordinal();facing=visual.facing().get3DDataValue();upper=visual.upper();small=visual.small();main=visual.housing().mainTexture;layout=visual.housing().layout();var data=visual.settings().light();style=data.texture;selected=data.faceTexture;off=data.offFaceTexture;lit=visual.on()&&visual.level()>0;this.projection=projection;
            var bounds=ProgrammableLightMesh.localBounds(kind,facing,upper,small);var body=materials.surface(main);housing=new NativeLightMesh(ProgrammableLightMesh.housing(bounds,projection.visibleFaces(),layout,body.aspect()),visual.facing(),body.texture());
            face=projection.visible(2)?new NativeLightMesh(ProgrammableLightMesh.face(bounds,projection.left(),projection.top(),projection.right(),projection.bottom()),visual.facing(),materials.surface(NativeLightArtwork.face(data,lit)).texture()):null;
        }
        boolean matches(NativeLightEntity.Visual visual,NativeLightRenderProjection projection){var data=visual.settings().light();return kind==visual.kind().ordinal()&&facing==visual.facing().get3DDataValue()&&upper==visual.upper()&&small==visual.small()&&main==visual.housing().mainTexture&&layout==visual.housing().layout()&&style==data.texture&&selected==data.faceTexture&&off==data.offFaceTexture&&lit==(visual.on()&&visual.level()>0)&&this.projection.equals(projection);}
        long bytes(){return 192+housing.retainedBytes()+(face==null?0:face.retainedBytes());}
    }
    public static final class Bake {
        private final Materials materials;private final LinkedHashMap<BlockPos,Draw> draws=new LinkedHashMap<>(32,.75F,true);private long bytes,builds;
        public Bake(Materials materials){this.materials=Objects.requireNonNull(materials);}
        Bake(NativeFlatWallModel.Bake source){this(new NativeLightMaterials(source));}
        private Draw draw(NativeLightEntity.Visual visual,NativeLightRenderProjection projection,BlockPos owner){var found=draws.get(owner);if(found!=null&&found.matches(visual,projection))return found;var next=new Draw(visual,projection,materials);builds++;if(found!=null){draws.remove(owner);bytes-=found.bytes();}if(next.bytes()<=BUDGET){draws.put(owner.immutable(),next);bytes+=next.bytes();while(bytes>BUDGET){var iterator=draws.values().iterator();bytes-=iterator.next().bytes();iterator.remove();}}return next;}
        public long retainedBytes(){return bytes;}public long meshBuilds(){return builds;}
        private void clearWorld(){draws.clear();bytes=0;}
    }
    private record Prepared(long generation,Bake bake){}
    private static final AtomicLong GENERATION=new AtomicLong();
    private static final com.google.common.cache.Cache<Object,Prepared> PENDING=com.google.common.cache.CacheBuilder.newBuilder().weakKeys().maximumSize(2).build();
    private static volatile Prepared active;
    static Bake currentBake(){var current=active;return current==null?null:current.bake;}
    static void prepare(Object bakery,NativeFlatWallModel.Bake source){PENDING.put(bakery,new Prepared(GENERATION.incrementAndGet(),new Bake(source)));}
    @SubscribeEvent public static synchronized void completed(ModelEvent.BakingCompleted event){var next=PENDING.getIfPresent(event.getModelBakery());PENDING.invalidate(event.getModelBakery());if(next!=null&&(active==null||next.generation>active.generation))active=next;}
    @EventBusSubscriber(modid="vandorlabs",value=Dist.CLIENT) public static final class WorldLifetime {
        @SubscribeEvent public static void unload(net.neoforged.neoforge.event.level.LevelEvent.Unload event){if(event.getLevel().isClientSide()){var current=active;if(current!=null)current.bake.clearWorld();}}
    }
    public static void render(NativeLightEntity.Visual visual,NativeLightRenderProjection projection,BlockPos owner,PoseStack poses,MultiBufferSource buffers,int packedNeighborLight,int overlay){var current=active;if(current==null)return;renderAt(current.bake,visual,projection,owner,poses,buffers,packedNeighborLight,overlay);}
    public static void renderAt(Bake bake,NativeLightEntity.Visual visual,NativeLightRenderProjection projection,BlockPos owner,PoseStack poses,MultiBufferSource buffers,int packedNeighborLight,int overlay){emitAt(bake,visual,projection,owner,poses.last(),buffers.getBuffer(NativePanelPlaybackRenderer.frameRenderType(true)),packedNeighborLight,overlay);}
    public static void emitAt(Bake bake,NativeLightEntity.Visual visual,NativeLightRenderProjection projection,BlockPos owner,PoseStack.Pose pose,VertexConsumer target,int packedNeighborLight,int overlay){var draw=bake.draw(visual,projection,owner);draw.housing.emit(pose,target,packedNeighborLight,overlay);if(draw.face!=null)draw.face.emit(pose,target,packedNeighborLight,overlay);}
}
