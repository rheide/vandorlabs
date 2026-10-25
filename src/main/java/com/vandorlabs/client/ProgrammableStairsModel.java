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

/** Retextures vanilla stair quads; geometry keeps vanilla corner and culling rules. */
public final class ProgrammableStairsModel implements IBakedModel {
    private final IBakedModel base;
    private final int itemFinish;
    private final int itemSide;
    private final FaceTextures itemFaces;
    private final int itemLayout;
    private int itemGeometry=8;
    private ItemCameraTransforms paddedTransforms;
    private final com.google.common.cache.Cache<String,List<BakedQuad>> geometry=com.google.common.cache.CacheBuilder.newBuilder().maximumSize(512).build();
    private final Map<BakedQuad,Map<Integer,BakedQuad>> cache=new java.util.concurrent.ConcurrentHashMap<>();
    public ProgrammableStairsModel(IBakedModel base) { this(base,0,-1,FaceTextures.DEFAULT,1); }
    private ProgrammableStairsModel(IBakedModel base,int finish,int sideFinish,FaceTextures faces,int layout) {
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
            source=geometry.getIfPresent(key);if(source==null){source=ProgrammableStairMesh.quads(shape,stairGeometry,base.getParticleTexture());geometry.put(key,source);}
        }
        for(BakedQuad quad:source) {
            IBlockState orientation=state==null?com.vandorlabs.blocks.ModBlocks.PROGRAMMABLE_STAIRS.getDefaultState().withProperty(BlockStairs.FACING,EnumFacing.EAST):state;
            EnumFacing textureFace=com.vandorlabs.blocks.ProgrammableStairGeometry.unrolledFace(orientation,stairGeometry>>5,quad.getFace());
            EnumFacing local=EnumFacing.getFront(com.vandorlabs.render.StairTextureMapping.localFace(textureFace.getIndex(),facing.getIndex()));
            int texture=faces.texture(local.getIndex(),ScreenHousingTextures.clamp(local.getAxis()!=EnumFacing.Axis.Y && sideFinish>=0?sideFinish:finish));
            final int key=texture*3+layout;final int mode=layout;
            result.add(cache.computeIfAbsent(quad,q->new java.util.concurrent.ConcurrentHashMap<>())
                    .computeIfAbsent(key,k->retexture(quad,texture,mode,textureFace)));
        }
        return result;
    }
    private static BakedQuad retexture(BakedQuad quad,int texture,int layout,EnumFacing textureFace) {
        TextureAtlasSprite old=quad.getSprite(),next=Minecraft.getMinecraft().getTextureMapBlocks()
                .getAtlasSprite(ScreenHousingTextures.texture(texture));
        int[] data=quad.getVertexData().clone();int stride=data.length/4;
        float[] u=new float[4],v=new float[4];
        for(int i=0;i<4;i++) {
            u[i]=(Float.intBitsToFloat(data[i*stride+4])-old.getMinU())/(old.getMaxU()-old.getMinU())*16;
            v[i]=(Float.intBitsToFloat(data[i*stride+5])-old.getMinV())/(old.getMaxV()-old.getMinV())*16;
        }
        com.vandorlabs.render.ControlMount.Point[] positions=new com.vandorlabs.render.ControlMount.Point[4];for(int i=0;i<4;i++)positions[i]=new com.vandorlabs.render.ControlMount.Point(Float.intBitsToFloat(data[i*stride]),Float.intBitsToFloat(data[i*stride+1]),Float.intBitsToFloat(data[i*stride+2]));
        double[][] mapped=com.vandorlabs.render.StairTextureMapping.map(positions,u,v,layout,UnifiedTextureSprites.aspect(next),textureFace.getIndex());
        for(int i=0;i<4;i++){data[i*stride+4]=Float.floatToRawIntBits(next.getInterpolatedU(mapped[i][0]));data[i*stride+5]=Float.floatToRawIntBits(next.getInterpolatedV(mapped[i][1]));}
        return new BakedQuad(data,quad.getTintIndex(),quad.getFace(),next,quad.shouldApplyDiffuseLighting(),quad.getFormat());
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
            ProgrammableStairsModel model=new ProgrammableStairsModel(base,tag.getInteger(com.vandorlabs.persistence.SaveSchema.Screen.HOUSING_TEXTURE),
                    tag.hasKey("SideTexture",3)?tag.getInteger("SideTexture"):-1,new FaceTextures(tag.getBoolean("FaceTexturesEnabled"),tag.getIntArray("FaceTextures")),tag.hasKey("MainTextureLayout",3)?tag.getInteger("MainTextureLayout"):tag.getBoolean("SlabTileSides")?1:0);
            model.itemGeometry=com.vandorlabs.persistence.StairState.read(new com.vandorlabs.persistence.NbtPrimitiveData(tag)).packed();
            return model;
        }
    };}
}
