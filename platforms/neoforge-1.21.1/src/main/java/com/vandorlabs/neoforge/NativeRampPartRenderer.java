package com.vandorlabs.neoforge;

import com.mojang.blaze3d.vertex.PoseStack;
import com.vandorlabs.neoforge.ramp.*;
import com.vandorlabs.ramp.RampGeometry;
import com.vandorlabs.render.RampFaceTexture;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.texture.*;
import net.minecraft.client.resources.model.Material;
import net.minecraft.core.*;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.model.data.ModelData;

/** Inactive authored part backend. Neither live owner NBT nor world references enter prepared meshes. */
@EventBusSubscriber(modid="vandorlabs",bus=EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
public final class NativeRampPartRenderer {
    private static final int BUDGET=8*1024*1024,MAX_VERTICES=1024*1024;
    private record Publication(long generation,Bake bake){}
    private static final AtomicLong GENERATION=new AtomicLong();
    private static final com.google.common.cache.Cache<Object,Publication> PENDING=com.google.common.cache.CacheBuilder.newBuilder().weakKeys().maximumSize(2).build();
    private static volatile Publication active;
    static void prepare(Object bakery,NativeFlatWallModel.Bake materials){PENDING.put(bakery,new Publication(GENERATION.incrementAndGet(),new Bake(materials)));}
    public static Bake currentBake(){var found=active;return found==null?null:found.bake;}
    @SubscribeEvent public static synchronized void completed(ModelEvent.BakingCompleted event){var next=PENDING.getIfPresent(event.getModelBakery());PENDING.invalidate(event.getModelBakery());if(next!=null&&(active==null||next.generation>active.generation)){if(active!=null)active.bake.clear();active=next;}}
    @EventBusSubscriber(modid="vandorlabs",value=Dist.CLIENT)
    public static final class WorldEvents {@SubscribeEvent public static void unload(net.neoforged.neoforge.event.level.LevelEvent.Unload event){if(event.getLevel().isClientSide()){var bake=currentBake();if(bake!=null)bake.clear();}}}
    /** Construct once when an immutable part publication changes, retaining original source separately. */
    public static final class Materials {
        private final BlockState source;private final Map<BlockPos,NativeRampPartMaterial> origins;private final NativeRampPartMaterial first;
        public Materials(BlockState source,Map<BlockPos,NativeRampPartMaterial> origins){this.source=Objects.requireNonNull(source);if(origins.size()>128)throw new IllegalArgumentException("Ramp material origin limit");var values=new LinkedHashMap<BlockPos,NativeRampPartMaterial>();origins.forEach((pos,value)->values.put(pos.immutable(),Objects.requireNonNull(value)));this.origins=Collections.unmodifiableMap(values);first=values.isEmpty()?null:values.values().iterator().next();}
        private NativeRampPartMaterial at(BlockPos pos){return origins.getOrDefault(pos,first);}
        public long retainedBytes(){return 192L+240L*origins.size();}
    }
    /** Client-thread source-face and biome-tint capture; contains sprites and scalar colors only. */
    public static final class SourceFaces {
        private final BlockState state;private final ProgrammableModel.MaterialSnapshot[] materials;private final int[] tints;private final boolean glass;
        private SourceFaces(BlockState state,ProgrammableModel.MaterialSnapshot[] materials,int[] tints,boolean glass){this.state=state;this.materials=materials;this.tints=tints;this.glass=glass;}
    }
    public static SourceFaces source(BlockState state,BlockAndTintGetter level,BlockPos pos){
        var mc=Minecraft.getInstance();var model=mc.getBlockRenderer().getBlockModel(state);var faces=new ProgrammableModel.MaterialSnapshot[6];var tints=new int[6];
        for(var face:Direction.values()){var quads=model.getQuads(state,face,RandomSource.create(0),ModelData.EMPTY,null);var first=quads.isEmpty()?null:quads.getFirst();var sprite=first==null?model.getParticleIcon():first.getSprite();faces[face.get3DDataValue()]=new ProgrammableModel.MaterialSnapshot(sprite,1,1);tints[face.get3DDataValue()]=first!=null&&first.isTinted()?mc.getBlockColors().getColor(state,level,pos,first.getTintIndex()):0xffffff;}
        return new SourceFaces(state,faces,tints,state.is(Blocks.GLASS)||state.getBlock() instanceof StainedGlassBlock);
    }
    private static int texture(NativeRampPartMaterial material,Direction face){if(material==null)return -1;var local=face;if(local.getAxis()!=Direction.Axis.Y)for(int i=0;i<material.rotation();i++)local=material.stairs()?local.getCounterClockWise():local.getClockWise();int choice=material.facesEnabled()?material.face(local.get3DDataValue()):-1;return choice>=0?choice:material.stairs()&&local.getAxis()!=Direction.Axis.Y&&material.sideTexture()>=0?material.sideTexture():material.mainTexture();}
    public static final class Draw {
        private final float[] data;private final int[] colors;private final boolean glass;private final int vertices;
        private Draw(Builder source,boolean glass){data=Arrays.copyOf(source.data,source.count*8);colors=Arrays.copyOf(source.colors,source.count);vertices=source.count;this.glass=glass;}
        public int vertices(){return vertices;}public long retainedBytes(){return 96L+data.length*4L+colors.length*4L;}
        public void emit(PoseStack.Pose pose,MultiBufferSource buffers,int light,int overlay){var target=buffers.getBuffer(NativePanelPlaybackRenderer.frameRenderType(true,!glass));NativePreparedVertices.emit(data,colors,vertices,pose,target,light,overlay);}

    }
    private static final class Builder {
        float[] data=new float[256*8];int[] colors=new int[256];int count;
        void vertex(double x,double y,double z,double u,double v,ProgrammableModel.MaterialSnapshot material,Direction face,int tint){
            if(count>=MAX_VERTICES)throw new IllegalArgumentException("Ramp part vertex budget");if(count==colors.length){colors=Arrays.copyOf(colors,Math.min(MAX_VERTICES,count*2));data=Arrays.copyOf(data,colors.length*8);}
            float shade=face==Direction.UP?1:face==Direction.DOWN?.5F:face.getAxis()==Direction.Axis.X?.6F:.8F;
            int red=(int)((tint>>16&255)/255F*shade*255F),green=(int)((tint>>8&255)/255F*shade*255F),blue=(int)((tint&255)/255F*shade*255F);colors[count]=0xff000000|blue<<16|green<<8|red;
            int at=count++*8;data[at]=(float)x;data[at+1]=(float)y;data[at+2]=(float)z;data[at+3]=NativeHousingQuads.atlas(material,u*16,true);data[at+4]=NativeHousingQuads.atlas(material,v*16,false);data[at+5]=face.getStepX();data[at+6]=face.getStepY();data[at+7]=face.getStepZ();
        }
    }
    private static final class Key {
        final NativeRampGeometry.Spec spec;final NativeRampGeometry.Snapshot snapshot;final long pose;final Materials materials;final SourceFaces source;final List<NativeRampTextureCoordinates.Frame> frames;
        Key(NativeRampGeometry.Spec spec,NativeRampGeometry.Snapshot snapshot,double pose,Materials materials,SourceFaces source,List<NativeRampTextureCoordinates.Frame> frames){this.frames=frames;this.spec=spec;this.snapshot=snapshot;this.pose=Double.doubleToLongBits(pose);this.materials=materials;this.source=source;}
        @Override public int hashCode(){int h=System.identityHashCode(spec);h=31*h+System.identityHashCode(snapshot);h=31*h+Long.hashCode(pose);h=31*h+System.identityHashCode(materials);h=31*h+System.identityHashCode(source);return 31*h+System.identityHashCode(frames);}
        @Override public boolean equals(Object other){return other instanceof Key k&&matches(k.spec,k.snapshot,k.pose,k.materials,k.source,k.frames);}
        boolean matches(NativeRampGeometry.Spec spec,NativeRampGeometry.Snapshot snapshot,long pose,Materials materials,SourceFaces source,List<NativeRampTextureCoordinates.Frame> frames){return this.spec==spec&&this.snapshot==snapshot&&this.pose==pose&&this.materials==materials&&this.source==source&&this.frames==frames;}
        long bytes(){long bytes=256+snapshot.retainedBytes()+materials.retainedBytes()+512+(frames==null?0:96L*frames.size());for(var origin:spec.origins())bytes+=128L+80L*origin.shapes().size();return bytes;}
    }
    public static final class Bake {
        private final NativeFlatWallModel.Bake materials;private final LinkedHashMap<Key,Draw> draws=new LinkedHashMap<>(16,.75F,true);private final IdentityHashMap<NativeRampGeometry.Snapshot,Key> bindings=new IdentityHashMap<>();private long retained,builds;private Key hotKey;private Draw hotDraw;
        private Bake(NativeFlatWallModel.Bake materials){this.materials=materials;}
        public synchronized void clear(){draws.clear();bindings.clear();retained=0;hotKey=null;hotDraw=null;}
        public synchronized long retainedBytes(){return retained;}public synchronized long builds(){return builds;}public synchronized int boundSnapshots(){return bindings.size();}public synchronized int cachedEntries(){return draws.size();}
        public Draw draw(NativeRampGeometry.Spec spec,NativeRampGeometry.Snapshot snapshot,double pose,Materials choices,SourceFaces source){return draw(spec,snapshot,pose,choices,source,null);}
        public Draw drawBelt(NativeRampBeltGeometry.Snapshot belt,Materials choices,SourceFaces source){return draw(belt.spec(),belt.geometry(),1,choices,source,belt.frames());}
        private synchronized Draw draw(NativeRampGeometry.Spec spec,NativeRampGeometry.Snapshot snapshot,double pose,Materials choices,SourceFaces source,List<NativeRampTextureCoordinates.Frame> frames){
            if(!Double.isFinite(pose)||pose<0||pose>1)throw new IllegalArgumentException("Ramp cosmetic pose");Objects.requireNonNull(choices);Objects.requireNonNull(source);if(source.state!=choices.source)throw new IllegalArgumentException("Ramp fallback source mismatch");
            if(hotKey!=null&&hotKey.matches(spec,snapshot,Double.doubleToLongBits(pose),choices,source,frames))return hotDraw;
            var key=bindings.get(snapshot);if(key==null||!key.matches(spec,snapshot,Double.doubleToLongBits(pose),choices,source,frames))key=new Key(spec,snapshot,pose,choices,source,frames);var found=draws.get(key);
            if(found==null){var result=new Builder();int pieceIndex=0;for(var piece:snapshot.pieces()){var frame=frames==null?NativeRampTextureCoordinates.frame(spec,piece,pose):frames.get(pieceIndex++);var choice=choices.at(frame.origin());var b=piece.box();var box=new RampGeometry.Box(b.minX,b.minY,b.minZ,b.maxX,b.maxY,b.maxZ);
                for(var face:Direction.values()){int index=face.get3DDataValue(),selected=texture(choice,face);var material=selected<0?source.materials[index]:materials.surfaces.get(materials.index(selected));double aspect=material.usedU()/material.usedV();if(selected<0){var contents=material.sprite().contents();aspect=(float)contents.width()/contents.height();}double u=face.getAxis()==Direction.Axis.X?frame.vShift():frame.uShift(),v=face.getAxis()==Direction.Axis.Y?frame.vShift():0;boolean stretch=choice!=null&&choice.stairs()&&choice.layout()==0;int layout=choice==null?-1:choice.layout();RampFaceTexture.emit(box,index,aspect,stretch?1:frame.bottom(),stretch?0:frame.top(),u,v,layout,(x,y,z,tu,tv)->result.vertex(x,y,z,tu,tv,material,face,source.tints[index]));}
            }found=new Draw(result,source.glass);builds++;long weight=key.bytes()+found.retainedBytes()+192;if(weight<=BUDGET){draws.put(key,found);retained+=weight;while(retained>BUDGET||draws.size()>1024){var old=draws.entrySet().iterator().next();retained-=old.getKey().bytes()+old.getValue().retainedBytes()+192;draws.remove(old.getKey());var binding=bindings.get(old.getKey().snapshot);if(binding!=null&&binding.equals(old.getKey()))bindings.remove(old.getKey().snapshot);}}}
            if(draws.containsKey(key)){bindings.put(snapshot,key);hotKey=key;hotDraw=found;}else{hotKey=null;hotDraw=null;}return found;
        }
    }
    private NativeRampPartRenderer(){}
}
