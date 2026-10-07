package com.vandorlabs.client;

import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.block.model.*;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.EnumFacing;
import java.util.*;

/** The merged item selects each type's native padded model, then its mount override. */
public final class ThrottleItemModel implements IBakedModel {
    private final IBakedModel[] models;
    public ThrottleItemModel(IBakedModel[] models){this.models=models;}
    public List<BakedQuad> getQuads(IBlockState state,EnumFacing side,long seed){return models[0].getQuads(state,side,seed);}
    public boolean isAmbientOcclusion(){return models[0].isAmbientOcclusion();}
    public boolean isGui3d(){return models[0].isGui3d();}
    public boolean isBuiltInRenderer(){return false;}
    public TextureAtlasSprite getParticleTexture(){return models[0].getParticleTexture();}
    public ItemCameraTransforms getItemCameraTransforms(){return models[0].getItemCameraTransforms();}
    public ItemOverrideList getOverrides(){return new ItemOverrideList(Collections.emptyList()){
        @Override public IBakedModel handleItemState(IBakedModel original,net.minecraft.item.ItemStack stack,net.minecraft.world.World world,net.minecraft.entity.EntityLivingBase entity){
            net.minecraft.nbt.NBTTagCompound tag=stack.getSubCompound("RedstoneChannelSettings");int type=tag==null?0:tag.getInteger("ControlType");
            IBakedModel selected=models[Math.max(0,Math.min(2,type))];return selected.getOverrides().handleItemState(selected,stack,world,entity);
        }
    };}
}
