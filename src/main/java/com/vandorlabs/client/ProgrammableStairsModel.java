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
    private final boolean itemTile;
    private final Map<BakedQuad,Map<Integer,BakedQuad>> cache=new java.util.concurrent.ConcurrentHashMap<>();
    public ProgrammableStairsModel(IBakedModel base) { this(base,0,-1,FaceTextures.DEFAULT,false); }
    private ProgrammableStairsModel(IBakedModel base,int finish,int sideFinish,FaceTextures faces,boolean tile) {
        this.base=base;itemFinish=finish;itemSide=sideFinish;itemFaces=faces;itemTile=tile;
    }
    public List<BakedQuad> getQuads(IBlockState state,EnumFacing side,long seed) {
        int finish=itemFinish,sideFinish=itemSide;FaceTextures faces=itemFaces;boolean tile=itemTile;
        EnumFacing facing=EnumFacing.EAST; // vanilla item stairs rise towards +X
        if (state!=null) facing=state.getValue(BlockStairs.FACING);
        if (state instanceof IExtendedBlockState) {
            IExtendedBlockState e=(IExtendedBlockState)state;
            Integer sf=e.getValue(ProgrammableHousingState.SIDE_FINISH);if(sf!=null)sideFinish=sf;
            Integer value=e.getValue(ProgrammableHousingState.FINISH);
            if(value!=null)finish=value;
            if(e.getValue(ProgrammableHousingState.FACES)!=null)faces=e.getValue(ProgrammableHousingState.FACES);
            tile=Objects.equals(e.getValue(ProgrammableHousingState.TILE_SIDES),1);
        }
        List<BakedQuad> result=new ArrayList<>();
        for(BakedQuad quad:base.getQuads(state,side,seed)) {
            EnumFacing local=quad.getFace();
            // Front is the low riser facing the player, opposite the rise direction.
            if(local.getAxis()!=EnumFacing.Axis.Y)
                for(int i=0;i<((facing.getOpposite().getHorizontalIndex()+2)&3);i++)local=local.rotateYCCW();
            int texture=faces.texture(local.getIndex(),ScreenHousingTextures.clamp(local.getAxis()!=EnumFacing.Axis.Y && sideFinish>=0?sideFinish:finish));
            final int key=texture*2+(tile?1:0);final boolean repeat=tile;
            result.add(cache.computeIfAbsent(quad,q->new java.util.concurrent.ConcurrentHashMap<>())
                    .computeIfAbsent(key,k->retexture(quad,texture,repeat)));
        }
        return result;
    }
    private static BakedQuad retexture(BakedQuad quad,int texture,boolean tile) {
        TextureAtlasSprite old=quad.getSprite(),next=Minecraft.getMinecraft().getTextureMapBlocks()
                .getAtlasSprite(ScreenHousingTextures.texture(texture));
        int[] data=quad.getVertexData().clone();int stride=data.length/4;
        float[] u=new float[4],v=new float[4];float lo=16,hi=0;
        for(int i=0;i<4;i++) {
            u[i]=(Float.intBitsToFloat(data[i*stride+4])-old.getMinU())/(old.getMaxU()-old.getMinU())*16;
            v[i]=(Float.intBitsToFloat(data[i*stride+5])-old.getMinV())/(old.getMaxV()-old.getMinV())*16;
            lo=Math.min(lo,v[i]);hi=Math.max(hi,v[i]);
        }
        for(int i=0;i<4;i++) {
            float vv=!tile&&quad.getFace().getAxis()!=EnumFacing.Axis.Y&&hi>lo?(v[i]-lo)*16/(hi-lo):v[i];
            data[i*stride+4]=Float.floatToRawIntBits(next.getInterpolatedU(u[i]));
            data[i*stride+5]=Float.floatToRawIntBits(next.getInterpolatedV(vv));
        }
        return new BakedQuad(data,quad.getTintIndex(),quad.getFace(),next,quad.shouldApplyDiffuseLighting(),quad.getFormat());
    }
    public boolean isAmbientOcclusion(){return base.isAmbientOcclusion();}
    public boolean isGui3d(){return base.isGui3d();}
    public boolean isBuiltInRenderer(){return false;}
    public TextureAtlasSprite getParticleTexture(){return base.getParticleTexture();}
    public ItemCameraTransforms getItemCameraTransforms(){return base.getItemCameraTransforms();}
    public ItemOverrideList getOverrides(){return new ItemOverrideList(Collections.emptyList()) {
        @Override public IBakedModel handleItemState(IBakedModel original,ItemStack stack,net.minecraft.world.World world,net.minecraft.entity.EntityLivingBase entity) {
            NBTTagCompound tag=stack.getSubCompound("BlockEntityTag");
            if(tag==null)return original;
            return new ProgrammableStairsModel(base,tag.getInteger(com.vandorlabs.persistence.SaveSchema.Screen.HOUSING_TEXTURE),
                    tag.hasKey("SideTexture",3)?tag.getInteger("SideTexture"):-1,new FaceTextures(tag.getBoolean("FaceTexturesEnabled"),tag.getIntArray("FaceTextures")),tag.getBoolean("SlabTileSides"));
        }
    };}
}
