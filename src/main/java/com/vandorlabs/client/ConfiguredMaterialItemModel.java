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
    private final java.util.function.Function<String,TextureAtlasSprite> sprites;
    private final ItemOverrideList overrides=new ItemOverrideList(Collections.emptyList()) {
        @Override public IBakedModel handleItemState(IBakedModel original,ItemStack stack,net.minecraft.world.World world,net.minecraft.entity.EntityLivingBase entity) {
            NBTTagCompound tag=stack.getSubCompound("BlockEntityTag");
            return tag==null?original:configured(net.minecraft.block.Block.getBlockFromItem(stack.getItem()),tag);
        }
    };
    public ConfiguredMaterialItemModel(IBakedModel base,boolean light){this(base,light,name->net.minecraft.client.Minecraft.getMinecraft().getTextureMapBlocks().getAtlasSprite(name));}
    ConfiguredMaterialItemModel(IBakedModel base,boolean light,java.util.function.Function<String,TextureAtlasSprite> sprites){this.base=base;this.light=light;this.sprites=sprites;}
    public List<BakedQuad> getQuads(IBlockState state,EnumFacing face,long seed){return base.getQuads(state,face,seed);}
    public boolean isAmbientOcclusion(){return base.isAmbientOcclusion();}
    public boolean isGui3d(){return base.isGui3d();}
    public boolean isBuiltInRenderer(){return base.isBuiltInRenderer();}
    public TextureAtlasSprite getParticleTexture(){return base.getParticleTexture();}
    public ItemCameraTransforms getItemCameraTransforms(){return base.getItemCameraTransforms();}
    public ItemOverrideList getOverrides(){return overrides;}
    IBakedModel configured(net.minecraft.block.Block block,NBTTagCompound tag){
        int choice=tag.getInteger(light?"LightFaceTexture":"housingTexture");
        boolean slab=block instanceof com.vandorlabs.blocks.BlockProgrammableSlab;
        boolean porthole=block instanceof com.vandorlabs.blocks.BlockProgrammableWall
                && ((com.vandorlabs.blocks.BlockProgrammableWall)block).isPortholeShape();
        boolean edges=!light&&(slab||porthole)&&tag.hasKey("SideTexture",3)&&tag.getInteger("SideTexture")>=0;
        boolean main=light?tag.hasKey("LightFaceTexture",3)&&choice>=0:com.vandorlabs.tiles.CustomBlockMaterials.isCustom(choice);
        TextureAtlasSprite next=main?sprites.apply(com.vandorlabs.tiles.ScreenHousingTextures.texture(choice,true)):null;
        TextureAtlasSprite side=edges?sprites.apply(com.vandorlabs.tiles.ScreenHousingTextures.texture(tag.getInteger("SideTexture"))):null;
        boolean layout=!light&&(slab||block instanceof com.vandorlabs.blocks.BlockProgrammableBlock);
        int mode=layout?com.vandorlabs.render.HousingTexturePolicy.itemLayout(tag.hasKey("MainTextureLayout",3),tag.getInteger("MainTextureLayout"),tag.hasKey("SlabTileSides",1),tag.getBoolean("SlabTileSides")):0;
        if(!main&&!edges&&!layout)return base;
        int flags=(light?1:0)|(edges?2:0)|(edges&&slab?4:0)|(edges&&porthole?8:0)|(layout?16:0);
        ItemVariantModels.Key key=new ItemVariantModels.Key(this,next,side,flags,mode,0);
        IBakedModel cached=ItemVariantModels.find(key);if(cached!=null)return cached;
        IBakedModel model=edges?new RetexturedItemModel(base,next,side,slab,porthole):main?new RetexturedItemModel(base,next,light):base;
        if(layout)model=new TextureLayoutItemModel(model,mode);
        return ItemVariantModels.retain(key,model);
    }
}
