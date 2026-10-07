package com.vandorlabs.client;

import com.vandorlabs.items.*;
import com.vandorlabs.tiles.ScreenHousingTextures;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.*;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.client.event.ModelBakeEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import java.util.*;

/** Material-independent padded silhouettes; selected artwork resolves without extra item IDs. */
public final class ProgrammableArmorItemModels {
    @SubscribeEvent public void bake(ModelBakeEvent event) {
        for (ItemProgrammableArmor item: new ItemProgrammableArmor[]{ModItems.PROGRAMMABLE_HELMET,
                ModItems.PROGRAMMABLE_CHESTPLATE, ModItems.PROGRAMMABLE_LEGGINGS, ModItems.PROGRAMMABLE_BOOTS}) {
            ModelResourceLocation location=new ModelResourceLocation(item.getRegistryName(),"inventory");
            IBakedModel base=event.getModelRegistry().getObject(location);
            if(base!=null)event.getModelRegistry().putObject(location,new MaterialModel(base,item.armorType,event));
        }
    }
    private static final class MaterialModel implements IBakedModel {
        private final IBakedModel base;
        private final Map<String,IBakedModel> cache=new LinkedHashMap<String,IBakedModel>(16,.75F,true) {
            @Override protected boolean removeEldestEntry(Map.Entry<String,IBakedModel> entry) { return size()>128; }
        };
        private final Map<Integer,IBakedModel> roles=new HashMap<>();
        MaterialModel(IBakedModel base,net.minecraft.inventory.EntityEquipmentSlot slot,ModelBakeEvent event) {
            this.base=base;
            for(ArmorTextures.Entry entry:ArmorTextures.ALL)if(entry.slot==slot) {
                IBakedModel model=event.getModelRegistry().getObject(new ModelResourceLocation(entry.model,"inventory"));
                if(model==null)throw new IllegalStateException("Armor icon model missing: "+entry.model);
                roles.put(entry.choice,model);
            }
        }
        private final ItemOverrideList overrides=new ItemOverrideList(Collections.emptyList()) {
            @Override public IBakedModel handleItemState(IBakedModel original,ItemStack stack,
                    net.minecraft.world.World world,net.minecraft.entity.EntityLivingBase entity) {
                int choice=ItemProgrammableArmor.texture(stack);
                String sample=ItemProgrammableArmor.sample(stack);
                if(sample==null && roles.containsKey(choice))return roles.get(choice);
                String sprite=sample==null?ScreenHousingTextures.fullTexture(choice):sample;
                return cache.computeIfAbsent(sprite,key->new FixedModel(base,Minecraft.getMinecraft().getTextureMapBlocks()
                        .getAtlasSprite(key)));
            }
        };
        @Override public List<BakedQuad> getQuads(IBlockState state,EnumFacing side,long seed) { return base.getQuads(state,side,seed); }
        @Override public boolean isAmbientOcclusion() { return base.isAmbientOcclusion(); }
        @Override public boolean isGui3d() { return false; }
        @Override public boolean isBuiltInRenderer() { return false; }
        @Override public TextureAtlasSprite getParticleTexture() { return base.getParticleTexture(); }
        @Override public ItemCameraTransforms getItemCameraTransforms() { return base.getItemCameraTransforms(); }
        @Override public ItemOverrideList getOverrides() { return overrides; }
    }
    private static final class FixedModel extends RetexturedItemModel {
        private final List<BakedQuad> quads;
        private final Map<EnumFacing,List<BakedQuad>> faces=new EnumMap<>(EnumFacing.class);
        FixedModel(IBakedModel base,TextureAtlasSprite sprite) {
            super(base,sprite);quads=super.getQuads(null,null,0);
            for(EnumFacing face:EnumFacing.values())faces.put(face,super.getQuads(null,face,0));
        }
        @Override public List<BakedQuad> getQuads(IBlockState state,EnumFacing side,long seed) { return side==null?quads:faces.get(side); }
        @Override public ItemOverrideList getOverrides() { return ItemOverrideList.NONE; }
    }
}
