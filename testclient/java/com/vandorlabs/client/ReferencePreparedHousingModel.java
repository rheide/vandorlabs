package com.vandorlabs.client;

import com.vandorlabs.blocks.BlockAnimatedScreenSelector;
import com.vandorlabs.blocks.BlockProgrammableSlab;
import com.vandorlabs.blocks.ProgrammableHousingState;
import com.vandorlabs.render.ScreenHousingMesh;
import com.vandorlabs.tiles.ScreenHousingTextures;
import net.minecraft.block.BlockSlab;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.EnumFaceDirection;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.block.model.ItemCameraTransforms;
import net.minecraft.client.renderer.block.model.ItemOverrideList;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.renderer.vertex.VertexFormat;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.common.property.IExtendedBlockState;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Frozen delivered performance-patch model for incremental CPU/allocation comparisons. */
public final class ReferencePreparedHousingModel implements IBakedModel {
    private static final EnumFacing[] FACE = {EnumFacing.UP, EnumFacing.DOWN,
            EnumFacing.WEST, EnumFacing.EAST, EnumFacing.NORTH, EnumFacing.SOUTH};
    private final IBakedModel delegate;
    private final boolean slab;
    private final boolean storage;
    private final java.util.function.Function<String,TextureAtlasSprite> sprites;
    private final java.util.function.Supplier<net.minecraft.client.renderer.vertex.VertexFormat> formats;
    static final int MAX_FACE_LISTS=1024;
    static final long MAX_PROFILE_BYTES=8L*1024*1024;
    /** A format change publishes a new generation; in-flight chunk workers retain theirs. */
    private static final class Geometry {
        final VertexFormat format;
        final Map<Long,BakedQuad> variants=new ConcurrentHashMap<>();
        final com.google.common.cache.Cache<String,List<BakedQuad>> joinedFaces=com.google.common.cache.CacheBuilder.newBuilder().maximumSize(2048).build();
        final com.google.common.cache.Cache<Long,List<BakedQuad>> faceLists=com.google.common.cache.CacheBuilder.newBuilder().maximumSize(MAX_FACE_LISTS).build();
        final com.google.common.cache.Cache<ProfileKey,List<BakedQuad>> profiles=com.google.common.cache.CacheBuilder.newBuilder().maximumWeight(MAX_PROFILE_BYTES)
                .weigher((ProfileKey key,List<BakedQuad> quads)->profileBytes(key,quads)).build();
        Geometry(VertexFormat live){format=new VertexFormat(live);}
    }
    private volatile Geometry geometry;
    private static final ThreadLocal<ProfileLookup> PROFILE_LOOKUP=ThreadLocal.withInitial(ProfileLookup::new);
    long cachedFaceLists(){Geometry current=geometry;if(current==null)return 0;current.faceLists.cleanUp();return current.faceLists.size();}

