package com.vandorlabs.client;

import com.vandorlabs.blocks.BlockAnimatedScreenSelector;
import com.vandorlabs.blocks.BlockProgrammableSlab;
import com.vandorlabs.blocks.ProgrammableHousingState;
import com.vandorlabs.render.ScreenHousingMesh;
import com.vandorlabs.tiles.ScreenHousingTextures;
import net.minecraft.block.BlockSlab;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.block.model.ItemCameraTransforms;
import net.minecraft.client.renderer.block.model.ItemOverrideList;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.common.property.IExtendedBlockState;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Reuses material/facing quads from a chunk mesh until a model reload. */
public final class ProgrammableHousingModel implements IBakedModel {
    private static final EnumFacing[] FACE = {EnumFacing.UP, EnumFacing.DOWN,
            EnumFacing.WEST, EnumFacing.EAST, EnumFacing.NORTH, EnumFacing.SOUTH};
    private final IBakedModel delegate;
    private final boolean slab;
    private final boolean storage;
    private final java.util.function.Function<String,TextureAtlasSprite> sprites;
    private final java.util.function.Supplier<net.minecraft.client.renderer.vertex.VertexFormat> formats;
    private volatile net.minecraft.client.renderer.vertex.VertexFormat buildFormat;
    private final Map<Long, BakedQuad> variants = new ConcurrentHashMap<>();
    private final com.google.common.cache.Cache<String,List<BakedQuad>> joinedFaces=com.google.common.cache.CacheBuilder.newBuilder().maximumSize(2048).build();
    static final int MAX_FACE_LISTS=1024;
    private final com.google.common.cache.Cache<Long,List<BakedQuad>> faceLists=
            com.google.common.cache.CacheBuilder.newBuilder().maximumSize(MAX_FACE_LISTS).build();
    long cachedFaceLists(){faceLists.cleanUp();return faceLists.size();}

    public ProgrammableHousingModel(IBakedModel delegate, boolean slab) {
        this(delegate, slab, false);
    }

    public ProgrammableHousingModel(IBakedModel delegate, boolean slab, boolean storage) {
        this(delegate,slab,storage,name -> Minecraft.getMinecraft().getTextureMapBlocks().getAtlasSprite(name));
    }

    ProgrammableHousingModel(IBakedModel delegate, boolean slab, boolean storage,java.util.function.Function<String,TextureAtlasSprite> sprites) {
        this(delegate,slab,storage,sprites,()->DefaultVertexFormats.BLOCK);
    }
    ProgrammableHousingModel(IBakedModel delegate,boolean slab,boolean storage,java.util.function.Function<String,TextureAtlasSprite> sprites,java.util.function.Supplier<net.minecraft.client.renderer.vertex.VertexFormat> formats){
        this.formats=formats;this.sprites=sprites;
        this.storage = storage;
        this.delegate = delegate;
        this.slab = slab;
    }

