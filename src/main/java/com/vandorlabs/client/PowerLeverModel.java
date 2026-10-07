package com.vandorlabs.client;

import com.vandorlabs.blocks.BlockTwinPowerLever;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.block.model.*;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.common.property.IExtendedBlockState;
import java.util.*;

/** Selects the native Small/Large geometry while retaining each item's padded transform. */
public final class PowerLeverModel implements IBakedModel {
    private final IBakedModel[] sizes;
    private final int itemSize;
    public PowerLeverModel(IBakedModel small,IBakedModel large,int size){sizes=new IBakedModel[]{small,large};itemSize=size;}
    private IBakedModel selected(IBlockState state){Integer size=state instanceof IExtendedBlockState?((IExtendedBlockState)state).getValue(BlockTwinPowerLever.SIZE):null;return sizes[size==null?itemSize:size];}
    public List<BakedQuad> getQuads(IBlockState state,EnumFacing side,long seed){return selected(state).getQuads(state,side,seed);}
    public boolean isAmbientOcclusion(){return sizes[itemSize].isAmbientOcclusion();}
    public boolean isGui3d(){return sizes[itemSize].isGui3d();}
    public boolean isBuiltInRenderer(){return false;}
    public TextureAtlasSprite getParticleTexture(){return sizes[itemSize].getParticleTexture();}
    public ItemCameraTransforms getItemCameraTransforms(){return sizes[itemSize].getItemCameraTransforms();}
    public ItemOverrideList getOverrides(){return new ItemOverrideList(Collections.emptyList()){
        @Override public IBakedModel handleItemState(IBakedModel original,net.minecraft.item.ItemStack stack,net.minecraft.world.World world,net.minecraft.entity.EntityLivingBase entity){
            net.minecraft.nbt.NBTTagCompound tag=stack.getSubCompound("RedstoneChannelSettings");int size=tag!=null && tag.hasKey("PowerLeverSize",3)?tag.getInteger("PowerLeverSize"):itemSize;
            return size<0 || size>1?original:new PowerLeverModel(sizes[0],sizes[1],size);
        }
    };}
}