    /** Both settings types defensively copy their arrays and expose only immutable values. */
    private static final class ProfileKey {
        final int finish,side,layout,visible,facing,hash;
        final boolean upper;
        final com.vandorlabs.tiles.FaceTextures faces;
        final com.vandorlabs.blocks.HousingFaceLayouts joined;
        ProfileKey(ProfileLookup lookup){finish=lookup.finish;side=lookup.side;layout=lookup.layout;visible=lookup.visible;facing=lookup.facing;upper=lookup.upper;faces=lookup.faces;joined=lookup.joined;hash=lookup.hash;}
        @Override public int hashCode(){return hash;}
        @Override public boolean equals(Object value){
            if(value instanceof ProfileLookup)return value.equals(this);
            if(!(value instanceof ProfileKey))return false;ProfileKey other=(ProfileKey)value;
            return finish==other.finish&&side==other.side&&layout==other.layout&&visible==other.visible&&facing==other.facing&&upper==other.upper
                    &&faces.equals(other.faces)&&samePlanes(joined,other.joined,visible);
        }
    }
    /** A thread-local probe is never published to a cache or retained after a lookup. */
    private static final class ProfileLookup {
        int finish,side,layout,visible,facing,hash;boolean upper;
        com.vandorlabs.tiles.FaceTextures faces;
        com.vandorlabs.blocks.HousingFaceLayouts joined;
        void set(int finish,int side,int layout,int visible,EnumFacing facing,boolean upper,com.vandorlabs.tiles.FaceTextures faces,com.vandorlabs.blocks.HousingFaceLayouts joined){
            this.finish=finish;this.side=side;this.layout=layout;this.visible=visible&63;this.facing=facing.getIndex();this.upper=upper;this.faces=faces;this.joined=joined;
            int h=31*(31*(31*(31*finish+side)+layout)+this.visible)+this.facing;h=31*h+(upper?1:0);h=31*h+faces.hashCode();
            for(EnumFacing face:FACE)if((this.visible&(1<<face.getIndex()))!=0){
                com.vandorlabs.blocks.HousingFaceLayouts.Plane plane=joined.face(face);h=31*h+(plane==null?0:1);
                if(plane!=null)for(int axis=0;axis<3;axis++){h=31*h+plane.min(axis);h=31*h+plane.size(axis);}
            }
            hash=h;
        }
        void clear(){faces=null;joined=null;}
        @Override public int hashCode(){return hash;}
        @Override public boolean equals(Object value){
            if(!(value instanceof ProfileKey))return false;ProfileKey key=(ProfileKey)value;
            return finish==key.finish&&side==key.side&&layout==key.layout&&visible==key.visible&&facing==key.facing&&upper==key.upper
                    &&faces.equals(key.faces)&&samePlanes(joined,key.joined,visible);
        }
    }
    private static boolean samePlanes(com.vandorlabs.blocks.HousingFaceLayouts a,com.vandorlabs.blocks.HousingFaceLayouts b,int visible){
        if(a==b)return true;
        for(EnumFacing face:FACE)if((visible&(1<<face.getIndex()))!=0){
            com.vandorlabs.blocks.HousingFaceLayouts.Plane x=a.face(face),y=b.face(face);if(x==y)continue;
            if(x==null||y==null)return false;
            for(int axis=0;axis<3;axis++)if(x.min(axis)!=y.min(axis)||x.size(axis)!=y.size(axis))return false;
        }
        return true;
    }
    private static int profileBytes(ProfileKey key,List<BakedQuad> quads){
        // Conservatively count shared snapshots/quads again for every retained list.
        long bytes=512+64;
        if(key.joined!=com.vandorlabs.blocks.HousingFaceLayouts.NONE){bytes+=64;for(EnumFacing face:FACE)if(key.joined.face(face)!=null)bytes+=96;}
        for(BakedQuad quad:quads)bytes+=96L+4L*quad.getVertexData().length;
        return (int)Math.min(Integer.MAX_VALUE,bytes);
    }
    long cachedProfileBytes(){Geometry current=geometry;if(current==null)return 0;current.profiles.cleanUp();long bytes=0;for(Map.Entry<ProfileKey,List<BakedQuad>> entry:current.profiles.asMap().entrySet())bytes+=profileBytes(entry.getKey(),entry.getValue());return bytes;}
    long cachedProfiles(){Geometry current=geometry;if(current==null)return 0;current.profiles.cleanUp();return current.profiles.size();}

    public ReferencePreparedHousingModel(IBakedModel delegate, boolean slab) {
        this(delegate, slab, false);
    }

    public ReferencePreparedHousingModel(IBakedModel delegate, boolean slab, boolean storage) {
        this(delegate,slab,storage,name -> Minecraft.getMinecraft().getTextureMapBlocks().getAtlasSprite(name));
    }

    ReferencePreparedHousingModel(IBakedModel delegate, boolean slab, boolean storage,java.util.function.Function<String,TextureAtlasSprite> sprites) {
        this(delegate,slab,storage,sprites,()->DefaultVertexFormats.BLOCK);
    }
    ReferencePreparedHousingModel(IBakedModel delegate,boolean slab,boolean storage,java.util.function.Function<String,TextureAtlasSprite> sprites,java.util.function.Supplier<net.minecraft.client.renderer.vertex.VertexFormat> formats){
        this.formats=formats;this.sprites=sprites;
        this.storage = storage;
        this.delegate = delegate;
        this.slab = slab;
    }

