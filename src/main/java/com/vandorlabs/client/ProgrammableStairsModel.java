package com.vandorlabs.client;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.vandorlabs.blocks.ModBlocks;
import com.vandorlabs.blocks.ProgrammableHousingState;
import com.vandorlabs.blocks.ProgrammableStairGeometry;
import com.vandorlabs.persistence.SaveSchema;
import com.vandorlabs.persistence.StairState;
import com.vandorlabs.render.HousingTexturePolicy;
import com.vandorlabs.tiles.FaceTextures;
import com.vandorlabs.tiles.ScreenHousingTextures;
import net.minecraft.block.BlockStairs;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.*;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.renderer.vertex.VertexFormat;
import net.minecraft.client.renderer.vertex.VertexFormatElement;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.common.property.IExtendedBlockState;
import java.util.*;
import java.util.function.Function;
import java.util.function.Supplier;

/** Retextures vanilla stair quads; geometry keeps vanilla corner and culling rules. */
public final class ProgrammableStairsModel implements IBakedModel {
    static final long MAX_RESULT_BYTES=8L*1024*1024;
    static final long MAX_GEOMETRY_BYTES=2L*1024*1024;
    private final IBakedModel base;
    private final Bake bake;
    private final int sourceKind;
    private final int itemFinish,itemSide,itemLayout,itemGeometry;
    private final FaceTextures itemFaces;
    private volatile ItemCameraTransforms paddedTransforms;
    private volatile int emptySides;
    private final ItemOverrideList overrides=new ItemOverrideList(Collections.emptyList()) {
        @Override public IBakedModel handleItemState(IBakedModel original,ItemStack stack,net.minecraft.world.World world,net.minecraft.entity.EntityLivingBase entity) {
            NBTTagCompound tag=stack.getSubCompound("BlockEntityTag");
            if(tag==null)return original;
            int tread=StairState.readTread(tag.hasKey(StairState.TREAD,3),tag.getInteger(StairState.TREAD));
            return new ProgrammableStairsModel(base,bake,tag.getInteger(SaveSchema.Screen.HOUSING_TEXTURE),
                    tag.hasKey("SideTexture",3)?tag.getInteger("SideTexture"):-1,
                    new FaceTextures(tag.getBoolean("FaceTexturesEnabled"),tag.getIntArray("FaceTextures")),
                    HousingTexturePolicy.stairItemLayout(tag.hasKey("MainTextureLayout",3),tag.getInteger("MainTextureLayout"),tag.getBoolean("SlabTileSides")),
                    StairState.packed(tread,StairState.readRoll(tag.getInteger(StairState.ROLL))));
        }
    };

