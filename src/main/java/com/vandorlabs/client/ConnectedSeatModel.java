package com.vandorlabs.client;

import com.vandorlabs.blocks.BlockConnectedSeat;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.block.model.*;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.common.property.IExtendedBlockState;
import java.util.*;

/** Stretches only leg tops; the cushion/backrest move together. Feet stay on the floor. */
public final class ConnectedSeatModel implements IBakedModel {
    private final IBakedModel base;private final int itemOffset;
    private final Map<BakedQuad,Map<Integer,BakedQuad>> cache=new java.util.concurrent.ConcurrentHashMap<>();
    public ConnectedSeatModel(IBakedModel base){this(base,1);}
    private ConnectedSeatModel(IBakedModel base,int offset){this.base=base;itemOffset=offset;}
    public List<BakedQuad> getQuads(IBlockState state,EnumFacing side,long seed){
        Integer height=state instanceof IExtendedBlockState?((IExtendedBlockState)state).getValue(BlockConnectedSeat.HEIGHT):null;
        final int offset=height==null?itemOffset:height;
        List<BakedQuad> result=new ArrayList<>();
        for(BakedQuad quad:base.getQuads(state,side,seed))result.add(cache.computeIfAbsent(quad,q->new java.util.concurrent.ConcurrentHashMap<>()).computeIfAbsent(offset,n->{
            int[] data=quad.getVertexData().clone();int stride=data.length/4;
            for(int i=0;i<4;i++){float y=Float.intBitsToFloat(data[i*stride+1]);if(y>=5F/16-1e-6)data[i*stride+1]=Float.floatToRawIntBits(y+n/16F);}
            return new BakedQuad(data,quad.getTintIndex(),quad.getFace(),quad.getSprite(),quad.shouldApplyDiffuseLighting(),quad.getFormat());
        }));return result;
    }
    public boolean isAmbientOcclusion(){return base.isAmbientOcclusion();}
    public boolean isGui3d(){return base.isGui3d();}
    public boolean isBuiltInRenderer(){return false;}
    public TextureAtlasSprite getParticleTexture(){return base.getParticleTexture();}
    public ItemCameraTransforms getItemCameraTransforms(){return base.getItemCameraTransforms();}
    public ItemOverrideList getOverrides(){return new ItemOverrideList(Collections.emptyList()){
        @Override public IBakedModel handleItemState(IBakedModel original,net.minecraft.item.ItemStack stack,net.minecraft.world.World world,net.minecraft.entity.EntityLivingBase entity){
            net.minecraft.nbt.NBTTagCompound tag=stack.getSubCompound("BlockEntityTag");
            int index=tag!=null&&tag.hasKey("ChairHeight")?Math.max(0,Math.min(2,tag.getInteger("ChairHeight"))):1;
            return new ConnectedSeatModel(base,index*2-1);
        }
    };}
}