    @Override public List<BakedQuad> getQuads(@Nullable IBlockState state,
            @Nullable EnumFacing side, long rand) {
        if (state == null) return delegate.getQuads(null, side, rand);
        if (side != null) return Collections.emptyList();
        Geometry current=ensureFormat();
        int finish = 0, sideFinish=-1, visible = 63, tileSides = 0;
        com.vandorlabs.tiles.FaceTextures faces = com.vandorlabs.tiles.FaceTextures.DEFAULT;
        com.vandorlabs.blocks.HousingFaceLayouts joined=com.vandorlabs.blocks.HousingFaceLayouts.NONE;
        if (state instanceof IExtendedBlockState) {
            IExtendedBlockState extended = (IExtendedBlockState)state;
            if (extended.getValue(ProgrammableHousingState.FACES) != null)
                faces = extended.getValue(ProgrammableHousingState.FACES);
            Integer sf=extended.getValue(ProgrammableHousingState.SIDE_FINISH);
            if(sf!=null)sideFinish=sf;
            Integer f = extended.getValue(ProgrammableHousingState.FINISH);
            Integer v = extended.getValue(ProgrammableHousingState.VISIBLE);
            Integer t = extended.getValue(ProgrammableHousingState.TILE_SIDES);
            if (f != null) finish = ScreenHousingTextures.clamp(f);
            if (v != null) visible = v;
            if (t != null) tileSides = t;
            if(extended.getUnlistedNames().contains(ProgrammableHousingState.JOINED)){
                com.vandorlabs.blocks.HousingFaceLayouts value=extended.getValue(ProgrammableHousingState.JOINED);if(value!=null)joined=value;
            }
        }
        EnumFacing facing = state.getValue(BlockAnimatedScreenSelector.FACING);
        boolean upper = slab && state.getValue(BlockProgrammableSlab.HALF)
                == BlockSlab.EnumBlockHalf.TOP;
        if((visible&63)==0)return Collections.emptyList();
        boolean uniform=joined==com.vandorlabs.blocks.HousingFaceLayouts.NONE&&!faces.enabled && (!slab || sideFinish<0 || sideFinish==finish);
        long listKey=(((((long)finish*6+facing.getIndex())*2+(upper?1:0))*3+tileSides)*64)+(visible&63);
        if(uniform) {
            List<BakedQuad> cached=current.faceLists.getIfPresent(listKey);
            if(cached!=null)return cached;
        }
        ProfileKey profile=null;
        if(!uniform){
            ProfileLookup lookup=PROFILE_LOOKUP.get();
            try{
                lookup.set(finish,sideFinish,tileSides,visible,facing,upper,faces,joined);
                List<BakedQuad> cached=current.profiles.getIfPresent(lookup);if(cached!=null)return cached;
                profile=new ProfileKey(lookup);
            }finally{lookup.clear();}
        }
        List<BakedQuad> result = new ArrayList<>(6);
        for (int local = 0; local < 6; local++) {
            EnumFacing worldFace = worldFace(FACE[local], facing);
            if ((visible & (1 << worldFace.getIndex())) == 0) continue;
            int texture = faces.texture(FACE[local].getIndex(), com.vandorlabs.render.HousingTexturePolicy.baseChoice(FACE[local].getIndex(),finish,sideFinish,slab));
            final boolean storageSet = storage && !(faces.enabled && faces.choice(FACE[local].getIndex()) >= 0);
            com.vandorlabs.blocks.HousingFaceLayouts.Plane plane=joined.face(worldFace);
            BakedQuad quad=face(current,texture,facing,upper,plane==null?tileSides:0,local,storageSet);
            if(plane!=null){String key=texture+":"+facing+":"+local+":"+tileSides+":"+plane;List<BakedQuad> mapped=current.joinedFaces.getIfPresent(key);if(mapped==null){mapped=mapJoined(quad,plane,tileSides,sprites.apply("minecraft:blocks/concrete_black"));current.joinedFaces.put(key,mapped);}result.addAll(mapped);}
            else result.add(quad);
        }
        if(uniform) {
            result=Collections.unmodifiableList(result);
            current.faceLists.put(listKey,result);
        }else{
            result=Collections.unmodifiableList(result);
            current.profiles.put(profile,result);
        }
        return result;
    }