    @Override public List<BakedQuad> getQuads(@Nullable IBlockState state,
            @Nullable EnumFacing side, long rand) {
        if (state == null) return delegate.getQuads(null, side, rand);
        if (side != null) return Collections.emptyList();
        ensureFormat();
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
            List<BakedQuad> cached=faceLists.getIfPresent(listKey);
            if(cached!=null)return cached;
        }
        List<BakedQuad> result = new ArrayList<>(6);
        for (int local = 0; local < 6; local++) {
            EnumFacing worldFace = worldFace(FACE[local], facing);
            if ((visible & (1 << worldFace.getIndex())) == 0) continue;
            int texture = faces.texture(FACE[local].getIndex(), slab && FACE[local].getAxis()!=EnumFacing.Axis.Y && sideFinish>=0?sideFinish:finish);
            final boolean storageSet = storage && !(faces.enabled && faces.choice(FACE[local].getIndex()) >= 0);
            com.vandorlabs.blocks.HousingFaceLayouts.Plane plane=joined.face(worldFace);
            BakedQuad quad=face(texture,facing,upper,plane==null?tileSides:0,local,storageSet);
            if(plane!=null){String key=texture+":"+facing+":"+local+":"+tileSides+":"+plane;List<BakedQuad> mapped=joinedFaces.getIfPresent(key);if(mapped==null){mapped=mapJoined(quad,plane,tileSides,sprites.apply("minecraft:blocks/concrete_black"));joinedFaces.put(key,mapped);}result.addAll(mapped);}
            else result.add(quad);
        }
        if(uniform) {
            result=Collections.unmodifiableList(result);
            faceLists.put(listKey,result);
        }
        return result;
    }

    /** OptiFine mutates the global format when shaders change, including without a resource reload. */
    private void ensureFormat(){
        net.minecraft.client.renderer.vertex.VertexFormat live=formats.get();
        if(live.equals(buildFormat))return;
        synchronized(this){if(live.equals(buildFormat))return;
            buildFormat=new net.minecraft.client.renderer.vertex.VertexFormat(live);
            variants.clear();joinedFaces.invalidateAll();faceLists.invalidateAll();
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
        return Collections.singletonList(new BakedQuad(data,quad.getTintIndex(),quad.getFace(),sprite,quad.shouldApplyDiffuseLighting(),quad.getFormat()));
    }

    private BakedQuad face(int texture,EnumFacing facing,boolean upper,int layout,int local,boolean storageSet) {
        long key=(((((long)texture*6+facing.getIndex())*2+(upper?1:0))*3+layout)*6+local)*2+(storageSet?1:0);
        BakedQuad cached=variants.get(key);
        if(cached!=null)return cached;
        BakedQuad built=build(texture,facing,upper,layout,local,storageSet);
        BakedQuad raced=variants.putIfAbsent(key,built);
        return raced==null?built:raced;
    }

    private BakedQuad build(int finish, EnumFacing facing, boolean upper, int layout, int i, boolean storageSet) {
        TextureAtlasSprite sprite = sprites.apply(storageSet ? ScreenHousingTextures.storageTexture(finish, FACE[i]) : ScreenHousingTextures.texture(finish));
        ScreenHousingMesh mesh = (slab ? ScreenHousingMesh.slab(upper, true)
                : ScreenHousingMesh.cube()).textureLayout(layout,UnifiedTextureSprites.aspect(sprite));
        int rotation = ((int)(180 - facing.getHorizontalAngle()) / 90) & 3;

            net.minecraft.client.renderer.vertex.VertexFormat format=buildFormat;
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
            // Flat block lighting samples the declared face. Use a perpendicular
            // direction to sample the housing position, preserving the old
            // maximum-neighbor light rule for every face.
            EnumFacing sampleFace = FACE[i].getAxis() == EnumFacing.Axis.Y
                    ? EnumFacing.NORTH : EnumFacing.UP;
            return new BakedQuad(data, -1, sampleFace,
                    sprite, false, format);
    }

    private static EnumFacing worldFace(EnumFacing face, EnumFacing facing) {
        int rotation = ((int)(180 - facing.getHorizontalAngle()) / 90) & 3;
        if (face.getAxis() == EnumFacing.Axis.Y) return face;
        switch (rotation) {
            case 1: return face.rotateYCCW();
            case 2: return face.getOpposite();
            case 3: return face.rotateY();
            default: return face;
        }
    }

    @Override public boolean isAmbientOcclusion() { return false; }
    @Override public boolean isGui3d() { return delegate.isGui3d(); }
    @Override public boolean isBuiltInRenderer() { return false; }
    @Override public TextureAtlasSprite getParticleTexture() { return delegate.getParticleTexture(); }
    @Override public ItemCameraTransforms getItemCameraTransforms() {
        return delegate.getItemCameraTransforms();
    }
    @Override public ItemOverrideList getOverrides() { return delegate.getOverrides(); }
}
