package com.vandorlabs.neoforge;

import com.vandorlabs.neoforge.segmented.NativeSegmentedTerrainSnapshot;
import com.vandorlabs.segmented.*;
import java.util.*;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.*;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.ChunkRenderTypeSet;
import net.neoforged.neoforge.client.model.BakedModelWrapper;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.common.util.TriState;

/** Immutable cell snapshots become ordinary terrain; section workers never inspect live tiles. */
@EventBusSubscriber(modid="vandorlabs",bus=EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
public final class NativeSegmentedTerrainModel extends BakedModelWrapper<BakedModel> {
    public static final ResourceLocation HEADER=ResourceLocation.parse("vandorlabs:block/segmented_door_header");
    @SubscribeEvent public static void additional(net.neoforged.neoforge.client.event.ModelEvent.RegisterAdditional event){
        if(net.minecraft.core.registries.BuiltInRegistries.BLOCK.containsKey(ResourceLocation.parse("vandorlabs:segmented_door"))||"true".equals(System.getenv("VANDOR_SEGMENTED_TERRAIN_CHECK")))event.register(ModelResourceLocation.standalone(HEADER));
    }
    private static final java.util.concurrent.atomic.AtomicLong GENERATIONS=new java.util.concurrent.atomic.AtomicLong();
    private static final com.google.common.cache.Cache<Object,Bake> PENDING=com.google.common.cache.CacheBuilder.newBuilder().weakKeys().maximumSize(2).build();
    private static volatile Bake active;
    public static long activeGeneration(){var value=active;return value==null?0:value.generation;}
    @SubscribeEvent public static synchronized void completed(net.neoforged.neoforge.client.event.ModelEvent.BakingCompleted event){var next=PENDING.getIfPresent(event.getModelBakery());PENDING.invalidate(event.getModelBakery());if(next!=null&&next.wrapped&&(active==null||next.generation>active.generation))active=next;}
    private record Key(Direction facing,int texture,boolean header,boolean body,boolean top,boolean bottom,long phase){}
    public static final class Bake {
        private final long generation=GENERATIONS.incrementAndGet();private volatile boolean wrapped;
        private final NativeSegmentedRenderer.Bake shared;
        private final BakedModel header;
        private final boolean canonical;
        private final TextureAtlasSprite headerSprite;
        private final SegmentedPanelMesh.Texture headerTexture,edge;
        private final com.google.common.cache.Cache<Key,List<BakedQuad>> quads=com.google.common.cache.CacheBuilder.newBuilder().maximumWeight(16*1024*1024)
                .weigher((Key key,List<BakedQuad> value)->1024+value.stream().mapToInt(q->96+q.getVertices().length*4).sum()).build();
        public Bake(Map<ModelResourceLocation,BakedModel> models,NativeSegmentedRenderer.Bake shared,ResourceManager resources){this(null,models,shared,resources);}
        public Bake(Object bakery,Map<ModelResourceLocation,BakedModel> models,NativeSegmentedRenderer.Bake shared,ResourceManager resources){
            this.shared=Objects.requireNonNull(shared);header=Objects.requireNonNull(models.get(ModelResourceLocation.standalone(HEADER)),"Missing authored Segmented header");
            canonical=NativeDoorArtwork.isStatic(header)&&NativeDoorArtwork.canonicalGeometry(HEADER.toString(),resources);
            var originals=header.getQuads(null,null,RandomSource.create(0),ModelData.EMPTY,null);
            headerSprite=originals.isEmpty()?shared.edgeSprite():originals.getFirst().getSprite();
            headerTexture=texture(headerSprite);edge=shared.edge();if(bakery!=null)PENDING.put(bakery,this);
        }
        List<BakedQuad> quads(NativeSegmentedTerrainSnapshot snapshot){
            var key=new Key(snapshot.facing(),snapshot.texture(),canonical&&snapshot.header(),snapshot.body(),snapshot.top(),snapshot.bottom(),snapshot.body()?SegmentedPanelMesh.tilePhase(snapshot.column(),snapshot.row(),shared.artwork(snapshot.texture()).surface().aspect):0);
            try{return quads.get(key,()->build(key));}catch(java.util.concurrent.ExecutionException failure){throw new IllegalStateException("Segmented terrain mesh",failure.getCause());}
        }
        private List<BakedQuad> build(Key key){
            var out=new ArrayList<BakedQuad>();var material=shared.artwork(key.texture);int rotation=rotation(key.facing);
            if(key.header)SegmentedTerrainQuads.build(rotation,true,false,key.top,key.bottom,material.surface(),headerTexture,0L,
                    (direction,headerFace,shade,data)->out.add(new BakedQuad(data,-1,Direction.from3DDataValue(direction),headerSprite,shade,header.useAmbientOcclusion()&&!key.body)));
            if(key.body)SegmentedTerrainQuads.build(rotation,false,true,key.top,key.bottom,material.surface(),edge,key.phase,(direction,headerFace,shade,data)->{
                boolean front=key.facing.getAxis()==Direction.Axis.Z?direction==2||direction==3:direction==4||direction==5;
                out.add(new BakedQuad(data,-1,Direction.from3DDataValue(direction),front?material.lowerSprite():shared.edgeSprite(),false,false));
            });
            return List.copyOf(out);
        }
        public long retainedBytes(){quads.cleanUp();return quads.asMap().values().stream().mapToLong(list->1024L+list.stream().mapToLong(q->96L+q.getVertices().length*4L).sum()).sum();}
        private List<BakedQuad> customHeader(NativeSegmentedTerrainSnapshot snapshot,Direction side,RandomSource random,ModelData data,RenderType layer){
            if(canonical||!snapshot.header())return List.of();int rotation=rotation(snapshot.facing());Direction original=side==null?null:inverse(side,rotation);
            var source=header.getQuads(null,original,random,data,layer);if(rotation==0)return source;var out=new ArrayList<BakedQuad>(source.size());for(var quad:source){int[] raw=quad.getVertices().clone();int stride=raw.length/4;if(raw.length%4!=0||stride<8)throw new IllegalArgumentException("Segmented header vertex format");for(int v=0;v<4;v++){int at=v*stride;float x=Float.intBitsToFloat(raw[at]),z=Float.intBitsToFloat(raw[at+2]);raw[at]=Float.floatToRawIntBits(rotation==1?z:rotation==2?1-x:1-z);raw[at+2]=Float.floatToRawIntBits(rotation==1?1-x:rotation==2?1-z:x);int n=raw[at+7],nx=(byte)n,nz=(byte)(n>>>16),rx=rotation==1?nz:rotation==2?-nx:-nz,rz=rotation==1?-nx:rotation==2?-nz:nx;raw[at+7]=(n&0xff00ff00)|(rx&255)|((rz&255)<<16);}out.add(new BakedQuad(raw,quad.getTintIndex(),rotate(quad.getDirection(),rotation),quad.getSprite(),quad.isShade(),quad.hasAmbientOcclusion()&&!snapshot.body()));}return out;
        }
    }
    private static SegmentedPanelMesh.Texture texture(TextureAtlasSprite sprite){float u0=sprite.getU0(),u1=u0+(sprite.getU1()-u0)*NativeDoorArtwork.usedU(sprite),v0=sprite.getV0(),v1=v0+(sprite.getV1()-v0)*NativeDoorArtwork.usedV(sprite);return new SegmentedPanelMesh.Texture(){public float u(double pixel){return u0+(u1-u0)*(float)pixel/16F;}public float v(double pixel){return v0+(v1-v0)*(float)pixel/16F;}};}
    private static int rotation(Direction facing){return switch(facing){case NORTH->0;case EAST->3;case SOUTH->2;case WEST->1;default->throw new IllegalArgumentException("Segmented facing");};}
    private static Direction rotate(Direction direction,int r){if(direction.getAxis().isVertical())return direction;return r==1?direction.getCounterClockWise():r==2?direction.getOpposite():r==3?direction.getClockWise():direction;}
    private static Direction inverse(Direction direction,int r){return rotate(direction,(4-r)&3);}
    private final Bake bake;
    private NativeSegmentedTerrainModel(BakedModel parent,Bake bake){super(parent);this.bake=Objects.requireNonNull(bake);}
    public static BakedModel wrap(BakedModel parent,Bake bake){bake.wrapped=true;return new NativeSegmentedTerrainModel(parent,bake);}
    private static final class Layers {static final RenderType SOLID=RenderType.solid();static final ChunkRenderTypeSet TYPES=ChunkRenderTypeSet.of(SOLID);}
    private static NativeSegmentedTerrainSnapshot snapshot(BlockState state,ModelData data){var snapshot=data.get(NativeSegmentedTerrainSnapshot.DATA);if(snapshot!=null)return snapshot;var facing=state!=null&&state.hasProperty(com.vandorlabs.neoforge.segmented.NativeSegmentedDoorBlock.FACING)?state.getValue(com.vandorlabs.neoforge.segmented.NativeSegmentedDoorBlock.FACING):Direction.NORTH;return new NativeSegmentedTerrainSnapshot(facing,-1,true,false,true,true,0);}
    @Override public ChunkRenderTypeSet getRenderTypes(BlockState state,RandomSource random,ModelData data){var value=snapshot(state,data);var base=super.getRenderTypes(state,random,data);return value.header()||value.body()?ChunkRenderTypeSet.union(base,Layers.TYPES):base;}
    @Override public List<BakedQuad> getQuads(BlockState state,Direction side,RandomSource random,ModelData data,RenderType layer){
        var base=super.getQuads(state,side,random,data,layer);if(layer!=null&&layer!=Layers.SOLID)return base;var value=snapshot(state,data);List<BakedQuad> terrain=side==null?bake.quads(value):List.of();var custom=bake.customHeader(value,side,random,data,layer);if(base.isEmpty()&&custom.isEmpty())return terrain;if(terrain.isEmpty()&&custom.isEmpty())return base;var out=new ArrayList<BakedQuad>(base.size()+terrain.size()+custom.size());out.addAll(base);out.addAll(terrain);out.addAll(custom);return List.copyOf(out);
    }
    @Override public boolean useAmbientOcclusion(){return bake.header.useAmbientOcclusion();}
    @Override public TriState useAmbientOcclusion(BlockState state,ModelData data,RenderType layer){return snapshot(state,data).body()?TriState.FALSE:bake.header.useAmbientOcclusion(state,data,layer);}
    @Override public TextureAtlasSprite getParticleIcon(){return bake.header.getParticleIcon();}
    @Override public TextureAtlasSprite getParticleIcon(ModelData data){return bake.header.getParticleIcon(data);}
}
