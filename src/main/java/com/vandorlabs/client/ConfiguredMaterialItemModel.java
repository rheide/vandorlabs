package com.vandorlabs.client;

import java.util.*;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.block.model.*;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;

/** Keeps item geometry and camera transforms while resolving dynamically selected artwork. */
public final class ConfiguredMaterialItemModel implements IBakedModel {
    private final IBakedModel base;
    private final boolean light;
    public ConfiguredMaterialItemModel(IBakedModel base,boolean light){this.base=base;this.light=light;}
    public List<BakedQuad> getQuads(IBlockState state,EnumFacing face,long seed){return base.getQuads(state,face,seed);}
    public boolean isAmbientOcclusion(){return base.isAmbientOcclusion();}
    public boolean isGui3d(){return base.isGui3d();}
    public boolean isBuiltInRenderer(){return base.isBuiltInRenderer();}
    public TextureAtlasSprite getParticleTexture(){return base.getParticleTexture();}
    public ItemCameraTransforms getItemCameraTransforms(){return base.getItemCameraTransforms();}
    public ItemOverrideList getOverrides(){return new ItemOverrideList(Collections.emptyList()) {
        @Override public IBakedModel handleItemState(IBakedModel original,ItemStack stack,net.minecraft.world.World world,net.minecraft.entity.EntityLivingBase entity) {
            NBTTagCompound tag=stack.getSubCompound("BlockEntityTag");if(tag==null)return original;
            int choice=tag.getInteger(light?"LightFaceTexture":"housingTexture");
            if(light && !tag.hasKey("LightFaceTexture",3) || !light && !com.vandorlabs.tiles.CustomBlockMaterials.isCustom(choice))return original;
            if(choice<0)return original;
            TextureAtlasSprite next=net.minecraft.client.Minecraft.getMinecraft().getTextureMapBlocks().getAtlasSprite(com.vandorlabs.tiles.ScreenHousingTextures.texture(choice,true));
            return new RetexturedItemModel(base,next,light);
        }
    };}
}
