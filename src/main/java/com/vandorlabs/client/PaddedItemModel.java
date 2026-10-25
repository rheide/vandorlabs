package com.vandorlabs.client;

import java.util.Collections;
import java.util.List;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.block.model.*;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;

/** Preserve an item's resolved artwork while fitting its actual GUI geometry. */
public final class PaddedItemModel implements IBakedModel {
    private final IBakedModel base;
    private ItemCameraTransforms transforms;
    public PaddedItemModel(IBakedModel base){this.base=base;}
    public List<BakedQuad> getQuads(IBlockState state,EnumFacing face,long seed){return base.getQuads(state,face,seed);}
    public boolean isAmbientOcclusion(){return base.isAmbientOcclusion();}
    public boolean isGui3d(){return base.isGui3d();}
    public boolean isBuiltInRenderer(){return base.isBuiltInRenderer();}
    public TextureAtlasSprite getParticleTexture(){return base.getParticleTexture();}
    public ItemCameraTransforms getItemCameraTransforms(){if(transforms==null)transforms=ControlItemPadding.fit(this,base.getItemCameraTransforms());return transforms;}
    public ItemOverrideList getOverrides(){return new ItemOverrideList(Collections.emptyList()){
        @Override public IBakedModel handleItemState(IBakedModel original,ItemStack stack,net.minecraft.world.World world,net.minecraft.entity.EntityLivingBase entity){
            IBakedModel selected=base.getOverrides().handleItemState(base,stack,world,entity);
            return selected==base?PaddedItemModel.this:new PaddedItemModel(selected);
        }
    };}
}