    /** OptiFine mutates the global format when shaders change, including without a resource reload. */
    private Geometry ensureFormat(){
        VertexFormat live=formats.get();Geometry current=geometry;
        if(current!=null&&live.equals(current.format))return current;
        synchronized(this){current=geometry;if(current!=null&&live.equals(current.format))return current;
            geometry=current=new Geometry(live);return current;
        }
    }
    /** Fit preserves the released centered crop and covers the entire joined face. */
    static List<BakedQuad> mapJoined(BakedQuad quad,com.vandorlabs.blocks.HousingFaceLayouts.Plane plane,int layout,TextureAtlasSprite margin){
        int[] data=quad.getVertexData().clone();int stride=data.length/4;TextureAtlasSprite sprite=quad.getSprite();int[] axes=new int[2];double[] signs=new double[2];
        for(int uv=0;uv<2;uv++)for(int vertex=1;vertex<4;vertex++){
            double delta=Float.intBitsToFloat(data[vertex*stride+4+uv])-Float.intBitsToFloat(data[4+uv]);
            double other=Float.intBitsToFloat(data[vertex*stride+5-uv])-Float.intBitsToFloat(data[5-uv]);
            if(Math.abs(delta)<1e-8||Math.abs(other)>1e-8)continue;
            for(int axis=0;axis<3;axis++){double change=Float.intBitsToFloat(data[vertex*stride+axis])-Float.intBitsToFloat(data[axis]);if(Math.abs(change)>.5){axes[uv]=axis;signs[uv]=delta/change;}}
        }
        double aspect=UnifiedTextureSprites.aspect(sprite),surface=plane.size(axes[0])/(double)plane.size(axes[1]);
        for(int vertex=0;vertex<4;vertex++)for(int uv=0;uv<2;uv++){
            double value=(Float.intBitsToFloat(data[vertex*stride+axes[uv]])-plane.min(axes[uv]))/plane.size(axes[uv]);if(signs[uv]<0)value=1-value;
            if(layout==2){double fraction=uv==0?Math.min(1,surface/aspect):Math.min(1,aspect/surface);value=.5+(value-.5)*fraction;}
            data[vertex*stride+4+uv]=Float.floatToRawIntBits(uv==0?sprite.getInterpolatedU(value*16):sprite.getInterpolatedV(value*16));
        }
        return Collections.singletonList(retainedQuad(data,quad.getTintIndex(),quad.getFace(),sprite,quad.shouldApplyDiffuseLighting(),quad.getFormat()));
    }

    private BakedQuad face(Geometry current,int texture,EnumFacing facing,boolean upper,int layout,int local,boolean storageSet) {
        long key=(((((long)texture*6+facing.getIndex())*2+(upper?1:0))*3+layout)*6+local)*2+(storageSet?1:0);
        BakedQuad cached=current.variants.get(key);
        if(cached!=null)return cached;
        BakedQuad built=build(current.format,texture,facing,upper,layout,local,storageSet);
        BakedQuad raced=current.variants.putIfAbsent(key,built);
        return raced==null?built:raced;
    }

    private BakedQuad build(VertexFormat format,int finish, EnumFacing facing, boolean upper, int layout, int i, boolean storageSet) {
        TextureAtlasSprite sprite = sprites.apply(storageSet ? ScreenHousingTextures.storageTexture(finish, FACE[i]) : ScreenHousingTextures.texture(finish));
        ScreenHousingMesh mesh = (slab ? ScreenHousingMesh.slab(upper, true)
                : ScreenHousingMesh.cube()).textureLayout(layout,UnifiedTextureSprites.aspect(sprite));
        int rotation = ((int)(180 - facing.getHorizontalAngle()) / 90) & 3;

            int stride=format.getIntegerSize(),color=format.getColorOffset()/4,uv=format.getUvOffsetById(0)/4;
            int[] data = new int[4*stride];
            EnumFacing normal=worldFace(FACE[i],facing);
            int packedNormal=((normal.getFrontOffsetX()*127)&255)|(((normal.getFrontOffsetY()*127)&255)<<8)|(((normal.getFrontOffsetZ()*127)&255)<<16);
            ScreenHousingMesh.Vertex[] points = mesh.quads[i].vertices;
            for (int j = 0; j < 4; j++) {
                ScreenHousingMesh.Vertex v = points[(4 - j) & 3];
                double a = v.x / 16, b = v.y / 16, c = v.z / 16;
                double x = a, z = c;
                switch (rotation) {
                    case 1: x = c; z = 1 - a; break;
                    case 2: x = 1 - a; z = 1 - c; break;
                    case 3: x = 1 - c; z = a; break;
                    default: break;
                }
                int offset = j * stride;
                data[offset] = Float.floatToRawIntBits((float)x);
                data[offset + 1] = Float.floatToRawIntBits((float)b);
                data[offset + 2] = Float.floatToRawIntBits((float)z);
                data[offset + color] = -1;
                data[offset + uv] = Float.floatToRawIntBits(sprite.getInterpolatedU(v.u));
                data[offset + uv + 1] = Float.floatToRawIntBits(sprite.getInterpolatedV(v.v));
                if(format.hasNormal())data[offset+format.getNormalOffset()/4]=packedNormal;
            }
            canonicalizeCorners(data, normal, stride);
            // Keep the geometric face: vanilla and OptiFine use it to choose
            // the visible surface's light sample, independently of its neighbours.
            return retainedQuad(data, -1, normal,
                    sprite, false, format);
    }

