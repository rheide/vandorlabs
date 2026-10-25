package com.vandorlabs.neoforge.wall;

import com.vandorlabs.neoforge.ModernFinishCatalog;
import com.vandorlabs.neoforge.client.NativeItemMaterials;
import com.vandorlabs.neoforge.client.NativeItemPadding;
import com.vandorlabs.neoforge.client.NativeLegacyUvs;
import com.vandorlabs.tiles.HousingFinishCatalog;
import java.util.*;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.*;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.resources.model.*;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.model.BakedModelWrapper;
import net.neoforged.neoforge.client.model.data.ModelData;

/** Canonical inventory models retain the reference material bindings, independently of joined world meshes. */
@EventBusSubscriber(modid="vandorlabs",bus=EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
public final class WallItemModel extends BakedModelWrapper<BakedModel> {
    private record Settings(int main,int side){}
    private static final Set<String> IDS=Set.of("programmable_wall","programmable_porthole_wall","programmable_porthole_block");
    private final Map<Integer,NativeItemMaterials.Material> materials;
    private final boolean porthole;
    private final Settings settings;
    private ItemTransforms transforms;
    private final Map<Integer,List<BakedQuad>> quads=new HashMap<>();
    private final Map<Settings,BakedModel> resolved=new LinkedHashMap<>(16,.75f,true){@Override protected boolean removeEldestEntry(Map.Entry<Settings,BakedModel> entry){return size()>64;}};
    private WallItemModel(BakedModel original,Map<Integer,NativeItemMaterials.Material> materials,boolean porthole,Settings settings){super(NativeLegacyUvs.wrap(original));this.materials=materials;this.porthole=porthole;this.settings=settings;}
    @SubscribeEvent public static void bake(ModelEvent.ModifyBakingResult event){
        Map<Integer,NativeItemMaterials.Material> values=new HashMap<>();for(var choice:ModernFinishCatalog.allChoices()){var entry=choice.entry();var sprite=event.getTextureGetter().apply(new Material(TextureAtlas.LOCATION_BLOCKS,ResourceLocation.parse(entry.texture())));values.put(choice.choice(),new NativeItemMaterials.Material(sprite,entry.u(),entry.v()));}var materials=Map.copyOf(values);
        event.getModels().replaceAll((id,model)->id.id().getNamespace().equals("vandorlabs")&&IDS.contains(id.id().getPath())&&id.getVariant().equals("inventory")?new WallItemModel(model,materials,!id.id().getPath().equals("programmable_wall"),null):model);
    }
    private NativeItemMaterials.Material material(int choice){return materials.getOrDefault(ModernFinishCatalog.renderChoice(choice),materials.get(0));}
    @Override public List<BakedQuad> getQuads(BlockState state,Direction face,RandomSource random){
        int key=face==null?-1:face.get3DDataValue();if(quads.containsKey(key))return quads.get(key);
        List<BakedQuad> result=new ArrayList<>();for(BakedQuad source:originalModel.getQuads(state,face,random)){
            if(settings==null){result.add(source);continue;}
            String texture=source.getSprite().contents().name().toString();
            boolean prebuilt=settings.main<HousingFinishCatalog.legacyCount();
            boolean housing=texture.equals("vandorlabs:block/dark_wall_panel")||texture.equals("vandorlabs:housing/dark_wall_panel");
            boolean edge=texture.equals("vandorlabs:block/programmable_glass/metal_side");
            // Appended catalog models are already uniformly retextured in Forge; their former edge binding is dormant.
            int selected=!prebuilt?settings.main:porthole&&edge&&settings.side>=0?settings.side:housing?settings.main:-1;
            result.add(selected<0?source:NativeItemMaterials.retexture(source,material(selected)));
        }
        List<BakedQuad> immutable=List.copyOf(result);quads.put(key,immutable);return immutable;
    }
    @Override public List<BakedQuad> getQuads(BlockState state,Direction side,RandomSource random,ModelData data,RenderType layer){return getQuads(state,side,random);}
    @Override public ItemTransforms getTransforms(){if(transforms==null)transforms=NativeItemPadding.fit(this,originalModel.getTransforms());return transforms;}
    @Override public BakedModel applyTransform(net.minecraft.world.item.ItemDisplayContext context,com.mojang.blaze3d.vertex.PoseStack pose,boolean left){getTransforms().getTransform(context).apply(left,pose);return this;}
    @Override public List<BakedModel> getRenderPasses(net.minecraft.world.item.ItemStack stack,boolean fabulous){return List.of(this);}
    @Override public ItemOverrides getOverrides(){return new ItemOverrides(){@Override public BakedModel resolve(BakedModel original,net.minecraft.world.item.ItemStack stack,net.minecraft.client.multiplayer.ClientLevel level,net.minecraft.world.entity.LivingEntity entity,int seed){
        var data=stack.get(DataComponents.BLOCK_ENTITY_DATA);if(data==null)data=stack.get(DataComponents.CUSTOM_DATA);if(data==null)return WallItemModel.this;var tag=data.copyTag();if(tag.contains("BlockEntityTag",10))tag=tag.getCompound("BlockEntityTag");
        Settings selected=new Settings(ModernFinishCatalog.renderChoice(tag.getInt("housingTexture")),tag.contains("SideTexture",3)?tag.getInt("SideTexture"):-1);
        return resolved.computeIfAbsent(selected,value->new WallItemModel(originalModel,materials,porthole,value));
    }};}
}
