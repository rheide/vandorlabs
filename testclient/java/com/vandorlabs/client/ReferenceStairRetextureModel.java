package com.vandorlabs.client;

import com.vandorlabs.blocks.ProgrammableHousingState;
import com.vandorlabs.tiles.*;
import net.minecraft.block.BlockStairs;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.*;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.common.property.IExtendedBlockState;
import java.util.*;

/** Frozen pre-fix renderer, with only an injected atlas and retention counters for the paired fixture. */
public final class ReferenceStairRetextureModel implements IBakedModel {
    private final IBakedModel base;
    private final java.util.function.Function<String,TextureAtlasSprite> sprites;
    private final int itemFinish;
    private final int itemSide;
    private final FaceTextures itemFaces;
    private final int itemLayout;
    private int itemGeometry=8;
    private ItemCameraTransforms paddedTransforms;
    private final com.google.common.cache.Cache<String,List<BakedQuad>> geometry=com.google.common.cache.CacheBuilder.newBuilder().maximumSize(512).build();
    private final Map<BakedQuad,Map<Integer,BakedQuad>> cache=new java.util.concurrent.ConcurrentHashMap<>();
    public ReferenceStairRetextureModel(IBakedModel base,java.util.function.Function<String,TextureAtlasSprite> sprites) { this(base,0,-1,FaceTextures.DEFAULT,1,sprites); }
    private ReferenceStairRetextureModel(IBakedModel base,int finish,int sideFinish,FaceTextures faces,int layout,java.util.function.Function<String,TextureAtlasSprite> sprites) {
        this.sprites=sprites;
        this.base=base;itemFinish=finish;itemSide=sideFinish;itemFaces=faces;itemLayout=layout;
    }
    public List<BakedQuad> getQuads(IBlockState state,EnumFacing side,long seed) {
        int finish=itemFinish,sideFinish=itemSide;FaceTextures faces=itemFaces;int layout=itemLayout;int stairGeometry=itemGeometry;
        EnumFacing facing=EnumFacing.EAST; // vanilla item stairs rise towards +X
        if (state!=null) facing=state.getValue(BlockStairs.FACING);
        if (state instanceof IExtendedBlockState) {
            IExtendedBlockState e=(IExtendedBlockState)state;
            Integer shape=e.getValue(ProgrammableHousingState.STAIR_GEOMETRY);if(shape!=null)stairGeometry=shape;
            Integer sf=e.getValue(ProgrammableHousingState.SIDE_FINISH);if(sf!=null)sideFinish=sf;
            Integer value=e.getValue(ProgrammableHousingState.FINISH);
            if(value!=null)finish=value;
            if(e.getValue(ProgrammableHousingState.FACES)!=null)faces=e.getValue(ProgrammableHousingState.FACES);
            Integer sampled=e.getValue(ProgrammableHousingState.TILE_SIDES);if(sampled!=null)layout=sampled;
        }
        List<BakedQuad> result=new ArrayList<>();
        List<BakedQuad> source;
        if(stairGeometry==8)source=base.getQuads(state,side,seed);
        else {
            if(side!=null)return Collections.emptyList();
            IBlockState shape=state==null?com.vandorlabs.blocks.ModBlocks.PROGRAMMABLE_STAIRS.getDefaultState().withProperty(BlockStairs.FACING,EnumFacing.EAST):state;
            String key=stairGeometry+":"+shape.getValue(BlockStairs.FACING)+":"+shape.getValue(BlockStairs.HALF)+":"+shape.getValue(BlockStairs.SHAPE);
            source=geometry.getIfPresent(key);if(source==null){source=ReferenceStairRetextureMesh.quads(shape,stairGeometry,base.getParticleTexture());geometry.put(key,source);}
        }
        for(BakedQuad quad:source) {
            IBlockState orientation=state==null?com.vandorlabs.blocks.ModBlocks.PROGRAMMABLE_STAIRS.getDefaultState().withProperty(BlockStairs.FACING,EnumFacing.EAST):state;
            EnumFacing textureFace=com.vandorlabs.blocks.ProgrammableStairGeometry.unrolledFace(orientation,stairGeometry>>5,quad.getFace());
            // Front is the low riser facing the player, opposite the rise direction.
            EnumFacing local=EnumFacing.getFront(com.vandorlabs.render.HousingTexturePolicy.stairLocalFace(textureFace.getIndex(),facing.getIndex()));
            int texture=faces.texture(local.getIndex(),ScreenHousingTextures.clamp(com.vandorlabs.render.HousingTexturePolicy.baseChoice(local.getIndex(),finish,sideFinish,true)));
            final int key=texture*3+layout;final int mode=layout;
            result.add(cache.computeIfAbsent(quad,q->new java.util.concurrent.ConcurrentHashMap<>())
                    .computeIfAbsent(key,k->retexture(quad,texture,mode,textureFace)));
        }
        return result;
    }
    private BakedQuad retexture(BakedQuad quad,int texture,int layout,EnumFacing textureFace) {
        TextureAtlasSprite old=quad.getSprite(),next=sprites.apply(ScreenHousingTextures.texture(texture));
        int[] data=quad.getVertexData().clone();int stride=data.length/4;
        float[] u=new float[4],v=new float[4];float lo=16,hi=0,ulo=16,uhi=0;
        for(int i=0;i<4;i++) {
            u[i]=(Float.intBitsToFloat(data[i*stride+4])-old.getMinU())/(old.getMaxU()-old.getMinU())*16;
            v[i]=(Float.intBitsToFloat(data[i*stride+5])-old.getMinV())/(old.getMaxV()-old.getMinV())*16;
            lo=Math.min(lo,v[i]);hi=Math.max(hi,v[i]);ulo=Math.min(ulo,u[i]);uhi=Math.max(uhi,u[i]);
        }
        double unitU=1D/16,unitV=1D/16,aspect=UnifiedTextureSprites.aspect(next);
        for(int a=0;a<4;a++)for(int b=0;b<4;b++){
            double length=0;for(int axis=0;axis<3;axis++){double delta=Float.intBitsToFloat(data[a*stride+axis])-Float.intBitsToFloat(data[b*stride+axis]);length+=delta*delta;}length=Math.sqrt(length);
            if(Math.abs(u[a]-u[b])>1e-6 && Math.abs(v[a]-v[b])<1e-6)unitU=length/Math.abs(u[a]-u[b]);
            if(Math.abs(v[a]-v[b])>1e-6 && Math.abs(u[a]-u[b])<1e-6)unitV=length/Math.abs(v[a]-v[b]);
        }
        boolean fit=com.vandorlabs.render.TextureLayoutMode.fit(layout,(uhi-ulo)*unitU,(hi-lo)*unitV,aspect);
        for(int i=0;i<4;i++) {
            double uu=com.vandorlabs.render.HousingTexturePolicy.stairU(layout,fit,u[i],ulo,uhi,aspect);
            double vv=com.vandorlabs.render.HousingTexturePolicy.stairV(layout,fit,v[i],lo,hi,textureFace.getIndex(),aspect);
            data[i*stride+4]=Float.floatToRawIntBits(next.getInterpolatedU(uu));
            data[i*stride+5]=Float.floatToRawIntBits(next.getInterpolatedV(vv));
        }
        return new BakedQuad(data,quad.getTintIndex(),quad.getFace(),next,quad.shouldApplyDiffuseLighting(),quad.getFormat());
    }
    long geometryEntries(){geometry.cleanUp();return geometry.size();}
    long activeSourceQuads(){geometry.cleanUp();long count=0;for(List<BakedQuad> quads:geometry.asMap().values())count+=quads.size();return count;}
    long retainedSourceQuads(){return cache.size();}
    long retainedVariants(){long count=0;for(Map<Integer,BakedQuad> values:cache.values())count+=values.size();return count;}
    long estimatedRetainedBytes(){
        geometry.cleanUp();Set<BakedQuad> sources=Collections.newSetFromMap(new IdentityHashMap<>());sources.addAll(cache.keySet());
        long bytes=256L*cache.size()+128L*geometry.size();
        for(List<BakedQuad> list:geometry.asMap().values()){sources.addAll(list);bytes+=32+8L*list.size();}
        for(BakedQuad quad:sources)bytes+=112L+4L*quad.getVertexData().length;
        for(Map<Integer,BakedQuad> values:cache.values())for(BakedQuad quad:values.values())bytes+=96L+112L+4L*quad.getVertexData().length;
        return bytes;
    }
    public boolean isAmbientOcclusion(){return base.isAmbientOcclusion();}
    public boolean isGui3d(){return base.isGui3d();}
    public boolean isBuiltInRenderer(){return false;}
    public TextureAtlasSprite getParticleTexture(){return base.getParticleTexture();}
    public ItemCameraTransforms getItemCameraTransforms(){if(paddedTransforms==null)paddedTransforms=ControlItemPadding.fit(this,base.getItemCameraTransforms());return paddedTransforms;}
    public ItemOverrideList getOverrides(){return new ItemOverrideList(Collections.emptyList()) {
        @Override public IBakedModel handleItemState(IBakedModel original,ItemStack stack,net.minecraft.world.World world,net.minecraft.entity.EntityLivingBase entity) {
            NBTTagCompound tag=stack.getSubCompound("BlockEntityTag");
            if(tag==null)return original;
            ReferenceStairRetextureModel model=new ReferenceStairRetextureModel(base,tag.getInteger(com.vandorlabs.persistence.SaveSchema.Screen.HOUSING_TEXTURE),
                    tag.hasKey("SideTexture",3)?tag.getInteger("SideTexture"):-1,new FaceTextures(tag.getBoolean("FaceTexturesEnabled"),tag.getIntArray("FaceTextures")),com.vandorlabs.render.HousingTexturePolicy.stairItemLayout(tag.hasKey("MainTextureLayout",3),tag.getInteger("MainTextureLayout"),tag.getBoolean("SlabTileSides")),sprites);
            int tread=com.vandorlabs.persistence.StairState.readTread(tag.hasKey(com.vandorlabs.persistence.StairState.TREAD,3),tag.getInteger(com.vandorlabs.persistence.StairState.TREAD));
            model.itemGeometry=com.vandorlabs.persistence.StairState.packed(tread,com.vandorlabs.persistence.StairState.readRoll(tag.getInteger(com.vandorlabs.persistence.StairState.ROLL)));
            return model;
        }
    };}
}
