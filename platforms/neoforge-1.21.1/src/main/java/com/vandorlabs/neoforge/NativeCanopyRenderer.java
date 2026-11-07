package com.vandorlabs.neoforge;

import com.mojang.blaze3d.vertex.*;
import com.vandorlabs.canopy.*;
import com.vandorlabs.neoforge.canopy.*;
import java.util.*;
import java.util.function.Function;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.texture.*;
import net.minecraft.client.resources.model.Material;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ModelEvent;

/** Inactive authored canopy backend. Hosts supply one immutable snapshot and their cosmetic pose. */
@EventBusSubscriber(modid="vandorlabs",bus=EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
public final class NativeCanopyRenderer {
    private NativeCanopyRenderer(){}
    private record Publication(long generation,Bake bake){}
    private static final AtomicLong GENERATION=new AtomicLong();
    private static final com.google.common.cache.Cache<Object,Publication> PENDING=com.google.common.cache.CacheBuilder.newBuilder().weakKeys().maximumSize(2).build();
    private static volatile Publication active;
    public static void prepare(Object bakery,NativeFlatWallModel.Bake materials,Function<Material,TextureAtlasSprite> textures){PENDING.put(bakery,new Publication(GENERATION.incrementAndGet(),new Bake(materials,textures)));}
    public static Bake currentBake(){Publication found=active;return found==null?null:found.bake;}
    @SubscribeEvent public static synchronized void completed(ModelEvent.BakingCompleted event){Publication found=PENDING.getIfPresent(event.getModelBakery());PENDING.invalidate(event.getModelBakery());if(found!=null&&(active==null||found.generation>active.generation)){if(active!=null)active.bake.clear();active=found;}}
    @EventBusSubscriber(modid="vandorlabs",value=Dist.CLIENT)
    public static final class WorldEvents {
        @SubscribeEvent public static void unload(net.neoforged.neoforge.event.level.LevelEvent.Unload event){if(event.getLevel().isClientSide()){Bake found=currentBake();if(found!=null)found.clear();}}
    }
    private record Key(String kind,int facing,int width,int mask,int roof,boolean centered,int depth,int scale,int shade,int tint,int mount,int motion,int hinge,int angle,int material,int layout,boolean openable,boolean slide,double aspect,long pose){}
    private static final class Scratch {final org.joml.Vector3f point=new org.joml.Vector3f(),normal=new org.joml.Vector3f();}
    private static final ThreadLocal<Scratch> SCRATCH=ThreadLocal.withInitial(Scratch::new);
    public static final class Mesh implements CanopyEmission.Sink {
        private final float[] data;private final int[] colors;private int count;
        private Mesh(int count){data=new float[count*8];colors=new int[count];}
        public int vertices(){return count;}
        private int bytes(){return 64+data.length*4+colors.length*4;}
        @Override public void quad(int direction){}
        @Override public void vertex(float x,float y,float z,float u,float v,int color,float nx,float ny,float nz){int at=count*8;data[at]=x;data[at+1]=y;data[at+2]=z;data[at+3]=u;data[at+4]=v;data[at+5]=nx;data[at+6]=ny;data[at+7]=nz;colors[count++]=color;}
        private void emit(PoseStack.Pose pose,VertexConsumer target,int light,int overlay){Scratch s=SCRATCH.get();for(int i=0;i<count;i++){int at=i*8,color=colors[i];pose.pose().transformPosition(data[at],data[at+1],data[at+2],s.point);pose.transformNormal(data[at+5],data[at+6],data[at+7],s.normal);target.addVertex(s.point.x,s.point.y,s.point.z).setColor(color&255,(color>>>8)&255,(color>>>16)&255,color>>>24).setUv(data[at+3],data[at+4]).setOverlay(overlay).setLight(light).setNormal(s.normal.x,s.normal.y,s.normal.z);}}
    }
    public static final class Draw {
        private final Mesh body,pane;
        private Draw(Mesh body,Mesh pane){this.body=body;this.pane=pane;}
        public int bodyVertices(){return body.vertices();}public int paneVertices(){return pane.vertices();}
        public int bytes(){return 64+body.bytes()+pane.bytes();}
        public double[] bounds(){double[] b={Double.POSITIVE_INFINITY,Double.POSITIVE_INFINITY,Double.POSITIVE_INFINITY,Double.NEGATIVE_INFINITY,Double.NEGATIVE_INFINITY,Double.NEGATIVE_INFINITY};for(Mesh mesh:new Mesh[]{body,pane})for(int i=0;i<mesh.count;i++)for(int a=0;a<3;a++){double v=mesh.data[i*8+a];b[a]=Math.min(b[a],v);b[a+3]=Math.max(b[a+3],v);}return b;}
        public void emit(PoseStack.Pose pose,MultiBufferSource target,int light,int overlay,boolean body,boolean pane){if(body&&this.body.count>0)this.body.emit(pose,target.getBuffer(NativePanelPlaybackRenderer.frameRenderType(true)),light,overlay);if(pane&&this.pane.count>0)this.pane.emit(pose,target.getBuffer(NativePanelPlaybackRenderer.frameRenderType(true,false)),light,overlay);}
    }
    public static final class Bake {
        private static final int LIMIT=8*1024*1024;
        private final NativeFlatWallModel.Bake materials;
        private final Function<Material,TextureAtlasSprite> textures;
        private final LinkedHashMap<Key,Draw> draws=new LinkedHashMap<>(16,.75F,true);
        private static final class Binding {long pose;Key key;final int bytes;Binding(long pose,Key key,int bytes){this.pose=pose;this.key=key;this.bytes=bytes;}}
        private final IdentityHashMap<NativeCanopyRenderSnapshot,Binding> bindings=new IdentityHashMap<>();
        private final ArrayDeque<NativeCanopyRenderSnapshot> bindingOrder=new ArrayDeque<>();
        private int bytes,bindingBytes;private long builds;
        Bake(NativeFlatWallModel.Bake materials,Function<Material,TextureAtlasSprite> textures){this.materials=materials;this.textures=textures;}
        public synchronized int retainedBytes(){return bytes+bindingBytes;}
        public synchronized long builds(){return builds;}
        public synchronized void clear(){draws.clear();bindings.clear();bindingOrder.clear();bytes=bindingBytes=0;}
        private ProgrammableModel.MaterialSnapshot panel(int choice){return materials.surfaces.get(materials.index(choice));}
        private ProgrammableModel.MaterialSnapshot surface(CanopyFace face,int material){
            if(face.baseMaterial.equals("panel"))return panel(material);
            String name=face.material;
            if(!name.contains(":"))name="vandorlabs:canopy/"+name;
            else if(name.startsWith("vandorlabs:blocks/programmable_glass/low/"))name="vandorlabs:glass-palette/"+name.substring("vandorlabs:blocks/programmable_glass/low/".length());
            return new ProgrammableModel.MaterialSnapshot(textures.apply(new Material(TextureAtlas.LOCATION_BLOCKS,ResourceLocation.parse(name))),1,1);
        }
        private static CanopyEmission.Sprite sprite(ProgrammableModel.MaterialSnapshot material){var s=material.sprite();return new CanopyEmission.Sprite(s.getU0(),s.getU0()+(s.getU1()-s.getU0())*material.usedU(),s.getV0(),s.getV0()+(s.getV1()-s.getV0())*material.usedV());}
        public synchronized Draw draw(NativeCanopyRenderSnapshot visual,double progress){
            if(!Double.isFinite(progress))throw new IllegalArgumentException("Canopy cosmetic pose");
            var state=visual.settings().data();if(!(visual.kind().equals("canopy")||visual.kind().equals("long_canopy")||com.vandorlabs.persistence.CanopySettingsData.supports(visual.kind())&&state.openable))progress=0;
            long pose=Double.doubleToLongBits(progress);Binding binding=bindings.get(visual);
            if(binding!=null&&binding.pose==pose){Draw found=draws.get(binding.key);if(found!=null)return found;}
            var s=visual.settings().data();double aspect=s.material<0?1:panel(s.material).usedU()/panel(s.material).usedV();
            Key key=binding!=null&&binding.pose==pose?binding.key:new Key(visual.kind(),visual.facing(),visual.width(),visual.mask(),visual.roof(),visual.centered(),s.depth,s.scale,s.shade,s.tint,s.mount,s.motion,s.hinge,s.angle,s.material,s.textureLayout(),s.openable,s.slideForward,aspect,pose);
            Draw draw=draws.get(key);
            if(draw==null){
                var base=NativeCanopyModels.builtin().base(visual.kind(),visual.width(),visual.mask(),visual.roof(),visual.centered(),s,aspect,choice->"vandorlabs:blocks/"+ModernFinishCatalog.option(choice).id());
                var faces=new CanopyDrawingGeometry(base,visual.kind(),visual.width(),s).posed(progress,visual.centered());
                List<CanopyEmission.Prepared> body=new ArrayList<>(),pane=new ArrayList<>();
                for(var face:faces){var prepared=new CanopyEmission.Prepared(face,visual.facing(),CanopyEmission.opacity(face,s.material,s.tint));(CanopyEmission.translucent(face,s.material,s.tint)?pane:body).add(prepared);}
                Mesh a=new Mesh(body.stream().mapToInt(CanopyEmission.Prepared::vertexCount).sum()),b=new Mesh(pane.stream().mapToInt(CanopyEmission.Prepared::vertexCount).sum());
                boolean chunk=visual.kind().equals("regular_canopy_glass");for(var face:body)CanopyEmission.emit(face,sprite(surface(face.source,s.material)),chunk,a);for(var face:pane)CanopyEmission.emit(face,sprite(surface(face.source,s.material)),chunk,b);
                draw=new Draw(a,b);builds++;if(draw.bytes()+128<=LIMIT){draws.put(key,draw);bytes+=draw.bytes()+128;}
            }
            if(binding==null){binding=new Binding(pose,key,512+s.channels.size()*32+visual.kind().length()*2);bindings.put(visual,binding);bindingOrder.addLast(visual);bindingBytes+=binding.bytes;}else{binding.pose=pose;binding.key=key;}
            while(bindings.size()>1024){var old=bindings.remove(bindingOrder.removeFirst());bindingBytes-=old.bytes;}
            while(bytes+bindingBytes>LIMIT&&!draws.isEmpty()){var old=draws.entrySet().iterator().next();bytes-=old.getValue().bytes()+128;draws.remove(old.getKey());}
            while(bytes+bindingBytes>LIMIT&&!bindingOrder.isEmpty()){var old=bindings.remove(bindingOrder.removeFirst());bindingBytes-=old.bytes;}
            return draw;
        }
    }
}