    /** One owner per ModelBakeEvent, shared by all listed states and configured item wrappers.
     * Resource reload replaces this owner; no static cache retains a previous atlas/model. */
    public static final class Bake {
        private final Function<String,TextureAtlasSprite> sprites;
        private final Supplier<VertexFormat> formats;
        private volatile Generation generation;
        public Bake(){this(name->Minecraft.getMinecraft().getTextureMapBlocks().getAtlasSprite(name),()->DefaultVertexFormats.ITEM);}
        Bake(Function<String,TextureAtlasSprite> sprites,Supplier<VertexFormat> formats){this.sprites=sprites;this.formats=formats;}
        public ProgrammableStairsModel wrap(IBakedModel base){return new ProgrammableStairsModel(base,this,0,-1,FaceTextures.DEFAULT,1,8);}
        private Generation current(){
            VertexFormat live=formats.get();Generation current=generation;
            if(current!=null&&live.equals(current.format))return current;
            synchronized(this){current=generation;if(current!=null&&live.equals(current.format))return current;
                generation=current=new Generation(live);return current;
            }
        }
        long cachedResultBytes(){Generation current=generation;return current==null?0:current.results.bytes;}
        long cachedGeometryBytes(){Generation current=generation;return current==null?0:retainedBytes(current.geometry);}
        long cachedResults(){Generation current=generation;return current==null?0:current.results.values.size();}
        long cachedGeometries(){Generation current=generation;if(current==null)return 0;current.geometry.cleanUp();return current.geometry.size();}
    }
    /** Old chunk workers can finish using their immutable format without repopulating a new generation. */
    private static final class Generation {
        final VertexFormat format,liveFormat;
        final Cache<GeometryKey,List<BakedQuad>> geometry=CacheBuilder.newBuilder().concurrencyLevel(1).maximumWeight(MAX_GEOMETRY_BYTES)
                .weigher((GeometryKey key,List<BakedQuad> quads)->retainedBytes(quads)).build();
        final ResultStore results=new ResultStore();
        Generation(VertexFormat live){liveFormat=live;format=new VertexFormat(live);}
    }
    /** Lock-free reads without per-hit LRU queue nodes. Admission alone evicts in
     * insertion order; all variable-sized records are charged before publication. */
    private static final class ResultStore {
        final java.util.concurrent.ConcurrentMap<ResultKey,ResultEntry> values=new java.util.concurrent.ConcurrentHashMap<>();
        final ArrayDeque<ResultEntry> order=new ArrayDeque<>();
        volatile long bytes;
        List<BakedQuad> get(ResultLookup key){ResultEntry entry=values.get(key);return entry==null?null:entry.quads;}
        List<BakedQuad> publish(ResultKey key,List<BakedQuad> quads){
            int weight=retainedBytes(quads);if(weight>MAX_RESULT_BYTES)return quads;
            ResultEntry built=new ResultEntry(key,quads,weight);
            synchronized(this){
                ResultEntry raced=values.get(key);if(raced!=null)return raced.quads;
                while(bytes+weight>MAX_RESULT_BYTES){ResultEntry oldest=order.removeFirst();values.remove(oldest.key);bytes-=oldest.weight;}
                order.addLast(built);bytes+=weight;values.put(key,built);
            }
            return quads;
        }
    }
    private static final class ResultEntry {
        final ResultKey key;final List<BakedQuad> quads;final int weight;
        ResultEntry(ResultKey key,List<BakedQuad> quads,int weight){this.key=key;this.quads=quads;this.weight=weight;}
    }
    /** Conservative owned-retention estimate: entry/key/list/settings overhead and complete quad arrays.
     * Source models and atlas sprites belong to Minecraft's bake, not to these additional caches.
     * The result key never retains source quads, so source-geometry eviction really releases its list. */
    private static int retainedBytes(List<BakedQuad> quads){
        long bytes=512;for(BakedQuad quad:quads)bytes+=112L+4L*quad.getVertexData().length;
        return (int)Math.min(Integer.MAX_VALUE,bytes);
    }
    private static long retainedBytes(Cache<?,List<BakedQuad>> cache){
        cache.cleanUp();long bytes=0;for(List<BakedQuad> quads:cache.asMap().values())bytes+=retainedBytes(quads);return bytes;
    }
    private static final class GeometryKey {
        final TextureAtlasSprite sprite;final int pose,settings,hash;
        GeometryKey(TextureAtlasSprite sprite,int pose,int settings){this.sprite=sprite;this.pose=pose;this.settings=settings;hash=31*(31*System.identityHashCode(sprite)+pose)+settings;}
        @Override public int hashCode(){return hash;}
        @Override public boolean equals(Object value){if(!(value instanceof GeometryKey))return false;GeometryKey key=(GeometryKey)value;return sprite==key.sprite&&pose==key.pose&&settings==key.settings;}
    }
    private static final class ResultKey {
        final Object source;final FaceTextures faces;final int pose,settings,side,finish,sideFinish,layout,hash;
        ResultKey(ResultLookup lookup){source=lookup.source;faces=lookup.faces;pose=lookup.pose;settings=lookup.settings;side=lookup.side;finish=lookup.finish;sideFinish=lookup.sideFinish;layout=lookup.layout;hash=lookup.hash;}
        @Override public int hashCode(){return hash;}
        @Override public boolean equals(Object value){
            if(value instanceof ResultLookup)return value.equals(this);
            if(!(value instanceof ResultKey))return false;ResultKey key=(ResultKey)value;
            return source==key.source&&pose==key.pose&&settings==key.settings&&side==key.side&&finish==key.finish&&sideFinish==key.sideFinish&&layout==key.layout&&faces.equals(key.faces);
        }
    }
    /** Cleared before calling delegate/atlas code; no world, item or state survives a worker lookup. */
    private static final class ResultLookup {
        Object source;FaceTextures faces;int pose,settings,side,finish,sideFinish,layout,hash;
        void set(Object source,int pose,int settings,EnumFacing side,int finish,int sideFinish,int layout,FaceTextures faces){
            this.source=source;this.pose=pose;this.settings=settings;this.side=side==null?6:side.getIndex();this.finish=finish;this.sideFinish=sideFinish;this.layout=layout;this.faces=faces;
            int h=31*(31*(31*System.identityHashCode(source)+pose)+settings)+this.side;h=31*(31*(31*h+finish)+sideFinish)+layout;hash=31*h+faces.hashCode();
        }
        void clear(){source=null;faces=null;}
        @Override public int hashCode(){return hash;}
        @Override public boolean equals(Object value){if(!(value instanceof ResultKey))return false;ResultKey key=(ResultKey)value;
            return source==key.source&&pose==key.pose&&settings==key.settings&&side==key.side&&finish==key.finish&&sideFinish==key.sideFinish&&layout==key.layout&&faces.equals(key.faces);}
    }
    private static final ThreadLocal<ResultLookup> LOOKUP=ThreadLocal.withInitial(ResultLookup::new);
    public ProgrammableStairsModel(IBakedModel base){this(base,new Bake(),0,-1,FaceTextures.DEFAULT,1,8);}
    private ProgrammableStairsModel(IBakedModel base,Bake bake,int finish,int sideFinish,FaceTextures faces,int layout,int geometry){
        this.base=base;this.bake=bake;itemFinish=finish;itemSide=sideFinish;itemFaces=faces;itemLayout=layout;itemGeometry=geometry;
        // Forge 1.12 wraps its JSON SimpleBakedModel in this exact animation-aware
        // class. Do not broaden this to arbitrary PerspectiveMapWrapper subclasses.
        sourceKind=base.getClass()==SimpleBakedModel.class?1:
                base.getClass().getName().equals("net.minecraftforge.client.model.ModelLoader$VanillaModelWrapper$1")?2:0;
    }
    public List<BakedQuad> getQuads(IBlockState state,EnumFacing side,long seed){
        int finish=itemFinish,sideFinish=itemSide,layout=itemLayout,settings=itemGeometry;FaceTextures faces=itemFaces;
        if(state instanceof IExtendedBlockState){
            IExtendedBlockState extended=(IExtendedBlockState)state;
            Integer value=extended.getValue(ProgrammableHousingState.STAIR_GEOMETRY);if(value!=null)settings=value;
            value=extended.getValue(ProgrammableHousingState.SIDE_FINISH);if(value!=null)sideFinish=value;
            value=extended.getValue(ProgrammableHousingState.FINISH);if(value!=null)finish=value;
            FaceTextures sampled=extended.getValue(ProgrammableHousingState.FACES);if(sampled!=null)faces=sampled;
            value=extended.getValue(ProgrammableHousingState.TILE_SIDES);if(value!=null)layout=value;
        }
        if(settings!=8&&side!=null)return Collections.emptyList();
        // The exact native model ignores state/seed. Forge's JSON wrapper
        // delegates to it only when no animation property is supplied.
        boolean reusable=sourceKind==1||sourceKind==2&&(settings!=8||!(state instanceof IExtendedBlockState)
                ||!((IExtendedBlockState)state).getUnlistedNames().contains(net.minecraftforge.common.property.Properties.AnimationProperty));
        int sideBit=1<<(side==null?6:side.getIndex());
        if(settings==8&&reusable&&(emptySides&sideBit)!=0)return Collections.emptyList();
        List<BakedQuad> source=settings==8&&!reusable?base.getQuads(state,side,seed):null;
        if(source!=null&&source.isEmpty())return Collections.emptyList();
        IBlockState orientation=state==null?ModBlocks.PROGRAMMABLE_STAIRS.getDefaultState().withProperty(BlockStairs.FACING,EnumFacing.EAST):state;
        EnumFacing facing=orientation.getValue(BlockStairs.FACING);
        // Native quads already encode half/corner geometry; with roll zero only
        // facing affects their local material selection.
        int pose=settings==8?facing.getHorizontalIndex():(facing.getHorizontalIndex()*2+(orientation.getValue(BlockStairs.HALF)==BlockStairs.EnumHalf.TOP?1:0))*5+orientation.getValue(BlockStairs.SHAPE).ordinal();
        Generation current=bake.current();Object sourceIdentity=settings==8?base:base.getParticleTexture();
        ResultKey key=null;
        if(reusable){
            ResultLookup lookup=LOOKUP.get();lookup.set(sourceIdentity,pose,settings,side,finish,sideFinish,layout,faces);
            try{List<BakedQuad> cached=current.results.get(lookup);if(cached!=null)return cached;key=new ResultKey(lookup);}
            finally{lookup.clear();}
        }
        // Admission proves the source is the immutable native bake in this format.
        // Rechecking its quads on every warm hit would restore the per-face work
        // this cache removes. Dynamic/nonstandard sources never publish a result.
        if(settings==8){
            if(source==null)source=base.getQuads(state,side,seed);
            if(source.isEmpty()){
                if(reusable)synchronized(this){emptySides|=sideBit;}
                return Collections.emptyList();
            }
            if(key!=null&&!compatible(source,current))key=null;
        }
        if(source==null){
            GeometryKey shape=new GeometryKey((TextureAtlasSprite)sourceIdentity,pose,settings);if(reusable)source=current.geometry.getIfPresent(shape);
            if(source==null){
                source=Collections.unmodifiableList(ProgrammableStairMesh.quads(orientation,settings,shape.sprite,current.format));
                if(reusable){List<BakedQuad> raced=current.geometry.asMap().putIfAbsent(shape,source);if(raced!=null)source=raced;}
            }
        }
        List<BakedQuad> result=new ArrayList<>(source.size());
        for(int i=0;i<source.size();i++){
            BakedQuad quad=source.get(i);EnumFacing textureFace=ProgrammableStairGeometry.unrolledFace(orientation,settings>>5,quad.getFace());
            int local=HousingTexturePolicy.stairLocalFace(textureFace.getIndex(),facing.getIndex());
            int texture=faces.texture(local,ScreenHousingTextures.clamp(HousingTexturePolicy.baseChoice(local,finish,sideFinish,true)));
            result.add(retexture(quad,bake.sprites.apply(ScreenHousingTextures.texture(texture)),layout,textureFace,current.format));
        }
        result=Collections.unmodifiableList(result);
        return key==null?result:current.results.publish(key,result);
    }
    private static boolean compatible(List<BakedQuad> source,Generation generation){
        for(int i=0;i<source.size();i++){BakedQuad quad=source.get(i);if(quad.getClass()!=BakedQuad.class||quad.getFormat()!=generation.liveFormat||quad.getVertexData().length!=4*generation.format.getIntegerSize())return false;}
        return true;
    }
    private static BakedQuad retexture(BakedQuad quad,TextureAtlasSprite next,int layout,EnumFacing textureFace,VertexFormat generationFormat){
        TextureAtlasSprite old=quad.getSprite();int[] data=quad.getVertexData().clone();
        VertexFormat sourceFormat=quad.getFormat();boolean coherent=data.length==4*sourceFormat.getIntegerSize();
        VertexFormat format=!coherent?sourceFormat:generationFormat.equals(sourceFormat)?generationFormat:new VertexFormat(sourceFormat);
        int stride=data.length/4,uv=coherent?format.getUvOffsetById(0)/4:4,position=0;
        if(coherent)for(int element=0;element<format.getElementCount();element++)if(format.getElement(element).getUsage()==VertexFormatElement.EnumUsage.POSITION){position=format.getOffset(element)/4;break;}
        float[] u=new float[4],v=new float[4];float lo=16,hi=0,ulo=16,uhi=0;
        for(int i=0;i<4;i++) {
            u[i]=(Float.intBitsToFloat(data[i*stride+uv])-old.getMinU())/(old.getMaxU()-old.getMinU())*16;
            v[i]=(Float.intBitsToFloat(data[i*stride+uv+1])-old.getMinV())/(old.getMaxV()-old.getMinV())*16;
            lo=Math.min(lo,v[i]);hi=Math.max(hi,v[i]);ulo=Math.min(ulo,u[i]);uhi=Math.max(uhi,u[i]);
        }
        double unitU=1D/16,unitV=1D/16,aspect=UnifiedTextureSprites.aspect(next);
        for(int a=0;a<4;a++)for(int b=0;b<4;b++){
            double length=0;for(int axis=0;axis<3;axis++){double delta=Float.intBitsToFloat(data[a*stride+position+axis])-Float.intBitsToFloat(data[b*stride+position+axis]);length+=delta*delta;}length=Math.sqrt(length);
            if(Math.abs(u[a]-u[b])>1e-6 && Math.abs(v[a]-v[b])<1e-6)unitU=length/Math.abs(u[a]-u[b]);
            if(Math.abs(v[a]-v[b])>1e-6 && Math.abs(u[a]-u[b])<1e-6)unitV=length/Math.abs(v[a]-v[b]);
        }
        boolean fit=com.vandorlabs.render.TextureLayoutMode.fit(layout,(uhi-ulo)*unitU,(hi-lo)*unitV,aspect);
        for(int i=0;i<4;i++) {
            double uu=com.vandorlabs.render.HousingTexturePolicy.stairU(layout,fit,u[i],ulo,uhi,aspect);
            double vv=com.vandorlabs.render.HousingTexturePolicy.stairV(layout,fit,v[i],lo,hi,textureFace.getIndex(),aspect);
            data[i*stride+uv]=Float.floatToRawIntBits(next.getInterpolatedU(uu));
            data[i*stride+uv+1]=Float.floatToRawIntBits(next.getInterpolatedV(vv));
        }
        // Some patched legacy quads normalize their record width independently of
        // Forge's stored format. Preserve that original pass-through contract on
        // the uncached path instead of throwing or guessing an extended layout.
        return coherent?ProgrammableStairMesh.retainedQuad(data,quad.getTintIndex(),quad.getFace(),next,quad.shouldApplyDiffuseLighting(),format)
                :new BakedQuad(data,quad.getTintIndex(),quad.getFace(),next,quad.shouldApplyDiffuseLighting(),format);
    }
    public boolean isAmbientOcclusion(){return base.isAmbientOcclusion();}
    public boolean isGui3d(){return base.isGui3d();}
    public boolean isBuiltInRenderer(){return false;}
    public TextureAtlasSprite getParticleTexture(){return base.getParticleTexture();}
    public ItemCameraTransforms getItemCameraTransforms(){
        ItemCameraTransforms current=paddedTransforms;if(current!=null)return current;
        synchronized(this){if(paddedTransforms==null)paddedTransforms=ControlItemPadding.fit(this,base.getItemCameraTransforms());return paddedTransforms;}
    }
    public ItemOverrideList getOverrides(){return overrides;}
}
