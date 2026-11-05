package com.vandorlabs.neoforge;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.vandorlabs.neoforge.client.NativeMaterialPlayback;
import com.vandorlabs.neoforge.shape.NativeWallEntity;
import com.vandorlabs.neoforge.shape.PanelPlayback;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.ModelEvent;

/** Direct source textures keep per-host Off/static/animated glazing out of global atlas animation. */
@EventBusSubscriber(modid="vandorlabs",bus=EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
public final class NativePanelPlaybackRenderer implements BlockEntityRenderer<ProgrammableBlockEntity> {
    // The subclass is only a namespace for vanilla's protected state shards; it is never instantiated.
    private static final class Types extends RenderType {
        private Types(){super("vandorlabs_glazing",com.mojang.blaze3d.vertex.DefaultVertexFormat.BLOCK,com.mojang.blaze3d.vertex.VertexFormat.Mode.QUADS,1536,false,false,()->{},()->{});}
        static RenderType pane(net.minecraft.resources.ResourceLocation texture,boolean opaque){
            return RenderType.create("vandorlabs_glazing",com.mojang.blaze3d.vertex.DefaultVertexFormat.BLOCK,com.mojang.blaze3d.vertex.VertexFormat.Mode.QUADS,1536,true,!opaque,
                    CompositeState.builder().setShaderState(new ShaderStateShard(()->shader)).setTextureState(new TextureStateShard(texture,false,false)).setTransparencyState(opaque?NO_TRANSPARENCY:TRANSLUCENT_TRANSPARENCY).setCullState(NO_CULL).setLightmapState(LIGHTMAP).setWriteMaskState(opaque?COLOR_DEPTH_WRITE:COLOR_WRITE).setOutputState(opaque?MAIN_TARGET:TRANSLUCENT_TARGET).createCompositeState(false));
        }
    }
    static RenderType renderType(net.minecraft.resources.ResourceLocation texture,boolean opaque){return Types.pane(texture,opaque);}
    private static net.minecraft.client.renderer.ShaderInstance shader;
    @SubscribeEvent public static void shaders(net.neoforged.neoforge.client.event.RegisterShadersEvent event)throws java.io.IOException{
        event.registerShader(new net.minecraft.client.renderer.ShaderInstance(event.getResourceProvider(),net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("vandorlabs","panel_glass"),com.mojang.blaze3d.vertex.DefaultVertexFormat.BLOCK),value->shader=value);
    }
    private record Prepared(long generation,NativeFlatWallModel.Bake bake){}
    private static final AtomicLong GENERATION=new AtomicLong();
    private static final com.google.common.cache.Cache<Object,Prepared> PENDING=com.google.common.cache.CacheBuilder.newBuilder().weakKeys().maximumSize(2).build();
    private static volatile Prepared active;
    /** The same successful bake can provide later section-edge panel fallback bodies. */
    static NativeFlatWallModel.Bake currentBake(){var value=active;return value==null?null:value.bake;}
    static void prepare(Object bakery,NativeFlatWallModel.Bake bake){PENDING.put(bakery,new Prepared(GENERATION.incrementAndGet(),bake));}
    @SubscribeEvent public static void completed(ModelEvent.BakingCompleted event){publish(event.getModelBakery());}
    private static synchronized void publish(Object bakery){var prepared=PENDING.getIfPresent(bakery);PENDING.invalidate(bakery);if(prepared!=null&&(active==null||prepared.generation>active.generation))active=prepared;}
    @SubscribeEvent public static void register(EntityRenderersEvent.RegisterRenderers event){if(net.minecraft.core.registries.BuiltInRegistries.BLOCK.containsKey(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("vandorlabs","programmable_porthole_wall")))event.registerBlockEntityRenderer(VandorLabsBootstrap.PROGRAMMABLE_ENTITY.get(),ignored->new NativePanelPlaybackRenderer());}
    private static final class BodyLayer {static final RenderType VALUE=renderType(net.minecraft.client.renderer.texture.TextureAtlas.LOCATION_BLOCKS,true);}
    @Override public net.minecraft.world.phys.AABB getRenderBoundingBox(ProgrammableBlockEntity tile){return tile instanceof NativeWallEntity wall?wall.renderBounds():new net.minecraft.world.phys.AABB(tile.getBlockPos());}
    @Override public boolean shouldRender(ProgrammableBlockEntity tile,Vec3 camera){
        if(!(tile instanceof NativeWallEntity wall))return false;var snapshot=wall.wallAppearance();boolean body=snapshot.geometry().diagonal()&&!snapshot.chunkBody();if(!body&&!wall.isPorthole())return false;
        var current=active;if(current==null||!body&&!(snapshot.geometry() instanceof com.vandorlabs.neoforge.shape.NativeFlatWallGeometry.Geometry))return false;var pos=tile.getBlockPos();double x=camera.x-(pos.getX()+.5D),y=camera.y-(pos.getY()+.5D),z=camera.z-(pos.getZ()+.5D),range=getViewDistance();return x*x+y*y+z*z<range*range;
    }
    @Override public void render(ProgrammableBlockEntity tile,float partialTick,PoseStack poses,MultiBufferSource buffers,int light,int overlay){
        if(!(tile instanceof NativeWallEntity wall))return;var current=active;if(current==null)return;
        var appearance=wall.wallAppearance();
        if(appearance.geometry().diagonal()&&!appearance.chunkBody())emit(current.bake.bodyProjection(appearance),current.bake.surfaces.get(0),null,PanelPlayback.DEFAULT,0,poses.last(),buffers.getBuffer(BodyLayer.VALUE),light,overlay);
        if(!wall.isPorthole())return;
        var pane=current.bake.paneProjection(appearance);if(pane==null||pane.quads().isEmpty())return;
        var playback=appearance.playback();var target=buffers.getBuffer(pane.renderType(playback.mode()));
        emit(pane.quads(),pane.source(),pane.material(),playback,NativeMaterialPlayback.tick(),poses.last(),target,light,overlay);
    }
    private static final class Transform {final org.joml.Vector3f position=new org.joml.Vector3f(),normal=new org.joml.Vector3f();}
    private static final ThreadLocal<Transform> TRANSFORM=ThreadLocal.withInitial(Transform::new);
    static void emit(List<BakedQuad> quads,ProgrammableModel.MaterialSnapshot source,NativeMaterialPlayback.Material material,PanelPlayback playback,long tick,PoseStack.Pose pose,VertexConsumer target,int light,int overlay){
        var transform=TRANSFORM.get();var sprite=source.sprite();float lowU=sprite.getU0(),lowV=sprite.getV0(),width=(sprite.getU1()-lowU)*source.usedU(),height=(sprite.getV1()-lowV)*source.usedV();int frame=material==null?0:material.frame(playback.mode(),playback.speed(),tick);double top=material==null?0:material.top(playback.mode(),frame),span=material==null?1:material.bottom(playback.mode(),frame)-top;
        // The direct block render type disables culling; native chunk records are paired backfaces.
        // Emit each physical face once so the duplicate does not compound its opacity.
        for(int q=0;q<quads.size();q+=2){
            int[] data=quads.get(q).getVertices();for(int v=0;v<4;v++){
                int at=v*8,normal=data[at+7];float u=Float.intBitsToFloat(data[at+4]);double mappedV=Float.intBitsToFloat(data[at+5]);if(material!=null){u=(u-lowU)/width;mappedV=top+span*((mappedV-lowV)/height);}
                // Vanilla's pose-aware consumer overloads allocate two Vector3f instances per vertex
                // unless HotSpot eliminates them. Reuse owner-free scratch with the exact same JOML calls.
                pose.pose().transformPosition(Float.intBitsToFloat(data[at]),Float.intBitsToFloat(data[at+1]),Float.intBitsToFloat(data[at+2]),transform.position);
                pose.transformNormal((byte)normal/127F,(byte)(normal>>>8)/127F,(byte)(normal>>>16)/127F,transform.normal);
                float x=transform.position.x,y=transform.position.y,z=transform.position.z,nx=transform.normal.x,ny=transform.normal.y,nz=transform.normal.z;
                target.addVertex(x,y,z).setColor(data[at+3]).setUv(u,(float)mappedV).setOverlay(overlay).setLight(light).setNormal(nx,ny,nz);
            }
        }
    }
}