    /** Vanilla's AO arrays address FaceBakery corners, not an arbitrary cyclic
     * ordering. Rotating the housing changes the first UP/DOWN corner even
     * though its normal stays vertical. Move complete records so artwork,
     * normals and shader attributes remain attached to their physical corners. */
    private static void canonicalizeCorners(int[] data, EnumFacing face, int stride) {
        float[] bounds = new float[6];
        bounds[EnumFacing.WEST.getIndex()] = bounds[EnumFacing.DOWN.getIndex()]
                = bounds[EnumFacing.NORTH.getIndex()] = Float.POSITIVE_INFINITY;
        bounds[EnumFacing.EAST.getIndex()] = bounds[EnumFacing.UP.getIndex()]
                = bounds[EnumFacing.SOUTH.getIndex()] = Float.NEGATIVE_INFINITY;
        for (int vertex = 0; vertex < 4; vertex++) {
            int at = vertex * stride;
            float x = Float.intBitsToFloat(data[at]);
            float y = Float.intBitsToFloat(data[at + 1]);
            float z = Float.intBitsToFloat(data[at + 2]);
            bounds[EnumFacing.WEST.getIndex()] = Math.min(bounds[EnumFacing.WEST.getIndex()], x);
            bounds[EnumFacing.EAST.getIndex()] = Math.max(bounds[EnumFacing.EAST.getIndex()], x);
            bounds[EnumFacing.DOWN.getIndex()] = Math.min(bounds[EnumFacing.DOWN.getIndex()], y);
            bounds[EnumFacing.UP.getIndex()] = Math.max(bounds[EnumFacing.UP.getIndex()], y);
            bounds[EnumFacing.NORTH.getIndex()] = Math.min(bounds[EnumFacing.NORTH.getIndex()], z);
            bounds[EnumFacing.SOUTH.getIndex()] = Math.max(bounds[EnumFacing.SOUTH.getIndex()], z);
        }
        int[] original = data.clone();
        EnumFaceDirection corners = EnumFaceDirection.getFacing(face);
        for (int vertex = 0; vertex < 4; vertex++) {
            EnumFaceDirection.VertexInformation corner = corners.getVertexInformation(vertex);
            int source = 0;
            while (source < 4 && (Float.intBitsToFloat(original[source * stride]) != bounds[corner.xIndex]
                    || Float.intBitsToFloat(original[source * stride + 1]) != bounds[corner.yIndex]
                    || Float.intBitsToFloat(original[source * stride + 2]) != bounds[corner.zIndex])) source++;
            if (source == 4) throw new IllegalStateException("Housing face is not an axis-aligned rectangle");
            System.arraycopy(original, source * stride, data, vertex * stride, stride);
        }
    }

    /** OptiFine may normalize BakedQuad's internal array from a global shader flag. */
    private static BakedQuad retainedQuad(int[] data,int tint,EnumFacing face,TextureAtlasSprite sprite,boolean diffuse,VertexFormat format){
        if(data.length!=4*format.getIntegerSize())throw new IllegalArgumentException("Housing quad format and record width differ");
        final int[] records=data.clone();
        return new BakedQuad(records,tint,face,sprite,diffuse,format){@Override public int[] getVertexData(){return records;}};
    }
    private static EnumFacing worldFace(EnumFacing face, EnumFacing facing) {
        return EnumFacing.getFront(com.vandorlabs.render.HousingTexturePolicy.housingWorldFace(face.getIndex(),facing.getIndex()));
    }

    @Override public boolean isAmbientOcclusion() { return true; }
    @Override public boolean isGui3d() { return delegate.isGui3d(); }
    @Override public boolean isBuiltInRenderer() { return false; }
    @Override public TextureAtlasSprite getParticleTexture() { return delegate.getParticleTexture(); }
    @Override public ItemCameraTransforms getItemCameraTransforms() {
        return delegate.getItemCameraTransforms();
    }
    @Override public ItemOverrideList getOverrides() { return delegate.getOverrides(); }
}
